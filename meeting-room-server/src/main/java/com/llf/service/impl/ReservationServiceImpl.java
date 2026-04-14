package com.llf.service.impl;

import com.llf.dto.MyReservationCancelDTO;
import com.llf.dto.MyReservationUpdateDTO;
import com.llf.dto.ReservationCreateDTO;
import com.llf.mapper.ReservationMapper;
import com.llf.mapper.RoomMapper;
import com.llf.result.BizException;
import com.llf.service.ReservationService;
import com.llf.util.DateTimeUtils;
import com.llf.vo.CalendarEventVO;
import com.llf.vo.MyReservationVO;
import com.llf.vo.ReservationCreateVO;
import com.llf.vo.RoomOptionVO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
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

    private static final Set<String> MY_SCOPES = Set.of("all", "organizer", "participant");
    private static final Set<String> RESERVATION_STATUS = Set.of("ACTIVE", "ENDED", "CANCELLED");

    @Override
    public List<CalendarEventVO> listCalendar(String startDate, String endDate, Long roomId, String status) {
        Timestamp startTime = DateTimeUtils.parseToTimestamp(startDate);
        Timestamp endTime = DateTimeUtils.parseToTimestamp(endDate);
        if (!endTime.after(startTime)) {
            throw new BizException(400, "endDate must be greater than startDate");
        }

        List<CalendarEventVO> events = reservationMapper.selectCalendarEvents(
                startTime,
                endTime,
                roomId,
                status
        );
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
                        Collectors.mapping(this::toDeviceVO, Collectors.toList())
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

        ReservationMapper.RoomRow room = reservationMapper.selectRoomById(dto.getRoomId());
        if (room == null) {
            throw new BizException(404, "room not found");
        }
        if (!"AVAILABLE".equalsIgnoreCase(room.status)) {
            throw new BizException(400, "room is under maintenance");
        }
        if (dto.getAttendees() > room.capacity) {
            throw new BizException(400, "attendees exceeds room capacity");
        }

        Timestamp start = Timestamp.valueOf(startTime);
        Timestamp end = Timestamp.valueOf(endTime);
        int conflict = reservationMapper.countConflictByRoomId(dto.getRoomId(), start, end);
        if (conflict > 0) {
            throw new BizException(400, "reservation time conflicts with another active reservation");
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
        Long id = reservationMapper.lastInsertId();
        return reservationMapper.selectCreateResultById(id);
    }

    @Override
    public void cancel(Long id) {
        reservationMapper.cancel(id);
    }

    @Override
    public List<MyReservationVO> myReservations(Long currentUserId, String startDate, String endDate, String scope, String status) {
        validateScope(scope);
        validateStatus(status);

        Timestamp start = DateTimeUtils.parseToTimestamp(startDate);
        Timestamp end = DateTimeUtils.parseToTimestamp(endDate);
        if (!end.after(start)) {
            throw new BizException(400, "endDate must be greater than startDate");
        }

        List<MyReservationVO> reservations = reservationMapper.selectMyReservations(currentUserId, start, end, scope, status);
        return fillReservationDevices(reservations);
    }

    @Override
    public List<RoomOptionVO> myRoomOptions() {
        return roomMapper.selectAvailableOptions();
    }

    @Override
    @Transactional
    public MyReservationVO updateMyReservation(Long id, Long currentUserId, MyReservationUpdateDTO dto) {
        ReservationMapper.ReservationEditableRow editable = requireEditableReservation(id, currentUserId);
        LocalDateTime startTime = parseMeetingTime(dto.getMeetingDate(), dto.getStartClock(), "startClock");
        LocalDateTime endTime = parseMeetingTime(dto.getMeetingDate(), dto.getEndClock(), "endClock");
        if (!endTime.isAfter(startTime)) {
            throw new BizException(400, "endClock must be greater than startClock");
        }

        RoomOptionVO room = roomMapper.selectOptionById(dto.getRoomId());
        if (room == null) {
            throw new BizException(404, "room not found");
        }
        if (dto.getAttendees() > room.getCapacity()) {
            throw new BizException(400, "attendees exceeds room capacity");
        }

        Timestamp start = Timestamp.valueOf(startTime);
        Timestamp end = Timestamp.valueOf(endTime);
        int conflict = reservationMapper.countConflictExcludeSelf(id, dto.getRoomId(), start, end);
        if (conflict > 0) {
            throw new BizException(400, "reservation time conflicts with another active reservation");
        }

        reservationMapper.updateMyReservation(
                id,
                dto.getTitle().trim(),
                dto.getRoomId(),
                start,
                end,
                dto.getAttendees(),
                trimToNull(dto.getRemark())
        );
        return requireMyReservationDetail(id, currentUserId);
    }

    @Override
    @Transactional
    public MyReservationVO cancelMyReservation(Long id, Long currentUserId, MyReservationCancelDTO dto) {
        requireEditableReservation(id, currentUserId);
        reservationMapper.cancelMyReservation(id, dto.getCancelReason().trim());
        return requireMyReservationDetail(id, currentUserId);
    }

    @Override
    public int markEnded() {
        return reservationMapper.markEnded();
    }

    private CalendarEventVO.DeviceVO toDeviceVO(CalendarEventVO.DeviceRow row) {
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
                        Collectors.mapping(this::toDeviceVO, Collectors.toList())
                ));

        for (MyReservationVO reservation : reservations) {
            reservation.setDevices(deviceMap.getOrDefault(reservation.getId(), List.of()));
        }
        return reservations;
    }

    private MyReservationVO.DeviceVO toDeviceVO(MyReservationVO.DeviceRow row) {
        MyReservationVO.DeviceVO vo = new MyReservationVO.DeviceVO();
        vo.setId(row.getId());
        vo.setDeviceId(row.getDeviceId());
        vo.setDeviceCode(row.getDeviceCode());
        vo.setName(row.getName());
        vo.setQuantity(row.getQuantity());
        vo.setStatus(row.getStatus());
        return vo;
    }

    private ReservationMapper.ReservationEditableRow requireEditableReservation(Long id, Long currentUserId) {
        ReservationMapper.ReservationEditableRow reservation = reservationMapper.selectEditableReservation(id, currentUserId);
        if (reservation == null) {
            throw new BizException(404, "reservation not found");
        }
        if (!"ACTIVE".equalsIgnoreCase(reservation.status)) {
            throw new BizException(400, "reservation is not active");
        }
        if (!reservation.endTime.after(Timestamp.valueOf(LocalDateTime.now()))) {
            throw new BizException(400, "reservation has already ended");
        }
        return reservation;
    }

    private MyReservationVO requireMyReservationDetail(Long id, Long currentUserId) {
        MyReservationVO reservation = reservationMapper.selectMyReservationDetail(id, currentUserId);
        if (reservation == null) {
            throw new BizException(404, "reservation not found");
        }
        return fillReservationDevices(List.of(reservation)).get(0);
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

    private LocalDateTime parseMeetingTime(String meetingDate, String clock, String fieldName) {
        try {
            return DateTimeUtils.parseToLocalDateTime(meetingDate.trim() + " " + normalizeClock(clock));
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

    private String trimToNull(String text) {
        if (text == null) {
            return null;
        }
        String value = text.trim();
        return value.isEmpty() ? null : value;
    }

    private String generateReservationNo() {
        return "RSV" + System.currentTimeMillis() + ThreadLocalRandom.current().nextInt(100, 1000);
    }
}
