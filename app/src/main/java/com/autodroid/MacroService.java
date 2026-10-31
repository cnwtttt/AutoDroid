package com.autodroid;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.media.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import org.json.*;

public class MacroService extends Service {
    private MediaPlayer player;
    private InboxMonitor inboxMonitor;
    private AudioFocusRequest focus;
    private View overlay;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable timeout = () -> stopAlert(false);
    private boolean foreground;
    static volatile boolean isRunning;
    static final int[] STREAMS = {AudioManager.STREAM_ALARM, AudioManager.STREAM_MUSIC, AudioManager.STREAM_RING, AudioManager.STREAM_NOTIFICATION};
    @Override public void onCreate() {
        super.onCreate();
        Store.log(this, "SERVICE_CREATE");
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("monitor", "后台监听", NotificationManager.IMPORTANCE_LOW));
        ensureAlertChannel(this);
    }
    private void ensureInboxMonitor() {
        if (!Store.prefs(this).getBoolean("monitor",false)) return;
        if (checkSelfPermission(android.Manifest.permission.READ_SMS)!=android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Store.log(this,"INBOX_READ_BLOCKED 请授予读取短信权限以启用数据库自动触发"); return;
        }
        if (inboxMonitor==null) {
            if (!Store.prefs(this).getBoolean("providerBaselineInitialized",false)) {
                if (!Store.prefs(this).edit().putLong("monitorSince",System.currentTimeMillis())
                        .putBoolean("providerBaselineInitialized",true).commit()) {
                    Store.log(this,"INBOX_BASELINE_FAILED 无法保存监听起点"); return;
                }
                Store.log(this,"INBOX_BASELINE_INITIALIZED 首次启用数据库触发，从现在开始监听，忽略升级前消息");
            }
            inboxMonitor=new InboxMonitor(this,(rule,id) -> {
                try { run(rule,"收件箱匹配 ID="+id); }
                catch (RuntimeException e) { Store.log(this,"INBOX_EXECUTION_FAILED id="+id+" "+e.getClass().getSimpleName()); stopAlert(false); }
            });
            inboxMonitor.start();
        }
        inboxMonitor.requestScan();
    }
    static void ensureAlertChannel(Context context) {
        NotificationChannel channel = new NotificationChannel("alerts", "重要宏警告", NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("宏执行时的重要警告通知；可在此设置横幅、声音和锁屏显示");
        context.getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }
    private PendingIntent command(String action) {
        return PendingIntent.getService(this, action.hashCode(), new Intent(this, MacroService.class).setAction(action), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    private Notification notification(String text) {
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, "monitor").setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("AutoDroid").setContentText(text).setContentIntent(open).setOngoing(true)
            .addAction(new Notification.Action.Builder(null, "停止声音", command("SILENCE")).build())
            .addAction(new Notification.Action.Builder(null, "关闭监听", command("STOP")).build()).build();
    }
    private void promote(boolean playing) {
        Notification n = notification(playing ? "警告播放中，点击可返回应用" : "短信宏监听已开启");
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE | (playing ? ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK : 0));
        else startForeground(1, n);
        foreground = true;
        isRunning = true;
        Store.log(this, "SERVICE_FOREGROUND playing=" + playing);
    }
    @Override public int onStartCommand(Intent intent, int flags, int id) {
        String action = intent == null ? "" : intent.getAction();
        Store.log(this, "SERVICE_COMMAND action=" + action + " monitor=" + Store.prefs(this).getBoolean("monitor",false));
        if ("STOP".equals(action)) {
            Store.prefs(this).edit().putBoolean("monitor", false).commit();
            if (inboxMonitor!=null) { inboxMonitor.close(); inboxMonitor=null; }
            stopAlert(); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); return START_NOT_STICKY;
        }
        if ("SILENCE".equals(action)) {
            stopAlert();
            if (Store.prefs(this).getBoolean("monitor",false)) { promote(false); ensureInboxMonitor(); return START_STICKY; }
            return START_NOT_STICKY;
        }
        promote(player != null);
        ensureInboxMonitor();
        if ("RUN".equals(action)) {
            try {
                JSONObject rule = new JSONObject(intent.getStringExtra("rule"));
                if (Store.allowCombinedRun(this,rule,intent.getStringExtra("eventBodyHash"),intent.getStringExtra("eventChannel"))) {
                    run(rule,intent.getStringExtra("source"));
                } else { Store.log(this,"COMBINED_DUPLICATE 同一宏10秒内相同内容的另一来源已执行，跳过本次重复"); }
            }
            catch (Exception e) { Store.log(this, "执行失败：" + e.getMessage()); stopAlert(false); }
        } else if (!Store.prefs(this).getBoolean("monitor", false)) { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); }
        return Store.prefs(this).getBoolean("monitor", false) ? START_STICKY : START_NOT_STICKY;
    }
    private void run(JSONObject rule, String source) {
        stopAlertResources();
        String title = rule.optString("name", "短信警告");
        Store.log(this, source + " · " + title + "（新警告替换上一条播放）");
        AudioManager am = getSystemService(AudioManager.class);
        int channel = Math.max(0, Math.min(3, rule.optInt("channel", 0)));
        int stream = STREAMS[channel];
        int percent = Math.max(0, Math.min(100, rule.optInt("volume", 80)));
        try {
            am.setStreamVolume(stream, Math.round(am.getStreamMaxVolume(stream) * percent / 100f), 0);
            Store.log(this, "音量设置：" + percent + "%，实际档位 " + am.getStreamVolume(stream));
        } catch (RuntimeException e) { Store.log(this, "音量设置失败（检查勿扰权限）：" + e.getMessage()); }
        showAlert(title, rule.optString("message", "收到匹配短信，请及时处理"));
        promote(true);
        try {
            AudioAttributes attrs = new AudioAttributes.Builder().setLegacyStreamType(stream).build();
            focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(attrs).setOnAudioFocusChangeListener(change -> {
                    if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                        Store.log(this, "失去音频焦点，停止声音并保留通知"); stopAlert(false);
                    }
                }).build();
            if (am.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) throw new IllegalStateException("系统未授予音频焦点");
            player = new MediaPlayer();
            player.setAudioAttributes(attrs);
            player.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK);
            String uri = rule.optString("sound", "");
            Uri soundUri=uri.isEmpty() ? Settings.System.DEFAULT_ALARM_ALERT_URI : Uri.parse(uri);
            String scheme=soundUri.getScheme();
            if (!("content".equals(scheme) || "android.resource".equals(scheme) || "file".equals(scheme)))
                throw new IllegalArgumentException("仅允许本地音源，不支持网络地址");
            player.setDataSource(this,soundUri);
            player.setLooping(true);
            player.setOnErrorListener((mp, what, extra) -> { Store.log(this, "音源播放失败：" + what + "/" + extra); stopAlert(false); return true; });
            player.setOnPreparedListener(mp -> { mp.start(); Store.log(this, "音源开始播放"); });
            player.prepareAsync();
        } catch (Exception e) { Store.log(this, "播放失败：" + e.getMessage()); releaseAudio(); promote(false); }
        handler.postDelayed(timeout, Math.max(5, Math.min(600, rule.optInt("duration", 60))) * 1000L);
    }
    private void showAlert(String title, String message) {
        Notification n = new Notification.Builder(this, "alerts").setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setCategory(Notification.CATEGORY_ALARM).setVisibility(Notification.VISIBILITY_PRIVATE)
            .setWhen(System.currentTimeMillis()).setShowWhen(true).setOnlyAlertOnce(false)
            .setContentTitle("重要警告 · " + title).setContentText(message).setStyle(new Notification.BigTextStyle().bigText(message))
            .setContentIntent(PendingIntent.getActivity(this, 2, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE))
            .addAction(new Notification.Action.Builder(null, "确认并停止", command("SILENCE")).build()).build();
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel channel = nm.getNotificationChannel("alerts");
        if (!nm.areNotificationsEnabled() || (channel != null && channel.getImportance() == NotificationManager.IMPORTANCE_NONE)) {
            Store.log(this,"IMPORTANT_NOTIFY_BLOCKED 请授予通知权限，并在重要警告通知设置中开启该通道");
        } else {
            try {
                nm.notify(2,n);
                Store.log(this,"IMPORTANT_NOTIFY_POSTED id=2 importance=" + (channel == null ? -1 : channel.getImportance()));
                if (channel != null && channel.getImportance() < NotificationManager.IMPORTANCE_HIGH)
                    Store.log(this,"IMPORTANT_NOTIFY_HINT 通道已被设为较低重要性，请在系统设置中允许横幅提醒");
            } catch (RuntimeException e) { Store.log(this,"IMPORTANT_NOTIFY_FAILED " + e.getMessage()); }
        }
        if (!Settings.canDrawOverlays(this)) { Store.log(this, "未授予悬浮窗权限，仅发送警告通知"); return; }
        LinearLayout box = new LinearLayout(this); box.setOrientation(1); box.setPadding(40, 36, 40, 24); box.setBackgroundColor(0xFFfff4e5);
        TextView heading = new TextView(this); heading.setText("⚠ " + title); heading.setTextSize(23); heading.setTextColor(0xFF9A3412); box.addView(heading);
        TextView body = new TextView(this); body.setText(message); body.setTextSize(18); body.setTextColor(0xFF202020); body.setPadding(0,24,0,24); box.addView(body);
        Button close = new Button(this); close.setText("确认 · 停止声音"); close.setOnClickListener(v -> stopAlert()); box.addView(close);
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON, PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.CENTER;
        try { getSystemService(WindowManager.class).addView(box, p); overlay = box; }
        catch (RuntimeException e) { Store.log(this, "弹窗失败：" + e.getMessage()); }
    }
    private void releaseAudio() {
        if (player != null) { player.release(); player = null; }
        if (focus != null) { getSystemService(AudioManager.class).abandonAudioFocusRequest(focus); focus = null; }
    }
    private void stopAlertResources() {
        handler.removeCallbacks(timeout);
        releaseAudio();
        if (overlay != null) { getSystemService(WindowManager.class).removeView(overlay); overlay = null; }
    }
    private void stopAlert() { stopAlert(true); }
    private void stopAlert(boolean dismissNotice) {
        stopAlertResources();
        if (dismissNotice) getSystemService(NotificationManager.class).cancel(2);
        else Store.log(this,"IMPORTANT_NOTIFY_RETAINED 声音已停止，通知保留至确认或手动划掉");
        if (Store.prefs(this).getBoolean("monitor", false)) { if (foreground) promote(false); }
        else { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); }
    }
    @Override public void onDestroy() { if (inboxMonitor!=null) { inboxMonitor.close(); inboxMonitor=null; } isRunning = false; Store.log(this, "SERVICE_DESTROY"); stopAlertResources(); foreground = false; super.onDestroy(); }
    @Override public IBinder onBind(Intent intent) { return null; }
}
