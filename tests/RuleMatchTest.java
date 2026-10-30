package com.autodroid;
import org.json.JSONObject;
import org.json.JSONArray;
import java.util.Arrays;
import java.util.LinkedHashSet;

public class RuleMatchTest {
    private static int checks;
    private static void check(boolean condition, String name) {
        checks++;
        if (!condition) throw new AssertionError(name);
    }
    public static void main(String[] args) throws Exception {
        JSONObject rule = new JSONObject().put("number", "13800138000").put("keyword", "报警").put("enabled", true);
        check(Store.matches(rule, "+86 138-0013-8000", "设备报警，请处理"), "country prefix and formatting");
        check(Store.matches(rule, "008613800138000", "报警"), "0086 prefix");
        check(!Store.matches(rule, "13800138001", "报警"), "other sender");
        check(!Store.matches(rule, "13800138000", "正常"), "keyword mismatch");
        rule.put("enabled", false);
        check(!Store.matches(rule, "13800138000", "报警"), "disabled macro");
        rule.put("enabled", true).put("keyword", "");
        check(Store.matches(rule, "13800138000", "任何内容"), "empty keyword");
        rule.put("number", "10086");
        check(Store.matches(rule, "10086", "服务消息"), "service sender");
        check(!Store.matches(rule, "110086", "服务消息"), "no suffix matching");
        rule.put("number", "");
        check(!Store.matches(rule, "", ""), "empty sender blocked");
        check(Store.parseNumbers("13800138000，+86 138-0013-8000;13900139000\n10086；10010\r\n").equals(
            new LinkedHashSet<>(Arrays.asList("13800138000","13900139000","10086","10010"))), "parse separators and deduplicate");
        check(Store.parseNumbers(" ,，;；\n\r ").isEmpty(), "blank entries ignored");
        JSONObject multi = new JSONObject().put("numbers", new JSONArray().put("13800138000").put("+86 139-0013-9000"))
            .put("keyword", "报警").put("enabled", true);
        check(Store.matches(multi, "+8613800138000", "报警"), "first selected sender");
        check(Store.matches(multi, "008613900139000", "设备报警"), "second selected sender");
        check(!Store.matches(multi, "13700137000", "报警"), "unselected sender");
        check(!Store.matches(multi, "13900139000", "正常"), "multi keyword mismatch");
        multi.put("enabled", false);
        check(!Store.matches(multi, "13900139000", "报警"), "disabled multi macro");
        multi.put("enabled", true).put("keyword", "");
        check(Store.matches(multi, "13900139000", "任何内容"), "multi empty keyword");
        check(!Store.matches(multi, "", ""), "multi empty sender");
        multi.put("number", "10086");
        check(!Store.matches(multi, "10086", ""), "new array takes priority over legacy field");
        multi.put("numbers", new JSONArray());
        check(!Store.matches(multi, "13800138000", ""), "empty array never matches");
        JSONObject legacy = new JSONObject().put("number", "+86 138-0013-8000");
        check(Store.numberText(legacy).equals("13800138000"), "legacy editor hydration");
        JSONObject saved = new JSONObject(new JSONObject().put("numbers", new JSONArray(Store.parseNumbers("13800138000,13900139000"))).toString());
        check(Store.matches(saved,"13900139000","正文"), "array survives JSON persistence");
        check(Store.parseNumbers(Store.numberText(saved)).equals(Store.numbers(saved)), "editor round trip");
        check(Store.numbers(new JSONObject().put("numbers",new JSONArray().put("13800138000").put("+8613800138000"))).size() == 1, "contact duplicates normalized");
        JSONObject prefixRule = new JSONObject().put("numbers", new JSONArray().put("13800138000"));
        check(Store.matches(prefixRule, "+8613800138000", "短信"), "ASCII +86 sender");
        check(Store.matches(prefixRule, "\u200e+8613800138000", "短信"), "bidi mark before +86");
        check(Store.matches(prefixRule, "+86\u00a0138\u202f0013\u00a08000", "短信"), "non-breaking spaces after +86");
        check(Store.matches(prefixRule, "＋８６１３８００１３８０００", "短信"), "fullwidth +86 sender");
        check(Store.matches(prefixRule, "+86 138–0013–8000", "短信"), "Unicode phone dashes");
        check(Store.matches(new JSONObject().put("numbers",new JSONArray().put("\u200e＋８６ 138\u00a00013 8000")),
            "+8613800138000", "短信"), "contact normalization uses same path");
        check(!Store.matches(prefixRule, "+8513800138000", "短信"), "different country prefix stays distinct");
        check(!Store.matches(prefixRule, "+8613800138001", "短信"), "different subscriber stays distinct");
        check(Store.normalize(null).isEmpty(), "null sender is safe");
        System.out.println("PASS: " + checks + " SMS matching checks");
    }
}
