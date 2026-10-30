package com.autodroid;

import android.content.*;
import org.json.*;
import java.text.SimpleDateFormat;
import java.text.Normalizer;
import java.util.*;

final class Store {
    static android.content.SharedPreferences prefs(Context c) { return c.getSharedPreferences("autodroid", 0); }
    static JSONArray rules(Context c) {
        try { return new JSONArray(prefs(c).getString("rules", "[]")); }
        catch (JSONException e) { log(c, "规则数据损坏：" + e.getMessage()); return new JSONArray(); }
    }
    static void save(Context c, JSONArray rules) { prefs(c).edit().putString("rules", rules.toString()).commit(); }
    static synchronized void log(Context c, String message) {
        String old = prefs(c).getString("logs", "");
        String line = new SimpleDateFormat("MM-dd HH:mm:ss", Locale.CHINA).format(new Date()) + "  " + message + "\n";
        prefs(c).edit().putString("logs", (line + old).substring(0, Math.min(16000, line.length() + old.length()))).apply();
    }
    static LinkedHashSet<String> parseNumbers(String text) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String entry : text.split("[,，;；\\n\\r]+")) {
            String n = normalize(entry);
            if (!n.isEmpty()) result.add(n);
        }
        return result;
    }
    static LinkedHashSet<String> numbers(JSONObject rule) {
        JSONArray saved = rule.optJSONArray("numbers");
        if (saved == null) return parseNumbers(rule.optString("number"));
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (int i = 0; i < saved.length(); i++) result.addAll(parseNumbers(saved.optString(i)));
        return result;
    }
    static String numberText(JSONObject rule) { return String.join("\n", numbers(rule)); }
    static boolean matches(JSONObject rule, String sender, String body) {
        String actual = normalize(sender);
        return rule.optBoolean("enabled", true) && !actual.isEmpty() && numbers(rule).contains(actual)
            && body.contains(rule.optString("keyword"));
    }
    static String normalize(String number) {
        if (number == null) return "";
        String compatible = Normalizer.normalize(number, Normalizer.Form.NFKC);
        StringBuilder cleaned = new StringBuilder();
        for (int i = 0; i < compatible.length();) {
            int code = compatible.codePointAt(i);
            i += Character.charCount(code);
            if (Character.isWhitespace(code) || Character.isSpaceChar(code)
                    || Character.getType(code) == Character.FORMAT
                    || Character.getType(code) == Character.DASH_PUNCTUATION
                    || code == '(' || code == ')') continue;
            int digit = Character.digit(code, 10);
            if (digit >= 0) cleaned.append((char) ('0' + digit));
            else cleaned.appendCodePoint(code);
        }
        String n = cleaned.toString();
        if (n.startsWith("0086")) n = n.substring(4);
        else if (n.startsWith("+86")) n = n.substring(3);
        return n;
    }
    static String maskedSender(String sender) {
        String n = normalize(sender);
        return n.isEmpty() ? "空号码" : "***" + n.substring(Math.max(0, n.length() - 4));
    }
}
