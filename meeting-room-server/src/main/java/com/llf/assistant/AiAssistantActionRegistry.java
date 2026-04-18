package com.llf.assistant;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class AiAssistantActionRegistry {

    private final Map<String, AiAssistantActionHandler> handlerMap = new LinkedHashMap<>();

    public AiAssistantActionRegistry(List<AiAssistantActionHandler> handlers) {
        for (AiAssistantActionHandler handler : handlers) {
            for (String actionType : handler.supportedActionTypes()) {
                handlerMap.put(actionType, handler);
            }
        }
    }

    public AiAssistantActionHandler get(String actionType) {
        return handlerMap.get(actionType);
    }

    public String detectActionType(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        String normalized = message.trim().toLowerCase(Locale.ROOT);
        if (containsAny(normalized, "今日安排", "今天安排", "今日日程", "今天日程")) {
            return "overview.todaySchedule.query";
        }
        if (containsAny(normalized, "概览", "概况", "统计", "摘要")) {
            return "overview.summary.query";
        }
        if (containsAny(normalized, "删除预约", "删除我的", "取消预约", "取消我的", "撤销预约", "删掉预约")) {
            return "reservations.cancel";
        }
        if (containsAny(normalized, "取消", "删除", "撤销") && containsAny(normalized, "预约", "会议")) {
            return "reservations.cancel";
        }
        if (containsAny(normalized, "修改", "调整", "改到", "改成") && containsAny(normalized, "预约", "会议")) {
            return "reservations.update";
        }
        if (containsAny(normalized, "评价", "点评", "打分")) {
            return "reservations.review";
        }
        if (containsAny(normalized, "创建预约", "创建一个预约", "帮我预约", "帮我创建", "新增预约", "安排会议")) {
            return "reservations.create";
        }
        if (containsAny(normalized, "预约详情", "这条预约", "这场预约", "详情")) {
            return "reservations.detail";
        }
        if (containsAny(normalized, "我的预约", "本周预约", "查看预约", "预约列表", "有预约", "有没有预约", "是否有预约")) {
            return "reservations.list";
        }
        if (containsAny(normalized, "会议室详情", "查看会议室", "会议室信息")) {
            return "rooms.detail";
        }
        if (containsAny(normalized, "会议室", "空闲", "可用", "找会议室")) {
            return "rooms.search";
        }
        if (containsAny(normalized, "日历", "排期", "日程")) {
            return "calendar.query";
        }
        return null;
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
