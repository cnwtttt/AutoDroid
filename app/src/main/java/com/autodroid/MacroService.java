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
    static final int[] STREAMS = {
        AudioManager.STREAM_ALARM,
        AudioManager.STREAM_MUSIC,
        AudioManager.STREAM_RING,
        AudioManager.STREAM_NOTIFICATION
    };

    @Override
    public void onCreate() {
        super.onCreate();
        Store.log(this, "SERVICE_CREATE");
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(
                new NotificationChannel(
                        "monitor",
                        getString(R.string.monitor_channel_name),
                        NotificationManager.IMPORTANCE_LOW));
        ensureAlertChannel(this);
    }

    private void ensureInboxMonitor() {
        if (!Store.prefs(this).getBoolean("monitor", false)) return;
        if (checkSelfPermission(android.Manifest.permission.READ_SMS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Store.log(this, getString(R.string.log_inbox_read_blocked));
            return;
        }
        if (inboxMonitor == null) {
            if (!Store.prefs(this).getBoolean("providerBaselineInitialized", false)) {
                if (!Store.prefs(this)
                        .edit()
                        .putLong("monitorSince", System.currentTimeMillis())
                        .putBoolean("providerBaselineInitialized", true)
                        .commit()) {
                    Store.log(this, getString(R.string.log_inbox_baseline_failed));
                    return;
                }
                Store.log(this, getString(R.string.log_inbox_baseline_initialized));
            }
            inboxMonitor =
                    new InboxMonitor(
                            this,
                            (rule, id) -> {
                                try {
                                    run(rule, getString(R.string.inbox_match_source, id));
                                } catch (RuntimeException e) {
                                    Store.log(
                                            this,
                                            "INBOX_EXECUTION_FAILED id="
                                                    + id
                                                    + " "
                                                    + e.getClass().getSimpleName());
                                    stopAlert(false);
                                }
                            });
            inboxMonitor.start();
        }
        inboxMonitor.requestScan();
    }

    static void ensureAlertChannel(Context context) {
        NotificationChannel channel =
                new NotificationChannel(
                        "alerts",
                        context.getString(R.string.alert_channel_name),
                        NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription(context.getString(R.string.alert_channel_description));
        context.getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    private PendingIntent command(String action) {
        return PendingIntent.getService(
                this,
                action.hashCode(),
                new Intent(this, MacroService.class).setAction(action),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private Notification notification(String text) {
        PendingIntent open =
                PendingIntent.getActivity(
                        this,
                        0,
                        new Intent(this, MainActivity.class),
                        PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, "monitor")
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(text)
                .setContentIntent(open)
                .setOngoing(true)
                .addAction(
                        new Notification.Action.Builder(
                                        null, getString(R.string.stop_audio), command("SILENCE"))
                                .build())
                .addAction(
                        new Notification.Action.Builder(
                                        null, getString(R.string.stop_monitor), command("STOP"))
                                .build())
                .build();
    }

    private void promote(boolean playing) {
        Notification n =
                notification(
                        playing
                                ? getString(R.string.monitor_playing_notification)
                                : getString(R.string.monitor_running_notification));
        if (Build.VERSION.SDK_INT >= 34)
            startForeground(
                    1,
                    n,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                            | (playing ? ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK : 0));
        else startForeground(1, n);
        foreground = true;
        isRunning = true;
        Store.log(this, "SERVICE_FOREGROUND playing=" + playing);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int id) {
        String action = intent == null ? "" : intent.getAction();
        Store.log(
                this,
                "SERVICE_COMMAND action="
                        + action
                        + " monitor="
                        + Store.prefs(this).getBoolean("monitor", false));
        if ("STOP".equals(action)) {
            Store.prefs(this).edit().putBoolean("monitor", false).commit();
            if (inboxMonitor != null) {
                inboxMonitor.close();
                inboxMonitor = null;
            }
            stopAlert();
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            return START_NOT_STICKY;
        }
        if ("SILENCE".equals(action)) {
            stopAlert();
            if (Store.prefs(this).getBoolean("monitor", false)) {
                promote(false);
                ensureInboxMonitor();
                return START_STICKY;
            }
            return START_NOT_STICKY;
        }
        promote(player != null);
        ensureInboxMonitor();
        if ("RUN".equals(action)) {
            try {
                JSONObject rule = new JSONObject(intent.getStringExtra("rule"));
                if (Store.allowCombinedRun(
                        this,
                        rule,
                        intent.getStringExtra("eventBodyHash"),
                        intent.getStringExtra("eventChannel"))) {
                    run(rule, intent.getStringExtra("source"));
                } else {
                    Store.log(this, getString(R.string.log_combined_duplicate));
                }
            } catch (Exception e) {
                Store.log(this, getString(R.string.log_execution_failed) + e.getMessage());
                stopAlert(false);
            }
        } else if (!Store.prefs(this).getBoolean("monitor", false)) {
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
        }
        return Store.prefs(this).getBoolean("monitor", false) ? START_STICKY : START_NOT_STICKY;
    }

    private void run(JSONObject rule, String source) {
        stopAlertResources();
        String title = rule.optString("name", getString(R.string.default_macro_name));
        Store.log(this, source + " · " + title + getString(R.string.log_replacing_alert));
        AudioManager am = getSystemService(AudioManager.class);
        int channel = Math.max(0, Math.min(3, rule.optInt("channel", 0)));
        int stream = STREAMS[channel];
        int percent = Math.max(0, Math.min(100, rule.optInt("volume", 80)));
        try {
            am.setStreamVolume(
                    stream, Math.round(am.getStreamMaxVolume(stream) * percent / 100f), 0);
            Store.log(
                    this, getString(R.string.log_volume_set, percent, am.getStreamVolume(stream)));
        } catch (RuntimeException e) {
            Store.log(this, getString(R.string.log_volume_failed) + e.getMessage());
        }
        showAlert(title, rule.optString("message", getString(R.string.default_alert_message)));
        promote(true);
        try {
            AudioAttributes attrs =
                    new AudioAttributes.Builder().setLegacyStreamType(stream).build();
            focus =
                    new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                            .setAudioAttributes(attrs)
                            .setOnAudioFocusChangeListener(
                                    change -> {
                                        if (change == AudioManager.AUDIOFOCUS_LOSS
                                                || change
                                                        == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                                            Store.log(
                                                    this, getString(R.string.log_audio_focus_lost));
                                            stopAlert(false);
                                        }
                                    })
                            .build();
            if (am.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
                throw new IllegalStateException(getString(R.string.audio_focus_denied));
            player = new MediaPlayer();
            player.setAudioAttributes(attrs);
            player.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK);
            String uri = rule.optString("sound", "");
            Uri soundUri = uri.isEmpty() ? Settings.System.DEFAULT_ALARM_ALERT_URI : Uri.parse(uri);
            String scheme = soundUri.getScheme();
            if (!("content".equals(scheme)
                    || "android.resource".equals(scheme)
                    || "file".equals(scheme)))
                throw new IllegalArgumentException(getString(R.string.local_audio_only));
            player.setDataSource(this, soundUri);
            player.setLooping(true);
            player.setOnErrorListener(
                    (mp, what, extra) -> {
                        Store.log(this, getString(R.string.log_audio_error) + what + "/" + extra);
                        stopAlert(false);
                        return true;
                    });
            player.setOnPreparedListener(
                    mp -> {
                        mp.start();
                        Store.log(this, getString(R.string.log_audio_started));
                    });
            player.prepareAsync();
        } catch (Exception e) {
            Store.log(this, getString(R.string.log_playback_failed) + e.getMessage());
            releaseAudio();
            promote(false);
        }
        handler.postDelayed(
                timeout, Math.max(5, Math.min(600, rule.optInt("duration", 60))) * 1000L);
    }

    private void showAlert(String title, String message) {
        Notification n =
                new Notification.Builder(this, "alerts")
                        .setSmallIcon(android.R.drawable.ic_dialog_alert)
                        .setCategory(Notification.CATEGORY_ALARM)
                        .setVisibility(Notification.VISIBILITY_PRIVATE)
                        .setWhen(System.currentTimeMillis())
                        .setShowWhen(true)
                        .setOnlyAlertOnce(false)
                        .setContentTitle(getString(R.string.important_alert_title, title))
                        .setContentText(message)
                        .setStyle(new Notification.BigTextStyle().bigText(message))
                        .setContentIntent(
                                PendingIntent.getActivity(
                                        this,
                                        2,
                                        new Intent(this, MainActivity.class),
                                        PendingIntent.FLAG_IMMUTABLE))
                        .addAction(
                                new Notification.Action.Builder(
                                                null,
                                                getString(R.string.confirm_and_stop),
                                                command("SILENCE"))
                                        .build())
                        .build();
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel channel = nm.getNotificationChannel("alerts");
        if (!nm.areNotificationsEnabled()
                || (channel != null
                        && channel.getImportance() == NotificationManager.IMPORTANCE_NONE)) {
            Store.log(this, getString(R.string.log_important_notify_blocked));
        } else {
            try {
                nm.notify(2, n);
                Store.log(
                        this,
                        "IMPORTANT_NOTIFY_POSTED id=2 importance="
                                + (channel == null ? -1 : channel.getImportance()));
                if (channel != null
                        && channel.getImportance() < NotificationManager.IMPORTANCE_HIGH)
                    Store.log(this, getString(R.string.log_important_notify_hint));
            } catch (RuntimeException e) {
                Store.log(this, "IMPORTANT_NOTIFY_FAILED " + e.getMessage());
            }
        }
        if (!Settings.canDrawOverlays(this)) {
            Store.log(this, getString(R.string.log_overlay_permission_missing));
            return;
        }
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(1);
        box.setPadding(40, 36, 40, 24);
        box.setBackgroundColor(0xFFfff4e5);
        TextView heading = new TextView(this);
        heading.setText(getString(R.string.overlay_alert_title, title));
        heading.setTextSize(23);
        heading.setTextColor(0xFF9A3412);
        box.addView(heading);
        TextView body = new TextView(this);
        body.setText(message);
        body.setTextSize(18);
        body.setTextColor(0xFF202020);
        body.setPadding(0, 24, 0, 24);
        box.addView(body);
        Button close = new Button(this);
        close.setText(getString(R.string.confirm_stop_audio));
        close.setOnClickListener(v -> stopAlert());
        box.addView(close);
        WindowManager.LayoutParams p =
                new WindowManager.LayoutParams(
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                        PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.CENTER;
        try {
            getSystemService(WindowManager.class).addView(box, p);
            overlay = box;
        } catch (RuntimeException e) {
            Store.log(this, getString(R.string.log_overlay_failed) + e.getMessage());
        }
    }

    private void releaseAudio() {
        if (player != null) {
            player.release();
            player = null;
        }
        if (focus != null) {
            getSystemService(AudioManager.class).abandonAudioFocusRequest(focus);
            focus = null;
        }
    }

    private void stopAlertResources() {
        handler.removeCallbacks(timeout);
        releaseAudio();
        if (overlay != null) {
            getSystemService(WindowManager.class).removeView(overlay);
            overlay = null;
        }
    }

    private void stopAlert() {
        stopAlert(true);
    }

    private void stopAlert(boolean dismissNotice) {
        stopAlertResources();
        if (dismissNotice) getSystemService(NotificationManager.class).cancel(2);
        else Store.log(this, getString(R.string.log_important_notify_retained));
        if (Store.prefs(this).getBoolean("monitor", false)) {
            if (foreground) promote(false);
        } else {
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
        }
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Store.log(
                this,
                "SERVICE_TASK_REMOVED monitor=" + Store.prefs(this).getBoolean("monitor", false));
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        if (inboxMonitor != null) {
            inboxMonitor.close();
            inboxMonitor = null;
        }
        isRunning = false;
        Store.log(this, "SERVICE_DESTROY");
        stopAlertResources();
        foreground = false;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
