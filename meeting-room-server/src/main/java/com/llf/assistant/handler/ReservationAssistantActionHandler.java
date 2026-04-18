package com.llf.assistant.handler;

import com.llf.assistant.AiAssistantActionHandler;
import com.llf.assistant.AiAssistantActionPlan;
import com.llf.assistant.AiAssistantExecutionResult;
import com.llf.assistant.AiAssistantHelper;
import com.llf.assistant.AiAssistantSessionStore;
import com.llf.auth.AuthUser;
import com.llf.dto.reservation.MyReservationCancelDTO;
import com.llf.dto.reservation.MyReservationReviewDTO;
import com.llf.dto.reservation.MyReservationUpdateDTO;
import com.llf.dto.reservation.ReservationCreateDTO;
import com.llf.dto.reservation.ReservationRecommendationDTO;
import com.llf.result.BizException;
import com.llf.service.ReservationService;
import com.llf.service.UserService;
import com.llf.vo.assistant.AiAssistantFieldOptionVO;
import com.llf.vo.assistant.AiAssistantMissingFieldVO;
import com.llf.vo.assistant.AiAssistantResultVO;
import com.llf.vo.reservation.MyReservationReviewResultVO;
import com.llf.vo.reservation.MyReservationVO;
import com.llf.vo.reservation.ReservationCreateVO;
import com.llf.vo.reservation.ReservationRecommendationVO;
import com.llf.vo.user.UserOptionVO;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class ReservationAssistantActionHandler implements AiAssistantActionHandler {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Pattern DATE_PATTERN = Pattern.compile("(20\\d{2}-\\d{2}-\\d{2})");
    private static final Pattern TIME_RANGE_PATTERN = Pattern.compile("(\\d{1,2}:\\d{2})\\s*(?:到|至|-|~)\\s*(\\d{1,2}:\\d{2})");
    private static final Pattern ATTENDEES_PATTERN = Pattern.compile("(\\d{1,3})\\s*人");
    private static final Pattern RATING_PATTERN = Pattern.compile("([1-5])\\s*(?:星|分)");
    private static final Pattern WITH_PARTICIPANT_PATTERN = Pattern.compile("(?:我和|我跟|我与)([^，。,.]{1,30})");
    private static final Pattern ADD_PARTICIPANT_PATTERN = Pattern.compile("(?:约上|再约上|加上|再加上)([^，。,.]{1,30})");
    private static final Set<String> PARTICIPANT_STOP_WORDS = Set.of("我", "我们", "大家", "会议", "会", "开会", "开个会", "这个会", "那个会");

    private final ReservationService reservationService;
    private final UserService userService;

    public ReservationAssistantActionHandler(ReservationService reservationService, UserService userService) {
        this.reservationService = reservationService;
        this.userService = userService;
    }

    @Override
    public List<String> supportedActionTypes() {
        return List.of("reservations.list", "reservations.detail", "reservations.create", "reservations.update", "reservations.cancel", "reservations.review");
    }

    @Override
    public AiAssistantActionPlan process(String actionType, AuthUser currentUser, AiAssistantSessionStore.Session session, String message) {
        return switch (actionType) {
            case "reservations.list" -> handleReservationList(currentUser, session, message);
            case "reservations.detail" -> handleReservationDetail(currentUser, session, message);
            case "reservations.create" -> handleReservationCreate(currentUser, session, message);
            case "reservations.update" -> handleReservationUpdate(currentUser, session, message);
            case "reservations.cancel" -> handleReservationCancel(currentUser, session, message);
            case "reservations.review" -> handleReservationReview(currentUser, session, message);
            default -> AiAssistantActionPlan.error("当前还不支持这个预约动作。", defaultSuggestions(), errorResult("动作不支持"));
        };
    }

    @Override
    public AiAssistantExecutionResult execute(String actionType, AuthUser currentUser, Map<String, Object> params) {
        return switch (actionType) {
            case "reservations.create" -> executeCreate(currentUser, params);
            case "reservations.update" -> executeUpdate(currentUser, params);
            case "reservations.cancel" -> executeCancel(currentUser, params);
            case "reservations.review" -> executeReview(currentUser, params);
            default -> AiAssistantExecutionResult.error("这个动作不需要执行。", "动作无需执行", List.of());
        };
    }

    private AiAssistantActionPlan handleReservationList(AuthUser currentUser, AiAssistantSessionStore.Session session, String message) {
        TimeWindow window = resolveTimeWindow(message, false, session == null ? Map.of() : session.getDraft());
        List<MyReservationVO> reservations = reservationService.myReservations(currentUser.getId(), window.start(), window.end(), "all", null, false);
        List<MyReservationVO> visibleReservations = reservations == null ? List.of() : reservations.stream().filter(item -> !isCancelled(item)).toList();
        if (visibleReservations.isEmpty()) {
            return AiAssistantActionPlan.reply(window.label() + "你当前没有预约。", List.of("帮我创建一个预约", "今天下午有哪些空闲会议室"));
        }

        StringBuilder builder = new StringBuilder(window.label()).append("共有 ").append(visibleReservations.size()).append(" 条预约");
        for (int i = 0; i < Math.min(3, visibleReservations.size()); i++) {
            MyReservationVO item = visibleReservations.get(i);
            builder.append(i == 0 ? "：" : "；")
                    .append(item.getTitle())
                    .append("（")
                    .append(item.getRoomName())
                    .append(" ")
                    .append(shortTime(item.getStartTime()))
                    .append("-")
                    .append(shortTime(item.getEndTime()))
                    .append(displayStatusSuffix(item))
                    .append("）");
        }
        return AiAssistantActionPlan.reply(builder.toString(), List.of("查看这场预约详情", "取消我明天下午的预约", "帮我创建一个预约"));
    }

    private AiAssistantActionPlan handleReservationDetail(AuthUser currentUser, AiAssistantSessionStore.Session session, String message) {
        SelectionResult selection = selectReservation(currentUser, session, message, null);
        if (selection.plan() != null) {
            return selection.plan();
        }
        MyReservationVO reservation = selection.selected();
        return AiAssistantActionPlan.reply(
                "预约详情：" + reservation.getTitle() + "，会议室 " + reservation.getRoomName() + "，时间 " + reservation.getStartTime() + " - " + reservation.getEndTime() + "，状态 " + reservation.getStatus() + "。",
                List.of("修改这场预约", "取消这场预约", "查看我本周的预约")
        );
    }

    private AiAssistantActionPlan handleReservationCreate(AuthUser currentUser, AiAssistantSessionStore.Session session, String message) {
        Map<String, Object> draft = session.getDraft();
        mergeCreateDraft(draft, message);
        ParticipantResolution participantResolution = mergeParticipantDraft(draft, message, currentUser == null ? null : currentUser.getId());
        if (participantResolution.hasAmbiguous()) {
            return AiAssistantActionPlan.collect("我识别到参会人名称可能有歧义，请搜索并选择具体参会人。", List.of("搜索并选择参会人"), buildCreateMissingFields(draft));
        }

        List<AiAssistantMissingFieldVO> missingFields = buildCreateMissingFields(draft);
        if (missingFields.stream().anyMatch(AiAssistantMissingFieldVO::isRequired)) {
            return AiAssistantActionPlan.collect("要继续创建预约，我还需要一些关键信息。", List.of("明天下午 14:00 到 15:00", "10 人"), missingFields);
        }

        if (AiAssistantHelper.longValue(draft, "roomId") == null) {
            return AiAssistantActionPlan.collect(
                    "我已经筛出推荐会议室了，请选择一个。",
                    List.of("换个时间段再试", "放宽人数要求"),
                    List.of(AiAssistantHelper.field("roomId", "推荐会议室", "select", true, "请选择会议室", draft.get("roomId"), buildRecommendedRoomOptions(draft)))
            );
        }

        return AiAssistantActionPlan.confirm(
                "信息已经齐了，请确认是否创建预约。",
                List.of("确认执行", "取消本次操作"),
                "创建预约",
                List.of(
                        AiAssistantHelper.summary("会议主题", AiAssistantHelper.stringValue(draft, "title")),
                        AiAssistantHelper.summary("会议日期", AiAssistantHelper.stringValue(draft, "meetingDate")),
                        AiAssistantHelper.summary("时间", AiAssistantHelper.stringValue(draft, "startClock") + " - " + AiAssistantHelper.stringValue(draft, "endClock")),
                        AiAssistantHelper.summary("参会人数", String.valueOf(AiAssistantHelper.integerValue(draft, "attendees"))),
                        AiAssistantHelper.summary("会议室", String.valueOf(AiAssistantHelper.longValue(draft, "roomId"))),
                        AiAssistantHelper.summary("参会人", participantSummary(draft))
                ),
                Map.copyOf(draft)
        );
    }

    private AiAssistantActionPlan handleReservationUpdate(AuthUser currentUser, AiAssistantSessionStore.Session session, String message) {
        SelectionResult selection = selectReservation(currentUser, session, message, "ACTIVE");
        if (selection.plan() != null) {
            return selection.plan();
        }

        MyReservationVO reservation = selection.selected();
        Map<String, Object> draft = session.getDraft();
        boolean hasDirectInput = hasAny(draft, "title", "meetingDate", "startClock", "endClock", "attendees", "roomId", "remark", "participantUserIds");
        fillDraftFromReservation(draft, reservation);
        boolean changedByMessage = mergeUpdateDraft(draft, message);
        ParticipantResolution participantResolution = mergeParticipantDraft(draft, message, currentUser == null ? null : currentUser.getId());
        if (participantResolution.hasAmbiguous()) {
            return AiAssistantActionPlan.collect("我识别到参会人名称可能有歧义，请搜索并选择具体参会人。", List.of("搜索并选择参会人"), buildUpdateFieldsWithParticipants(draft));
        }
        if (!(hasDirectInput || changedByMessage || participantResolution.detectedCandidate())) {
            return AiAssistantActionPlan.collect("我已经定位到目标预约了，请补充你想修改成什么内容。", List.of("改到明天下午 16:00 到 17:00", "这个会再加上王五"), buildUpdateFieldsWithParticipants(draft));
        }

        return AiAssistantActionPlan.confirm(
                "修改信息已经整理好了，请确认是否执行。",
                List.of("确认执行", "取消本次操作"),
                "修改预约",
                List.of(
                        AiAssistantHelper.summary("预约标题", AiAssistantHelper.stringValue(draft, "title")),
                        AiAssistantHelper.summary("会议日期", AiAssistantHelper.stringValue(draft, "meetingDate")),
                        AiAssistantHelper.summary("时间", AiAssistantHelper.stringValue(draft, "startClock") + " - " + AiAssistantHelper.stringValue(draft, "endClock")),
                        AiAssistantHelper.summary("参会人数", String.valueOf(AiAssistantHelper.integerValue(draft, "attendees"))),
                        AiAssistantHelper.summary("会议室", String.valueOf(AiAssistantHelper.longValue(draft, "roomId"))),
                        AiAssistantHelper.summary("参会人", participantSummary(draft))
                ),
                Map.copyOf(draft)
        );
    }

    private AiAssistantActionPlan handleReservationCancel(AuthUser currentUser, AiAssistantSessionStore.Session session, String message) {
        SelectionResult selection = selectReservation(currentUser, session, message, "ACTIVE");
        if (selection.plan() != null) {
            return selection.plan();
        }
        Map<String, Object> draft = session.getDraft();
        if (AiAssistantHelper.stringValue(draft, "cancelReason") == null) {
            putIfPresent(draft, "cancelReason", parseCancelReason(message));
        }
        if (AiAssistantHelper.stringValue(draft, "cancelReason") == null) {
            return AiAssistantActionPlan.collect("取消预约前，我还需要你确认取消原因。", List.of("时间冲突，需要重新安排", "需求取消，会议无需继续"), List.of(AiAssistantHelper.field("cancelReason", "取消原因", "textarea", true, "例如：时间冲突，需要重新安排", null, List.of())));
        }

        MyReservationVO reservation = selection.selected();
        return AiAssistantActionPlan.confirm("已匹配到目标预约，请确认是否取消。", List.of("确认执行", "查看我的预约"), "取消预约", List.of(
                AiAssistantHelper.summary("预约标题", reservation.getTitle()),
                AiAssistantHelper.summary("会议时间", reservation.getStartTime() + " - " + reservation.getEndTime()),
                AiAssistantHelper.summary("取消原因", AiAssistantHelper.stringValue(draft, "cancelReason"))
        ), Map.copyOf(draft));
    }

    private AiAssistantActionPlan handleReservationReview(AuthUser currentUser, AiAssistantSessionStore.Session session, String message) {
        SelectionResult selection = selectReservation(currentUser, session, message, "ENDED");
        if (selection.plan() != null) {
            return selection.plan();
        }

        Map<String, Object> draft = session.getDraft();
        if (AiAssistantHelper.integerValue(draft, "rating") == null) {
            putIfPresent(draft, "rating", parseRating(message));
        }
        if (AiAssistantHelper.integerValue(draft, "rating") == null) {
            return AiAssistantActionPlan.collect("提交评价前，我还需要你的评分。", List.of("5 星", "4 星"), List.of(AiAssistantHelper.field("rating", "评分", "number", true, "1 到 5", null, List.of())));
        }

        MyReservationVO reservation = selection.selected();
        return AiAssistantActionPlan.confirm("评价信息已经准备好，请确认提交。", List.of("确认执行", "查看我的预约"), "提交评价", List.of(
                AiAssistantHelper.summary("预约标题", reservation.getTitle()),
                AiAssistantHelper.summary("评分", String.valueOf(AiAssistantHelper.integerValue(draft, "rating")))
        ), Map.copyOf(draft));
    }

    private AiAssistantExecutionResult executeCreate(AuthUser currentUser, Map<String, Object> params) {
        ReservationCreateDTO dto = new ReservationCreateDTO();
        dto.setTitle(AiAssistantHelper.stringValue(params, "title"));
        dto.setMeetingDate(AiAssistantHelper.stringValue(params, "meetingDate"));
        dto.setStartClock(AiAssistantHelper.stringValue(params, "startClock"));
        dto.setEndClock(AiAssistantHelper.stringValue(params, "endClock"));
        dto.setAttendees(AiAssistantHelper.integerValue(params, "attendees"));
        dto.setRoomId(AiAssistantHelper.longValue(params, "roomId"));
        dto.setRemark(AiAssistantHelper.stringValue(params, "remark"));
        dto.setParticipantUserIds(AiAssistantHelper.longListValue(params, "participantUserIds"));
        ReservationCreateVO created = reservationService.create(dto, currentUser.getId());
        return AiAssistantExecutionResult.success("预约创建成功。", "预约创建成功", List.of(AiAssistantHelper.summary("预约编号", created.getReservationNo())), "/reservations/index");
    }

    private AiAssistantExecutionResult executeUpdate(AuthUser currentUser, Map<String, Object> params) {
        MyReservationUpdateDTO dto = new MyReservationUpdateDTO();
        dto.setTitle(AiAssistantHelper.stringValue(params, "title"));
        dto.setMeetingDate(AiAssistantHelper.stringValue(params, "meetingDate"));
        dto.setStartClock(AiAssistantHelper.stringValue(params, "startClock"));
        dto.setEndClock(AiAssistantHelper.stringValue(params, "endClock"));
        dto.setAttendees(AiAssistantHelper.integerValue(params, "attendees"));
        dto.setRoomId(AiAssistantHelper.longValue(params, "roomId"));
        dto.setRemark(AiAssistantHelper.stringValue(params, "remark"));
        dto.setParticipantUserIds(AiAssistantHelper.longListValue(params, "participantUserIds"));
        MyReservationVO updated = reservationService.updateMyReservation(AiAssistantHelper.longValue(params, "reservationId"), currentUser.getId(), dto);
        return AiAssistantExecutionResult.success("预约修改成功。", "预约修改成功", List.of(AiAssistantHelper.summary("会议主题", updated.getTitle())), "/reservations/index");
    }

    private AiAssistantExecutionResult executeCancel(AuthUser currentUser, Map<String, Object> params) {
        MyReservationCancelDTO dto = new MyReservationCancelDTO();
        dto.setCancelReason(AiAssistantHelper.stringValue(params, "cancelReason"));
        MyReservationVO cancelled = reservationService.cancelMyReservation(AiAssistantHelper.longValue(params, "reservationId"), currentUser.getId(), dto);
        return AiAssistantExecutionResult.success("预约已取消。", "取消预约成功", List.of(AiAssistantHelper.summary("会议主题", cancelled.getTitle())), "/reservations/index");
    }

    private AiAssistantExecutionResult executeReview(AuthUser currentUser, Map<String, Object> params) {
        MyReservationReviewDTO dto = new MyReservationReviewDTO();
        dto.setRating(AiAssistantHelper.integerValue(params, "rating"));
        dto.setContent(AiAssistantHelper.stringValue(params, "content"));
        MyReservationReviewResultVO result = reservationService.submitMyReservationReview(AiAssistantHelper.longValue(params, "reservationId"), currentUser.getId(), dto);
        return AiAssistantExecutionResult.success("评价提交成功。", "提交评价成功", List.of(
                AiAssistantHelper.summary("评分", String.valueOf(dto.getRating())),
                AiAssistantHelper.summary("已评价", String.valueOf(Boolean.TRUE.equals(result.getReviewed())))
        ), "/reservations/index");
    }

    private SelectionResult selectReservation(AuthUser currentUser, AiAssistantSessionStore.Session session, String message, String status) {
        Map<String, Object> draft = session.getDraft();
        Long reservationId = AiAssistantHelper.longValue(draft, "reservationId");
        if (reservationId != null) {
            try {
                MyReservationVO reservation = reservationService.myReservationDetail(reservationId, currentUser.getId());
                if (reservation != null) {
                    session.setLastReservationId(reservation.getId());
                    return new SelectionResult(reservation, null);
                }
            } catch (BizException ignored) {
                draft.remove("reservationId");
            }
        }

        TimeWindow window = resolveTimeWindow(message, "ENDED".equals(status), draft);
        List<MyReservationVO> reservations = reservationService.myReservations(currentUser.getId(), window.start(), window.end(), "all", status, false);
        List<MyReservationVO> matched = filterReservations(reservations, message);
        if (matched.isEmpty()) {
            return new SelectionResult(null, AiAssistantActionPlan.error("没有匹配到符合条件的预约，请重新描述一下目标预约。", defaultSuggestions(), errorResult("未匹配到预约")));
        }
        if (matched.size() > 1) {
            List<AiAssistantFieldOptionVO> options = matched.stream()
                    .map(item -> AiAssistantHelper.option(item.getTitle() + " | " + item.getStartTime() + " | " + item.getRoomName(), item.getId()))
                    .toList();
            return new SelectionResult(null, AiAssistantActionPlan.collect("我匹配到了多条预约，请先选择一条。", List.of("选择其中一条继续"), List.of(
                    AiAssistantHelper.field("reservationId", "目标预约", "select", true, "请选择预约", null, options)
            )));
        }

        MyReservationVO selected = matched.get(0);
        draft.put("reservationId", selected.getId());
        session.setLastReservationId(selected.getId());
        return new SelectionResult(selected, null);
    }

    private List<AiAssistantMissingFieldVO> buildCreateMissingFields(Map<String, Object> draft) {
        List<AiAssistantMissingFieldVO> fields = new ArrayList<>();
        addMissingFieldIfBlank(fields, draft, "title", "会议主题", "text", "例如：项目周会");
        addMissingFieldIfBlank(fields, draft, "meetingDate", "会议日期", "date", "请选择日期");
        addMissingFieldIfBlank(fields, draft, "startClock", "开始时间", "time", "请选择开始时间");
        addMissingFieldIfBlank(fields, draft, "endClock", "结束时间", "time", "请选择结束时间");
        if (AiAssistantHelper.integerValue(draft, "attendees") == null) {
            fields.add(AiAssistantHelper.field("attendees", "参会人数", "number", true, "例如：10", draft.get("attendees"), List.of()));
        }
        fields.add(buildParticipantField(draft));
        return fields;
    }

    private List<AiAssistantMissingFieldVO> buildUpdateFieldsWithParticipants(Map<String, Object> draft) {
        List<AiAssistantFieldOptionVO> roomOptions = reservationService.myRoomOptions().stream()
                .map(item -> AiAssistantHelper.option(item.getName() + " | " + item.getLocation() + " | " + item.getCapacity() + " 人", item.getId()))
                .toList();

        List<AiAssistantMissingFieldVO> fields = new ArrayList<>();
        fields.add(AiAssistantHelper.field("title", "会议主题", "text", true, "可选", draft.get("title"), List.of()));
        fields.add(AiAssistantHelper.field("meetingDate", "会议日期", "date", true, "可选", draft.get("meetingDate"), List.of()));
        fields.add(AiAssistantHelper.field("startClock", "开始时间", "time", true, "可选", draft.get("startClock"), List.of()));
        fields.add(AiAssistantHelper.field("endClock", "结束时间", "time", true, "可选", draft.get("endClock"), List.of()));
        fields.add(AiAssistantHelper.field("attendees", "参会人数", "number", true, "可选", draft.get("attendees"), List.of()));
        fields.add(AiAssistantHelper.field("roomId", "会议室", "select", true, "请选择会议室", draft.get("roomId"), roomOptions));
        fields.add(buildParticipantField(draft));
        return fields;
    }

    private AiAssistantMissingFieldVO buildParticipantField(Map<String, Object> draft) {
        return AiAssistantHelper.field("participantUserIds", "参会人", "user-select", false, "搜索并选择参会人", draft.get("participantUserIds"), List.of());
    }

    private ParticipantResolution mergeParticipantDraft(Map<String, Object> draft, String message, Long excludeUserId) {
        List<String> keywords = extractParticipantKeywords(message);
        if (keywords.isEmpty()) {
            return new ParticipantResolution(false, false, AiAssistantHelper.longListValue(draft, "participantUserIds"));
        }

        LinkedHashSet<Long> participantIds = new LinkedHashSet<>(AiAssistantHelper.longListValue(draft, "participantUserIds"));
        boolean ambiguous = false;
        for (String keyword : keywords) {
            List<UserOptionVO> users = userService.searchActiveUsersByDisplayName(keyword, 10, excludeUserId);
            if (users.size() == 1) {
                participantIds.add(users.get(0).getId());
            } else if (users.size() > 1) {
                ambiguous = true;
            }
        }
        draft.put("participantUserIds", new ArrayList<>(participantIds));
        return new ParticipantResolution(true, ambiguous, new ArrayList<>(participantIds));
    }

    private List<String> extractParticipantKeywords(String message) {
        if (message == null || message.isBlank()) {
            return List.of();
        }
        LinkedHashSet<String> keywords = new LinkedHashSet<>();
        collectParticipantKeywords(message, WITH_PARTICIPANT_PATTERN, keywords);
        collectParticipantKeywords(message, ADD_PARTICIPANT_PATTERN, keywords);
        return keywords.stream()
                .filter(keyword -> !PARTICIPANT_STOP_WORDS.contains(keyword))
                .filter(keyword -> keyword.length() >= 2 && keyword.length() <= 20)
                .toList();
    }

    private void collectParticipantKeywords(String message, Pattern pattern, LinkedHashSet<String> keywords) {
        Matcher matcher = pattern.matcher(message);
        while (matcher.find()) {
            String segment = cleanParticipantSegment(matcher.group(1));
            if (segment.isBlank()) {
                continue;
            }
            for (String token : segment.split("[、，,\\s]+|和|跟|与|及|还有")) {
                String keyword = token == null ? "" : token.trim();
                if (!keyword.isBlank()) {
                    keywords.add(keyword);
                }
            }
        }
    }

    private String cleanParticipantSegment(String segment) {
        if (segment == null || segment.isBlank()) {
            return "";
        }
        String value = segment.trim();
        for (String suffix : List.of("的会", "开个会", "开会", "一起开会", "一起开个会", "一起参会", "参会")) {
            int index = value.indexOf(suffix);
            if (index >= 0) {
                value = value.substring(0, index);
            }
        }
        return value.trim();
    }

    private void mergeCreateDraft(Map<String, Object> draft, String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        putIfAbsentNonNull(draft, "title", parseTitle(message));
        putIfAbsentNonNull(draft, "meetingDate", parseDate(message));
        putIfAbsentNonNull(draft, "attendees", parseAttendees(message));
        String[] range = parseTimeRange(message);
        if (range != null) {
            putIfAbsentNonNull(draft, "startClock", range[0]);
            putIfAbsentNonNull(draft, "endClock", range[1]);
        }
        putIfAbsentNonNull(draft, "rating", parseRating(message));
    }

    private boolean mergeUpdateDraft(Map<String, Object> draft, String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        boolean changed = false;
        changed |= putOverrideIfNonNull(draft, "title", parseTitle(message));
        changed |= putOverrideIfNonNull(draft, "meetingDate", parseDate(message));
        changed |= putOverrideIfNonNull(draft, "attendees", parseAttendees(message));
        String[] range = parseTimeRange(message);
        if (range != null) {
            changed |= putOverrideIfNonNull(draft, "startClock", range[0]);
            changed |= putOverrideIfNonNull(draft, "endClock", range[1]);
        }
        return changed;
    }

    private List<AiAssistantFieldOptionVO> buildRecommendedRoomOptions(Map<String, Object> draft) {
        if (AiAssistantHelper.integerValue(draft, "attendees") == null
                || AiAssistantHelper.stringValue(draft, "meetingDate") == null
                || AiAssistantHelper.stringValue(draft, "startClock") == null
                || AiAssistantHelper.stringValue(draft, "endClock") == null) {
            return List.of();
        }

        ReservationRecommendationDTO dto = new ReservationRecommendationDTO();
        dto.setTitle(AiAssistantHelper.stringValue(draft, "title"));
        dto.setAttendees(AiAssistantHelper.integerValue(draft, "attendees"));
        dto.setStartTime(AiAssistantHelper.stringValue(draft, "meetingDate") + " " + AiAssistantHelper.stringValue(draft, "startClock") + ":00");
        dto.setEndTime(AiAssistantHelper.stringValue(draft, "meetingDate") + " " + AiAssistantHelper.stringValue(draft, "endClock") + ":00");
        ReservationRecommendationVO recommendation = reservationService.recommend(dto);
        if (recommendation == null || recommendation.getRecommendations() == null) {
            return List.of();
        }
        return recommendation.getRecommendations().stream()
                .map(item -> AiAssistantHelper.option(item.getRoomName() + " | " + item.getLocation() + " | " + item.getCapacity() + " 人", item.getRoomId()))
                .toList();
    }

    private void addMissingFieldIfBlank(List<AiAssistantMissingFieldVO> fields, Map<String, Object> draft, String key, String label, String inputType, String placeholder) {
        if (AiAssistantHelper.stringValue(draft, key) == null) {
            fields.add(AiAssistantHelper.field(key, label, inputType, true, placeholder, draft.get(key), List.of()));
        }
    }

    private void fillDraftFromReservation(Map<String, Object> draft, MyReservationVO reservation) {
        putIfAbsentNonNull(draft, "title", reservation.getTitle());
        if (reservation.getStartTime() != null && reservation.getStartTime().length() >= 16) {
            putIfAbsentNonNull(draft, "meetingDate", reservation.getStartTime().substring(0, 10));
            putIfAbsentNonNull(draft, "startClock", reservation.getStartTime().substring(11, 16));
        }
        if (reservation.getEndTime() != null && reservation.getEndTime().length() >= 16) {
            putIfAbsentNonNull(draft, "endClock", reservation.getEndTime().substring(11, 16));
        }
        putIfAbsentNonNull(draft, "attendees", reservation.getAttendees());
        putIfAbsentNonNull(draft, "roomId", reservation.getRoomId());
        putIfAbsentNonNull(draft, "remark", reservation.getRemark());
        if (!draft.containsKey("participantUserIds")) {
            List<Long> participantIds = reservation.getParticipants() == null ? List.of() : reservation.getParticipants().stream().map(UserOptionVO::getId).toList();
            draft.put("participantUserIds", participantIds);
        }
    }

    private boolean hasAny(Map<String, Object> draft, String... keys) {
        for (String key : keys) {
            if (draft.get(key) != null) {
                return true;
            }
        }
        return false;
    }

    private boolean putOverrideIfNonNull(Map<String, Object> draft, String key, Object value) {
        if (value == null) {
            return false;
        }
        Object previous = draft.put(key, value);
        return !value.equals(previous);
    }

    private void putIfAbsentNonNull(Map<String, Object> draft, String key, Object value) {
        if (value != null && !draft.containsKey(key)) {
            draft.put(key, value);
        }
    }

    private void putIfPresent(Map<String, Object> draft, String key, Object value) {
        if (value != null) {
            draft.put(key, value);
        }
    }

    private TimeWindow resolveTimeWindow(String message, boolean endedDefault, Map<String, Object> draft) {
        String draftStart = AiAssistantHelper.stringValue(draft, "dateFrom");
        String draftEnd = AiAssistantHelper.stringValue(draft, "dateTo");
        if (draftStart != null && draftEnd != null) {
            return new TimeWindow(draftStart, draftEnd, resolveDraftLabel(draft));
        }

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
            if (message.contains("今天")) {
                return new TimeWindow(today.atStartOfDay().format(DATE_TIME), today.plusDays(1).atStartOfDay().format(DATE_TIME), "今天");
            }
            if (message.contains("本周") || message.contains("这周")) {
                LocalDate monday = today.with(DayOfWeek.MONDAY);
                return new TimeWindow(monday.atStartOfDay().format(DATE_TIME), monday.plusDays(7).atStartOfDay().format(DATE_TIME), "本周");
            }
        }

        if (endedDefault) {
            return new TimeWindow(today.minusDays(30).atStartOfDay().format(DATE_TIME), today.plusDays(1).atStartOfDay().format(DATE_TIME), "最近");
        }
        return new TimeWindow(today.atStartOfDay().format(DATE_TIME), today.plusDays(30).atStartOfDay().format(DATE_TIME), "未来 30 天");
    }

    private String resolveDraftLabel(Map<String, Object> draft) {
        String label = AiAssistantHelper.stringValue(draft, "timeRangeLabel");
        return switch (label == null ? "" : label) {
            case "today" -> "今天";
            case "tomorrow" -> "明天";
            case "day_after_tomorrow" -> "后天";
            case "today_afternoon" -> "今天下午";
            case "tomorrow_afternoon" -> "明天下午";
            case "today_evening" -> "今天晚上";
            case "tomorrow_morning" -> "明天上午";
            case "tomorrow_noon" -> "明天中午";
            case "this_week" -> "本周";
            case "next_week" -> "下周";
            case "this_weekend" -> "这周末";
            case "next_weekend" -> "下周末";
            case "next_monday_morning" -> "下周一上午";
            case "next_tuesday_morning" -> "下周二上午";
            case "day_3_from_today" -> "大后天";
            case "day_3_from_today_morning" -> "大后天上午";
            case "day_3_from_today_evening" -> "大后天晚上";
            case "week_after_next" -> "下下周";
            case "last" -> "最近";
            default -> "指定时间";
        };
    }

    private List<MyReservationVO> filterReservations(List<MyReservationVO> reservations, String message) {
        if (reservations == null || reservations.isEmpty()) {
            return List.of();
        }

        List<MyReservationVO> result = new ArrayList<>(reservations);
        if (message != null && !message.isBlank()) {
            List<MyReservationVO> named = result.stream()
                    .filter(item -> message.contains(item.getTitle()) || message.contains(item.getRoomName()))
                    .toList();
            if (!named.isEmpty()) {
                result = named;
            }
            if (message.contains("下午")) {
                result = result.stream().filter(item -> hour(item.getStartTime()) >= 12 && hour(item.getStartTime()) < 18).toList();
            }
        }
        return result;
    }

    private String participantSummary(Map<String, Object> draft) {
        List<Long> participantIds = AiAssistantHelper.longListValue(draft, "participantUserIds");
        if (participantIds.isEmpty()) {
            return "未设置";
        }
        List<UserOptionVO> participants = userService.listActiveUsersByIds(participantIds);
        if (participants.isEmpty()) {
            return participantIds.stream().map(String::valueOf).collect(Collectors.joining(", "));
        }
        return participants.stream()
                .map(item -> item.getDisplayName() == null || item.getDisplayName().isBlank() ? item.getUsername() : item.getDisplayName())
                .collect(Collectors.joining("、"));
    }

    private Integer parseAttendees(String message) {
        if (message == null) {
            return null;
        }
        Matcher matcher = ATTENDEES_PATTERN.matcher(message);
        return matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
    }

    private Integer parseRating(String message) {
        if (message == null) {
            return null;
        }
        Matcher matcher = RATING_PATTERN.matcher(message);
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
        if (message.contains("今天")) {
            return LocalDate.now().toString();
        }
        if (message.contains("明天")) {
            return LocalDate.now().plusDays(1).toString();
        }
        if (message.contains("后天")) {
            return LocalDate.now().plusDays(2).toString();
        }
        return null;
    }

    private String[] parseTimeRange(String message) {
        if (message == null) {
            return null;
        }
        Matcher matcher = TIME_RANGE_PATTERN.matcher(message);
        if (!matcher.find()) {
            return null;
        }
        return new String[]{normalizeClock(matcher.group(1)), normalizeClock(matcher.group(2))};
    }

    private String parseTitle(String message) {
        if (message == null) {
            return null;
        }
        if (message.contains("周会")) {
            return "周会";
        }
        if (message.contains("复盘")) {
            return "复盘会";
        }
        if (message.contains("评审")) {
            return "评审会";
        }
        if (message.contains("沟通")) {
            return "沟通会";
        }
        return null;
    }

    private String parseCancelReason(String message) {
        if (message == null) {
            return null;
        }
        if (message.contains("冲突")) {
            return "时间冲突，需要重新安排";
        }
        if (message.contains("取消")) {
            return "需求取消，会议无需继续";
        }
        return null;
    }

    private String normalizeClock(String clock) {
        String[] parts = clock.trim().split(":");
        return String.format("%02d:%02d", Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
    }

    private int hour(String dateTime) {
        return Integer.parseInt(dateTime.substring(11, 13));
    }

    private String shortTime(String dateTime) {
        return dateTime == null || dateTime.length() < 16 ? dateTime : dateTime.substring(11, 16);
    }

    private boolean isCancelled(MyReservationVO reservation) {
        return reservation != null && "CANCELLED".equalsIgnoreCase(reservation.getStatus());
    }

    private String displayStatusSuffix(MyReservationVO reservation) {
        if (reservation == null || reservation.getStatus() == null || "ACTIVE".equalsIgnoreCase(reservation.getStatus())) {
            return "";
        }
        if ("ENDED".equalsIgnoreCase(reservation.getStatus())) {
            return "，已结束";
        }
        return "，" + reservation.getStatus();
    }

    private AiAssistantResultVO errorResult(String title) {
        AiAssistantResultVO result = new AiAssistantResultVO();
        result.setStatus("error");
        result.setTitle(title);
        return result;
    }

    private List<String> defaultSuggestions() {
        return List.of("帮我创建一个预约", "取消我明天下午的预约", "查看我本周的预约");
    }

    private record SelectionResult(MyReservationVO selected, AiAssistantActionPlan plan) {
    }

    private record TimeWindow(String start, String end, String label) {
    }

    private record ParticipantResolution(boolean detectedCandidate, boolean hasAmbiguous, List<Long> participantUserIds) {
    }
}
