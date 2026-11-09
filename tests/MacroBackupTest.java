package com.autodroid;

import org.json.*;

public class MacroBackupTest {
    private static int checks;

    interface Checked {
        void run() throws Exception;
    }

    static void check(boolean ok) {
        checks++;
        if (!ok) throw new AssertionError("check " + checks);
    }

    static void rejects(Checked action) throws Exception {
        try {
            action.run();
        } catch (JSONException e) {
            checks++;
            return;
        }
        throw new AssertionError("Invalid backup accepted");
    }

    static JSONObject rule() throws Exception {
        return new JSONObject()
                .put("name", "测试宏")
                .put("numbers", new JSONArray().put("+8613800138000"))
                .put("keyword", "报警")
                .put("message", "请处理")
                .put("volume", 100)
                .put("duration", 60)
                .put("channel", 1)
                .put("enabled", false)
                .put("sound", "content://media/external/audio/media/7");
    }

    public static void main(String[] args) throws Exception {
        String json = MacroBackup.exportRules(new JSONArray().put(rule()));
        JSONObject restored = MacroBackup.importRules(json).getJSONObject(0);
        check(restored.getString("name").equals("测试宏") && restored.getInt("volume") == 100);
        check(!restored.getBoolean("enabled") && restored.getString("message").equals("请处理"));
        check(restored.getJSONArray("numbers").getString(0).equals("13800138000"));
        check(restored.getString("sound").startsWith("content://"));
        check(MacroBackup.importRules("\ufeff" + json).length() == 1);
        check(
                MacroBackup.importRules(
                                        new JSONArray()
                                                .put(
                                                        new JSONObject()
                                                                .put("name", "旧宏")
                                                                .put("number", "10086"))
                                                .toString())
                                .length()
                        == 1);
        rejects(() -> MacroBackup.importRules("{bad json"));
        rejects(() -> MacroBackup.importRules(json + "garbage"));
        rejects(() -> MacroBackup.importRules(new JSONObject(json).put("version", 3).toString()));
        rejects(() -> MacroBackup.importRules(new JSONObject(json).put("version", 1.5).toString()));
        rejects(
                () ->
                        MacroBackup.validate(
                                new JSONArray()
                                        .put(rule())
                                        .put(new JSONObject().put("name", "无号码"))));
        rejects(() -> MacroBackup.validate(new JSONArray().put(rule().put("volume", 101))));
        rejects(() -> MacroBackup.validate(new JSONArray().put(rule().put("channel", 4))));
        rejects(() -> MacroBackup.validate(new JSONArray().put(rule().put("duration", 0))));
        rejects(() -> MacroBackup.validate(new JSONArray().put(rule().put("volume", "80"))));
        rejects(() -> MacroBackup.validate(new JSONArray().put(rule().put("enabled", "true"))));
        rejects(
                () ->
                        MacroBackup.validate(
                                new JSONArray()
                                        .put(rule().put("numbers", new JSONArray().put(123)))));
        rejects(
                () ->
                        MacroBackup.validate(
                                new JSONArray()
                                        .put(rule().put("sound", "https://example.com/a.mp3"))));
        check(MacroBackup.importRules(MacroBackup.exportRules(new JSONArray())).length() == 0);
        JSONObject clean =
                MacroBackup.validate(
                                new JSONArray()
                                        .put(rule().put("logs", "secret").put("source", "both")))
                        .getJSONObject(0);
        check(!clean.has("logs") && clean.getString("source").equals("sms"));
        JSONArray tooMany = new JSONArray();
        for (int i = 0; i < 501; i++) tooMany.put(rule());
        rejects(() -> MacroBackup.validate(tooMany));
        JSONObject missingMessage = rule();
        missingMessage.remove("message");
        JSONArray incomplete = new JSONArray().put(missingMessage);
        check(
                MacroBackup.importRules(incomplete.toString(), "localized warning")
                        .getJSONObject(0)
                        .getString("message")
                        .equals("localized warning"));
        check(
                MacroBackup.importRules(MacroBackup.exportRules(incomplete, "localized warning"))
                        .getJSONObject(0)
                        .getString("message")
                        .equals("localized warning"));
        try {
            MacroBackup.validate(new JSONArray().put(rule().put("name", "")));
            throw new AssertionError("Missing name accepted");
        } catch (MacroBackup.ValidationException e) {
            check(e.error == MacroBackup.Error.MISSING_NAME && e.arguments[0].equals(1));
        }
        for (String mode : new String[] {"any", "all"}) {
            JSONObject multi =
                    rule().put("enabled", true).put("keyword", "报警\n断电").put("keywordMode", mode);
            String backup = MacroBackup.exportRules(new JSONArray().put(multi));
            check(new JSONObject(backup).getInt("version") == 2);
            JSONObject restoredMulti = MacroBackup.importRules(backup).getJSONObject(0);
            check(restoredMulti.getString("keywordMode").equals(mode));
            check(restoredMulti.getString("keyword").equals("报警\n断电"));
            check(Store.matches(restoredMulti, "13800138000", "报警断电"));
        }
        check(
                MacroBackup.importRules(new JSONObject(json).put("version", 1).toString()).length()
                        == 1);
        check(
                MacroBackup.validate(new JSONArray().put(rule()))
                        .getJSONObject(0)
                        .getString("keywordMode")
                        .equals("single"));
        rejects(
                () ->
                        MacroBackup.validate(
                                new JSONArray().put(rule().put("keywordMode", "unknown"))));
        rejects(() -> MacroBackup.validate(new JSONArray().put(rule().put("keywordMode", 2))));
        System.out.println("PASS: " + checks + " macro backup checks");
    }
}
