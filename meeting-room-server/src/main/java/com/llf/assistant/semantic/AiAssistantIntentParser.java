package com.llf.assistant.semantic;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.llf.assistant.AiAssistantActionRegistry;
import com.llf.assistant.AiAssistantSessionStore;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class AiAssistantIntentParser {

    private static final Pattern DIGIT_ATTENDEES_PATTERN = Pattern.compile("(\\d{1,3})\\s*人");
    private static final Pattern RATING_PATTERN = Pattern.compile("([1-5])\\s*(?:分|星)");
    private static final Pattern ROOM_NAME_PATTERN = Pattern.compile("([\\u4e00-\\u9fa5A-Za-z0-9]+(?:会议室|厅))");
    private static final Pattern SINGLE_CLOCK_PATTERN = Pattern.compile("(?:改到|改成|调到)?\\s*(?:上午|下午|晚上|中午|傍晚)?\\s*(\\d{1,2})(?:(?::(\\d{1,2}))|点)");
    private static final Pattern DURATION_PATTERN = Pattern.compile("(\\d{2,3})分钟");
    private static final Pattern RESERVATION_ID_PATTERN = Pattern.compile("(\\d{3,})");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AiAssistantActionRegistry actionRegistry;
    private final AiAssistantIntentSchemaValidator schemaValidator;
    private final AiAssistantIntentLlmClient llmClient;

    public AiAssistantIntentParser(AiAssistantActionRegistry actionRegistry,
                                   AiAssistantIntentSchemaValidator schemaValidator,
                                   AiAssistantIntentLlmClient llmClient) {
        this.actionRegistry = actionRegistry;
        this.schemaValidator = schemaValidator;
        this.llmClient = llmClient;
    }

    public AiAssistantIntentParseResult parseByRules(String originalText, String normalizedText) {
        AiAssistantIntentParseResult result = new AiAssistantIntentParseResult();
        result.setNormalizedText(normalizedText);
        result.setActionType(resolveActionType(originalText, normalizedText));
        fillBasicFields(result, originalText, normalizedText);
        result.setConfidence(resolveConfidence(result));
        result.setNeedClarification(resolveNeedClarification(result, normalizedText));
        result.setClarificationReason(resolveClarificationReason(result, normalizedText));
        return result;
    }

    public AiAssistantIntentParseResult parseWithSchema(String originalText,
                                                        String normalizedText,
                                                        AiAssistantSessionStore.Session session) {
        try {
            String json = llmClient.parseIntentJson(new AiAssistantIntentLlmRequest(
                    originalText,
                    normalizedText,
                    session == null ? null : session.getCurrentActionType(),
                    session == null ? List.of() : List.copyOf(session.getRecentMessages()),
                    schemaValidator.getSchemaText()
            ));
            if (json == null || json.isBlank() || !schemaValidator.validateJson(json)) {
                return null;
            }
            return objectMapper.readValue(json, AiAssistantIntentParseResult.class);
        } catch (Exception e) {
            return null;
        }
    }

    private String resolveActionType(String originalText, String normalizedText) {
        String source = normalizedText == null ? "" : normalizedText;
        if (containsAny(source, "待审核预约", "待审批预约", "待处理预约")) {
            return "admin.reservations.pending";
        }
        if (containsAny(source, "通过预约", "批准预约", "同意预约", "审核通过")) {
            return "admin.reservations.approve";
        }
        if (containsAny(source, "驳回预约", "拒绝预约", "审核驳回")) {
            return "admin.reservations.reject";
        }
        if (containsAny(source, "概览", "概况", "统计", "摘要")) {
            return "overview.summary.query";
        }
        if (containsAny(source, "安排", "日程", "排期")) {
            if (containsAny(source, "今天", "今晚", "今早", "今天上午", "今天下午", "今天中午", "今天晚上")) {
                return "overview.todaySchedule.query";
            }
            return "calendar.query";
        }
        if (containsAny(source, "会议室详情", "会议室信息", "会议室情况", "详情")
                && (containsAny(source, "会议室", "厅") || parseRoomName(originalText) != null)) {
            return "rooms.detail";
        }
        if (containsAny(source, "空闲会议室", "空闲", "可用会议室", "找会议室", "会议室")) {
            if (containsAny(source, "创建", "预约", "订", "约") && !containsAny(source, "查询预约", "预约数量", "我的预约")) {
                return "reservations.create";
            }
            return "rooms.search";
        }
        if (containsAny(source, "评价", "点评", "评分", "打星", "五星", "四星")) {
            return "reservations.review";
        }
        if (containsAny(source, "取消", "撤销")) {
            return "reservations.cancel";
        }
        if (containsAny(source, "加上", "再加上")
                && containsAny(source, "这个会", "那个会", "这场会", "上次那个会")) {
            return "reservations.update";
        }
        if (containsAny(source, "约上")
                || (containsAny(source, "开个会", "开会") && containsAny(source, "我和", "我跟", "我与"))) {
            return "reservations.create";
        }
        if (containsAny(source, "修改", "调整", "改晚", "提前", "延长", "缩短", "挪", "改到", "改成", "改一下")) {
            return "reservations.update";
        }
        if (containsAny(source, "预约详情", "预约信息", "那个预约", "那场会详情", "会详情")) {
            return "reservations.detail";
        }
        if (containsAny(source, "查询预约", "预约数量", "我的预约", "有会", "有预约", "有没有预约", "是否有预约", "开了几场会", "多少场会")
                || (containsAny(source, "查询") && containsAny(source, "预约", "会"))) {
            return "reservations.list";
        }
        if (containsAny(source, "创建", "新建", "安排会议")
                || (containsAny(source, "预约") && !containsAny(source, "查询", "查询预约", "预约数量", "我的预约", "有预约", "有没有预约", "是否有预约"))) {
            return "reservations.create";
        }
        String actionType = actionRegistry.detectActionType(originalText);
        return actionType == null ? "unknown" : actionType;
    }

    private void fillBasicFields(AiAssistantIntentParseResult result, String originalText, String normalizedText) {
        AiAssistantIntentFields fields = result.getFields();
        String actionType = result.getActionType();
        if (actionType.startsWith("reservations.") || "calendar.query".equals(actionType)) {
            fields.setTargetScope("mine");
        }
        if (actionType.startsWith("admin.reservations.")) {
            fields.setTargetScope("all");
            fields.setReservationId(parseReservationId(normalizedText));
        }
        Integer attendees = parseAttendees(normalizedText);
        if (attendees != null) {
            fields.setAttendees(attendees);
        }
        Integer rating = parseRating(normalizedText);
        if (rating != null) {
            fields.setRating(rating);
        }
        String title = parseTitle(originalText);
        if (title != null) {
            fields.setTitle(title);
        }
        String roomName = parseRoomName(originalText);
        if (roomName != null) {
            fields.setRoomName(roomName);
        }
        String devices = parseDeviceRequirements(normalizedText);
        if (devices != null) {
            fields.setDeviceRequirements(devices);
        }
        String relativeTarget = parseRelativeTarget(normalizedText);
        if (relativeTarget != null) {
            fields.setRelativeTarget(relativeTarget);
        }
        String quantityHint = parseQuantityHint(normalizedText);
        if (quantityHint != null) {
            fields.setQuantityHint(quantityHint);
        }
        fillMutationFields(fields, normalizedText);
        if (fields.getStartClock() == null) {
            String startClock = parseSingleClock(normalizedText);
            if (startClock != null && "reservations.update".equals(actionType)) {
                fields.setStartClock(startClock);
            }
        }
    }

    private double resolveConfidence(AiAssistantIntentParseResult result) {
        if ("unknown".equals(result.getActionType())) {
            return 0.25D;
        }
        if (result.getFields().getRelativeTarget() != null || result.getNormalizedText().contains("那个会")) {
            return 0.68D;
        }
        if (result.getFields().getMutationHint() != null) {
            return 0.82D;
        }
        return 0.93D;
    }

    private boolean resolveNeedClarification(AiAssistantIntentParseResult result, String normalizedText) {
        if ("unknown".equals(result.getActionType())) {
            return true;
        }
        if (containsAny(normalizedText, "那个", "那场", "这场", "上次那个")) {
            return true;
        }
        return switch (result.getActionType()) {
            case "reservations.create", "reservations.update", "reservations.cancel", "reservations.review" -> true;
            default -> false;
        };
    }

    private String resolveClarificationReason(AiAssistantIntentParseResult result, String normalizedText) {
        if ("unknown".equals(result.getActionType())) {
            if (containsAny(normalizedText, "明天的", "那个会", "处理一下")) {
                return "你的意思还不够明确，请再具体一点。告诉我是要查询预约、查看日历，还是处理某一场预约。";
            }
            return "我还不能准确判断你的需求，请再具体一点。";
        }
        return switch (result.getActionType()) {
            case "reservations.create" -> "创建预约还需要会议日期、时间、人数或会议室信息。";
            case "reservations.cancel" -> "取消预约前需要先明确是哪一场预约。";
            case "reservations.update" -> "修改预约前需要先明确目标预约和新的时间或会议室。";
            case "reservations.review" -> "评价预约前需要先明确目标预约和评分。";
            default -> result.isNeedClarification() ? "请再补充一点关键业务信息。" : null;
        };
    }

    private Integer parseAttendees(String text) {
        if (text == null) {
            return null;
        }
        Matcher matcher = DIGIT_ATTENDEES_PATTERN.matcher(text);
        return matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
    }

    private Integer parseRating(String text) {
        if (text == null) {
            return null;
        }
        Matcher matcher = RATING_PATTERN.matcher(text);
        if (matcher.find()) {
            return Integer.valueOf(matcher.group(1));
        }
        if (text.contains("五星")) {
            return 5;
        }
        if (text.contains("四星")) {
            return 4;
        }
        if (text.contains("三星")) {
            return 3;
        }
        return null;
    }

    private String parseTitle(String text) {
        if (text == null) {
            return null;
        }
        if (text.contains("周会")) {
            return "周会";
        }
        if (text.contains("复盘")) {
            return "复盘会";
        }
        if (text.contains("评审")) {
            return "评审会";
        }
        if (text.contains("沟通")) {
            return "沟通会";
        }
        return null;
    }

    private String parseRoomName(String text) {
        if (text == null) {
            return null;
        }
        Matcher matcher = ROOM_NAME_PATTERN.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String parseDeviceRequirements(String text) {
        if (text == null) {
            return null;
        }
        List<String> values = new ArrayList<>();
        if (text.contains("投影")) {
            values.add("投影");
        }
        if (text.contains("视频会议")) {
            values.add("视频会议");
        }
        if (text.contains("白板")) {
            values.add("白板");
        }
        if (text.contains("大屏")) {
            values.add("大屏");
        }
        return values.isEmpty() ? null : String.join("、", values);
    }

    private String parseRelativeTarget(String text) {
        if (text == null) {
            return null;
        }
        if (containsAny(text, "上次那个", "上次", "上一个")) {
            return "last";
        }
        return null;
    }

    private String parseQuantityHint(String text) {
        if (text == null) {
            return null;
        }
        return containsAny(text, "预约数量", "几场会", "多少场会", "几场预约") ? "count" : null;
    }

    private void fillMutationFields(AiAssistantIntentFields fields, String text) {
        if (text == null) {
            return;
        }
        Integer minutes = parseDurationMinutes(text);
        if (containsAny(text, "改晚一点", "改晚", "往后挪", "往后拖")) {
            fields.setMutationHint("delay_later");
            fields.setTimeShiftMinutes(minutes == null ? 30 : minutes);
            return;
        }
        if (containsAny(text, "提前")) {
            fields.setMutationHint("delay_earlier");
            fields.setTimeShiftMinutes(minutes == null ? 30 : minutes);
            return;
        }
        if (containsAny(text, "延长")) {
            fields.setMutationHint("extend");
            fields.setTimeShiftMinutes(minutes == null ? 30 : minutes);
            return;
        }
        if (containsAny(text, "缩短")) {
            fields.setMutationHint("shorten");
            fields.setTimeShiftMinutes(minutes == null ? 30 : minutes);
        }
    }

    private Integer parseDurationMinutes(String text) {
        if (text == null) {
            return null;
        }
        Matcher matcher = DURATION_PATTERN.matcher(text);
        return matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
    }

    private Long parseReservationId(String text) {
        if (text == null) {
            return null;
        }
        Matcher matcher = RESERVATION_ID_PATTERN.matcher(text);
        return matcher.find() ? Long.valueOf(matcher.group(1)) : null;
    }

    private String parseSingleClock(String text) {
        if (text == null) {
            return null;
        }
        Matcher matcher = SINGLE_CLOCK_PATTERN.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        int hour = Integer.parseInt(matcher.group(1));
        int minute = matcher.group(2) == null ? 0 : Integer.parseInt(matcher.group(2));
        String source = text.toLowerCase(Locale.ROOT);
        if ((source.contains("下午") || source.contains("晚上") || source.contains("傍晚")) && hour < 12) {
            hour += 12;
        } else if (source.contains("中午") && hour < 11) {
            hour += 12;
        } else if (!source.contains("上午") && hour > 0 && hour <= 7) {
            hour += 12;
        }
        return String.format("%02d:%02d", hour, minute);
    }

    private boolean containsAny(String text, String... fragments) {
        if (text == null) {
            return false;
        }
        for (String fragment : fragments) {
            if (text.contains(fragment)) {
                return true;
            }
        }
        return false;
    }
}
