package com.llf.assistant.handler;

import com.llf.assistant.AiAssistantActionHandler;
import com.llf.assistant.AiAssistantActionPlan;
import com.llf.assistant.AiAssistantExecutionResult;
import com.llf.assistant.AiAssistantSessionStore;
import com.llf.auth.AuthUser;
import com.llf.service.ReservationService;
import com.llf.vo.reservation.CalendarEventVO;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class CalendarAssistantActionHandler implements AiAssistantActionHandler {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Pattern DATE_PATTERN = Pattern.compile("(20\\d{2}-\\d{2}-\\d{2})");

    private final ReservationService reservationService;

    public CalendarAssistantActionHandler(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @Override
    public List<String> supportedActionTypes() {
        return List.of("calendar.query");
    }

    @Override
    public AiAssistantActionPlan process(String actionType, AuthUser currentUser, AiAssistantSessionStore.Session session, String message) {
        TimeWindow window = resolveWindow(message);
        List<CalendarEventVO> events = reservationService.listCalendar(window.start(), window.end(), null, "ACTIVE");
        if (events == null || events.isEmpty()) {
            return AiAssistantActionPlan.reply(window.label() + " 日历里暂时没有查到安排。", List.of("查看我的预约", "帮我创建一个预约", "查看会议室"));
        }

        StringBuilder builder = new StringBuilder(window.label()).append(" 日历里共有 ").append(events.size()).append(" 场安排");
        for (int i = 0; i < Math.min(3, events.size()); i++) {
            CalendarEventVO event = events.get(i);
            builder.append(i == 0 ? "，包括：" : "；")
                    .append(event.getTitle())
                    .append("（")
                    .append(event.getRoomName())
                    .append("，")
                    .append(shortTime(event.getStartTime()))
                    .append("-")
                    .append(shortTime(event.getEndTime()))
                    .append("）");
        }
        builder.append("。");
        return AiAssistantActionPlan.reply(builder.toString(), List.of("查看我的预约", "帮我创建一个预约", "查看会议室详情"));
    }

    @Override
    public AiAssistantExecutionResult execute(String actionType, AuthUser currentUser, Map<String, Object> params) {
        return AiAssistantExecutionResult.error("查询动作不需要确认执行。", "动作无需执行", List.of());
    }

    private TimeWindow resolveWindow(String message) {
        LocalDate today = LocalDate.now();
        if (message != null) {
            Matcher matcher = DATE_PATTERN.matcher(message);
            if (matcher.find()) {
                LocalDate target = LocalDate.parse(matcher.group(1));
                return new TimeWindow(target.atStartOfDay().format(DATE_TIME), target.plusDays(1).atStartOfDay().format(DATE_TIME), matcher.group(1));
            }
            if (message.contains("明天")) {
                LocalDate target = today.plusDays(1);
                return new TimeWindow(target.atStartOfDay().format(DATE_TIME), target.plusDays(1).atStartOfDay().format(DATE_TIME), "明天");
            }
            if (message.contains("后天")) {
                LocalDate target = today.plusDays(2);
                return new TimeWindow(target.atStartOfDay().format(DATE_TIME), target.plusDays(1).atStartOfDay().format(DATE_TIME), "后天");
            }
            if (message.contains("本周")) {
                LocalDate monday = today.with(DayOfWeek.MONDAY);
                return new TimeWindow(monday.atStartOfDay().format(DATE_TIME), monday.plusDays(7).atStartOfDay().format(DATE_TIME), "本周");
            }
        }
        return new TimeWindow(today.atStartOfDay().format(DATE_TIME), today.plusDays(1).atStartOfDay().format(DATE_TIME), "今天");
    }

    private String shortTime(String dateTime) {
        return dateTime == null || dateTime.length() < 16 ? dateTime : dateTime.substring(11, 16);
    }

    private record TimeWindow(String start, String end, String label) {
    }
}
