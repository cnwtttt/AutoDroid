package com.autodroid;
import android.content.*;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import org.json.*;

public class SmsReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent intent) {
        if (!Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction()) || !Store.prefs(c).getBoolean("monitor", false)) return;
        SmsMessage[] parts = Telephony.Sms.Intents.getMessagesFromIntent(intent);
        if (parts == null || parts.length == 0) return;
        String sender = parts[0].getOriginatingAddress();
        if (sender == null) return;
        StringBuilder body = new StringBuilder();
        for (SmsMessage part : parts) body.append(part.getMessageBody());
        JSONArray rules = Store.rules(c);
        int enabled = 0, senderMatches = 0, triggered = 0;
        for (int i = 0; i < rules.length(); i++) {
            JSONObject rule = rules.optJSONObject(i);
            if (rule != null && rule.optBoolean("enabled", true)) {
                enabled++;
                if (Store.numbers(rule).contains(Store.normalize(sender))) senderMatches++;
            }
            if (rule != null && Store.matches(rule, sender, body.toString())) {
                triggered++;
                try { c.startForegroundService(new Intent(c, MacroService.class).setAction("RUN").putExtra("rule", rule.toString()).putExtra("source", "短信匹配")); }
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
