package com.autodroid;
import android.content.*;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import org.json.*;

public class SmsReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        boolean monitor = Store.prefs(c).getBoolean("monitor", false);
        Store.log(c, "SMS_RECEIVER_ENTER action=" + action + " monitor=" + monitor
            + " receiveSmsPermission=" + (c.checkSelfPermission(android.Manifest.permission.RECEIVE_SMS)
                == android.content.pm.PackageManager.PERMISSION_GRANTED));
        if (!Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(action)) {
            Store.log(c, "SMS_IGNORE 非 SMS_RECEIVED 广播"); return;
        }
        if (!monitor) { Store.log(c, "SMS_IGNORE 监听已关闭，请在应用中开启监听"); return; }
        try { processSms(c, intent); }
        catch (RuntimeException e) {
            android.util.Log.e("AutoDroid", "SMS_PROCESS_ERROR", e);
            Store.log(c, "SMS_PROCESS_ERROR " + e.getClass().getSimpleName());
        }
    }
    private void processSms(Context c, Intent intent) {
        SmsMessage[] parts = Telephony.Sms.Intents.getMessagesFromIntent(intent);
        if (parts == null || parts.length == 0) { Store.log(c, "SMS_PARSE_EMPTY 未解析到短信分段"); return; }
        String sender = parts[0].getOriginatingAddress();
        if (sender == null) { Store.log(c, "SMS_PARSE_NO_SENDER 短信发送号码为空"); return; }
        StringBuilder body = new StringBuilder();
        for (SmsMessage part : parts) body.append(part.getMessageBody());
        JSONArray rules = Store.rules(c);
        Store.log(c, "SMS_PARSED sender=" + Store.maskedSender(sender) + " parts=" + parts.length
            + " rules=" + rules.length());
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
                Store.log(c, "SMS_RULE_MATCH index=" + i + "，请求启动宏服务");
                try { c.startForegroundService(new Intent(c, MacroService.class).setAction("RUN").putExtra("rule", rule.toString()).putExtra("source", "短信匹配").putExtra("eventChannel","sms").putExtra("eventBodyHash",Store.digest(body.toString()))); }
                catch (RuntimeException e) { Store.log(c, "短信启动失败：" + e.getMessage()); }
            }
        }
        String reason = triggered > 0 ? "匹配宏 " + triggered + " 条（执行结果见后续日志）"
            : enabled == 0 ? "没有启用的宏"
            : senderMatches == 0 ? "发送号码未匹配任何启用的宏"
            : "号码已匹配，短信关键词未命中";
        Store.log(c, "收到短信 · " + Store.maskedSender(sender) + " · " + reason);
    }
}
