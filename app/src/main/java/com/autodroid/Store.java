package com.autodroid;

import android.content.*;
import org.json.*;
import java.text.SimpleDateFormat;
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
    static boolean matches(JSONObject rule, String sender, String body) {
        String expected = normalize(rule.optString("number"));
        String actual = normalize(sender);
        return rule.optBoolean("enabled", true) && !expected.isEmpty() && expected.equals(actual)
            && body.contains(rule.optString("keyword"));
    }
    static String normalize(String number) {
        String n = number.replaceAll("[\\s()\\-]", "");
        if (n.startsWith("0086")) n = n.substring(4);
        else if (n.startsWith("+86")) n = n.substring(3);
        return n;
    }
}
