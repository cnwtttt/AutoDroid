package com.autodroid;

import android.content.*;

import org.json.*;

import java.text.Normalizer;
import java.text.SimpleDateFormat;
import java.util.*;

final class Store {
    static android.content.SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("autodroid", 0);
    }

    static JSONArray rules(Context c) {
        try {
            return new JSONArray(prefs(c).getString("rules", "[]"));
        } catch (JSONException e) {
            log(c, "RULES_CORRUPTED " + e.getMessage());
            return new JSONArray();
        }
    }

    static void save(Context c, JSONArray rules) {
        prefs(c).edit().putString("rules", rules.toString()).commit();
    }

    static synchronized void log(Context c, String message) {
        android.util.Log.i("AutoDroid", message);
        String old = prefs(c).getString("logs", "");
        String line =
                new SimpleDateFormat("MM-dd HH:mm:ss", Locale.CHINA).format(new Date())
                        + "  "
                        + message
                        + "\n";
        prefs(c).edit()
                .putString(
                        "logs",
                        (line + old).substring(0, Math.min(16000, line.length() + old.length())))
                .apply();
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

    static String numberText(JSONObject rule) {
        return String.join("\n", numbers(rule));
    }

    static boolean matches(JSONObject rule, String sender, String body) {
        String actual = normalize(sender);
        return !"notification".equals(rule.optString("source"))
                && rule.optBoolean("enabled", true)
                && !actual.isEmpty()
                && numbers(rule).contains(actual)
                && keywordsMatch(rule, body);
    }

    // Absence of keywordMode preserves legacy literal matching, including whitespace/newlines.
    static boolean keywordsMatch(JSONObject rule, String body) {
        if (body == null) return false;
        String mode = rule.optString("keywordMode", "single");
        String text = rule.optString("keyword");
        if ("single".equals(mode)) return body.contains(text);
        if (!("any".equals(mode) || "all".equals(mode))) return false;
        LinkedHashSet<String> keywords = parseKeywords(text);
        if (keywords.isEmpty()) return true;
        boolean all = "all".equals(mode);
        for (String keyword : keywords) {
            boolean found = body.contains(keyword);
            if (all && !found) return false;
            if (!all && found) return true;
        }
        return all;
    }

    static LinkedHashSet<String> parseKeywords(String text) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String line : text.split("[\\r\\n]+")) {
            String keyword = line.trim();
            if (!keyword.isEmpty()) result.add(keyword);
        }
        return result;
    }

    static LinkedHashSet<String> parseNames(String text) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String entry : text.split("[,，;；\\n\\r]+")) {
            String name = Normalizer.normalize(entry, Normalizer.Form.NFKC).trim();
            if (!name.isEmpty()) result.add(name);
        }
        return result;
    }

    static boolean migrateToSms(JSONArray rules) {
        boolean changed = false;
        for (int i = 0; i < rules.length(); i++) {
            JSONObject r = rules.optJSONObject(i);
            if (r == null
                    || !("notification".equals(r.optString("source"))
                            || "both".equals(r.optString("source")))) continue;
            try {
                r.put("source", "sms");
                if (numbers(r).isEmpty()) r.put("enabled", false);
                changed = true;
            } catch (JSONException e) {
                throw new IllegalStateException(e);
            }
        }
        return changed;
    }

    static boolean notificationMatches(JSONObject rule, Collection<String> senders, String body) {
        return "MATCH".equals(notificationResult(rule, senders, body));
    }

    static String notificationResult(JSONObject rule, Collection<String> senders, String body) {
        if (!rule.optBoolean("enabled", true)) return "DISABLED";
        if (!("notification".equals(rule.optString("source"))
                || "both".equals(rule.optString("source")))) return "SOURCE_MISMATCH";
        Set<String> aliases = parseNames(rule.optString("senderNames"));
        Set<String> phones = numbers(rule);
        boolean senderMatch = false;
        for (String sender : senders) {
            if (sender == null || sender.trim().isEmpty()) continue;
            if (aliases.contains(Normalizer.normalize(sender, Normalizer.Form.NFKC).trim())
                    || phones.contains(normalize(sender))) senderMatch = true;
        }
        boolean keywordMatch = keywordsMatch(rule, body);
        if (!senderMatch && !keywordMatch) return "SENDER_AND_KEYWORD_MISMATCH";
        if (!senderMatch) return "SENDER_MISMATCH";
        if (!keywordMatch) return "KEYWORD_MISMATCH";
        return "MATCH";
    }

    // Pair equal-content events from opposite sources; repeats on the same source remain valid.
    static boolean allowCombinedEvent(
            JSONArray history, String ruleHash, String bodyHash, String channel, long now) {
        try {
            for (int i = history.length() - 1; i >= 0; i--) {
                JSONObject event = history.optJSONObject(i);
                if (event == null
                        || now - event.optLong("time") > 10000
                        || now < event.optLong("time")) history.remove(i);
            }
            for (int i = 0; i < history.length(); i++) {
                JSONObject event = history.getJSONObject(i);
                if (!event.optBoolean("paired")
                        && ruleHash.equals(event.optString("rule"))
                        && bodyHash.equals(event.optString("body"))
                        && !channel.equals(event.optString("channel"))) {
                    event.put("paired", true);
                    return false;
                }
            }
            while (history.length() >= 128) history.remove(0);
            history.put(
                    new JSONObject()
                            .put("rule", ruleHash)
                            .put("body", bodyHash)
                            .put("channel", channel)
                            .put("time", now));
            return true;
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    static synchronized boolean allowCombinedRun(
            Context c, JSONObject rule, String bodyHash, String channel) {
        if (!"both".equals(rule.optString("source")) || bodyHash == null || channel == null)
            return true;
        JSONArray history;
        try {
            history = new JSONArray(prefs(c).getString("combinedHistory", "[]"));
        } catch (JSONException e) {
            history = new JSONArray();
        }
        boolean allowed =
                allowCombinedEvent(
                        history,
                        digest(rule.toString()),
                        bodyHash,
                        channel,
                        System.currentTimeMillis());
        prefs(c).edit().putString("combinedHistory", history.toString()).commit();
        return allowed;
    }

    static String digest(String value) {
        try {
            byte[] hash =
                    java.security.MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte b : hash) out.append(String.format(Locale.ROOT, "%02x", b & 255));
            return out.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static boolean rememberEvent(JSONArray history, String fingerprint) {
        for (int i = 0; i < history.length(); i++)
            if (fingerprint.equals(history.optString(i))) return false;
        while (history.length() >= 128) history.remove(0);
        history.put(fingerprint);
        return true;
    }

    static synchronized boolean seenNotification(Context c, String event) {
        String fingerprint = digest(event);
        try {
            JSONArray history = new JSONArray(prefs(c).getString("notificationHistory", "[]"));
            for (int i = 0; i < history.length(); i++)
                if (fingerprint.equals(history.optString(i))) return true;
        } catch (JSONException ignored) {
        }
        return false;
    }

    static synchronized boolean acceptNotification(Context c, String event) {
        JSONArray history;
        try {
            history = new JSONArray(prefs(c).getString("notificationHistory", "[]"));
        } catch (JSONException e) {
            history = new JSONArray();
        }
        if (!rememberEvent(history, digest(event))) return false;
        prefs(c).edit().putString("notificationHistory", history.toString()).commit();
        return true;
    }

    static String normalize(String number) {
        if (number == null) return "";
        String compatible = Normalizer.normalize(number, Normalizer.Form.NFKC);
        StringBuilder cleaned = new StringBuilder();
        for (int i = 0; i < compatible.length(); ) {
            int code = compatible.codePointAt(i);
            i += Character.charCount(code);
            if (Character.isWhitespace(code)
                    || Character.isSpaceChar(code)
                    || Character.getType(code) == Character.FORMAT
                    || Character.getType(code) == Character.DASH_PUNCTUATION
                    || code == '('
                    || code == ')') continue;
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
        return n.isEmpty() ? "<empty>" : "***" + n.substring(Math.max(0, n.length() - 4));
    }
}
