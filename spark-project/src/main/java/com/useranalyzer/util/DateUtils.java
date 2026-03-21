package com.useranalyzer.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 日期工具类
 */
public class DateUtils {

    private static final DateTimeFormatter DATE_FMT      = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FMT  = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) return null;
        try {
            return LocalDate.parse(dateStr.trim(), DATE_FMT);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    public static LocalDateTime parseDateTime(String datetimeStr) {
        if (datetimeStr == null || datetimeStr.isEmpty()) return null;
        try {
            return LocalDateTime.parse(datetimeStr.trim(), DATETIME_FMT);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * 判断某个日期是否在 [startDate, endDate] 范围内（inclusive）
     */
    public static boolean between(String actionDate, String startDate, String endDate) {
        if (startDate == null || endDate == null) return true;
        LocalDate d  = parseDate(actionDate);
        LocalDate s  = parseDate(startDate);
        LocalDate e  = parseDate(endDate);
        if (d == null || s == null || e == null) return true;
        return !d.isBefore(s) && !d.isAfter(e);
    }

    /**
     * 计算两个 "yyyy-MM-dd HH:mm:ss" 之间的秒数差（abs）
     */
    public static long secondsBetween(String t1, String t2) {
        LocalDateTime dt1 = parseDateTime(t1);
        LocalDateTime dt2 = parseDateTime(t2);
        if (dt1 == null || dt2 == null) return 0L;
        return Math.abs(java.time.Duration.between(dt1, dt2).getSeconds());
    }
}
