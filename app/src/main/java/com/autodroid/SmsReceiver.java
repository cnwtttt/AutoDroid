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
        for (int i = 0; i < rules.length(); i++) {
            JSONObject rule = rules.optJSONObject(i);
            if (rule != null && Store.matches(rule, sender, body.toString())) {
                try { c.startForegroundService(new Intent(c, MacroService.class).setAction("RUN").putExtra("rule", rule.toString()).putExtra("source", "短信匹配")); }
                catch (RuntimeException e) { Store.log(c, "短信启动失败：" + e.getMessage()); }
            }
        }
    }
}
