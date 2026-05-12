package com.llf.service.impl;

import com.llf.dto.reservation.MyReservationCancelDTO;
import com.llf.dto.reservation.MyReservationReviewDTO;
import com.llf.dto.reservation.MyReservationUpdateDTO;
import com.llf.dto.reservation.ReservationCreateDTO;
import com.llf.dto.reservation.ReservationDeviceRequirementDTO;
import com.llf.dto.reservation.ReservationRecommendationDTO;
import com.llf.mapper.ReservationMapper;
import com.llf.mapper.RoomMapper;
import com.llf.result.BizException;
import com.llf.service.NotificationService;
import com.llf.service.ReservationService;
import com.llf.service.UserService;
import com.llf.util.DateTimeUtils;
import com.llf.vo.admin.reservation.AdminReservationItemVO;
import com.llf.vo.admin.reservation.AdminReservationPageVO;
import com.llf.vo.admin.reservation.AdminReservationStatsVO;
import com.llf.vo.reservation.CalendarEventVO;
import com.llf.vo.reservation.MyReservationReviewResultVO;
import com.llf.vo.reservation.MyReservationVO;
import com.llf.vo.notification.NotificationTodoTargetVO;
import com.llf.vo.common.PageResultVO;
import com.llf.vo.reservation.ReservationReviewVO;
import com.llf.vo.reservation.ReservationCreateVO;
import com.llf.vo.reservation.ReservationRecommendationItemVO;
import com.llf.vo.reservation.ReservationRecommendationVO;
import com.llf.vo.room.RoomOptionVO;
import com.llf.vo.user.UserOptionVO;
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
    @Resource
    private UserService userService;

    private static final Set<String> MY_SCOPES = Set.of("all", "organizer", "participant");
    private static final Set<String> RESERVATION_STATUS = Set.of("PENDING", "ACTIVE", "ENDED", "CANCELLED", "REJECTED", "EXCEPTION");
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

        String normalizedStatus = normalizeReservationStatusNullable(status);
        if (normalizedStatus == null) {
            normalizedStatus = "ACTIVE";
        }
        List<CalendarEventVO> events = reservationMapper.selectCalendarEvents(startTime, endTime, roomId, normalizedStatus);
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
        List<Long> participantUserIds = normalizeParticipantUserIds(dto.getParticipantUserIds());
        if (participantUserIds == null) {
            participantUserIds = List.of();
        }

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
        rewriteReservationParticipants(reservationId, participantUserIds);
        ReservationCreateVO result = reservationMapper.selectCreateResultById(reservationId);
        fillCreateParticipants(result);
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
    public List<MyReservationVO> myReservations(Long currentUserId, String startDate, String endDate, String scope, String status, boolean futureOnly) {
        validateScope(scope);
        String normalizedStatus = normalizeReservationStatusNullable(status);

        Timestamp start = resolveMyReservationStart(startDate, futureOnly);
        Timestamp end = DateTimeUtils.parseToTimestamp(endDate);
        if (!end.after(start)) {
            throw new BizException(400, "endDate must be greater than startDate");
        }

        List<MyReservationVO> reservations = reservationMapper.selectMyReservations(currentUserId, start, end, scope, normalizedStatus);
        return fillReservationReviews(currentUserId, fillReservationParticipants(fillReservationDevices(reservations)));
    }

    @Override
    public MyReservationVO myReservationDetail(Long id, Long currentUserId) {
        return requireMyReservationDetail(id, currentUserId);
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
        List<MyReservationVO> result = fillReservationReviews(currentUserId, fillReservationParticipants(fillReservationDevices(reservations)));

        PageResultVO<MyReservationVO> pageResult = new PageResultVO<>();
        pageResult.setList(result);
        pageResult.setTotal(total);
        pageResult.setPageNum(resolvedPageNum);
        pageResult.setPageSize(resolvedPageSize);
        return pageResult;
    }

    @Override
    public AdminReservationPageVO adminReservations(Integer currentPage, Integer size, String keyword, String status) {
        int resolvedPageNum = resolvePageNum(currentPage);
        int resolvedPageSize = resolveAdminPageSize(size);
        int offset = (resolvedPageNum - 1) * resolvedPageSize;
        String normalizedStatus = normalizeReservationStatusNullable(status);
        String normalizedKeyword = trimToNull(keyword);

        Long total = reservationMapper.countAdminReservations(normalizedKeyword, normalizedStatus);
        List<AdminReservationItemVO> list = total != null && total > 0
                ? reservationMapper.selectAdminReservations(normalizedKeyword, normalizedStatus, resolvedPageSize, offset)
                : List.of();

        AdminReservationStatsVO stats = new AdminReservationStatsVO();
        stats.setTotalCount(defaultZero(reservationMapper.countAdminReservationsByStatus(null)));
        stats.setPendingCount(defaultZero(reservationMapper.countAdminReservationsByStatus("PENDING")));
        stats.setActiveCount(defaultZero(reservationMapper.countAdminReservationsByStatus("ACTIVE")));
        stats.setRejectedCount(defaultZero(reservationMapper.countAdminReservationsByStatus("REJECTED")));
        stats.setExceptionCount(defaultZero(reservationMapper.countAdminReservationsByStatus("EXCEPTION")));

        AdminReservationPageVO page = new AdminReservationPageVO();
        page.setList(fillAdminReservationDetails(list));
        page.setTotal(total == null ? 0L : total);
        page.setStats(stats);
        return page;
    }

    @Override
    @Transactional
    public AdminReservationItemVO adminApproveReservation(Long id, Long adminUserId, String remark) {
        ReservationMapper.AdminReservationProcessRow reservation = requireAdminReservationProcess(id);
        if (!"PENDING".equalsIgnoreCase(reservation.getStatus())) {
            throw new BizException(400, "only pending reservation can be approved");
        }

        revalidateBeforeApprove(reservation);
        int updated = reservationMapper.approveReservation(id, adminUserId, trimToNull(remark));
        if (updated <= 0) {
            throw new BizException(400, "reservation status changed");
        }
        notificationService.createReservationApprovedNotification(
                reservation.getOrganizerId(),
                reservation.getTitle(),
                reservation.getRoomName(),
                formatTimestamp(reservation.getStartTime()),
                formatTimestamp(reservation.getEndTime())
        );
        return requireAdminReservationDetail(id);
    }

    @Override
    @Transactional
    public AdminReservationItemVO adminRejectReservation(Long id, Long adminUserId, String reason) {
        String normalizedReason = requireReason(reason);
        ReservationMapper.AdminReservationProcessRow reservation = requireAdminReservationProcess(id);
        if (!"PENDING".equalsIgnoreCase(reservation.getStatus())) {
            throw new BizException(400, "only pending reservation can be rejected");
        }

        int updated = reservationMapper.rejectReservation(id, adminUserId, normalizedReason);
        if (updated <= 0) {
            throw new BizException(400, "reservation status changed");
        }
        notificationService.createReservationRejectedNotification(reservation.getOrganizerId(), reservation.getTitle(), normalizedReason);
        return requireAdminReservationDetail(id);
    }

    @Override
    @Transactional
    public AdminReservationItemVO adminExceptionReservation(Long id, Long adminUserId, String reason) {
        String normalizedReason = requireReason(reason);
        ReservationMapper.AdminReservationProcessRow reservation = requireAdminReservationProcess(id);
        if (!"ACTIVE".equalsIgnoreCase(reservation.getStatus())) {
            throw new BizException(400, "only active reservation can be marked exception");
        }

        int updated = reservationMapper.markReservationException(id, adminUserId, normalizedReason);
        if (updated <= 0) {
            throw new BizException(400, "reservation status changed");
        }
        notificationService.createReservationExceptionNotification(reservation.getOrganizerId(), reservation.getTitle(), normalizedReason);
        return requireAdminReservationDetail(id);
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
        List<Long> participantUserIds = normalizeParticipantUserIds(dto.getParticipantUserIds());

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
        if (participantUserIds != null) {
            rewriteReservationParticipants(id, participantUserIds);
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

    private List<AdminReservationItemVO> fillAdminReservationDetails(List<AdminReservationItemVO> reservations) {
        if (reservations == null || reservations.isEmpty()) {
            return List.of();
        }

        for (AdminReservationItemVO reservation : reservations) {
            reservation.setDevices(new ArrayList<>());
            reservation.setParticipants(new ArrayList<>());
        }

        List<Long> reservationIds = reservations.stream()
                .map(AdminReservationItemVO::getId)
                .toList();

        Map<Long, List<MyReservationVO.DeviceVO>> deviceMap = reservationMapper.selectMyReservationDevices(reservationIds)
                .stream()
                .collect(Collectors.groupingBy(
                        MyReservationVO.DeviceRow::getReservationId,
                        LinkedHashMap::new,
                        Collectors.mapping(this::toMyReservationDeviceVO, Collectors.toList())
                ));

        List<ReservationMapper.ReservationParticipantRow> participantRows = reservationMapper.selectReservationParticipants(reservationIds);
        Map<Long, List<UserOptionVO>> participantMap = participantRows == null || participantRows.isEmpty()
                ? Map.of()
                : participantRows.stream()
                .collect(Collectors.groupingBy(
                        ReservationMapper.ReservationParticipantRow::getReservationId,
                        LinkedHashMap::new,
                        Collectors.mapping(this::toParticipantVO, Collectors.toList())
                ));

        for (AdminReservationItemVO reservation : reservations) {
            reservation.setDevices(deviceMap.getOrDefault(reservation.getId(), List.of()));
            reservation.setParticipants(participantMap.getOrDefault(reservation.getId(), List.of()));
        }
        return reservations;
    }

    private AdminReservationItemVO requireAdminReservationDetail(Long id) {
        AdminReservationItemVO reservation = reservationMapper.selectAdminReservationById(id);
        if (reservation == null) {
            throw new BizException(404, "reservation not found");
        }
        return fillAdminReservationDetails(List.of(reservation)).get(0);
    }

    private ReservationMapper.AdminReservationProcessRow requireAdminReservationProcess(Long id) {
        ReservationMapper.AdminReservationProcessRow reservation = reservationMapper.selectAdminReservationProcessById(id);
        if (reservation == null) {
            throw new BizException(404, "reservation not found");
        }
        return reservation;
    }

    private void revalidateBeforeApprove(ReservationMapper.AdminReservationProcessRow reservation) {
        RoomOptionVO room = roomMapper.selectOptionById(reservation.getRoomId());
        if (room == null) {
            throw new BizException(404, "room not found");
        }
        if (!"AVAILABLE".equalsIgnoreCase(room.getStatus())) {
            throw new BizException(400, "room is under maintenance");
        }
        if (reservation.getAttendees() != null && room.getCapacity() != null && reservation.getAttendees() > room.getCapacity()) {
            throw new BizException(400, "attendees exceeds room capacity");
        }
        if (reservationMapper.countConflictExcludeSelf(reservation.getId(), reservation.getRoomId(), reservation.getStartTime(), reservation.getEndTime()) > 0) {
            throw new BizException(400, "reservation time conflicts with another active reservation");
        }
        validateRoomDeviceRequirements(reservation.getRoomId(), loadReservationDeviceRequirements(reservation.getId()));
    }

    private Map<Long, Integer> loadReservationDeviceRequirements(Long reservationId) {
        List<MyReservationVO.DeviceRow> rows = reservationMapper.selectMyReservationDevices(List.of(reservationId));
        if (rows == null || rows.isEmpty()) {
            return Map.of();
        }
        Map<Long, Integer> requirements = new LinkedHashMap<>();
        for (MyReservationVO.DeviceRow row : rows) {
            if (row.getDeviceId() != null && row.getQuantity() != null) {
                requirements.merge(row.getDeviceId(), row.getQuantity(), Integer::sum);
            }
        }
        return requirements;
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

    private List<MyReservationVO> fillReservationParticipants(List<MyReservationVO> reservations) {
        if (reservations == null || reservations.isEmpty()) {
            return List.of();
        }

        for (MyReservationVO reservation : reservations) {
            reservation.setParticipants(new ArrayList<>());
        }

        List<Long> reservationIds = reservations.stream()
                .map(MyReservationVO::getId)
                .toList();

        List<ReservationMapper.ReservationParticipantRow> participantRows = reservationMapper.selectReservationParticipants(reservationIds);
        if (participantRows == null || participantRows.isEmpty()) {
            return reservations;
        }

        Map<Long, List<UserOptionVO>> participantMap = participantRows.stream()
                .collect(Collectors.groupingBy(
                        ReservationMapper.ReservationParticipantRow::getReservationId,
                        LinkedHashMap::new,
                        Collectors.mapping(this::toParticipantVO, Collectors.toList())
                ));

        for (MyReservationVO reservation : reservations) {
            reservation.setParticipants(participantMap.getOrDefault(reservation.getId(), List.of()));
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
        return fillReservationReviews(currentUserId, fillReservationParticipants(fillReservationDevices(List.of(reservation)))).get(0);
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

    private String normalizeScope(String scope) {
        if (scope == null || scope.isBlank()) {
            return "all";
        }
        return scope.trim().toLowerCase();
    }

    private String normalizeReservationStatusNullable(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String value = status.trim();
        if ("1".equals(value)) {
            return "PENDING";
        }
        if ("2".equals(value)) {
            return "ACTIVE";
        }
        if ("3".equals(value)) {
            return "ENDED";
        }
        if ("4".equals(value)) {
            return "CANCELLED";
        }
        if ("5".equals(value)) {
            return "REJECTED";
        }
        if ("6".equals(value)) {
            return "EXCEPTION";
        }
        if (!RESERVATION_STATUS.contains(value)) {
            throw new BizException(400, "status must be one of PENDING, ACTIVE, ENDED, CANCELLED, REJECTED, EXCEPTION");
        }
        return value;
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

    private int resolveAdminPageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return DEFAULT_ENDED_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    private String requireReason(String reason) {
        String value = trimToNull(reason);
        if (value == null) {
            throw new BizException(400, "reason must not be blank");
        }
        return value;
    }

    private String formatTimestamp(Timestamp timestamp) {
        if (timestamp == null) {
            return null;
        }
        return timestamp.toLocalDateTime().format(DATE_TIME_FORMATTER);
    }

    private int defaultZero(Integer value) {
        return value == null ? 0 : value;
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

    private List<Long> normalizeParticipantUserIds(List<Long> participantUserIds) {
        if (participantUserIds == null) {
            return null;
        }
        if (participantUserIds.isEmpty()) {
            return List.of();
        }
        List<Long> normalizedIds = participantUserIds.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
        if (normalizedIds.isEmpty()) {
            return List.of();
        }
        List<UserOptionVO> users = userService.listActiveUsersByIds(normalizedIds);
        if (users.size() != normalizedIds.size()) {
            throw new BizException(400, "participant user does not exist");
        }
        return users.stream().map(UserOptionVO::getId).toList();
    }

    private void rewriteReservationParticipants(Long reservationId, List<Long> participantUserIds) {
        reservationMapper.deleteReservationParticipantsByReservationId(reservationId);
        for (Long participantUserId : participantUserIds) {
            reservationMapper.insertReservationParticipant(reservationId, participantUserId);
        }
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

    private ReservationCreateVO fillCreateParticipants(ReservationCreateVO reservation) {
        if (reservation == null || reservation.getId() == null) {
            return reservation;
        }
        List<ReservationMapper.ReservationParticipantRow> participantRows = reservationMapper.selectReservationParticipants(List.of(reservation.getId()));
        if (participantRows == null || participantRows.isEmpty()) {
            reservation.setParticipants(List.of());
            return reservation;
        }
        reservation.setParticipants(participantRows.stream()
                .map(this::toParticipantVO)
                .toList());
        return reservation;
    }

    private UserOptionVO toParticipantVO(ReservationMapper.ReservationParticipantRow row) {
        UserOptionVO vo = new UserOptionVO();
        vo.setId(row.getUserId());
        vo.setUsername(row.getUsername());
        vo.setNickname(row.getDisplayName());
        vo.setDisplayName(row.getDisplayName() + "（" + row.getUsername() + "）");
        return vo;
    }

    private record DeviceMatchSummary(int requiredTypeCount, int matchedTypeCount, boolean fullyMatched) {
    }
}
