package com.autodroid;

import java.util.*;
import org.json.*;

/** Versioned, configuration-only backups. No permissions, logs, or signing keys. */
final class MacroBackup {
    static final int MAX_BYTES = 1024 * 1024;
    static String exportRules(JSONArray rules) throws JSONException {
        return new JSONObject().put("format","AutoDroid.macros").put("version",1)
            .put("createdAt",System.currentTimeMillis()).put("rules",validate(rules)).toString(2);
    }
    static JSONArray importRules(String json) throws JSONException {
        if (json.startsWith("\ufeff")) json = json.substring(1);
        JSONTokener tokener = new JSONTokener(json);
        Object parsed = tokener.nextValue();
        if (tokener.nextClean() != 0) throw new JSONException("备份末尾包含多余内容");
        if (parsed instanceof JSONArray) return validate((JSONArray)parsed);
        if (!(parsed instanceof JSONObject)) throw new JSONException("不是有效的宏备份文件");
        JSONObject backup = (JSONObject)parsed;
        if (!"AutoDroid.macros".equals(backup.optString("format")) || !(backup.opt("version") instanceof Number) || ((Number)backup.opt("version")).doubleValue() != 1)
            throw new JSONException("不支持的备份格式或版本");
        return validate(backup.getJSONArray("rules"));
    }
    static JSONArray validate(JSONArray rules) throws JSONException {
        if (rules.length() > 500) throw new JSONException("最多支持500条宏");
        JSONArray result = new JSONArray();
        for (int i = 0; i < rules.length(); i++) {
            JSONObject r = rules.getJSONObject(i);
            String name = string(r,"name",null,256).trim();
            if (name.isEmpty()) throw new JSONException("第"+(i+1)+"条宏缺少名称");
            JSONArray raw = r.optJSONArray("numbers");
            if (r.has("numbers") && raw == null) throw new JSONException("号码列表类型无效");
            if (raw != null) {
                if (raw.length() > 100) throw new JSONException("每条宏最多100个号码");
                for (int j = 0; j < raw.length(); j++) if (!(raw.get(j) instanceof String) || raw.getString(j).length() > 256)
                    throw new JSONException("号码必须是字符串且长度不超过256");
            } else string(r,"number","",4096);
            Set<String> numbers = Store.numbers(r);
            if (numbers.isEmpty() || numbers.size() > 100) throw new JSONException("第"+(i+1)+"条宏需包含1～100个号码");
            String sound = string(r,"sound","",4096);
            if (!sound.isEmpty() && !(sound.startsWith("content://") || sound.startsWith("android.resource://") || sound.startsWith("file://")))
                throw new JSONException("不支持的音源地址");
            if (r.has("enabled") && !(r.get("enabled") instanceof Boolean)) throw new JSONException("启用状态类型无效");
            result.put(new JSONObject().put("name",name).put("source","sms").put("numbers",new JSONArray(numbers))
                .put("keyword",string(r,"keyword","",4096)).put("message",string(r,"message","收到匹配短信，请及时处理",4096))
                .put("sound",sound).put("channel",integer(r,"channel",0,0,3))
                .put("volume",integer(r,"volume",80,0,100)).put("duration",integer(r,"duration",60,5,600))
                .put("enabled",r.optBoolean("enabled",true)));
        }
        return result;
    }
    private static String string(JSONObject r,String key,String fallback,int max) throws JSONException {
        if (!r.has(key)) {
            if (fallback == null) throw new JSONException("缺少字段："+key);
            return fallback;
        }
        Object v = r.get(key);
        if (!(v instanceof String) || ((String)v).length() > max) throw new JSONException("字段类型或长度无效："+key);
        return (String)v;
    }
    private static int integer(JSONObject r,String key,int fallback,int min,int max) throws JSONException {
        if (!r.has(key)) return fallback;
        Object value = r.get(key);
        if (!(value instanceof Number)) throw new JSONException("字段必须是数字："+key);
        double number = ((Number)value).doubleValue();
        if (!Double.isFinite(number) || number != Math.rint(number) || number < min || number > max)
            throw new JSONException("字段超出范围："+key);
        return (int)number;
    }
}
