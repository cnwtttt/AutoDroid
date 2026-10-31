package com.autodroid;

import android.content.*;
import android.provider.Telephony;
import android.telephony.SmsMessage;

import org.json.*;

public class SmsReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        boolean monitor = Store.prefs(c).getBoolean("monitor", false);
        Store.log(
                c,
                "SMS_RECEIVER_ENTER action="
                        + action
                        + " monitor="
                        + monitor
                        + " receiveSmsPermission="
                        + (c.checkSelfPermission(android.Manifest.permission.RECEIVE_SMS)
                                == android.content.pm.PackageManager.PERMISSION_GRANTED));
        if (!Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(action)) {
            Store.log(c, c.getString(R.string.log_sms_ignore));
            return;
        }
        if (!monitor) {
            Store.log(c, c.getString(R.string.log_sms_ignore_detail));
            return;
        }
        if (c.checkSelfPermission(android.Manifest.permission.READ_SMS)
                == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            // All SMS/RCS records use the same persisted ID checkpoint when provider access is
            // enabled.
            try {
                c.startForegroundService(new Intent(c, MacroService.class).setAction("SCAN_INBOX"));
                Store.log(c, c.getString(R.string.log_sms_provider_route));
            } catch (RuntimeException e) {
                Store.log(c, "SMS_PROVIDER_WAKE_FAILED " + e.getClass().getSimpleName());
            }
            return;
        }
        try {
            processSms(c, intent);
        } catch (RuntimeException e) {
            android.util.Log.e("AutoDroid", "SMS_PROCESS_ERROR", e);
            Store.log(c, "SMS_PROCESS_ERROR " + e.getClass().getSimpleName());
        }
    }

    private void processSms(Context c, Intent intent) {
        SmsMessage[] parts = Telephony.Sms.Intents.getMessagesFromIntent(intent);
        if (parts == null || parts.length == 0) {
            Store.log(c, c.getString(R.string.log_sms_parse_empty));
            return;
        }
        String sender = parts[0].getOriginatingAddress();
        if (sender == null) {
            Store.log(c, c.getString(R.string.log_sms_parse_no_sender));
            return;
        }
        StringBuilder body = new StringBuilder();
        for (SmsMessage part : parts) body.append(part.getMessageBody());
        JSONArray rules = Store.rules(c);
        Store.log(
                c,
                "SMS_PARSED sender="
                        + Store.maskedSender(sender)
                        + " parts="
                        + parts.length
                        + " rules="
                        + rules.length());
        int enabled = 0, senderMatches = 0, triggered = 0;
        for (int i = 0; i < rules.length(); i++) {
            JSONObject rule = rules.optJSONObject(i);
            if (rule == null || "notification".equals(rule.optString("source"))) continue;
            if (rule != null && rule.optBoolean("enabled", true)) {
                enabled++;
                if (Store.numbers(rule).contains(Store.normalize(sender))) senderMatches++;
            }
            if (rule != null && Store.matches(rule, sender, body.toString())) {
                triggered++;
                Store.log(
                        c,
                        "SMS_RULE_MATCH index="
                                + i
                                + c.getString(R.string.log_sms_start_requested));
                try {
                    c.startForegroundService(
                            new Intent(c, MacroService.class)
                                    .setAction("RUN")
                                    .putExtra("rule", rule.toString())
                                    .putExtra("source", c.getString(R.string.sms_match_source))
                                    .putExtra("eventChannel", "sms")
                                    .putExtra("eventBodyHash", Store.digest(body.toString())));
                } catch (RuntimeException e) {
                    Store.log(c, c.getString(R.string.log_sms_start_failed) + e.getMessage());
                }
            }
        }
        String reason =
                triggered > 0
                        ? c.getString(R.string.log_sms_match_count, triggered)
                        : enabled == 0
                                ? c.getString(R.string.log_no_enabled_rules)
                                : senderMatches == 0
                                        ? c.getString(R.string.log_no_sender_match)
                                        : c.getString(R.string.log_no_keyword_match);
        Store.log(
                c,
                c.getString(R.string.log_sms_received_prefix)
                        + Store.maskedSender(sender)
                        + " · "
                        + reason);
    }
}
