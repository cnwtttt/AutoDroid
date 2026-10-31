package com.autodroid;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.*;
import android.net.Uri;
import android.os.*;
import android.provider.Telephony;

import org.json.*;

/** Single worker serializes queries; macro execution is handed back to the service main thread. */
final class InboxMonitor implements AutoCloseable {
    private static final long FALLBACK_SCAN_INTERVAL_MS = 5 * 60 * 1000L;

    interface Listener {
        void matched(JSONObject rule, long id);
    }

    private final Context context;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final HandlerThread thread = new HandlerThread("inbox-monitor");
    private final Handler worker;
    private final long since;
    private final ContentObserver observer;
    private volatile boolean closed;
    private boolean scheduled;
    private boolean registered;
    private boolean ready;
    private InboxCheckpoint checkpoint;

    InboxMonitor(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        since = Store.prefs(context).getLong("monitorSince", System.currentTimeMillis());
        thread.start();
        worker = new Handler(thread.getLooper());
        observer =
                new ContentObserver(worker) {
                    @Override
                    public void onChange(boolean selfChange) {
                        changed();
                    }

                    @Override
                    public void onChange(boolean selfChange, Uri uri) {
                        changed();
                    }
                };
    }

    void start() {
        worker.post(
                () -> {
                    initialize();
                    if (active()) worker.postDelayed(this::fallbackTick, FALLBACK_SCAN_INTERVAL_MS);
                });
    }

    private void initialize() {
        if (!active() || registered) return;

        try {
            context.getContentResolver()
                    .registerContentObserver(Telephony.Sms.CONTENT_URI, true, observer);
            registered = true;
            context.getContentResolver()
                    .registerContentObserver(Uri.parse("content://mms-sms"), true, observer);
            long last =
                    Store.prefs(context).getLong("inboxSince", -1) == since
                            ? Store.prefs(context).getLong("inboxLastId", 0)
                            : 0;
            checkpoint = new InboxCheckpoint(since, last);
            Store.log(
                    context,
                    "INBOX_OBSERVER_REGISTERED since="
                            + since
                            + " lastId="
                            + last
                            + context.getString(R.string.log_inbox_since_hint));
            schedule();
        } catch (RuntimeException e) {
            Store.log(
                    context,
                    "INBOX_OBSERVER_FAILED "
                            + e.getClass().getSimpleName()
                            + context.getString(R.string.log_read_sms_permission_hint));
            if (registered) {
                context.getContentResolver().unregisterContentObserver(observer);
                registered = false;
            }
        }
    }

    private String accessState() {
        android.app.ActivityManager.RunningAppProcessInfo info =
                new android.app.ActivityManager.RunningAppProcessInfo();
        android.app.ActivityManager.getMyMemoryState(info);
        String op;
        try {
            op =
                    Integer.toString(
                            context.getSystemService(android.app.AppOpsManager.class)
                                    .checkOpNoThrow(
                                            android.app.AppOpsManager.OPSTR_READ_SMS,
                                            android.os.Process.myUid(),
                                            context.getPackageName()));
        } catch (RuntimeException e) {
            op = e.getClass().getSimpleName();
        }
        return " processImportance="
                + info.importance
                + " readSms="
                + (context.checkSelfPermission(Manifest.permission.READ_SMS)
                        == PackageManager.PERMISSION_GRANTED)
                + " appOpCheck="
                + op
                + " batteryExempt="
                + context.getSystemService(PowerManager.class)
                        .isIgnoringBatteryOptimizations(context.getPackageName());
    }

    // Observer callbacks are the primary trigger; this delayed scan only covers missed events.
    private void fallbackTick() {
        if (!active()) return;
        if (!registered) initialize();
        schedule();
        worker.postDelayed(this::fallbackTick, FALLBACK_SCAN_INTERVAL_MS);
    }

    void requestScan() {
        worker.post(this::schedule);
    }

    private void changed() {
        if (!active()) return;
        Store.log(context, context.getString(R.string.log_inbox_observer_change));
        schedule();
    }

    private boolean active() {
        return !closed
                && Store.prefs(context).getBoolean("monitor", false)
                && Store.prefs(context).getLong("monitorSince", since) == since;
    }

    private void schedule() {
        if (!active() || checkpoint == null || scheduled) return;
        scheduled = true;
        worker.postDelayed(this::scan, 200);
    }

    private void scan() {
        scheduled = false;
        if (!active()) return;

        if (context.checkSelfPermission(Manifest.permission.READ_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            Store.log(context, context.getString(R.string.log_inbox_read_blocked_detail));
            return;
        }
        int processed = 0;
        try (Cursor cursor =
                context.getContentResolver()
                        .query(
                                Telephony.Sms.Inbox.CONTENT_URI,
                                new String[] {"_id", "address", "body", "date", "type"},
                                "_id > ? AND date >= ?",
                                new String[] {
                                    Long.toString(checkpoint.lastId()), Long.toString(since)
                                },
                                "_id ASC")) {
            if (cursor == null) throw new IllegalStateException("null cursor");

            if (!ready) {
                ready = true;
                Store.log(context, context.getString(R.string.log_inbox_observer_ready));
            }
            while (processed < 100 && active() && cursor.moveToNext()) {
                long id = cursor.getLong(0), date = cursor.getLong(3);
                long previous = checkpoint.lastId();
                if (!checkpoint.accept(id, date)) continue;
                processed++;
                // Persist progress before scheduling actions: a crash must not replay completed
                // rows.
                if (!active()) return;

                if (!Store.prefs(context)
                        .edit()
                        .putLong("inboxSince", since)
                        .putLong("inboxLastId", checkpoint.lastId())
                        .commit()) {
                    checkpoint = new InboxCheckpoint(since, previous);
                    throw new IllegalStateException("checkpoint save failed");
                }
                String sender = cursor.getString(1), body = cursor.getString(2);

                if (sender == null || body == null) {
                    Store.log(
                            context,
                            "INBOX_SKIP id="
                                    + id
                                    + context.getString(R.string.log_inbox_empty_fields));
                    continue;
                }
                JSONArray rules = Store.rules(context);
                int matched = 0;
                for (int i = 0; i < rules.length(); i++) {
                    JSONObject rule = rules.optJSONObject(i);
                    if (rule != null && Store.matches(rule, sender, body)) {
                        matched++;
                        main.post(
                                () -> {
                                    if (active()) listener.matched(rule, id);
                                });
                    }
                }
                Store.log(
                        context,
                        "INBOX_MESSAGE id="
                                + id
                                + " type="
                                + cursor.getInt(4)
                                + " sender="
                                + Store.maskedSender(sender)
                                + " matchedRules="
                                + matched);
            }
        } catch (RuntimeException e) {

            Store.log(context, "INBOX_SCAN_FAILED " + e.getClass().getSimpleName() + accessState());
            return;
        }

        Store.log(
                context,
                "INBOX_SCAN_RESULT rows="
                        + processed
                        + " lastId="
                        + checkpoint.lastId()
                        + accessState());

        if (processed == 100) schedule();
    }

    @Override
    public void close() {
        closed = true;
        Store.log(context, context.getString(R.string.log_inbox_observer_closing));
        // Registration and unregistration run on the same worker to avoid a stop/start race.
        worker.post(
                () -> {
                    if (registered) {
                        try {
                            context.getContentResolver().unregisterContentObserver(observer);
                        } catch (RuntimeException e) {
                            Store.log(
                                    context,
                                    "INBOX_UNREGISTER_FAILED " + e.getClass().getSimpleName());
                        }
                        registered = false;
                    }
                    worker.removeCallbacksAndMessages(null);
                    thread.quitSafely();
                });
    }
}
