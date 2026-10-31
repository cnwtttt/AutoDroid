package com.autodroid;

import android.app.Notification;
import android.app.Person;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import org.json.*;

import java.util.*;

/** Opt-in notification source; the SMS receiver remains separate. */
public class MessageNotificationService extends NotificationListenerService {
    private static final boolean SOURCE_ENABLED = false;
    static volatile boolean connected;
    private long connectedAt;

    @Override
    public void onListenerConnected() {
        connected = true;
        connectedAt = System.currentTimeMillis();
        Store.log(this, getString(R.string.log_notification_connected));
    }

    @Override
    public void onListenerDisconnected() {
        connected = false;
        Store.log(this, "NOTIFICATION_DISCONNECTED");
    }

    @Override
    public void onDestroy() {
        connected = false;
        super.onDestroy();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (!SOURCE_ENABLED) return; // Ignore any callbacks already queued during an upgrade.
        // Inspect only Xiaomi's messaging app; ignore all other apps immediately.
        if (sbn == null || !"com.android.mms".equals(sbn.getPackageName())) return;
        if (!Store.prefs(this).getBoolean("monitor", false)) return;
        Store.log(this, "NOTIFICATION_RECEIVED package=com.android.mms");
        try {
            NotificationDiagnostics.dump(this, sbn);
            process(sbn);
        } catch (RuntimeException e) {
            android.util.Log.e("AutoDroid", "NOTIFICATION_ERROR", e);
            Store.log(this, "NOTIFICATION_ERROR " + e.getClass().getSimpleName());
        }
    }

    private static String value(CharSequence text) {
        return text == null ? "" : text.toString();
    }

    private void process(StatusBarNotification sbn) {
        Notification n = sbn.getNotification();
        if ((n.flags & Notification.FLAG_FOREGROUND_SERVICE) != 0
                || "Channel_Foreground_Service".equals(n.getChannelId())) {
            Store.log(
                    this,
                    getString(R.string.log_notification_skip)
                            + n.flags
                            + " channel="
                            + n.getChannelId());
            return;
        }
        if ((n.flags & (Notification.FLAG_GROUP_SUMMARY | Notification.FLAG_ONGOING_EVENT)) != 0) {
            Store.log(this, getString(R.string.log_notification_skip_detail));
            return;
        }
        if (n.category != null && !Notification.CATEGORY_MESSAGE.equals(n.category)) {
            Store.log(this, getString(R.string.log_notification_skip_detail_detail));
            return;
        }
        Bundle extras = n.extras;
        if (extras == null) {
            Store.log(this, getString(R.string.log_notification_skip_detail_detail_detail));
            return;
        }
        LinkedHashSet<String> senders = new LinkedHashSet<>();
        String body;
        long messageTime = n.when > 0 ? n.when : sbn.getPostTime();
        String extraction;
        List<Notification.MessagingStyle.Message> messages = Collections.emptyList();
        if (Build.VERSION.SDK_INT >= 30)
            messages =
                    Notification.MessagingStyle.Message.getMessagesFromBundleArray(
                            extras.getParcelableArray(Notification.EXTRA_MESSAGES));
        if (!messages.isEmpty()) {
            extraction = getString(R.string.log_messagenotificationservice_5);
            // Never concatenate past messages: match only the newest message in the notification.
            Notification.MessagingStyle.Message latest = messages.get(messages.size() - 1);
            String sender = value(latest.getSender());
            if (Build.VERSION.SDK_INT >= 28) {
                Person person = latest.getSenderPerson();
                if (person != null) {
                    sender = value(person.getName());
                    String uri = person.getUri();
                    if (uri != null && uri.startsWith("tel:"))
                        senders.add(android.net.Uri.decode(uri.substring(4)));
                }
            }
            if (sender.isEmpty() && senders.isEmpty()) {
                Store.log(
                        this,
                        getString(R.string.log_notification_skip_detail_detail_detail_detail));
                return;
            }
            senders.add(sender);
            body = value(latest.getText());
            if (latest.getTimestamp() > 0) messageTime = latest.getTimestamp();
        } else {
            extraction = getString(R.string.log_messagenotificationservice_7);
            senders.add(value(extras.getCharSequence(Notification.EXTRA_TITLE)));
            body = value(extras.getCharSequence(Notification.EXTRA_BIG_TEXT));
            if (body.isEmpty()) body = value(extras.getCharSequence(Notification.EXTRA_TEXT));
        }
        long cutoff = Math.max(connectedAt, Store.prefs(this).getLong("monitorSince", 0));
        if (sbn.getPostTime() < cutoff || messageTime < cutoff) {
            Store.log(
                    this,
                    getString(R.string.log_notification_skip_detail_detail_detail_detail_detail));
            return;
        }
        if (body.isEmpty()) {
            Store.log(
                    this,
                    getString(
                            R.string
                                    .log_notification_skip_detail_detail_detail_detail_detail_detail));
            return;
        }
        JSONArray rules = Store.rules(this);
        Store.log(
                this,
                "NOTIFICATION_FIELDS senderFrom="
                        + extraction
                        + " bodyLength="
                        + body.length()
                        + " rules="
                        + rules.length());
        boolean detailed = Store.prefs(this).getBoolean("senderDiagnostics", false);
        if (detailed) Store.log(this, getString(R.string.log_notification_senders) + senders);
        ArrayList<JSONObject> matched = new ArrayList<>();
        for (int i = 0; i < rules.length(); i++) {
            JSONObject rule = rules.optJSONObject(i);
            if (rule == null) {
                Store.log(
                        this,
                        "NOTIFICATION_RULE index="
                                + i
                                + getString(R.string.log_messagenotificationservice_11));
                continue;
            }
            String result = Store.notificationResult(rule, senders, body);
            Store.log(
                    this,
                    "NOTIFICATION_RULE index="
                            + i
                            + " result="
                            + result
                            + " configuredNumbers="
                            + Store.numbers(rule).size()
                            + " configuredNames="
                            + Store.parseNames(rule.optString("senderNames")).size()
                            + " keywordLength="
                            + rule.optString("keyword").length());
            if (detailed)
                Store.log(
                        this,
                        "NOTIFICATION_CONFIG index="
                                + i
                                + getString(R.string.log_messagenotificationservice_12)
                                + Store.numbers(rule)
                                + getString(R.string.log_messagenotificationservice_13)
                                + Store.parseNames(rule.optString("senderNames")));
            if ("MATCH".equals(result)) matched.add(rule);
        }
        Store.log(
                this,
                "NOTIFICATION_PARSED senderCandidates="
                        + senders.size()
                        + " matchedRules="
                        + matched.size()
                        + (matched.isEmpty()
                                ? getString(R.string.log_messagenotificationservice_14)
                                : getString(R.string.log_messagenotificationservice_15)));
        if (matched.isEmpty()) return;
        String event = sbn.getKey() + "\n" + messageTime + "\n" + senders + "\n" + body;
        if (Store.seenNotification(this, event)) {
            Store.log(
                    this,
                    getString(
                            R.string
                                    .log_notification_skip_detail_detail_detail_detail_detail_detail_detail));
            return;
        }
        boolean started = false;
        for (JSONObject rule : matched) {
            try {
                startForegroundService(
                        new Intent(this, MacroService.class)
                                .setAction("RUN")
                                .putExtra("rule", rule.toString())
                                .putExtra(
                                        "source",
                                        getString(R.string.log_messagenotificationservice_17))
                                .putExtra("eventChannel", "notification")
                                .putExtra("eventBodyHash", Store.digest(body)));
                started = true;
            } catch (RuntimeException e) {
                Store.log(this, "NOTIFICATION_START_FAILED " + e.getMessage());
            }
        }
        if (started) Store.acceptNotification(this, event);
        if (!started) Store.log(this, getString(R.string.log_notification_hint));
    }
}
