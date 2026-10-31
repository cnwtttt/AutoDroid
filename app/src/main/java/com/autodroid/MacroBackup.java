package com.autodroid;

import org.json.*;

import java.util.*;

/** Versioned, configuration-only backups. No permissions, logs, or signing keys. */
final class MacroBackup {
    static final int MAX_BYTES = 1024 * 1024;

    enum Error {
        TRAILING_CONTENT,
        INVALID_FILE,
        UNSUPPORTED_VERSION,
        TOO_MANY_RULES,
        MISSING_NAME,
        INVALID_NUMBERS,
        TOO_MANY_NUMBERS,
        INVALID_NUMBER,
        MISSING_NUMBERS,
        INVALID_SOUND,
        INVALID_ENABLED,
        MISSING_FIELD,
        INVALID_FIELD,
        NOT_NUMBER,
        OUT_OF_RANGE
    }

    static final class ValidationException extends JSONException {
        final Error error;
        final Object[] arguments;

        ValidationException(Error error, Object... arguments) {
            super(error.name());
            this.error = error;
            this.arguments = arguments;
        }
    }

    static String exportRules(JSONArray rules) throws JSONException {
        return exportRules(rules, "");
    }

    static String exportRules(JSONArray rules, String defaultMessage) throws JSONException {
        return new JSONObject()
                .put("format", "AutoDroid.macros")
                .put("version", 1)
                .put("createdAt", System.currentTimeMillis())
                .put("rules", validate(rules, defaultMessage))
                .toString(2);
    }

    static JSONArray importRules(String json) throws JSONException {
        return importRules(json, "");
    }

    static JSONArray importRules(String json, String defaultMessage) throws JSONException {
        if (json.startsWith("\ufeff")) json = json.substring(1);
        JSONTokener tokener = new JSONTokener(json);
        Object parsed = tokener.nextValue();
        if (tokener.nextClean() != 0) throw new ValidationException(Error.TRAILING_CONTENT);
        if (parsed instanceof JSONArray) return validate((JSONArray) parsed, defaultMessage);
        if (!(parsed instanceof JSONObject)) throw new ValidationException(Error.INVALID_FILE);
        JSONObject backup = (JSONObject) parsed;
        if (!"AutoDroid.macros".equals(backup.optString("format"))
                || !(backup.opt("version") instanceof Number)
                || ((Number) backup.opt("version")).doubleValue() != 1)
            throw new ValidationException(Error.UNSUPPORTED_VERSION);
        return validate(backup.getJSONArray("rules"), defaultMessage);
    }

    static JSONArray validate(JSONArray rules) throws JSONException {
        return validate(rules, "");
    }

    static JSONArray validate(JSONArray rules, String defaultMessage) throws JSONException {
        if (rules.length() > 500) throw new ValidationException(Error.TOO_MANY_RULES);
        JSONArray result = new JSONArray();
        for (int i = 0; i < rules.length(); i++) {
            JSONObject r = rules.getJSONObject(i);
            String name = string(r, "name", null, 256).trim();
            if (name.isEmpty()) throw new ValidationException(Error.MISSING_NAME, i + 1);
            JSONArray raw = r.optJSONArray("numbers");
            if (r.has("numbers") && raw == null)
                throw new ValidationException(Error.INVALID_NUMBERS);
            if (raw != null) {
                if (raw.length() > 100) throw new ValidationException(Error.TOO_MANY_NUMBERS);
                for (int j = 0; j < raw.length(); j++)
                    if (!(raw.get(j) instanceof String) || raw.getString(j).length() > 256)
                        throw new ValidationException(Error.INVALID_NUMBER);
            } else string(r, "number", "", 4096);
            Set<String> numbers = Store.numbers(r);
            if (numbers.isEmpty() || numbers.size() > 100)
                throw new ValidationException(Error.MISSING_NUMBERS, i + 1);
            String sound = string(r, "sound", "", 4096);
            if (!sound.isEmpty()
                    && !(sound.startsWith("content://")
                            || sound.startsWith("android.resource://")
                            || sound.startsWith("file://")))
                throw new ValidationException(Error.INVALID_SOUND);
            if (r.has("enabled") && !(r.get("enabled") instanceof Boolean))
                throw new ValidationException(Error.INVALID_ENABLED);
            result.put(
                    new JSONObject()
                            .put("name", name)
                            .put("source", "sms")
                            .put("numbers", new JSONArray(numbers))
                            .put("keyword", string(r, "keyword", "", 4096))
                            .put("message", string(r, "message", defaultMessage, 4096))
                            .put("sound", sound)
                            .put("channel", integer(r, "channel", 0, 0, 3))
                            .put("volume", integer(r, "volume", 80, 0, 100))
                            .put("duration", integer(r, "duration", 60, 5, 600))
                            .put("enabled", r.optBoolean("enabled", true)));
        }
        return result;
    }

    private static String string(JSONObject r, String key, String fallback, int max)
            throws JSONException {
        if (!r.has(key)) {
            if (fallback == null) throw new ValidationException(Error.MISSING_FIELD, key);
            return fallback;
        }
        Object v = r.get(key);
        if (!(v instanceof String) || ((String) v).length() > max)
            throw new ValidationException(Error.INVALID_FIELD, key);
        return (String) v;
    }

    private static int integer(JSONObject r, String key, int fallback, int min, int max)
            throws JSONException {
        if (!r.has(key)) return fallback;
        Object value = r.get(key);
        if (!(value instanceof Number)) throw new ValidationException(Error.NOT_NUMBER, key);
        double number = ((Number) value).doubleValue();
        if (!Double.isFinite(number) || number != Math.rint(number) || number < min || number > max)
            throw new ValidationException(Error.OUT_OF_RANGE, key);
        return (int) number;
    }
}
