package com.autodroid;

import android.app.Notification;
import android.app.Person;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import java.util.*;
import org.json.*;

/** Opt-in notification source; the SMS receiver remains separate. */
public class MessageNotificationService extends NotificationListenerService {
    static volatile boolean connected;
    private long connectedAt;
    @Override public void onListenerConnected() {
        connected = true; connectedAt = System.currentTimeMillis();
        Store.log(this, "NOTIFICATION_CONNECTED 不重放已有通知");
    }
    @Override public void onListenerDisconnected() {
        connected = false; Store.log(this, "NOTIFICATION_DISCONNECTED");
    }
    @Override public void onDestroy() { connected = false; super.onDestroy(); }
    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        // Inspect only Xiaomi's messaging app; ignore all other apps immediately.
        if (sbn == null || !"com.android.mms".equals(sbn.getPackageName())) return;
        if (!Store.prefs(this).getBoolean("monitor", false)) return;
        Store.log(this, "NOTIFICATION_RECEIVED package=com.android.mms");
        try { process(sbn); }
        catch (RuntimeException e) {
            android.util.Log.e("AutoDroid", "NOTIFICATION_ERROR", e);
            Store.log(this, "NOTIFICATION_ERROR " + e.getClass().getSimpleName());
        }
    }
    private static String value(CharSequence text) { return text == null ? "" : text.toString(); }
    private void process(StatusBarNotification sbn) {
        Notification n = sbn.getNotification();
        if ((n.flags & (Notification.FLAG_GROUP_SUMMARY | Notification.FLAG_ONGOING_EVENT)) != 0) {
            Store.log(this, "NOTIFICATION_SKIP 汇总或常驻通知"); return;
        }
        if (n.category != null && !Notification.CATEGORY_MESSAGE.equals(n.category)) {
            Store.log(this, "NOTIFICATION_SKIP 非消息类别"); return;
        }
        Bundle extras = n.extras;
        if (extras == null) { Store.log(this, "NOTIFICATION_SKIP 无通知内容"); return; }
        LinkedHashSet<String> senders = new LinkedHashSet<>();
        String body;
        long messageTime = n.when > 0 ? n.when : sbn.getPostTime();
        List<Notification.MessagingStyle.Message> messages = Collections.emptyList();
        if (Build.VERSION.SDK_INT >= 30) messages = Notification.MessagingStyle.Message.getMessagesFromBundleArray(extras.getParcelableArray(Notification.EXTRA_MESSAGES));
        if (!messages.isEmpty()) {
            // Never concatenate past messages: match only the newest message in the notification.
            Notification.MessagingStyle.Message latest = messages.get(messages.size() - 1);
            String sender = value(latest.getSender());
            if (Build.VERSION.SDK_INT >= 28) {
                Person person = latest.getSenderPerson();
                if (person != null) {
                    sender = value(person.getName());
                    String uri = person.getUri();
                    if (uri != null && uri.startsWith("tel:")) senders.add(android.net.Uri.decode(uri.substring(4)));
                }
            }
            if (sender.isEmpty() && senders.isEmpty()) { Store.log(this, "NOTIFICATION_SKIP 无明确发送人（可能是发出的消息）"); return; }
            senders.add(sender);
            body = value(latest.getText());
            if (latest.getTimestamp() > 0) messageTime = latest.getTimestamp();
        } else {
            senders.add(value(extras.getCharSequence(Notification.EXTRA_TITLE)));
            body = value(extras.getCharSequence(Notification.EXTRA_BIG_TEXT));
            if (body.isEmpty()) body = value(extras.getCharSequence(Notification.EXTRA_TEXT));
        }
        long cutoff = Math.max(connectedAt, Store.prefs(this).getLong("monitorSince", 0));
        if (sbn.getPostTime() < cutoff || messageTime < cutoff) {
            Store.log(this, "NOTIFICATION_SKIP 监听前的历史消息"); return;
        }
        if (body.isEmpty()) { Store.log(this, "NOTIFICATION_SKIP 正文为空或已隐藏"); return; }
        JSONArray rules = Store.rules(this);
        ArrayList<JSONObject> matched = new ArrayList<>();
        for (int i = 0; i < rules.length(); i++) {
            JSONObject rule = rules.optJSONObject(i);
            if (rule != null && Store.notificationMatches(rule, senders, body)) matched.add(rule);
        }
        Store.log(this, "NOTIFICATION_PARSED senderCandidates=" + senders.size() + " matchedRules=" + matched.size()
            + "（未匹配时请核对通知标题中的姓名/号码和关键词）");
        if (matched.isEmpty()) return;
        String event = sbn.getKey() + "\n" + messageTime + "\n" + senders + "\n" + body;
        if (Store.seenNotification(this, event)) { Store.log(this, "NOTIFICATION_SKIP 重复更新"); return; }
        boolean started = false;
        for (JSONObject rule : matched) {
            try {
                startForegroundService(new Intent(this, MacroService.class).setAction("RUN")
                    .putExtra("rule", rule.toString()).putExtra("source", "短信应用通知匹配").putExtra("eventChannel","notification").putExtra("eventBodyHash",Store.digest(body)));
                started = true;
            } catch (RuntimeException e) { Store.log(this, "NOTIFICATION_START_FAILED " + e.getMessage()); }
        }
        if (started) Store.acceptNotification(this, event);
        if (!started) Store.log(this, "NOTIFICATION_HINT 请打开应用重新开启监听并检查后台限制");
    }
}
