package com.llf.util;

import java.sql.Timestamp;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class DateTimeUtils {

    private DateTimeUtils() {}

    /**
     * 将任意常见 ISO / 带时区 / 不带时区 / 空格分隔时间字符串
     * 统一解析为 LocalDateTime（系统默认时区）
     */
    public static LocalDateTime parseToLocalDateTime(String text) {

        if (text == null || text.isBlank()) {
            throw new RuntimeException("时间不能为空");
        }

        String t = text.trim();

        // 1️⃣ 带 offset，例如 2026-03-13T11:00:00+08:00
        try {
            return OffsetDateTime.parse(t).toLocalDateTime();
        } catch (DateTimeParseException ignore) {}

        // 2️⃣ 带 Z（UTC）例如 2026-03-13T03:00:00Z
        try {
            return Instant.parse(t)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime();
        } catch (DateTimeParseException ignore) {}

        // 3️⃣ 标准 ISO 不带时区 2026-03-13T11:00[:ss]
        try {
            return LocalDateTime.parse(t);
        } catch (DateTimeParseException ignore) {}

        // 4️⃣ 空格分隔 yyyy-MM-dd HH:mm
        try {
            DateTimeFormatter f1 = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            return LocalDateTime.parse(t, f1);
        } catch (DateTimeParseException ignore) {}

        // 5️⃣ 空格分隔 yyyy-MM-dd HH:mm:ss
        try {
            DateTimeFormatter f2 = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            return LocalDateTime.parse(t, f2);
        } catch (DateTimeParseException ignore) {}

        throw new RuntimeException(
                "不支持的时间格式: " + t +
                        "（支持 ISO / 带+08:00 / 带Z / yyyy-MM-dd HH:mm / yyyy-MM-dd HH:mm:ss）"
        );
    }

    /**
     * 转为 Timestamp（数据库用）
     */
    public static java.sql.Timestamp toTimestamp(LocalDateTime ldt) {
        return java.sql.Timestamp.valueOf(ldt);
    }

    /**
     * String -> Timestamp
     */
    public static java.sql.Timestamp parseToTimestamp(String text) {
        return toTimestamp(parseToLocalDateTime(text));
    }

    /**
     * 判断时间区间是否重叠
     */
    public static boolean isOverlap(LocalDateTime start1,
                                    LocalDateTime end1,
                                    LocalDateTime start2,
                                    LocalDateTime end2) {
        return start1.isBefore(end2) && end1.isAfter(start2);
    }

    /**
     * 计算分钟差
     */
    public static long minutesBetween(LocalDateTime start,
                                      LocalDateTime end) {
        return Duration.between(start, end).toMinutes();

    }

    public static long toEpochMillis(Object v) {
        if (v == null) return -1;

        if (v instanceof Timestamp ts) {
            return ts.getTime();
        }
        if (v instanceof Date d) {
            return d.getTime();
        }
        if (v instanceof LocalDateTime ldt) {
            // 你的系统是 +08:00（中国/新加坡），用 ZoneId.systemDefault() 一般就是 Asia/Shanghai
            return ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        }
        if (v instanceof LocalDate ld) {
            return ld.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        }
        if (v instanceof Instant ins) {
            return ins.toEpochMilli();
        }
        if (v instanceof String s) {
            String str = s.trim();
            if (str.isEmpty()) return -1;

            // 兼容：2026-03-11T09:00:00+08:00
            try {
                return OffsetDateTime.parse(str).toInstant().toEpochMilli();
            } catch (Exception ignore) {}

            // 兼容：2026-03-11T09:00:00
            try {
                LocalDateTime ldt = LocalDateTime.parse(str);
                return ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            } catch (Exception ignore) {}

            // 兼容：2026-03-11 09:00:00
            try {
                DateTimeFormatter f = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                LocalDateTime ldt = LocalDateTime.parse(str, f);
                return ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            } catch (Exception ignore) {}
        }

        throw new RuntimeException("不支持的时间类型: " + v.getClass());
    }
}