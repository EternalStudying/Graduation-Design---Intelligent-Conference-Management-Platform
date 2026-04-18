package com.llf.assistant.semantic;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class AiAssistantTextNormalizer {

    private static final Pattern CHINESE_COUNT_PATTERN = Pattern.compile("([一二两三四五六七八九十百\\d]+(?:来|几)?)\\s*个?人");
    private static final Pattern CHINESE_OCLOCK_HALF_PATTERN = Pattern.compile("([一二两三四五六七八九十\\d]+)点半");
    private static final Pattern CHINESE_OCLOCK_PATTERN = Pattern.compile("([一二两三四五六七八九十\\d]+)点");
    private static final Pattern HOURS_LATER_PATTERN = Pattern.compile("([一二两三四五六七八九十\\d]+)个?小时后");

    private static final Map<String, String> DIRECT_REPLACEMENTS = new LinkedHashMap<>();

    static {
        DIRECT_REPLACEMENTS.put("帮我把", "");
        DIRECT_REPLACEMENTS.put("帮我", "");
        DIRECT_REPLACEMENTS.put("给我", "");
        DIRECT_REPLACEMENTS.put("麻烦你", "");
        DIRECT_REPLACEMENTS.put("麻烦", "");
        DIRECT_REPLACEMENTS.put("我想", "");
        DIRECT_REPLACEMENTS.put("我要", "");
        DIRECT_REPLACEMENTS.put("想请你", "");
        DIRECT_REPLACEMENTS.put("请", "");
        DIRECT_REPLACEMENTS.put("查一下", "查询");
        DIRECT_REPLACEMENTS.put("查下", "查询");
        DIRECT_REPLACEMENTS.put("看下", "查询");
        DIRECT_REPLACEMENTS.put("看看", "查询");
        DIRECT_REPLACEMENTS.put("撤掉", "取消");
        DIRECT_REPLACEMENTS.put("撤了", "取消");
        DIRECT_REPLACEMENTS.put("删掉", "取消");
        DIRECT_REPLACEMENTS.put("删了", "取消");
        DIRECT_REPLACEMENTS.put("删除", "取消");
        DIRECT_REPLACEMENTS.put("订个", "创建");
        DIRECT_REPLACEMENTS.put("订一个", "创建");
        DIRECT_REPLACEMENTS.put("定个", "创建");
        DIRECT_REPLACEMENTS.put("定一个", "创建");
        DIRECT_REPLACEMENTS.put("约个", "创建");
        DIRECT_REPLACEMENTS.put("约一个", "创建");
        DIRECT_REPLACEMENTS.put("建个", "创建");
        DIRECT_REPLACEMENTS.put("新建", "创建");
        DIRECT_REPLACEMENTS.put("往后挪", "改晚");
        DIRECT_REPLACEMENTS.put("往后拖", "改晚");
        DIRECT_REPLACEMENTS.put("改晚点", "改晚一点");
        DIRECT_REPLACEMENTS.put("那场会", "那个会");
        DIRECT_REPLACEMENTS.put("上个会", "上次那个会");
        DIRECT_REPLACEMENTS.put("上一个会", "上次那个会");
        DIRECT_REPLACEMENTS.put("打个分", "评价");
        DIRECT_REPLACEMENTS.put("打分", "评价");
        DIRECT_REPLACEMENTS.put("评个", "评价");
        DIRECT_REPLACEMENTS.put("评一下", "评价");
        DIRECT_REPLACEMENTS.put("啥情况", "详情");
        DIRECT_REPLACEMENTS.put("什么情况", "详情");
        DIRECT_REPLACEMENTS.put("空着", "空闲");
        DIRECT_REPLACEMENTS.put("房间", "会议室");
        DIRECT_REPLACEMENTS.put("投屏", "投影");
        DIRECT_REPLACEMENTS.put("视频会", "视频会议");
        DIRECT_REPLACEMENTS.put("今早", "今天上午");
        DIRECT_REPLACEMENTS.put("明早", "明天上午");
        DIRECT_REPLACEMENTS.put("今晚", "今天晚上");
        DIRECT_REPLACEMENTS.put("今儿", "今天");
    }

    public String normalize(String userText) {
        if (userText == null) {
            return "";
        }
        String text = userText.trim();
        for (Map.Entry<String, String> entry : DIRECT_REPLACEMENTS.entrySet()) {
            text = text.replace(entry.getKey(), entry.getValue());
        }
        text = normalizeCounts(text);
        text = text.replaceAll("点(\\d{1,3}人)", "点 $1");
        text = normalizeClock(text);
        text = normalizeDurations(text);
        text = normalizeIntentHints(text);
        return text.replace("？", "").replace("?", "").replaceAll("\\s+", "").trim();
    }

    private String normalizeCounts(String text) {
        Matcher matcher = CHINESE_COUNT_PATTERN.matcher(text);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            Integer value = parseApproximateChineseNumber(matcher.group(1));
            String replacement = value == null ? matcher.group() : value + "人";
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String normalizeClock(String text) {
        Matcher halfMatcher = CHINESE_OCLOCK_HALF_PATTERN.matcher(text);
        StringBuffer halfBuffer = new StringBuffer();
        while (halfMatcher.find()) {
            Integer hour = parseApproximateChineseNumber(halfMatcher.group(1));
            String replacement = hour == null ? halfMatcher.group() : hour + ":30";
            halfMatcher.appendReplacement(halfBuffer, Matcher.quoteReplacement(replacement));
        }
        halfMatcher.appendTail(halfBuffer);

        Matcher clockMatcher = CHINESE_OCLOCK_PATTERN.matcher(halfBuffer.toString());
        StringBuffer clockBuffer = new StringBuffer();
        while (clockMatcher.find()) {
            Integer hour = parseApproximateChineseNumber(clockMatcher.group(1));
            String replacement = hour == null ? clockMatcher.group() : hour + "点";
            clockMatcher.appendReplacement(clockBuffer, Matcher.quoteReplacement(replacement));
        }
        clockMatcher.appendTail(clockBuffer);
        return clockBuffer.toString();
    }

    private String normalizeDurations(String text) {
        text = text.replace("半个小时", "30分钟");
        text = text.replace("半小时", "30分钟");
        text = text.replace("一个小时", "60分钟");
        text = text.replace("一小时", "60分钟");

        Matcher matcher = HOURS_LATER_PATTERN.matcher(text);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            Integer hours = parseApproximateChineseNumber(matcher.group(1));
            String replacement = hours == null ? matcher.group() : (hours * 60) + "分钟后";
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String normalizeIntentHints(String text) {
        if (text.contains("有没有预约")) {
            text = text.replace("有没有预约", "查询预约");
        }
        if (text.contains("是否有预约")) {
            text = text.replace("是否有预约", "查询预约");
        }
        if (text.contains("有预约")) {
            text = text.replace("有预约", "查询预约");
        }
        if (text.contains("有会")) {
            text = text.replace("有会", "查询预约");
        }
        if (text.contains("几场会") || text.contains("多少场会")) {
            text = text.replace("几场会", "查询预约数量");
            text = text.replace("多少场会", "查询预约数量");
        }
        return text;
    }

    private Integer parseApproximateChineseNumber(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String value = token.trim();
        if (value.matches("\\d+")) {
            return Integer.parseInt(value);
        }
        boolean fuzzy = value.endsWith("来") || value.endsWith("几");
        if (fuzzy) {
            value = value.substring(0, value.length() - 1);
        }
        value = value.replace("两", "二");
        if (value.isBlank()) {
            return null;
        }
        if ("十".equals(value)) {
            return 10;
        }
        if (value.contains("十")) {
            String[] parts = value.split("十", -1);
            int tens = parts[0].isBlank() ? 1 : singleDigit(parts[0]);
            int ones = parts.length < 2 || parts[1].isBlank() ? 0 : singleDigit(parts[1]);
            return tens * 10 + ones;
        }
        return singleDigit(value);
    }

    private int singleDigit(String token) {
        return switch (token) {
            case "一" -> 1;
            case "二" -> 2;
            case "三" -> 3;
            case "四" -> 4;
            case "五" -> 5;
            case "六" -> 6;
            case "七" -> 7;
            case "八" -> 8;
            case "九" -> 9;
            default -> 0;
        };
    }
}
