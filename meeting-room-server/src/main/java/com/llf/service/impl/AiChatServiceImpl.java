package com.llf.service.impl;

import com.llf.ai.AiChatSessionStore;
import com.llf.ai.ReservationAssistantLlmClient;
import com.llf.ai.ReservationAssistantLlmRequest;
import com.llf.ai.ReservationKnowledgeService;
import com.llf.auth.AuthUser;
import com.llf.dto.reservation.ReservationRecommendationDTO;
import com.llf.mapper.ReservationMapper;
import com.llf.mapper.RoomMapper;
import com.llf.result.BizException;
import com.llf.service.AiChatService;
import com.llf.service.ReservationService;
import com.llf.util.DateTimeUtils;
import com.llf.vo.assistant.AiChatResponseVO;
import com.llf.vo.assistant.AiChatSessionVO;
import com.llf.vo.reservation.MyReservationVO;
import com.llf.vo.reservation.ReservationRecommendationItemVO;
import com.llf.vo.reservation.ReservationRecommendationVO;
import com.llf.vo.room.RoomListItemVO;
import com.llf.vo.room.RoomPageDeviceVO;
import jakarta.annotation.PreDestroy;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AiChatServiceImpl implements AiChatService {

    private static final String RESERVATION_SCENE = "reservation";
    private static final List<String> DEFAULT_SUGGESTIONS = List.of(
            "帮我找今天下午可用的10人会议室",
            "查看我本周的预约",
            "判断明天下午3点是否容易冲突"
    );
    private static final List<String> ROOM_SUGGESTIONS = List.of(
            "优先推荐带投影的会议室",
            "查看14:00到16:00的空闲时段",
            "只看1号楼A区的会议室"
    );
    private static final List<String> RESERVATION_SUGGESTIONS = List.of(
            "只看今天剩余的预约",
            "帮我找这个时间附近的空闲会议室",
            "判断明天下午3点是否容易冲突"
    );
    private static final List<String> CONFLICT_SUGGESTIONS = List.of(
            "改成14:00开始是否更稳妥",
            "推荐冲突更少的时间段",
            "帮我找同时间可用会议室"
    );
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter SHORT_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final Pattern ATTENDEES_PATTERN = Pattern.compile("(\\d{1,3})\\s*人");
    private static final Pattern POINT_TIME_PATTERN = Pattern.compile("(上午|下午|中午|晚上)?\\s*(\\d{1,2})点(半)?");
    private static final Pattern RANGE_TIME_PATTERN = Pattern.compile("(\\d{1,2}):(\\d{2})\\s*(?:到|至|-|~)\\s*(\\d{1,2}):(\\d{2})");
    private static final Pattern LOCATION_PATTERN = Pattern.compile("(\\d+号楼[^，。\\s]*)");
    private static final long STREAM_TIMEOUT_MS = 0L;
    private static final int STREAM_DELTA_CHUNK_SIZE = 12;
    private static final String STREAM_ERROR_MESSAGE = "AI 服务暂时不可用，请稍后重试";

    private final AiChatSessionStore sessionStore;
    private final ReservationService reservationService;
    private final RoomMapper roomMapper;
    private final ReservationMapper reservationMapper;
    private final ReservationAssistantLlmClient llmClient;
    private final ReservationKnowledgeService knowledgeService;
    private final ExecutorService streamExecutor = Executors.newVirtualThreadPerTaskExecutor();

    public AiChatServiceImpl(AiChatSessionStore sessionStore,
                             ReservationService reservationService,
                             RoomMapper roomMapper,
                             ReservationMapper reservationMapper,
                             ReservationAssistantLlmClient llmClient,
                             ReservationKnowledgeService knowledgeService) {
        this.sessionStore = sessionStore;
        this.reservationService = reservationService;
        this.roomMapper = roomMapper;
        this.reservationMapper = reservationMapper;
        this.llmClient = llmClient;
        this.knowledgeService = knowledgeService;
    }

    @Override
    public AiChatSessionVO createSession(AuthUser currentUser) {
        Long userId = requireUserId(currentUser);
        AiChatSessionStore.Session session = sessionStore.create(userId);

        AiChatSessionVO vo = new AiChatSessionVO();
        vo.setSessionId(session.getSessionId());
        vo.setWelcome("我是智能预约助手，可以帮你查空闲会议室、梳理我的预约、判断时间冲突。");
        vo.setSuggestions(DEFAULT_SUGGESTIONS);
        return vo;
    }

    @Override
    public AiChatResponseVO chat(AuthUser currentUser, String sessionId, String message, String scene) {
        ChatContext context = prepareChat(currentUser, sessionId, message, scene);
        return completeChat(context);
    }

    @Override
    public SseEmitter streamChat(AuthUser currentUser, String sessionId, String message, String scene) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        streamExecutor.submit(() -> {
            try {
                ChatContext context = prepareChat(currentUser, sessionId, message, scene);
                sendEvent(emitter, "start", startPayload(context.session().getSessionId()));

                AiChatResponseVO response = completeChat(context);
                // 当前上游模型调用仍是同步生成，这里先将完整答案拆成多个 delta 事件输出。
                for (String delta : splitAnswer(response.getAnswer())) {
                    sendEvent(emitter, "delta", deltaPayload(delta));
                }
                sendEvent(emitter, "done", donePayload(response));
            } catch (Exception e) {
                sendQuietly(emitter, "error", errorPayload(resolveStreamErrorMessage(e)));
            } finally {
                emitter.complete();
            }
        });
        return emitter;
    }

    @PreDestroy
    public void destroy() {
        streamExecutor.shutdownNow();
    }

    private ChatContext prepareChat(AuthUser currentUser, String sessionId, String message, String scene) {
        Long userId = requireUserId(currentUser);
        String normalizedMessage = normalizeMessage(message);
        AiChatSessionStore.Session session = sessionStore.getOrCreate(userId, sessionId);
        sessionStore.addUserMessage(session, normalizedMessage);

        AssistantReply reply = resolveReply(currentUser, session, normalizedMessage, scene);
        return new ChatContext(session, normalizedMessage, reply);
    }

    private AiChatResponseVO completeChat(ChatContext context) {
        String answer = tryGenerateAiAnswer(context.message(), context.reply());
        sessionStore.addAssistantMessage(context.session(), answer);

        AiChatResponseVO vo = new AiChatResponseVO();
        vo.setSessionId(context.session().getSessionId());
        vo.setAnswer(answer);
        vo.setSuggestions(context.reply().suggestions());
        return vo;
    }

    private AssistantReply resolveReply(AuthUser currentUser,
                                        AiChatSessionStore.Session session,
                                        String message,
                                        String scene) {
        if (scene != null && !scene.isBlank() && !RESERVATION_SCENE.equalsIgnoreCase(scene.trim())) {
            return scopeReply();
        }

        Intent intent = detectIntent(message);
        return switch (intent) {
            case ROOM_SEARCH -> handleRoomSearch(session, message);
            case MY_RESERVATIONS -> handleMyReservations(currentUser, message);
            case CONFLICT -> handleConflictCheck(currentUser, session, message);
            case GUIDE -> guideReply();
            case OUT_OF_SCOPE -> scopeReply();
        };
    }

    private AssistantReply handleRoomSearch(AiChatSessionStore.Session session, String message) {
        if (message.contains("当前楼层")) {
            return new AssistantReply(
                    "我暂时还不能直接识别“当前楼层”，你可以直接告诉我具体位置，例如“1号楼A区2层”。",
                    ROOM_SUGGESTIONS,
                    "用户希望按楼层筛选会议室，但还没有提供具体位置。"
            );
        }

        AiChatSessionStore.RoomQueryContext previousQuery = session.getLastRoomQuery();
        Integer attendees = parseAttendees(message);
        if (attendees == null && previousQuery != null) {
            attendees = previousQuery.attendees();
        }

        TimeRange timeRange = resolveRoomTimeRange(message, previousQuery);
        if (attendees == null || timeRange == null) {
            return new AssistantReply(
                    "可以，我先帮你查，但你至少要告诉我日期、时间段和参会人数，例如“今天下午10人会议室”。",
                    DEFAULT_SUGGESTIONS,
                    "用户没有提供完整的会议室检索条件。"
            );
        }

        ReservationRecommendationDTO dto = new ReservationRecommendationDTO();
        dto.setTitle("AI助手查询");
        dto.setAttendees(attendees);
        dto.setStartTime(formatDateTime(timeRange.start()));
        dto.setEndTime(formatDateTime(timeRange.end()));

        ReservationRecommendationVO recommendation = reservationService.recommend(dto);
        List<ReservationRecommendationItemVO> items = recommendation == null || recommendation.getRecommendations() == null
                ? List.of()
                : new ArrayList<>(recommendation.getRecommendations());

        String deviceKeyword = parseDeviceKeyword(message);
        if (deviceKeyword != null) {
            items = items.stream()
                    .filter(item -> matchesDeviceKeyword(item.getRoomId(), deviceKeyword))
                    .toList();
        }

        String locationKeyword = parseLocationKeyword(message);
        if (locationKeyword != null) {
            items = items.stream()
                    .filter(item -> item.getLocation() != null && item.getLocation().contains(locationKeyword))
                    .toList();
        }

        session.setLastRoomQuery(new AiChatSessionStore.RoomQueryContext(timeRange.start(), timeRange.end(), attendees));

        if (items.isEmpty()) {
            return new AssistantReply(
                    buildNoRoomAnswer(timeRange, attendees, deviceKeyword, locationKeyword),
                    List.of("换一个时间段再查", "放宽人数要求", "查看我这个时间段的预约"),
                    "系统推荐结果为空。"
            );
        }

        List<ReservationRecommendationItemVO> topRooms = items.stream().limit(3).toList();
        return new AssistantReply(
                buildRoomAnswer(timeRange, attendees, deviceKeyword, topRooms),
                ROOM_SUGGESTIONS,
                buildRoomFacts(timeRange, attendees, deviceKeyword, topRooms)
        );
    }

    private AssistantReply handleMyReservations(AuthUser currentUser, String message) {
        TimeRange timeRange = resolveReservationWindow(message);
        List<MyReservationVO> reservations = reservationService.myReservations(
                currentUser.getId(),
                formatDateTime(timeRange.start()),
                formatDateTime(timeRange.end()),
                "all",
                null,
                false
        );

        if (reservations == null || reservations.isEmpty()) {
            return new AssistantReply(
                    timeRange.label() + "你当前没有预约。",
                    RESERVATION_SUGGESTIONS,
                    timeRange.label() + "没有预约记录。"
            );
        }

        return new AssistantReply(
                buildReservationAnswer(timeRange, reservations),
                RESERVATION_SUGGESTIONS,
                buildReservationFacts(timeRange, reservations)
        );
    }

    private AssistantReply handleConflictCheck(AuthUser currentUser,
                                               AiChatSessionStore.Session session,
                                               String message) {
        TimeRange targetRange = resolveConflictTimeRange(message, session.getLastRoomQuery());
        if (targetRange == null) {
            return new AssistantReply(
                    "可以帮你判断冲突，但你需要告诉我具体时间，例如“明天下午3点”或“今天14:00到16:00”。",
                    CONFLICT_SUGGESTIONS,
                    "用户没有提供明确的冲突判断时间。"
            );
        }

        LocalDate targetDate = targetRange.start().toLocalDate();
        List<MyReservationVO> reservations = reservationService.myReservations(
                currentUser.getId(),
                formatDateTime(targetDate.atStartOfDay()),
                formatDateTime(targetDate.plusDays(1).atStartOfDay()),
                "all",
                "ACTIVE",
                false
        );
        List<MyReservationVO> conflicts = reservations == null
                ? List.of()
                : reservations.stream().filter(item -> overlaps(item, targetRange)).toList();

        int availableRoomCount = countAvailableRooms(targetRange);
        if (!conflicts.isEmpty()) {
            return new AssistantReply(
                    buildConflictAnswer(targetRange, conflicts),
                    CONFLICT_SUGGESTIONS,
                    buildConflictFacts(targetRange, conflicts, availableRoomCount)
            );
        }

        return new AssistantReply(
                buildNoConflictAnswer(targetRange, availableRoomCount),
                CONFLICT_SUGGESTIONS,
                buildConflictFacts(targetRange, List.of(), availableRoomCount)
        );
    }

    private AssistantReply guideReply() {
        return new AssistantReply(
                "我是会议室预约助手，目前可以帮你查空闲会议室、推荐会议室、梳理我的预约、判断时间冲突。你可以直接告诉我日期、时间段和人数。",
                DEFAULT_SUGGESTIONS,
                "用户在询问助手身份、能力或提问方式。"
        );
    }

    private AssistantReply scopeReply() {
        return new AssistantReply(
                "我现在只支持会议室和预约相关问题。你可以问我空闲会议室、我的预约或时间冲突。",
                DEFAULT_SUGGESTIONS,
                "用户问题超出会议室预约场景范围。"
        );
    }

    private String tryGenerateAiAnswer(String message, AssistantReply reply) {
        String aiAnswer = llmClient.generate(new ReservationAssistantLlmRequest(
                message,
                reply.facts(),
                knowledgeService.search(message)
        ));
        if (aiAnswer == null || aiAnswer.isBlank()) {
            return reply.answer();
        }
        return aiAnswer.trim();
    }

    private Intent detectIntent(String message) {
        if (containsAny(message, "怎么问", "如何提问", "你能做什么", "你会什么", "帮助", "你是谁", "你是做什么的")) {
            return Intent.GUIDE;
        }
        if (containsAny(message, "冲突", "撞期", "是否容易冲突")) {
            return Intent.CONFLICT;
        }
        if (containsAny(message, "我的预约", "本周的预约", "本月的预约", "查看我", "梳理我的预约")) {
            return Intent.MY_RESERVATIONS;
        }
        if (containsAny(message, "会议室", "空闲", "可用", "推荐", "找会议室")) {
            return Intent.ROOM_SEARCH;
        }
        return containsAny(message, "预约", "会议") ? Intent.GUIDE : Intent.OUT_OF_SCOPE;
    }

    private TimeRange resolveRoomTimeRange(String message, AiChatSessionStore.RoomQueryContext previousQuery) {
        LocalDate baseDate = detectBaseDate(message, previousQuery == null ? null : previousQuery.start().toLocalDate());
        TimeRange explicitRange = parseExplicitRange(message, baseDate);
        if (explicitRange != null) {
            return explicitRange;
        }

        TimeRange pointRange = parsePointTimeRange(message, baseDate);
        if (pointRange != null) {
            return pointRange;
        }

        if (baseDate != null && message.contains("下午")) {
            return new TimeRange(baseDate.atTime(13, 0), baseDate.atTime(18, 0), buildDateLabel(baseDate) + "下午");
        }
        if (baseDate != null && message.contains("上午")) {
            return new TimeRange(baseDate.atTime(9, 0), baseDate.atTime(12, 0), buildDateLabel(baseDate) + "上午");
        }
        if (baseDate != null && message.contains("晚上")) {
            return new TimeRange(baseDate.atTime(18, 0), baseDate.atTime(21, 0), buildDateLabel(baseDate) + "晚上");
        }
        if (previousQuery != null && containsAny(message, "投影", "白板", "视频", "推荐", "空闲")) {
            return new TimeRange(previousQuery.start(), previousQuery.end(), buildRangeLabel(previousQuery.start(), previousQuery.end()));
        }
        return null;
    }

    private TimeRange resolveConflictTimeRange(String message, AiChatSessionStore.RoomQueryContext previousQuery) {
        TimeRange roomTimeRange = resolveRoomTimeRange(message, previousQuery);
        if (roomTimeRange != null) {
            return roomTimeRange;
        }
        if (previousQuery != null) {
            return new TimeRange(previousQuery.start(), previousQuery.end(), buildRangeLabel(previousQuery.start(), previousQuery.end()));
        }
        return null;
    }

    private TimeRange resolveReservationWindow(String message) {
        LocalDate today = LocalDate.now();
        if (message.contains("本月")) {
            LocalDate firstDay = today.withDayOfMonth(1);
            return new TimeRange(firstDay.atStartOfDay(), firstDay.plusMonths(1).atStartOfDay(), "本月");
        }
        if (message.contains("明天")) {
            LocalDate tomorrow = today.plusDays(1);
            return new TimeRange(tomorrow.atStartOfDay(), tomorrow.plusDays(1).atStartOfDay(), "明天");
        }
        if (message.contains("今天")) {
            return new TimeRange(today.atStartOfDay(), today.plusDays(1).atStartOfDay(), "今天");
        }

        LocalDate monday = today.with(DayOfWeek.MONDAY);
        return new TimeRange(monday.atStartOfDay(), monday.plusDays(7).atStartOfDay(), "本周");
    }

    private TimeRange parseExplicitRange(String message, LocalDate baseDate) {
        if (baseDate == null) {
            return null;
        }
        Matcher matcher = RANGE_TIME_PATTERN.matcher(message);
        if (!matcher.find()) {
            return null;
        }
        LocalDateTime start = baseDate.atTime(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)));
        LocalDateTime end = baseDate.atTime(Integer.parseInt(matcher.group(3)), Integer.parseInt(matcher.group(4)));
        if (!end.isAfter(start)) {
            return null;
        }
        return new TimeRange(start, end, buildRangeLabel(start, end));
    }

    private TimeRange parsePointTimeRange(String message, LocalDate baseDate) {
        if (baseDate == null) {
            return null;
        }
        Matcher matcher = POINT_TIME_PATTERN.matcher(message);
        if (!matcher.find()) {
            return null;
        }
        String period = matcher.group(1);
        int hour = Integer.parseInt(matcher.group(2));
        int minute = matcher.group(3) == null ? 0 : 30;
        hour = normalizeHour(period, hour);
        LocalDateTime start = baseDate.atTime(hour, minute);
        LocalDateTime end = start.plusHours(1);
        return new TimeRange(start, end, buildRangeLabel(start, end));
    }

    private int normalizeHour(String period, int hour) {
        if ("下午".equals(period) || "晚上".equals(period)) {
            return hour < 12 ? hour + 12 : hour;
        }
        if ("中午".equals(period)) {
            return hour < 11 ? hour + 12 : hour;
        }
        if (hour == 24) {
            return 0;
        }
        return hour;
    }

    private LocalDate detectBaseDate(String message, LocalDate fallbackDate) {
        if (message.contains("后天")) {
            return LocalDate.now().plusDays(2);
        }
        if (message.contains("明天")) {
            return LocalDate.now().plusDays(1);
        }
        if (message.contains("今天")) {
            return LocalDate.now();
        }
        if (fallbackDate != null) {
            return fallbackDate;
        }
        if (containsAny(message, "下午", "上午", "晚上", ":")) {
            return LocalDate.now();
        }
        return null;
    }

    private Integer parseAttendees(String message) {
        Matcher matcher = ATTENDEES_PATTERN.matcher(message);
        if (!matcher.find()) {
            return null;
        }
        return Integer.parseInt(matcher.group(1));
    }

    private String parseDeviceKeyword(String message) {
        if (containsAny(message, "投影", "投影仪")) {
            return "投影";
        }
        if (containsAny(message, "视频", "远程")) {
            return "视频";
        }
        if (message.contains("白板")) {
            return "白板";
        }
        if (containsAny(message, "麦克风", "话筒")) {
            return "麦克风";
        }
        return null;
    }

    private String parseLocationKeyword(String message) {
        Matcher matcher = LOCATION_PATTERN.matcher(message);
        if (!matcher.find()) {
            return null;
        }
        return matcher.group(1);
    }

    private boolean matchesDeviceKeyword(Long roomId, String deviceKeyword) {
        List<RoomPageDeviceVO> devices = roomMapper.selectDevicesByRoomId(roomId);
        if (devices == null || devices.isEmpty()) {
            return false;
        }
        for (RoomPageDeviceVO device : devices) {
            if (!"ENABLED".equalsIgnoreCase(device.getStatus())) {
                continue;
            }
            String name = device.getName() == null ? "" : device.getName();
            if (name.contains(deviceKeyword)) {
                return true;
            }
        }
        return false;
    }

    private int countAvailableRooms(TimeRange range) {
        List<RoomListItemVO> rooms = roomMapper.selectRooms(null, null, true);
        if (rooms == null || rooms.isEmpty()) {
            return 0;
        }
        List<Long> roomIds = rooms.stream().map(RoomListItemVO::getId).toList();
        if (roomIds.isEmpty()) {
            return 0;
        }
        List<Long> conflictRoomIds = reservationMapper.selectConflictRoomIds(
                Timestamp.valueOf(range.start()),
                Timestamp.valueOf(range.end()),
                roomIds
        );
        Set<Long> conflictSet = conflictRoomIds == null ? Set.of() : new LinkedHashSet<>(conflictRoomIds);
        return roomIds.size() - conflictSet.size();
    }

    private boolean overlaps(MyReservationVO reservation, TimeRange range) {
        LocalDateTime start = DateTimeUtils.parseToLocalDateTime(reservation.getStartTime());
        LocalDateTime end = DateTimeUtils.parseToLocalDateTime(reservation.getEndTime());
        return DateTimeUtils.isOverlap(start, end, range.start(), range.end());
    }

    private String buildRoomAnswer(TimeRange range,
                                   Integer attendees,
                                   String deviceKeyword,
                                   List<ReservationRecommendationItemVO> rooms) {
        StringBuilder answer = new StringBuilder();
        answer.append(range.label())
                .append(" ")
                .append(attendees)
                .append(" 人规模可优先看：");
        for (int i = 0; i < rooms.size(); i++) {
            ReservationRecommendationItemVO room = rooms.get(i);
            if (i > 0) {
                answer.append("；");
            }
            answer.append(i + 1)
                    .append(". ")
                    .append(room.getRoomName())
                    .append("（")
                    .append(room.getLocation())
                    .append("，")
                    .append(room.getCapacity())
                    .append("人");
            if (deviceKeyword != null) {
                answer.append("，匹配").append(deviceKeyword);
            }
            answer.append("）");
        }
        answer.append("。");
        return answer.toString();
    }

    private String buildNoRoomAnswer(TimeRange range,
                                     Integer attendees,
                                     String deviceKeyword,
                                     String locationKeyword) {
        StringBuilder answer = new StringBuilder();
        answer.append(range.label())
                .append(" 暂时没有合适的 ")
                .append(attendees)
                .append(" 人会议室");
        if (deviceKeyword != null) {
            answer.append("，并且需要匹配").append(deviceKeyword);
        }
        if (locationKeyword != null) {
            answer.append("，位置限定在").append(locationKeyword);
        }
        answer.append("。建议换一个时间段或放宽筛选条件。");
        return answer.toString();
    }

    private String buildReservationAnswer(TimeRange range, List<MyReservationVO> reservations) {
        StringBuilder answer = new StringBuilder();
        answer.append(range.label())
                .append(" 你共有 ")
                .append(reservations.size())
                .append(" 场预约");
        int limit = Math.min(3, reservations.size());
        for (int i = 0; i < limit; i++) {
            MyReservationVO reservation = reservations.get(i);
            answer.append(i == 0 ? "：" : "；")
                    .append(reservation.getTitle())
                    .append("（")
                    .append(formatReservationSlot(reservation))
                    .append("，")
                    .append(reservation.getRoomName())
                    .append("，")
                    .append(formatStatus(reservation.getStatus()))
                    .append("）");
        }
        if (reservations.size() > limit) {
            answer.append("；其余 ").append(reservations.size() - limit).append(" 场可继续细看。");
        } else {
            answer.append("。");
        }
        return answer.toString();
    }

    private String buildConflictAnswer(TimeRange range, List<MyReservationVO> conflicts) {
        StringBuilder answer = new StringBuilder();
        answer.append(range.label())
                .append(" 与你现有预约有冲突");
        int limit = Math.min(2, conflicts.size());
        for (int i = 0; i < limit; i++) {
            MyReservationVO conflict = conflicts.get(i);
            answer.append(i == 0 ? "：" : "；")
                    .append(conflict.getTitle())
                    .append("（")
                    .append(formatReservationSlot(conflict))
                    .append("，")
                    .append(conflict.getRoomName())
                    .append("）");
        }
        if (conflicts.size() > limit) {
            answer.append("；另外还有 ").append(conflicts.size() - limit).append(" 场重叠预约。");
        } else {
            answer.append("。");
        }
        return answer.toString();
    }

    private String buildNoConflictAnswer(TimeRange range, int availableRoomCount) {
        String riskLevel = availableRoomCount <= 2 ? "偏高" : availableRoomCount <= 5 ? "中等" : "较低";
        return range.label()
                + " 你当前没有个人预约冲突，系统里大约还有 "
                + availableRoomCount
                + " 间可用会议室，整体冲突风险"
                + riskLevel
                + "。";
    }

    private String buildRoomFacts(TimeRange range,
                                  Integer attendees,
                                  String deviceKeyword,
                                  List<ReservationRecommendationItemVO> rooms) {
        StringBuilder facts = new StringBuilder();
        facts.append("查询范围=").append(range.label())
                .append("，人数=").append(attendees);
        if (deviceKeyword != null) {
            facts.append("，设备要求=").append(deviceKeyword);
        }
        for (ReservationRecommendationItemVO room : rooms) {
            facts.append(" | ").append(room.getRoomName())
                    .append("(").append(room.getLocation()).append("，").append(room.getCapacity()).append("人)");
        }
        return facts.toString();
    }

    private String buildReservationFacts(TimeRange range, List<MyReservationVO> reservations) {
        StringBuilder facts = new StringBuilder();
        facts.append("预约窗口=").append(range.label()).append("，数量=").append(reservations.size());
        for (MyReservationVO reservation : reservations.stream().limit(5).toList()) {
            facts.append(" | ").append(reservation.getTitle())
                    .append("(").append(reservation.getStartTime()).append("~").append(reservation.getEndTime()).append(")");
        }
        return facts.toString();
    }

    private String buildConflictFacts(TimeRange range, List<MyReservationVO> conflicts, int availableRoomCount) {
        StringBuilder facts = new StringBuilder();
        facts.append("冲突判断窗口=").append(range.label())
                .append("，重叠预约数=").append(conflicts.size())
                .append("，可用会议室数=").append(availableRoomCount);
        for (MyReservationVO conflict : conflicts) {
            facts.append(" | ").append(conflict.getTitle())
                    .append("(").append(conflict.getStartTime()).append("~").append(conflict.getEndTime()).append(")");
        }
        return facts.toString();
    }

    private String formatReservationSlot(MyReservationVO reservation) {
        LocalDateTime start = DateTimeUtils.parseToLocalDateTime(reservation.getStartTime());
        LocalDateTime end = DateTimeUtils.parseToLocalDateTime(reservation.getEndTime());
        return start.format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
                + "-"
                + end.format(SHORT_TIME_FORMATTER);
    }

    private String formatStatus(String status) {
        if ("ACTIVE".equalsIgnoreCase(status)) {
            return "进行中/待开始";
        }
        if ("ENDED".equalsIgnoreCase(status)) {
            return "已结束";
        }
        if ("CANCELLED".equalsIgnoreCase(status)) {
            return "已取消";
        }
        return status == null ? "未知状态" : status;
    }

    private String formatDateTime(LocalDateTime value) {
        return value.format(DATE_TIME_FORMATTER);
    }

    private String buildDateLabel(LocalDate date) {
        if (date.equals(LocalDate.now())) {
            return "今天";
        }
        if (date.equals(LocalDate.now().plusDays(1))) {
            return "明天";
        }
        if (date.equals(LocalDate.now().plusDays(2))) {
            return "后天";
        }
        return date.toString();
    }

    private String buildRangeLabel(LocalDateTime start, LocalDateTime end) {
        return buildDateLabel(start.toLocalDate())
                + " "
                + start.toLocalTime().format(SHORT_TIME_FORMATTER)
                + "-"
                + end.toLocalTime().format(SHORT_TIME_FORMATTER);
    }

    private List<String> splitAnswer(String answer) {
        List<String> deltas = new ArrayList<>();
        if (answer == null || answer.isBlank()) {
            return deltas;
        }

        String normalized = answer.trim();
        for (int start = 0; start < normalized.length(); start += STREAM_DELTA_CHUNK_SIZE) {
            deltas.add(normalized.substring(start, Math.min(start + STREAM_DELTA_CHUNK_SIZE, normalized.length())));
        }
        return deltas;
    }

    private void sendEvent(SseEmitter emitter, String eventName, Object payload) throws IOException {
        emitter.send(SseEmitter.event()
                .name(eventName)
                .data(payload, MediaType.APPLICATION_JSON));
    }

    private void sendQuietly(SseEmitter emitter, String eventName, Object payload) {
        try {
            sendEvent(emitter, eventName, payload);
        } catch (IOException ignored) {
        }
    }

    private Map<String, Object> startPayload(String sessionId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sessionId", sessionId);
        return payload;
    }

    private Map<String, Object> deltaPayload(String delta) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("delta", delta);
        return payload;
    }

    private Map<String, Object> donePayload(AiChatResponseVO response) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sessionId", response.getSessionId());
        payload.put("suggestions", response.getSuggestions());
        return payload;
    }

    private Map<String, Object> errorPayload(String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("message", message);
        return payload;
    }

    private String resolveStreamErrorMessage(Exception e) {
        if (e instanceof BizException bizException && bizException.getMessage() != null && !bizException.getMessage().isBlank()) {
            return bizException.getMessage();
        }
        return STREAM_ERROR_MESSAGE;
    }

    private String normalizeMessage(String message) {
        String value = message == null ? "" : message.trim();
        if (value.isEmpty()) {
            throw new BizException(400, "message must not be blank");
        }
        return value;
    }

    private Long requireUserId(AuthUser currentUser) {
        if (currentUser == null || currentUser.getId() == null) {
            throw new BizException(401, "not logged in");
        }
        return currentUser.getId();
    }

    private boolean containsAny(String text, String... keywords) {
        String normalized = text == null ? "" : text.toLowerCase(Locale.ROOT);
        for (String keyword : keywords) {
            if (normalized.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private enum Intent {
        ROOM_SEARCH,
        MY_RESERVATIONS,
        CONFLICT,
        GUIDE,
        OUT_OF_SCOPE
    }

    private record TimeRange(LocalDateTime start, LocalDateTime end, String label) {
    }

    private record AssistantReply(String answer, List<String> suggestions, String facts) {
    }

    private record ChatContext(AiChatSessionStore.Session session, String message, AssistantReply reply) {
    }
}
