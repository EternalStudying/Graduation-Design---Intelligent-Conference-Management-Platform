package com.llf.assistant.handler;

import com.llf.assistant.AiAssistantActionHandler;
import com.llf.assistant.AiAssistantActionPlan;
import com.llf.assistant.AiAssistantExecutionResult;
import com.llf.assistant.AiAssistantHelper;
import com.llf.assistant.AiAssistantSessionStore;
import com.llf.auth.AuthUser;
import com.llf.dto.reservation.ReservationRecommendationDTO;
import com.llf.service.ReservationService;
import com.llf.service.RoomService;
import com.llf.vo.assistant.AiAssistantFieldOptionVO;
import com.llf.vo.assistant.AiAssistantResultVO;
import com.llf.vo.room.RoomPageDataVO;
import com.llf.vo.room.RoomPageItemVO;
import com.llf.vo.reservation.ReservationRecommendationItemVO;
import com.llf.vo.reservation.ReservationRecommendationVO;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class RoomAssistantActionHandler implements AiAssistantActionHandler {

    private static final Pattern DATE_PATTERN = Pattern.compile("(20\\d{2}-\\d{2}-\\d{2})");
    private static final Pattern TIME_RANGE_PATTERN = Pattern.compile("(\\d{1,2}:\\d{2})\\s*(?:到|至|-|~)\\s*(\\d{1,2}:\\d{2})");
    private static final Pattern ATTENDEES_PATTERN = Pattern.compile("(\\d{1,3})\\s*人");

    private final ReservationService reservationService;
    private final RoomService roomService;

    public RoomAssistantActionHandler(ReservationService reservationService, RoomService roomService) {
        this.reservationService = reservationService;
        this.roomService = roomService;
    }

    @Override
    public List<String> supportedActionTypes() {
        return List.of("rooms.search", "rooms.detail");
    }

    @Override
    public AiAssistantActionPlan process(String actionType, AuthUser currentUser, AiAssistantSessionStore.Session session, String message) {
        return switch (actionType) {
            case "rooms.search" -> handleRoomSearch(message);
            case "rooms.detail" -> handleRoomDetail(session, message);
            default -> AiAssistantActionPlan.error("当前还不支持这个会议室动作。", defaultSuggestions(), errorResult("动作不支持"));
        };
    }

    @Override
    public AiAssistantExecutionResult execute(String actionType, AuthUser currentUser, Map<String, Object> params) {
        return AiAssistantExecutionResult.error("查询动作不需要确认执行。", "动作无需执行", List.of());
    }

    private AiAssistantActionPlan handleRoomSearch(String message) {
        Integer attendees = parseAttendees(message);
        String meetingDate = parseDate(message);
        String[] range = parseTimeRange(message);
        if (attendees != null && meetingDate != null && range != null) {
            ReservationRecommendationDTO dto = new ReservationRecommendationDTO();
            dto.setAttendees(attendees);
            dto.setStartTime(meetingDate + " " + range[0] + ":00");
            dto.setEndTime(meetingDate + " " + range[1] + ":00");
            ReservationRecommendationVO recommendation = reservationService.recommend(dto);
            List<ReservationRecommendationItemVO> items = recommendation == null ? List.of() : recommendation.getRecommendations();
            if (items == null || items.isEmpty()) {
                return AiAssistantActionPlan.reply(
                        "按你给的时间和人数，暂时没有筛到合适的空闲会议室。你可以换个时段再试。",
                        List.of("换成今天下午 16:00 到 17:00", "放宽到 8 人会议室", "查看全部可用会议室")
                );
            }
            StringBuilder builder = new StringBuilder("按你给的时间和人数，可优先看：");
            for (int i = 0; i < Math.min(3, items.size()); i++) {
                ReservationRecommendationItemVO item = items.get(i);
                if (i > 0) {
                    builder.append("；");
                }
                builder.append(item.getRoomName())
                        .append("（")
                        .append(item.getLocation())
                        .append("，")
                        .append(item.getCapacity())
                        .append(" 人）");
            }
            builder.append("。");
            return AiAssistantActionPlan.reply(builder.toString(), List.of("查看其中一个会议室详情", "帮我创建一个预约", "只看带视频设备的会议室"));
        }

        String capacityType = resolveCapacityType(attendees);
        String location = resolveLocation(message);
        RoomPageDataVO page = roomService.pageRooms(1, 6, null, "AVAILABLE", capacityType, location, null);
        List<RoomPageItemVO> rooms = page == null || page.getList() == null ? List.of() : page.getList();
        if (rooms.isEmpty()) {
            return AiAssistantActionPlan.reply("当前没有找到符合条件的会议室。", List.of("查看全部会议室", "帮我创建一个预约", "查看会议室详情"));
        }

        StringBuilder builder = new StringBuilder("当前可用会议室包括：");
        for (int i = 0; i < Math.min(3, rooms.size()); i++) {
            RoomPageItemVO room = rooms.get(i);
            if (i > 0) {
                builder.append("；");
            }
            builder.append(room.getName())
                    .append("（")
                    .append(room.getLocation())
                    .append("，")
                    .append(room.getCapacity())
                    .append(" 人）");
        }
        builder.append("。");
        return AiAssistantActionPlan.reply(builder.toString(), List.of("查看其中一个会议室详情", "帮我创建一个预约", "只看当前楼层的会议室"));
    }

    private AiAssistantActionPlan handleRoomDetail(AiAssistantSessionStore.Session session, String message) {
        Map<String, Object> draft = session.getDraft();
        Long roomId = AiAssistantHelper.longValue(draft, "roomId");
        if (roomId != null) {
            session.setLastRoomId(roomId);
            return roomDetailReply(roomService.userDetailById(roomId));
        }

        RoomPageDataVO page = roomService.pageRooms(1, 20, null, null, null, null, null);
        List<RoomPageItemVO> rooms = page == null || page.getList() == null ? List.of() : page.getList();
        List<RoomPageItemVO> matched = rooms.stream()
                .filter(item -> matchesRoom(message, item))
                .toList();

        if (matched.size() == 1) {
            RoomPageItemVO room = matched.get(0);
            draft.put("roomId", room.getId());
            session.setLastRoomId(room.getId());
            return roomDetailReply(roomService.userDetailById(room.getId()));
        }

        List<AiAssistantFieldOptionVO> options = rooms.stream()
                .map(item -> AiAssistantHelper.option(item.getName() + " · " + item.getLocation() + " · " + item.getCapacity() + " 人", item.getId()))
                .toList();
        String text = matched.size() > 1
                ? "我匹配到了多间会议室，请先明确选择一间。"
                : "请先告诉我你想查看哪一间会议室。";
        return AiAssistantActionPlan.collect(
                text,
                List.of("查看云杉会议室详情", "查看潮汐会议室详情"),
                List.of(AiAssistantHelper.field("roomId", "目标会议室", "select", true, "请选择会议室", null, options))
        );
    }

    private AiAssistantActionPlan roomDetailReply(RoomPageItemVO room) {
        String description = room.getDescription() == null || room.getDescription().isBlank() ? "暂无额外说明" : room.getDescription();
        String deviceSummary = room.getDeviceBindingSummary() == null || room.getDeviceBindingSummary().isBlank()
                ? "暂无设备信息"
                : room.getDeviceBindingSummary();
        return AiAssistantActionPlan.reply(
                room.getName() + " 位于 " + room.getLocation() + "，可容纳 " + room.getCapacity() + " 人，当前状态为 " + room.getStatus()
                        + "，设备情况：" + deviceSummary + "，备注：" + description + "。",
                List.of("帮我预约这间会议室", "查看其他会议室", "查看日历安排")
        );
    }

    private boolean matchesRoom(String message, RoomPageItemVO room) {
        if (message == null || message.isBlank()) {
            return false;
        }
        return message.contains(room.getName())
                || (room.getRoomCode() != null && message.contains(room.getRoomCode()))
                || (room.getLocation() != null && message.contains(room.getLocation()));
    }

    private Integer parseAttendees(String message) {
        if (message == null) {
            return null;
        }
        Matcher matcher = ATTENDEES_PATTERN.matcher(message);
        return matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
    }

    private String parseDate(String message) {
        if (message == null) {
            return null;
        }
        Matcher matcher = DATE_PATTERN.matcher(message);
        if (matcher.find()) {
            return matcher.group(1);
        }
        LocalDate today = LocalDate.now();
        if (message.contains("今天")) {
            return today.toString();
        }
        if (message.contains("明天")) {
            return today.plusDays(1).toString();
        }
        if (message.contains("后天")) {
            return today.plusDays(2).toString();
        }
        return null;
    }

    private String[] parseTimeRange(String message) {
        if (message == null) {
            return null;
        }
        Matcher matcher = TIME_RANGE_PATTERN.matcher(message);
        if (matcher.find()) {
            return new String[]{normalizeClock(matcher.group(1)), normalizeClock(matcher.group(2))};
        }
        if (message.contains("上午")) {
            return new String[]{"09:00", "12:00"};
        }
        if (message.contains("下午")) {
            return new String[]{"14:00", "18:00"};
        }
        if (message.contains("晚上")) {
            return new String[]{"18:00", "21:00"};
        }
        return null;
    }

    private String resolveCapacityType(Integer attendees) {
        if (attendees == null) {
            return null;
        }
        if (attendees <= 8) {
            return "small";
        }
        if (attendees <= 15) {
            return "medium";
        }
        return "large";
    }

    private String resolveLocation(String message) {
        if (message == null) {
            return null;
        }
        for (String token : List.of("1号楼", "2号楼", "3号楼", "4号楼")) {
            if (message.contains(token)) {
                return token;
            }
        }
        return null;
    }

    private String normalizeClock(String clock) {
        String[] parts = clock.trim().split(":");
        return String.format("%02d:%02d", Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
    }

    private AiAssistantResultVO errorResult(String title) {
        AiAssistantResultVO result = new AiAssistantResultVO();
        result.setStatus("error");
        result.setTitle(title);
        return result;
    }

    private List<String> defaultSuggestions() {
        return List.of("今天下午有哪些空闲会议室", "查看云杉会议室详情", "帮我创建一个预约");
    }
}
