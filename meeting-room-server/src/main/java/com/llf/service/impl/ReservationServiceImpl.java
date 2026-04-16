package com.llf.service.impl;

import com.llf.dto.MyReservationCancelDTO;
import com.llf.dto.MyReservationReviewDTO;
import com.llf.dto.MyReservationUpdateDTO;
import com.llf.dto.ReservationCreateDTO;
import com.llf.dto.ReservationDeviceRequirementDTO;
import com.llf.dto.ReservationRecommendationDTO;
import com.llf.mapper.ReservationMapper;
import com.llf.mapper.RoomMapper;
import com.llf.result.BizException;
import com.llf.service.NotificationService;
import com.llf.service.ReservationService;
import com.llf.util.DateTimeUtils;
import com.llf.vo.CalendarEventVO;
import com.llf.vo.MyReservationReviewResultVO;
import com.llf.vo.MyReservationVO;
import com.llf.vo.NotificationTodoTargetVO;
import com.llf.vo.PageResultVO;
import com.llf.vo.ReservationReviewVO;
import com.llf.vo.ReservationCreateVO;
import com.llf.vo.ReservationRecommendationItemVO;
import com.llf.vo.ReservationRecommendationVO;
import com.llf.vo.RoomOptionVO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
public class ReservationServiceImpl implements ReservationService {

    @Resource
    private ReservationMapper reservationMapper;
    @Resource
    private RoomMapper roomMapper;
    @Resource
    private NotificationService notificationService;

    private static final Set<String> MY_SCOPES = Set.of("all", "organizer", "participant");
    private static final Set<String> RESERVATION_STATUS = Set.of("ACTIVE", "ENDED", "CANCELLED");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int DEFAULT_PAGE_NUM = 1;
    private static final int DEFAULT_ENDED_PAGE_SIZE = 6;
    private static final int MAX_PAGE_SIZE = 100;

    @Override
    public List<CalendarEventVO> listCalendar(String startDate, String endDate, Long roomId, String status) {
        Timestamp startTime = DateTimeUtils.parseToTimestamp(startDate);
        Timestamp endTime = DateTimeUtils.parseToTimestamp(endDate);
        if (!endTime.after(startTime)) {
            throw new BizException(400, "endDate must be greater than startDate");
        }

        List<CalendarEventVO> events = reservationMapper.selectCalendarEvents(startTime, endTime, roomId, status);
        if (events.isEmpty()) {
            return List.of();
        }

        for (CalendarEventVO event : events) {
            event.setDevices(new ArrayList<>());
        }

        List<Long> reservationIds = events.stream()
                .map(CalendarEventVO::getId)
                .toList();

        Map<Long, List<CalendarEventVO.DeviceVO>> deviceMap = reservationMapper.selectCalendarEventDevices(reservationIds)
                .stream()
                .collect(Collectors.groupingBy(
                        CalendarEventVO.DeviceRow::getReservationId,
                        LinkedHashMap::new,
                        Collectors.mapping(this::toCalendarDeviceVO, Collectors.toList())
                ));

        for (CalendarEventVO event : events) {
            event.setDevices(deviceMap.getOrDefault(event.getId(), List.of()));
        }
        return events;
    }

    @Override
    @Transactional
    public ReservationCreateVO create(ReservationCreateDTO dto, Long organizerId) {
        if (dto == null) {
            throw new BizException(400, "request body must not be null");
        }

        LocalDateTime startTime = parseMeetingTime(dto.getMeetingDate(), dto.getStartClock(), "startClock");
        LocalDateTime endTime = parseMeetingTime(dto.getMeetingDate(), dto.getEndClock(), "endClock");
        if (!endTime.isAfter(startTime)) {
            throw new BizException(400, "endClock must be greater than startClock");
        }

        RoomOptionVO room = roomMapper.selectOptionById(dto.getRoomId());
        if (room == null) {
            throw new BizException(404, "room not found");
        }
        if (!"AVAILABLE".equalsIgnoreCase(room.getStatus())) {
            throw new BizException(400, "room is under maintenance");
        }
        if (dto.getAttendees() > room.getCapacity()) {
            throw new BizException(400, "attendees exceeds room capacity");
        }

        Timestamp start = Timestamp.valueOf(startTime);
        Timestamp end = Timestamp.valueOf(endTime);
        if (reservationMapper.countConflictByRoomId(dto.getRoomId(), start, end) > 0) {
            throw new BizException(400, "reservation time conflicts with another active reservation");
        }

        Map<Long, Integer> requiredDevices = normalizeDeviceRequirements(dto.getDeviceRequirements());
        validateRoomDeviceRequirements(dto.getRoomId(), requiredDevices);

        String reservationNo = generateReservationNo();
        reservationMapper.insertReservation(
                reservationNo,
                dto.getRoomId(),
                organizerId,
                dto.getTitle().trim(),
                trimToNull(dto.getRemark()),
                dto.getAttendees(),
                start,
                end
        );
        Long reservationId = reservationMapper.lastInsertId();
        for (Map.Entry<Long, Integer> entry : requiredDevices.entrySet()) {
            reservationMapper.insertReservationDevice(reservationId, entry.getKey(), entry.getValue());
        }
        ReservationCreateVO result = reservationMapper.selectCreateResultById(reservationId);
        notificationService.createReservationCreatedNotification(
                organizerId,
                result.getTitle(),
                room.getName(),
                result.getStartTime(),
                result.getEndTime()
        );
        return result;
    }

    @Override
    public ReservationRecommendationVO recommend(ReservationRecommendationDTO dto) {
        if (dto == null) {
            throw new BizException(400, "request body must not be null");
        }

        Timestamp start = parseRequestTimestamp(dto.getStartTime(), "startTime");
        Timestamp end = parseRequestTimestamp(dto.getEndTime(), "endTime");
        if (!end.after(start)) {
            throw new BizException(400, "endTime must be greater than startTime");
        }

        List<RoomMapper.RecommendationRoomRow> candidateRooms = roomMapper.selectRecommendationCandidates(dto.getAttendees());
        if (candidateRooms == null || candidateRooms.isEmpty()) {
            return emptyRecommendationResult();
        }

        List<Long> roomIds = candidateRooms.stream()
                .map(RoomMapper.RecommendationRoomRow::getId)
                .toList();
        List<Long> conflictRoomIds = reservationMapper.selectConflictRoomIds(start, end, roomIds);
        Set<Long> conflictSet = conflictRoomIds == null ? Set.of() : Set.copyOf(conflictRoomIds);

        List<RoomMapper.RecommendationRoomRow> availableRooms = candidateRooms.stream()
                .filter(room -> !conflictSet.contains(room.getId()))
                .toList();
        if (availableRooms.isEmpty()) {
            return emptyRecommendationResult();
        }

        Map<Long, Integer> requiredDevices = normalizeDeviceRequirements(dto.getDeviceRequirements());
        Map<Long, Map<Long, Integer>> roomDeviceMap = buildRoomDeviceMap(
                roomMapper.selectEnabledRoomDevices(
                        availableRooms.stream().map(RoomMapper.RecommendationRoomRow::getId).toList()
                )
        );

        List<ReservationRecommendationItemVO> items = availableRooms.stream()
                .map(room -> toRecommendationItem(room, roomDeviceMap.getOrDefault(room.getId(), Map.of()), dto, requiredDevices))
                .filter(item -> requiredDevices.isEmpty() || Boolean.TRUE.equals(item.getDeviceFullyMatched()))
                .sorted(Comparator
                        .comparing(ReservationRecommendationItemVO::getScore).reversed()
                        .thenComparing(ReservationRecommendationItemVO::getCapacity)
                        .thenComparing(ReservationRecommendationItemVO::getRoomId))
                .toList();

        ReservationRecommendationVO result = new ReservationRecommendationVO();
        result.setRecommendations(items);
        return result;
    }

    @Override
    public void cancel(Long id) {
        reservationMapper.cancel(id);
    }

    @Override
    public List<MyReservationVO> myReservations(Long currentUserId, String startDate, String endDate, String scope, String status, boolean futureOnly) {
        validateScope(scope);
        validateStatus(status);

        Timestamp start = resolveMyReservationStart(startDate, futureOnly);
        Timestamp end = DateTimeUtils.parseToTimestamp(endDate);
        if (!end.after(start)) {
            throw new BizException(400, "endDate must be greater than startDate");
        }

        List<MyReservationVO> reservations = reservationMapper.selectMyReservations(currentUserId, start, end, scope, status);
        return fillReservationReviews(currentUserId, fillReservationDevices(reservations));
    }

    @Override
    public PageResultVO<MyReservationVO> myEndedReservations(Long currentUserId, String scope, Integer pageNum, Integer pageSize) {
        String normalizedScope = normalizeScope(scope);
        validateScope(normalizedScope);

        int resolvedPageNum = resolvePageNum(pageNum);
        int resolvedPageSize = resolveEndedPageSize(pageSize);
        int offset = (resolvedPageNum - 1) * resolvedPageSize;

        long total = reservationMapper.countMyEndedReservations(currentUserId, normalizedScope);
        List<MyReservationVO> reservations = total <= 0
                ? List.of()
                : reservationMapper.selectMyEndedReservationsPage(currentUserId, normalizedScope, resolvedPageSize, offset);
        List<MyReservationVO> result = fillReservationReviews(currentUserId, fillReservationDevices(reservations));

        PageResultVO<MyReservationVO> pageResult = new PageResultVO<>();
        pageResult.setList(result);
        pageResult.setTotal(total);
        pageResult.setPageNum(resolvedPageNum);
        pageResult.setPageSize(resolvedPageSize);
        return pageResult;
    }

    @Override
    public List<RoomOptionVO> myRoomOptions() {
        return roomMapper.selectAvailableOptions();
    }

    @Override
    @Transactional
    public MyReservationVO updateMyReservation(Long id, Long currentUserId, MyReservationUpdateDTO dto) {
        requireEditableReservation(id, currentUserId);
        LocalDateTime startTime = parseMeetingTime(dto.getMeetingDate(), dto.getStartClock(), "startClock");
        LocalDateTime endTime = parseMeetingTime(dto.getMeetingDate(), dto.getEndClock(), "endClock");
        if (!endTime.isAfter(startTime)) {
            throw new BizException(400, "endClock must be greater than startClock");
        }

        RoomOptionVO room = roomMapper.selectOptionById(dto.getRoomId());
        if (room == null) {
            throw new BizException(404, "room not found");
        }
        if (!"AVAILABLE".equalsIgnoreCase(room.getStatus())) {
            throw new BizException(400, "room is under maintenance");
        }
        if (dto.getAttendees() > room.getCapacity()) {
            throw new BizException(400, "attendees exceeds room capacity");
        }

        Timestamp start = Timestamp.valueOf(startTime);
        Timestamp end = Timestamp.valueOf(endTime);
        if (reservationMapper.countConflictExcludeSelf(id, dto.getRoomId(), start, end) > 0) {
            throw new BizException(400, "reservation time conflicts with another active reservation");
        }

        Map<Long, Integer> requiredDevices = normalizeDeviceRequirements(dto.getDeviceRequirements());
        validateRoomDeviceRequirements(dto.getRoomId(), requiredDevices);

        reservationMapper.updateMyReservation(
                id,
                dto.getTitle().trim(),
                dto.getRoomId(),
                start,
                end,
                dto.getAttendees(),
                trimToNull(dto.getRemark())
        );
        reservationMapper.deleteReservationDevicesByReservationId(id);
        for (Map.Entry<Long, Integer> entry : requiredDevices.entrySet()) {
            reservationMapper.insertReservationDevice(id, entry.getKey(), entry.getValue());
        }
        MyReservationVO result = requireMyReservationDetail(id, currentUserId);
        notificationService.createReservationUpdatedNotification(
                currentUserId,
                result.getTitle(),
                result.getRoomName(),
                result.getStartTime(),
                result.getEndTime()
        );
        return result;
    }

    @Override
    @Transactional
    public MyReservationVO cancelMyReservation(Long id, Long currentUserId, MyReservationCancelDTO dto) {
        requireEditableReservation(id, currentUserId);
        reservationMapper.cancelMyReservation(id, dto.getCancelReason().trim());
        MyReservationVO result = requireMyReservationDetail(id, currentUserId);
        notificationService.createReservationCancelledNotification(
                currentUserId,
                result.getTitle(),
                result.getCancelReason()
        );
        return result;
    }

    @Override
    @Transactional
    public MyReservationReviewResultVO submitMyReservationReview(Long id, Long currentUserId, MyReservationReviewDTO dto) {
        ReservationMapper.ReviewableReservationRow reservation = requireReviewableReservation(id, currentUserId);
        if (!"ENDED".equalsIgnoreCase(reservation.getStatus())) {
            throw new BizException(400, "only ended reservation can be reviewed");
        }

        ReservationMapper.ReservationReviewRow existingReview =
                reservationMapper.selectReservationReviewByReservationIdAndUserId(id, currentUserId);
        if (existingReview != null) {
            throw new BizException(400, "review already exists");
        }

        reservationMapper.insertReservationReview(id, currentUserId, dto.getRating(), normalizeReviewContent(dto.getContent()));

        ReservationMapper.ReservationReviewRow savedReview =
                reservationMapper.selectReservationReviewByReservationIdAndUserId(id, currentUserId);

        MyReservationReviewResultVO result = new MyReservationReviewResultVO();
        result.setReviewed(Boolean.TRUE);
        result.setMyReview(toReservationReviewVO(savedReview));
        return result;
    }

    @Override
    @Transactional
    public int markEnded() {
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        List<NotificationTodoTargetVO> todoTargets = reservationMapper.selectReviewTodoTargets(now)
                .stream()
                .map(this::toNotificationTodoTargetVO)
                .toList();
        int updated = reservationMapper.markEnded(now);
        if (updated > 0 && !todoTargets.isEmpty()) {
            notificationService.createReviewTodoNotifications(todoTargets);
        }
        return updated;
    }

    private ReservationRecommendationVO emptyRecommendationResult() {
        ReservationRecommendationVO result = new ReservationRecommendationVO();
        result.setRecommendations(List.of());
        return result;
    }

    private CalendarEventVO.DeviceVO toCalendarDeviceVO(CalendarEventVO.DeviceRow row) {
        CalendarEventVO.DeviceVO vo = new CalendarEventVO.DeviceVO();
        vo.setId(row.getId());
        vo.setDeviceId(row.getDeviceId());
        vo.setDeviceCode(row.getDeviceCode());
        vo.setName(row.getName());
        vo.setQuantity(row.getQuantity());
        vo.setStatus(row.getStatus());
        return vo;
    }

    private List<MyReservationVO> fillReservationDevices(List<MyReservationVO> reservations) {
        if (reservations == null || reservations.isEmpty()) {
            return List.of();
        }

        for (MyReservationVO reservation : reservations) {
            reservation.setDevices(new ArrayList<>());
        }

        List<Long> reservationIds = reservations.stream()
                .map(MyReservationVO::getId)
                .toList();

        Map<Long, List<MyReservationVO.DeviceVO>> deviceMap = reservationMapper.selectMyReservationDevices(reservationIds)
                .stream()
                .collect(Collectors.groupingBy(
                        MyReservationVO.DeviceRow::getReservationId,
                        LinkedHashMap::new,
                        Collectors.mapping(this::toMyReservationDeviceVO, Collectors.toList())
                ));

        for (MyReservationVO reservation : reservations) {
            reservation.setDevices(deviceMap.getOrDefault(reservation.getId(), List.of()));
        }
        return reservations;
    }

    private List<MyReservationVO> fillReservationReviews(Long currentUserId, List<MyReservationVO> reservations) {
        if (reservations == null || reservations.isEmpty()) {
            return List.of();
        }

        for (MyReservationVO reservation : reservations) {
            reservation.setReviewed(Boolean.FALSE);
            reservation.setMyReview(null);
        }

        List<Long> reservationIds = reservations.stream()
                .map(MyReservationVO::getId)
                .toList();

        List<ReservationMapper.ReservationReviewRow> reviewRows =
                reservationMapper.selectMyReservationReviews(currentUserId, reservationIds);
        if (reviewRows == null || reviewRows.isEmpty()) {
            return reservations;
        }

        Map<Long, ReservationMapper.ReservationReviewRow> reviewMap = reviewRows.stream()
                .collect(Collectors.toMap(
                        ReservationMapper.ReservationReviewRow::getReservationId,
                        row -> row,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        for (MyReservationVO reservation : reservations) {
            ReservationMapper.ReservationReviewRow reviewRow = reviewMap.get(reservation.getId());
            if (reviewRow != null) {
                reservation.setReviewed(Boolean.TRUE);
                reservation.setMyReview(toReservationReviewVO(reviewRow));
            }
        }
        return reservations;
    }

    private MyReservationVO.DeviceVO toMyReservationDeviceVO(MyReservationVO.DeviceRow row) {
        MyReservationVO.DeviceVO vo = new MyReservationVO.DeviceVO();
        vo.setId(row.getId());
        vo.setDeviceId(row.getDeviceId());
        vo.setDeviceCode(row.getDeviceCode());
        vo.setName(row.getName());
        vo.setQuantity(row.getQuantity());
        vo.setStatus(row.getStatus());
        return vo;
    }

    private ReservationReviewVO toReservationReviewVO(ReservationMapper.ReservationReviewRow row) {
        if (row == null) {
            return null;
        }
        ReservationReviewVO vo = new ReservationReviewVO();
        vo.setRating(row.getRating());
        vo.setContent(row.getContent());
        vo.setCreatedAt(row.getCreatedAt() == null ? null : row.getCreatedAt().toLocalDateTime().format(DATE_TIME_FORMATTER));
        return vo;
    }

    private NotificationTodoTargetVO toNotificationTodoTargetVO(ReservationMapper.ReviewTodoTargetRow row) {
        NotificationTodoTargetVO vo = new NotificationTodoTargetVO();
        vo.setReservationId(row.getReservationId());
        vo.setUserId(row.getUserId());
        vo.setTitle(row.getTitle());
        return vo;
    }

    private ReservationMapper.ReservationEditableRow requireEditableReservation(Long id, Long currentUserId) {
        ReservationMapper.ReservationEditableRow reservation = reservationMapper.selectEditableReservation(id, currentUserId);
        if (reservation == null) {
            throw new BizException(404, "reservation not found");
        }
        if (!"ACTIVE".equalsIgnoreCase(reservation.getStatus())) {
            throw new BizException(400, "reservation is not active");
        }
        if (!reservation.getEndTime().after(Timestamp.valueOf(LocalDateTime.now()))) {
            throw new BizException(400, "reservation has already ended");
        }
        return reservation;
    }

    private MyReservationVO requireMyReservationDetail(Long id, Long currentUserId) {
        MyReservationVO reservation = reservationMapper.selectMyReservationDetail(id, currentUserId);
        if (reservation == null) {
            throw new BizException(404, "reservation not found");
        }
        return fillReservationReviews(currentUserId, fillReservationDevices(List.of(reservation))).get(0);
    }

    private ReservationMapper.ReviewableReservationRow requireReviewableReservation(Long id, Long currentUserId) {
        ReservationMapper.ReviewableReservationRow reservation =
                reservationMapper.selectReviewableReservation(id, currentUserId);
        if (reservation == null) {
            throw new BizException(404, "reservation not found");
        }
        return reservation;
    }

    private void validateScope(String scope) {
        if (scope == null || !MY_SCOPES.contains(scope)) {
            throw new BizException(400, "scope must be one of all, organizer, participant");
        }
    }

    private void validateStatus(String status) {
        if (status != null && !status.isBlank() && !RESERVATION_STATUS.contains(status)) {
            throw new BizException(400, "status must be one of ACTIVE, ENDED, CANCELLED");
        }
    }

    private String normalizeScope(String scope) {
        if (scope == null || scope.isBlank()) {
            return "all";
        }
        return scope.trim().toLowerCase();
    }

    private int resolvePageNum(Integer pageNum) {
        if (pageNum == null || pageNum < 1) {
            return DEFAULT_PAGE_NUM;
        }
        return pageNum;
    }

    private int resolveEndedPageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return DEFAULT_ENDED_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    private Timestamp resolveMyReservationStart(String startDate, boolean futureOnly) {
        Timestamp todayStart = Timestamp.valueOf(LocalDate.now().atStartOfDay());
        if (startDate == null || startDate.isBlank()) {
            if (futureOnly) {
                return todayStart;
            }
            return DateTimeUtils.parseToTimestamp(startDate);
        }

        Timestamp parsedStart = DateTimeUtils.parseToTimestamp(startDate);
        if (!futureOnly || !todayStart.after(parsedStart)) {
            return parsedStart;
        }
        return todayStart;
    }

    private LocalDateTime parseMeetingTime(String meetingDate, String clock, String fieldName) {
        try {
            return DateTimeUtils.parseToLocalDateTime(meetingDate.trim() + " " + normalizeClock(clock));
        } catch (Exception e) {
            throw new BizException(400, fieldName + " format is invalid");
        }
    }

    private Timestamp parseRequestTimestamp(String text, String fieldName) {
        try {
            return DateTimeUtils.parseToTimestamp(text);
        } catch (Exception e) {
            throw new BizException(400, fieldName + " format is invalid");
        }
    }

    private String normalizeClock(String clock) {
        String value = clock.trim();
        try {
            LocalTime.parse(value, DateTimeFormatter.ofPattern("HH:mm:ss"));
            return value;
        } catch (DateTimeParseException ignore) {
        }
        try {
            LocalTime.parse(value, DateTimeFormatter.ofPattern("HH:mm"));
            return value;
        } catch (DateTimeParseException ignore) {
        }
        throw new BizException(400, "clock format is invalid");
    }

    private Map<Long, Integer> normalizeDeviceRequirements(List<ReservationDeviceRequirementDTO> requirements) {
        if (requirements == null || requirements.isEmpty()) {
            return Map.of();
        }
        Map<Long, Integer> normalized = new LinkedHashMap<>();
        for (ReservationDeviceRequirementDTO requirement : requirements) {
            if (requirement == null || requirement.getDeviceId() == null || requirement.getQuantity() == null) {
                continue;
            }
            normalized.merge(requirement.getDeviceId(), requirement.getQuantity(), Integer::sum);
        }
        return normalized;
    }

    private void validateRoomDeviceRequirements(Long roomId, Map<Long, Integer> requiredDevices) {
        if (requiredDevices.isEmpty()) {
            return;
        }
        Map<Long, Map<Long, Integer>> roomDeviceMap = buildRoomDeviceMap(roomMapper.selectEnabledRoomDevices(List.of(roomId)));
        DeviceMatchSummary matchSummary = evaluateDeviceMatch(requiredDevices, roomDeviceMap.getOrDefault(roomId, Map.of()));
        if (!matchSummary.fullyMatched()) {
            throw new BizException(400, "device requirements cannot be satisfied");
        }
    }

    private Map<Long, Map<Long, Integer>> buildRoomDeviceMap(List<RoomMapper.RoomDeviceAvailabilityRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return Map.of();
        }
        Map<Long, Map<Long, Integer>> result = new HashMap<>();
        for (RoomMapper.RoomDeviceAvailabilityRow row : rows) {
            result.computeIfAbsent(row.getRoomId(), key -> new HashMap<>())
                    .put(row.getDeviceId(), row.getQuantity());
        }
        return result;
    }

    private ReservationRecommendationItemVO toRecommendationItem(RoomMapper.RecommendationRoomRow room,
                                                                 Map<Long, Integer> roomDevices,
                                                                 ReservationRecommendationDTO request,
                                                                 Map<Long, Integer> requiredDevices) {
        DeviceMatchSummary deviceMatch = evaluateDeviceMatch(requiredDevices, roomDevices);
        boolean preferred = request.getPreferredRoomId() != null && request.getPreferredRoomId().equals(room.getId());
        double wasteRate = calculateWasteRate(room.getCapacity(), request.getAttendees());
        int score = clampScore(100 - calculateCapacityPenalty(wasteRate) + calculateDeviceBonus(deviceMatch.requiredTypeCount(), deviceMatch.matchedTypeCount()));

        List<String> tags = new ArrayList<>();
        tags.add(buildCapacityTag(wasteRate));
        if (deviceMatch.requiredTypeCount() > 0) {
            tags.add(buildDeviceTag(deviceMatch.matchedTypeCount(), deviceMatch.requiredTypeCount()));
        }
        if (preferred) {
            tags.add("当前已选");
        }

        ReservationRecommendationItemVO item = new ReservationRecommendationItemVO();
        item.setRoomId(room.getId());
        item.setRoomCode(room.getRoomCode());
        item.setRoomName(room.getName());
        item.setLocation(room.getLocation());
        item.setCapacity(room.getCapacity());
        item.setScore(score);
        item.setWasteRate(roundWasteRate(wasteRate));
        item.setRequiredDeviceTypeCount(deviceMatch.requiredTypeCount());
        item.setMatchedDeviceTypeCount(deviceMatch.matchedTypeCount());
        item.setDeviceFullyMatched(deviceMatch.fullyMatched());
        item.setIsPreferred(preferred);
        item.setTags(tags);
        return item;
    }

    private DeviceMatchSummary evaluateDeviceMatch(Map<Long, Integer> requiredDevices, Map<Long, Integer> roomDevices) {
        int requiredTypeCount = requiredDevices.size();
        if (requiredTypeCount == 0) {
            return new DeviceMatchSummary(0, 0, true);
        }
        int matchedTypeCount = 0;
        for (Map.Entry<Long, Integer> entry : requiredDevices.entrySet()) {
            Integer availableQuantity = roomDevices.get(entry.getKey());
            if (availableQuantity != null && availableQuantity >= entry.getValue()) {
                matchedTypeCount++;
            }
        }
        return new DeviceMatchSummary(requiredTypeCount, matchedTypeCount, matchedTypeCount == requiredTypeCount);
    }

    private double calculateWasteRate(Integer capacity, Integer attendees) {
        if (capacity == null || capacity <= 0 || attendees == null || attendees <= 0) {
            return 0D;
        }
        return Math.max(0D, (capacity - attendees) * 1D / capacity);
    }

    private int calculateCapacityPenalty(double wasteRate) {
        return (int) Math.floor(wasteRate * 40);
    }

    private int calculateDeviceBonus(int requiredTypeCount, int matchedTypeCount) {
        if (requiredTypeCount == 0) {
            return 0;
        }
        if (matchedTypeCount == requiredTypeCount) {
            return 15;
        }
        return (int) Math.floor((matchedTypeCount * 1D / requiredTypeCount) * 10);
    }

    private int clampScore(int score) {
        return Math.max(0, Math.min(100, score));
    }

    private String buildCapacityTag(double wasteRate) {
        if (wasteRate <= 0.1D) {
            return "容量匹配好";
        }
        if (wasteRate <= 0.4D) {
            return "容量略大";
        }
        return "浪费" + (int) Math.floor(wasteRate * 100) + "%";
    }

    private String buildDeviceTag(int matchedTypeCount, int requiredTypeCount) {
        if (matchedTypeCount == requiredTypeCount) {
            return "设备齐全";
        }
        if (matchedTypeCount > 0) {
            return "设备部分满足";
        }
        return "设备不足";
    }

    private double roundWasteRate(double wasteRate) {
        return BigDecimal.valueOf(wasteRate)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private String trimToNull(String text) {
        if (text == null) {
            return null;
        }
        String value = text.trim();
        return value.isEmpty() ? null : value;
    }

    private String normalizeReviewContent(String content) {
        String value = trimToNull(content);
        if (value != null && value.length() > 300) {
            throw new BizException(400, "content length must be less than or equal to 300");
        }
        return value;
    }

    private String generateReservationNo() {
        return "RSV" + System.currentTimeMillis() + ThreadLocalRandom.current().nextInt(100, 1000);
    }

    private record DeviceMatchSummary(int requiredTypeCount, int matchedTypeCount, boolean fullyMatched) {
    }
}
