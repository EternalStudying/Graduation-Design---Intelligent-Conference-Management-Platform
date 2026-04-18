package com.llf.assistant.semantic;

import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class AiAssistantTimeResolver {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Pattern DATE_PATTERN = Pattern.compile("(20\\d{2}-\\d{2}-\\d{2})");
    private static final Pattern TIME_RANGE_PATTERN = Pattern.compile("(上午|下午|晚上|中午|傍晚)?\\s*(\\d{1,2})(?:(?::(\\d{1,2}))|点)?\\s*(?:到|至|-|~)\\s*(\\d{1,2})(?:(?::(\\d{1,2}))|点)?");
    private static final Pattern SINGLE_CLOCK_PATTERN = Pattern.compile("(改到|改成|调到)?\\s*(上午|下午|晚上|中午|傍晚)?\\s*(\\d{1,2})(?:(?::(\\d{1,2}))|点)");
    private static final Pattern NEXT_WEEKDAY_PATTERN = Pattern.compile("下周([一二三四五六日天])(上午|下午|晚上|中午)?");

    private static final Map<String, DayOfWeek> WEEKDAY_MAP = Map.of(
            "一", DayOfWeek.MONDAY,
            "二", DayOfWeek.TUESDAY,
            "三", DayOfWeek.WEDNESDAY,
            "四", DayOfWeek.THURSDAY,
            "五", DayOfWeek.FRIDAY,
            "六", DayOfWeek.SATURDAY,
            "日", DayOfWeek.SUNDAY,
            "天", DayOfWeek.SUNDAY
    );

    public void resolve(String originalText, String normalizedText, AiAssistantIntentFields fields) {
        if (fields == null) {
            return;
        }
        String source = (normalizedText == null ? "" : normalizedText) + " " + (originalText == null ? "" : originalText);
        LocalDate today = LocalDate.now();

        resolveTimeRangeLabel(source, fields);
        resolveMeetingDate(source, fields, today);
        resolveExplicitClockRange(source, fields);
        resolveSingleClock(source, fields);
        resolveDateWindow(fields, today);
    }

    private void resolveTimeRangeLabel(String source, AiAssistantIntentFields fields) {
        if (fields.getTimeRangeLabel() != null) {
            return;
        }
        if (source.contains("上次那个会") || source.contains("上次")) {
            fields.setTimeRangeLabel("last");
            return;
        }
        if (containsIndependent(source, "下下周")) {
            fields.setTimeRangeLabel("week_after_next");
            return;
        }
        if (containsIndependent(source, "下周末")) {
            fields.setTimeRangeLabel("next_weekend");
            return;
        }
        if (source.contains("这周末") || source.contains("本周末") || source.contains("周末")) {
            fields.setTimeRangeLabel("this_weekend");
            return;
        }
        Matcher nextWeekday = NEXT_WEEKDAY_PATTERN.matcher(source);
        if (nextWeekday.find()) {
            DayOfWeek dayOfWeek = WEEKDAY_MAP.get(nextWeekday.group(1));
            String period = resolvePeriodSuffix(nextWeekday.group(2));
            if (dayOfWeek == DayOfWeek.MONDAY && "morning".equals(period)) {
                fields.setTimeRangeLabel("next_monday_morning");
            } else if (dayOfWeek == DayOfWeek.TUESDAY && "morning".equals(period)) {
                fields.setTimeRangeLabel("next_tuesday_morning");
            } else {
                fields.setTimeRangeLabel("next_week");
            }
            return;
        }
        if (source.contains("大后天上午")) {
            fields.setTimeRangeLabel("day_3_from_today_morning");
            return;
        }
        if (source.contains("大后天晚上")) {
            fields.setTimeRangeLabel("day_3_from_today_evening");
            return;
        }
        if (source.contains("大后天")) {
            fields.setTimeRangeLabel("day_3_from_today");
            return;
        }
        if (source.contains("明天上午")) {
            fields.setTimeRangeLabel("tomorrow_morning");
            return;
        }
        if (source.contains("明天下午")) {
            fields.setTimeRangeLabel("tomorrow_afternoon");
            return;
        }
        if (source.contains("明天中午")) {
            fields.setTimeRangeLabel("tomorrow_noon");
            return;
        }
        if (source.contains("明天晚上")) {
            fields.setTimeRangeLabel("tomorrow_evening");
            return;
        }
        if (source.contains("今天上午")) {
            fields.setTimeRangeLabel("today_morning");
            return;
        }
        if (source.contains("今天下午")) {
            fields.setTimeRangeLabel("today_afternoon");
            return;
        }
        if (source.contains("今天中午")) {
            fields.setTimeRangeLabel("today_noon");
            return;
        }
        if (source.contains("今天晚上") || source.contains("今晚") || source.contains("晚上")) {
            fields.setTimeRangeLabel("today_evening");
            return;
        }
        if (source.contains("后天")) {
            fields.setTimeRangeLabel("day_after_tomorrow");
            return;
        }
        if (source.contains("明天")) {
            fields.setTimeRangeLabel("tomorrow");
            return;
        }
        if (source.contains("今天")) {
            fields.setTimeRangeLabel("today");
            return;
        }
        if (containsIndependent(source, "下周")) {
            fields.setTimeRangeLabel("next_week");
            return;
        }
        if (source.contains("本周") || source.contains("这周")) {
            fields.setTimeRangeLabel("this_week");
        }
    }

    private void resolveMeetingDate(String source, AiAssistantIntentFields fields, LocalDate today) {
        if (fields.getMeetingDate() != null) {
            return;
        }
        Matcher directDate = DATE_PATTERN.matcher(source);
        if (directDate.find()) {
            fields.setMeetingDate(directDate.group(1));
            return;
        }
        String label = fields.getTimeRangeLabel();
        if ("today".equals(label) || label != null && label.startsWith("today_")) {
            fields.setMeetingDate(today.toString());
            return;
        }
        if ("tomorrow".equals(label) || label != null && label.startsWith("tomorrow_")) {
            fields.setMeetingDate(today.plusDays(1).toString());
            return;
        }
        if ("day_after_tomorrow".equals(label)) {
            fields.setMeetingDate(today.plusDays(2).toString());
            return;
        }
        if (label != null && label.startsWith("day_3_from_today")) {
            fields.setMeetingDate(today.plusDays(3).toString());
            return;
        }
        Matcher nextWeekday = NEXT_WEEKDAY_PATTERN.matcher(source);
        if (nextWeekday.find()) {
            DayOfWeek target = WEEKDAY_MAP.get(nextWeekday.group(1));
            fields.setMeetingDate(resolveNextWeekday(today, target).toString());
        }
    }

    private void resolveExplicitClockRange(String source, AiAssistantIntentFields fields) {
        if (fields.getStartClock() != null && fields.getEndClock() != null) {
            return;
        }
        Matcher matcher = TIME_RANGE_PATTERN.matcher(source);
        if (!matcher.find()) {
            return;
        }
        String period = matcher.group(1);
        int startHour = Integer.parseInt(matcher.group(2));
        int endHour = Integer.parseInt(matcher.group(4));
        int startMinute = matcher.group(3) == null ? 0 : Integer.parseInt(matcher.group(3));
        int endMinute = matcher.group(5) == null ? 0 : Integer.parseInt(matcher.group(5));
        fields.setStartClock(formatClock(adjustHour(startHour, period), startMinute));
        fields.setEndClock(formatClock(adjustHour(endHour, period), endMinute));
    }

    private void resolveSingleClock(String source, AiAssistantIntentFields fields) {
        if (fields.getStartClock() != null) {
            return;
        }
        Matcher matcher = SINGLE_CLOCK_PATTERN.matcher(source);
        if (!matcher.find()) {
            return;
        }
        String period = matcher.group(2);
        int hour = Integer.parseInt(matcher.group(3));
        int minute = matcher.group(4) == null ? 0 : Integer.parseInt(matcher.group(4));
        fields.setStartClock(formatClock(adjustHour(hour, period), minute));
    }

    private void resolveDateWindow(AiAssistantIntentFields fields, LocalDate today) {
        if (fields.getDateFrom() != null && fields.getDateTo() != null) {
            return;
        }
        String label = fields.getTimeRangeLabel();
        LocalDate meetingDate = fields.getMeetingDate() == null ? null : LocalDate.parse(fields.getMeetingDate());

        if ("this_week".equals(label)) {
            LocalDate monday = today.with(DayOfWeek.MONDAY);
            setWindow(fields, monday.atStartOfDay(), monday.plusDays(7).atStartOfDay());
            return;
        }
        if ("next_week".equals(label)) {
            LocalDate monday = today.with(TemporalAdjusters.next(DayOfWeek.MONDAY));
            setWindow(fields, monday.atStartOfDay(), monday.plusDays(7).atStartOfDay());
            return;
        }
        if ("week_after_next".equals(label)) {
            LocalDate monday = today.with(TemporalAdjusters.next(DayOfWeek.MONDAY)).plusDays(7);
            setWindow(fields, monday.atStartOfDay(), monday.plusDays(7).atStartOfDay());
            return;
        }
        if ("this_weekend".equals(label)) {
            LocalDate saturday = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY));
            setWindow(fields, saturday.atStartOfDay(), saturday.plusDays(2).atStartOfDay());
            return;
        }
        if ("next_weekend".equals(label)) {
            LocalDate saturday = today.with(TemporalAdjusters.next(DayOfWeek.SATURDAY));
            setWindow(fields, saturday.atStartOfDay(), saturday.plusDays(2).atStartOfDay());
            return;
        }
        if ("last".equals(label)) {
            setWindow(fields, today.minusDays(30).atStartOfDay(), today.plusDays(1).atStartOfDay());
            return;
        }
        if (meetingDate == null) {
            return;
        }

        LocalDateTime from = meetingDate.atStartOfDay();
        LocalDateTime to = meetingDate.plusDays(1).atStartOfDay();
        switch (label == null ? "" : label) {
            case "today_morning", "tomorrow_morning", "next_monday_morning", "next_tuesday_morning", "day_3_from_today_morning" -> {
                from = meetingDate.atTime(9, 0);
                to = meetingDate.atTime(12, 0);
            }
            case "today_afternoon", "tomorrow_afternoon" -> {
                from = meetingDate.atTime(12, 0);
                to = meetingDate.atTime(18, 0);
            }
            case "today_noon", "tomorrow_noon" -> {
                from = meetingDate.atTime(11, 0);
                to = meetingDate.atTime(13, 30);
            }
            case "today_evening", "tomorrow_evening", "day_3_from_today_evening" -> {
                from = meetingDate.atTime(18, 0);
                to = meetingDate.atTime(21, 0);
            }
            default -> {
                if (fields.getStartClock() != null && fields.getEndClock() != null) {
                    from = meetingDate.atTime(LocalTime.parse(fields.getStartClock()));
                    to = meetingDate.atTime(LocalTime.parse(fields.getEndClock()));
                }
            }
        }
        setWindow(fields, from, to);
    }

    private LocalDate resolveNextWeekday(LocalDate today, DayOfWeek target) {
        LocalDate nextMonday = today.with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        return nextMonday.with(TemporalAdjusters.nextOrSame(target));
    }

    private String resolvePeriodSuffix(String period) {
        if (period == null) {
            return "";
        }
        return switch (period) {
            case "上午" -> "morning";
            case "下午" -> "afternoon";
            case "中午" -> "noon";
            case "晚上", "傍晚" -> "evening";
            default -> "";
        };
    }

    private int adjustHour(int hour, String period) {
        if (period == null) {
            return hour > 0 && hour <= 7 ? hour + 12 : hour;
        }
        return switch (period) {
            case "下午", "晚上", "傍晚" -> hour < 12 ? hour + 12 : hour;
            case "中午" -> hour < 11 ? hour + 12 : hour;
            default -> hour;
        };
    }

    private String formatClock(int hour, int minute) {
        return String.format("%02d:%02d", hour, minute);
    }

    private void setWindow(AiAssistantIntentFields fields, LocalDateTime from, LocalDateTime to) {
        fields.setDateFrom(from.format(DATE_TIME));
        fields.setDateTo(to.format(DATE_TIME));
    }

    private boolean containsIndependent(String source, String keyword) {
        int index = source.indexOf(keyword);
        while (index >= 0) {
            if (index == 0 || source.charAt(index - 1) != '查') {
                return true;
            }
            index = source.indexOf(keyword, index + 1);
        }
        return false;
    }
}
