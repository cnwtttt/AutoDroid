package com.autodroid;
import org.json.JSONObject;

public class RuleMatchTest {
    private static void check(boolean condition, String name) {
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
        System.out.println("PASS: 9 SMS matching checks");
    }
}
