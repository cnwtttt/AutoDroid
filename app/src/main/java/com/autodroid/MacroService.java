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
    private AudioFocusRequest focus;
    private View overlay;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable timeout = () -> stopAlert();
    private boolean foreground;
    static final int[] STREAMS = {AudioManager.STREAM_ALARM, AudioManager.STREAM_MUSIC, AudioManager.STREAM_RING, AudioManager.STREAM_NOTIFICATION};
    @Override public void onCreate() {
        super.onCreate();
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("monitor", "后台监听", NotificationManager.IMPORTANCE_LOW));
        nm.createNotificationChannel(new NotificationChannel("alerts", "宏警告", NotificationManager.IMPORTANCE_HIGH));
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
    }
    @Override public int onStartCommand(Intent intent, int flags, int id) {
        String action = intent == null ? "" : intent.getAction();
        if ("STOP".equals(action)) {
            Store.prefs(this).edit().putBoolean("monitor", false).commit();
            stopAlert(); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); return START_NOT_STICKY;
        }
        if ("SILENCE".equals(action)) { stopAlert(); return START_STICKY; }
        promote(false);
        if ("RUN".equals(action)) {
            try { run(new JSONObject(intent.getStringExtra("rule")), intent.getStringExtra("source")); }
            catch (Exception e) { Store.log(this, "执行失败：" + e.getMessage()); stopAlert(); }
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
                        Store.log(this, "失去音频焦点，停止警告"); stopAlert();
                    }
                }).build();
            if (am.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) throw new IllegalStateException("系统未授予音频焦点");
            player = new MediaPlayer();
            player.setAudioAttributes(attrs);
            player.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK);
            String uri = rule.optString("sound", "");
            player.setDataSource(this, uri.isEmpty() ? Settings.System.DEFAULT_ALARM_ALERT_URI : Uri.parse(uri));
            player.setLooping(true);
            player.setOnErrorListener((mp, what, extra) -> { Store.log(this, "音源播放失败：" + what + "/" + extra); stopAlert(); return true; });
            player.setOnPreparedListener(mp -> { mp.start(); Store.log(this, "音源开始播放"); });
            player.prepareAsync();
        } catch (Exception e) { Store.log(this, "播放失败：" + e.getMessage()); releaseAudio(); promote(false); }
        handler.postDelayed(timeout, Math.max(5, Math.min(600, rule.optInt("duration", 60))) * 1000L);
    }
    private void showAlert(String title, String message) {
        Notification n = new Notification.Builder(this, "alerts").setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title).setContentText(message).setStyle(new Notification.BigTextStyle().bigText(message))
            .setContentIntent(PendingIntent.getActivity(this, 2, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE))
            .addAction(new Notification.Action.Builder(null, "确认并停止", command("SILENCE")).build()).build();
        getSystemService(NotificationManager.class).notify(2, n);
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
    private void stopAlert() {
        stopAlertResources(); getSystemService(NotificationManager.class).cancel(2);
        if (Store.prefs(this).getBoolean("monitor", false)) { if (foreground) promote(false); }
        else { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); }
    }
    @Override public void onDestroy() { stopAlertResources(); foreground = false; super.onDestroy(); }
    @Override public IBinder onBind(Intent intent) { return null; }
}
