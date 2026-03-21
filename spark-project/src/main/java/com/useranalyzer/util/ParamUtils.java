package com.useranalyzer.util;

import com.alibaba.fastjson.JSONObject;
import com.useranalyzer.constant.Constants;

/**
 * 任务参数解析工具类
 * 任务参数以 JSON 格式存储在 MySQL task 表的 task_param 字段中
 * 示例: {"startDate":"2019-01-01","endDate":"2019-12-31","minAge":10,"maxAge":60,...}
 */
public class ParamUtils {

    public static String getParam(JSONObject param, String key) {
        if (param == null || !param.containsKey(key)) return null;
        String val = param.getString(key);
        return (val != null && !val.trim().isEmpty()) ? val.trim() : null;
    }

    public static int getIntParam(JSONObject param, String key, int defaultValue) {
        if (param == null || !param.containsKey(key)) return defaultValue;
        try {
            return param.getIntValue(key);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * 检查某个值是否在逗号分隔的参数列表中（忽略大小写）
     * 如果参数为 null 或为空，则认为不过滤（全部通过）
     */
    public static boolean inParam(String value, String param) {
        if (param == null || param.isEmpty()) return true;
        if (value == null || value.isEmpty()) return false;
        for (String p : param.split(",")) {
            if (p.trim().equalsIgnoreCase(value.trim())) return true;
        }
        return false;
    }

    /**
     * 检查两个逗号分隔的列表是否有交集（用于品类ID、搜索词等）
     */
    public static boolean hasIntersection(String listStr, String paramStr) {
        if (paramStr == null || paramStr.isEmpty()) return true;
        if (listStr == null || listStr.isEmpty()) return false;
        for (String item : listStr.split(",")) {
            for (String p : paramStr.split(",")) {
                if (p.trim().equalsIgnoreCase(item.trim())) return true;
            }
        }
        return false;
    }
}
