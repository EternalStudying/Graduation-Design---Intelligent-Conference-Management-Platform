package com.llf.assistant.handler;

import com.llf.assistant.AiAssistantActionHandler;
import com.llf.assistant.AiAssistantActionPlan;
import com.llf.assistant.AiAssistantExecutionResult;
import com.llf.assistant.AiAssistantSessionStore;
import com.llf.assistant.AiAssistantHelper;
import com.llf.auth.AuthUser;
import com.llf.service.DashboardService;
import com.llf.vo.dashboard.DashboardOverviewSummaryVO;
import com.llf.vo.dashboard.DashboardOverviewVO;
import com.llf.vo.dashboard.DashboardTodayScheduleVO;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class OverviewAssistantActionHandler implements AiAssistantActionHandler {

    private final DashboardService dashboardService;

    public OverviewAssistantActionHandler(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @Override
    public List<String> supportedActionTypes() {
        return List.of("overview.summary.query", "overview.todaySchedule.query");
    }

    @Override
    public AiAssistantActionPlan process(String actionType, AuthUser currentUser, AiAssistantSessionStore.Session session, String message) {
        DashboardOverviewVO overview = dashboardService.getOverview();
        if ("overview.todaySchedule.query".equals(actionType)) {
            return AiAssistantActionPlan.reply(buildTodayScheduleText(overview.getTodaySchedules()), List.of(
                    "查看我本周的预约",
                    "今天下午有哪些空闲会议室",
                    "帮我创建一个预约"
            ));
        }
        return AiAssistantActionPlan.reply(buildSummaryText(overview), List.of(
                "查看今天的安排",
                "查看我本周的预约",
                "今天下午有哪些空闲会议室"
        ));
    }

    @Override
    public AiAssistantExecutionResult execute(String actionType, AuthUser currentUser, Map<String, Object> params) {
        return AiAssistantExecutionResult.error("查询动作不需要确认执行。", "动作不支持执行", List.of());
    }

    private String buildSummaryText(DashboardOverviewVO overview) {
        DashboardOverviewSummaryVO summary = overview.getSummary();
        if (summary == null) {
            return "当前暂无概览数据。";
        }
        return "今日共有 " + safe(summary.getTodayMeetingCount())
                + " 场会议，待处理预约 " + safe(summary.getPendingCount())
                + " 条，可用会议室 " + safe(summary.getAvailableRoomCount())
                + " 间，总体利用率约 " + safe(summary.getUtilizationRate()) + "%。";
    }

    private String buildTodayScheduleText(List<DashboardTodayScheduleVO> schedules) {
        if (schedules == null || schedules.isEmpty()) {
            return "你今天还没有安排中的会议。";
        }
        StringBuilder builder = new StringBuilder("你今天共有 ").append(schedules.size()).append(" 场安排");
        int limit = Math.min(3, schedules.size());
        for (int i = 0; i < limit; i++) {
            DashboardTodayScheduleVO item = schedules.get(i);
            builder.append(i == 0 ? "，包括：" : "；");
            builder.append(item.getTitle())
                    .append("（")
                    .append(item.getRoomName())
                    .append("）");
        }
        builder.append("。");
        return builder.toString();
    }

    private int safe(Integer value) {
        return value == null ? 0 : value;
    }
}
