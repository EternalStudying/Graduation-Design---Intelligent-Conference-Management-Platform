package com.llf.service.impl;

import com.llf.dto.reservation.MyReservationUpdateDTO;
import com.llf.dto.reservation.MyReservationCancelDTO;
import com.llf.dto.reservation.MyReservationReviewDTO;
import com.llf.dto.reservation.ReservationCreateDTO;
import com.llf.dto.reservation.ReservationDeviceRequirementDTO;
import com.llf.dto.reservation.ReservationRecommendationDTO;
import com.llf.mapper.ReservationMapper;
import com.llf.mapper.RoomMapper;
import com.llf.result.BizException;
import com.llf.service.NotificationService;
import com.llf.service.UserService;
import com.llf.vo.reservation.MyReservationReviewResultVO;
import com.llf.vo.reservation.ReservationReviewVO;
import com.llf.vo.common.PageResultVO;
import com.llf.vo.reservation.ReservationCreateVO;
import com.llf.vo.reservation.ReservationRecommendationItemVO;
import com.llf.vo.reservation.ReservationRecommendationVO;
import com.llf.vo.reservation.MyReservationVO;
import com.llf.vo.notification.NotificationTodoTargetVO;
import com.llf.vo.room.RoomOptionVO;
import com.llf.vo.user.UserOptionVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservationServiceImplTest {

    @Mock
    private ReservationMapper reservationMapper;

    @Mock
    private RoomMapper roomMapper;

    @Mock
    private NotificationService notificationService;

    @Mock
    private UserService userService;

    @InjectMocks
    private ReservationServiceImpl reservationService;

    @Test
    void recommend_shouldOnlyReturnFullyMatchedRoomsAndSortByScore() {
        ReservationRecommendationDTO dto = new ReservationRecommendationDTO();
        dto.setTitle("项目周会");
        dto.setAttendees(8);
        dto.setStartTime("2026-04-15 09:00:00");
        dto.setEndTime("2026-04-15 10:30:00");
        dto.setPreferredRoomId(101L);
        dto.setDeviceRequirements(List.of(
                deviceRequirement(1L, 1),
                deviceRequirement(2L, 1)
        ));

        when(roomMapper.selectRecommendationCandidates(8)).thenReturn(List.of(
                roomCandidate(101L, "R-A101", "A101多媒体会议室", "A楼-1层", 12),
                roomCandidate(102L, "R-A102", "A102讨论间", "A楼-1层", 8)
        ));
        when(reservationMapper.selectConflictRoomIds(any(), any(), eq(List.of(101L, 102L)))).thenReturn(List.of());
        when(roomMapper.selectEnabledRoomDevices(List.of(101L, 102L))).thenReturn(List.of(
                roomDeviceRow(101L, 1L, 1),
                roomDeviceRow(102L, 1L, 1),
                roomDeviceRow(102L, 2L, 1)
        ));

        ReservationRecommendationVO result = reservationService.recommend(dto);

        assertEquals(1, result.getRecommendations().size());
        ReservationRecommendationItemVO first = result.getRecommendations().get(0);

        assertEquals(102L, first.getRoomId());
        assertEquals(100, first.getScore());
        assertTrue(first.getDeviceFullyMatched());
        assertTrue(first.getTags().contains("容量匹配好"));
        assertTrue(first.getTags().contains("设备齐全"));
    }

    @Test
    void recommend_shouldExcludeMaintenanceCapacityInsufficientAndActiveConflictRooms() {
        ReservationRecommendationDTO dto = new ReservationRecommendationDTO();
        dto.setAttendees(10);
        dto.setStartTime("2026-04-15 09:00:00");
        dto.setEndTime("2026-04-15 10:30:00");

        when(roomMapper.selectRecommendationCandidates(10)).thenReturn(List.of(
                roomCandidate(201L, "R-B201", "B201", "B楼-2层", 10),
                roomCandidate(202L, "R-B202", "B202", "B楼-2层", 16)
        ));
        when(reservationMapper.selectConflictRoomIds(any(), any(), eq(List.of(201L, 202L)))).thenReturn(List.of(201L));
        when(roomMapper.selectEnabledRoomDevices(List.of(202L))).thenReturn(List.of());

        ReservationRecommendationVO result = reservationService.recommend(dto);

        assertEquals(1, result.getRecommendations().size());
        assertEquals(202L, result.getRecommendations().get(0).getRoomId());
    }

    @Test
    void recommend_shouldTreatDisabledDevicesAsUnavailable() {
        ReservationRecommendationDTO dto = new ReservationRecommendationDTO();
        dto.setAttendees(6);
        dto.setStartTime("2026-04-15 09:00:00");
        dto.setEndTime("2026-04-15 10:00:00");
        dto.setDeviceRequirements(List.of(deviceRequirement(9L, 1)));

        when(roomMapper.selectRecommendationCandidates(6)).thenReturn(List.of(
                roomCandidate(501L, "R-C501", "C501", "C楼-5层", 10)
        ));
        when(reservationMapper.selectConflictRoomIds(any(), any(), eq(List.of(501L)))).thenReturn(List.of());
        when(roomMapper.selectEnabledRoomDevices(List.of(501L))).thenReturn(List.of());

        ReservationRecommendationVO result = reservationService.recommend(dto);

        assertTrue(result.getRecommendations().isEmpty());
    }

    @Test
    void recommend_shouldNotAddDeviceTagWhenNoRequirements() {
        ReservationRecommendationDTO dto = new ReservationRecommendationDTO();
        dto.setAttendees(6);
        dto.setStartTime("2026-04-15 09:00:00");
        dto.setEndTime("2026-04-15 10:00:00");

        when(roomMapper.selectRecommendationCandidates(6)).thenReturn(List.of(
                roomCandidate(601L, "R-D601", "D601", "D楼-6层", 12)
        ));
        when(reservationMapper.selectConflictRoomIds(any(), any(), eq(List.of(601L)))).thenReturn(List.of());
        when(roomMapper.selectEnabledRoomDevices(List.of(601L))).thenReturn(List.of());

        ReservationRecommendationVO result = reservationService.recommend(dto);

        assertEquals(1, result.getRecommendations().size());
        assertEquals(0, result.getRecommendations().get(0).getRequiredDeviceTypeCount());
        assertEquals(List.of("浪费50%"), result.getRecommendations().get(0).getTags());
    }

    @Test
    void recommend_shouldRejectInvalidTimeRange() {
        ReservationRecommendationDTO dto = new ReservationRecommendationDTO();
        dto.setAttendees(4);
        dto.setStartTime("2026-04-15 10:00:00");
        dto.setEndTime("2026-04-15 09:00:00");

        BizException ex = assertThrows(BizException.class, () -> reservationService.recommend(dto));

        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("endTime"));
    }

    @Test
    void recommend_shouldMergeDuplicateDeviceRequirementsBeforeMatching() {
        ReservationRecommendationDTO dto = new ReservationRecommendationDTO();
        dto.setAttendees(6);
        dto.setStartTime("2026-04-15 09:00:00");
        dto.setEndTime("2026-04-15 10:00:00");
        dto.setDeviceRequirements(List.of(
                deviceRequirement(7L, 1),
                deviceRequirement(7L, 2)
        ));

        when(roomMapper.selectRecommendationCandidates(6)).thenReturn(List.of(
                roomCandidate(701L, "R-E701", "E701", "E楼-7层", 8)
        ));
        when(reservationMapper.selectConflictRoomIds(any(), any(), eq(List.of(701L)))).thenReturn(List.of());
        when(roomMapper.selectEnabledRoomDevices(List.of(701L))).thenReturn(List.of(
                roomDeviceRow(701L, 7L, 2)
        ));

        ReservationRecommendationVO result = reservationService.recommend(dto);

        assertTrue(result.getRecommendations().isEmpty());
    }

    @Test
    void recommend_shouldNotAddBonusScoreForPreferredRoom() {
        ReservationRecommendationDTO dto = new ReservationRecommendationDTO();
        dto.setAttendees(8);
        dto.setStartTime("2026-04-15 09:00:00");
        dto.setEndTime("2026-04-15 10:00:00");
        dto.setPreferredRoomId(801L);

        when(roomMapper.selectRecommendationCandidates(8)).thenReturn(List.of(
                roomCandidate(801L, "R-F801", "F801", "F楼8层", 10)
        ));
        when(reservationMapper.selectConflictRoomIds(any(), any(), eq(List.of(801L)))).thenReturn(List.of());
        when(roomMapper.selectEnabledRoomDevices(List.of(801L))).thenReturn(List.of());

        ReservationRecommendationVO result = reservationService.recommend(dto);

        assertEquals(92, result.getRecommendations().get(0).getScore());
        assertTrue(result.getRecommendations().get(0).getIsPreferred());
    }

    @Test
    void create_shouldValidateDeviceRequirementsBeforeInsert() {
        ReservationCreateDTO dto = new ReservationCreateDTO();
        dto.setRoomId(301L);
        dto.setTitle("设备校验");
        dto.setMeetingDate("2026-04-15");
        dto.setStartClock("09:00");
        dto.setEndClock("10:00");
        dto.setAttendees(6);
        dto.setDeviceRequirements(List.of(deviceRequirement(1L, 2)));

        RoomOptionVO room = new RoomOptionVO();
        room.setId(301L);
        room.setCapacity(8);
        room.setStatus("AVAILABLE");
        when(roomMapper.selectOptionById(301L)).thenReturn(room);
        when(reservationMapper.countConflictByRoomId(eq(301L), any(), any())).thenReturn(0);
        when(roomMapper.selectEnabledRoomDevices(List.of(301L))).thenReturn(List.of(
                roomDeviceRow(301L, 1L, 1)
        ));

        BizException ex = assertThrows(BizException.class, () -> reservationService.create(dto, 1L));

        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("device"));
        verify(reservationMapper, never()).insertReservation(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void create_shouldRejectWhenAttendeesExceedCapacity() {
        ReservationCreateDTO dto = new ReservationCreateDTO();
        dto.setRoomId(303L);
        dto.setTitle("人数超限");
        dto.setMeetingDate("2026-04-15");
        dto.setStartClock("09:00");
        dto.setEndClock("10:00");
        dto.setAttendees(12);

        RoomOptionVO room = new RoomOptionVO();
        room.setId(303L);
        room.setCapacity(8);
        room.setStatus("AVAILABLE");
        when(roomMapper.selectOptionById(303L)).thenReturn(room);

        BizException ex = assertThrows(BizException.class, () -> reservationService.create(dto, 1L));

        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("capacity"));
    }

    @Test
    void create_shouldRejectWhenActiveConflictExists() {
        ReservationCreateDTO dto = new ReservationCreateDTO();
        dto.setRoomId(304L);
        dto.setTitle("冲突校验");
        dto.setMeetingDate("2026-04-15");
        dto.setStartClock("09:00");
        dto.setEndClock("10:00");
        dto.setAttendees(6);

        RoomOptionVO room = new RoomOptionVO();
        room.setId(304L);
        room.setCapacity(8);
        room.setStatus("AVAILABLE");
        when(roomMapper.selectOptionById(304L)).thenReturn(room);
        when(reservationMapper.countConflictByRoomId(eq(304L), any(), any())).thenReturn(1);

        BizException ex = assertThrows(BizException.class, () -> reservationService.create(dto, 1L));

        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("conflicts"));
    }

    @Test
    void create_shouldMergeDuplicateDeviceRequirementsBeforeInsert() {
        ReservationCreateDTO dto = new ReservationCreateDTO();
        dto.setRoomId(305L);
        dto.setTitle("重复设备");
        dto.setMeetingDate("2026-04-15");
        dto.setStartClock("09:00");
        dto.setEndClock("10:00");
        dto.setAttendees(6);
        dto.setDeviceRequirements(List.of(
                deviceRequirement(5L, 1),
                deviceRequirement(5L, 2)
        ));

        RoomOptionVO room = new RoomOptionVO();
        room.setId(305L);
        room.setCapacity(8);
        room.setStatus("AVAILABLE");
        when(roomMapper.selectOptionById(305L)).thenReturn(room);
        when(reservationMapper.countConflictByRoomId(eq(305L), any(), any())).thenReturn(0);
        when(roomMapper.selectEnabledRoomDevices(List.of(305L))).thenReturn(List.of(
                roomDeviceRow(305L, 5L, 3)
        ));
        when(reservationMapper.lastInsertId()).thenReturn(1001L);
        ReservationCreateVO createVO = new ReservationCreateVO();
        createVO.setId(1001L);
        when(reservationMapper.selectCreateResultById(1001L)).thenReturn(createVO);

        reservationService.create(dto, 9L);

        verify(reservationMapper, times(1)).insertReservationDevice(1001L, 5L, 3);
    }

    @Test
    void create_shouldInsertReservationAndReservationDevicesWhenRequirementsAreSatisfied() {
        ReservationCreateDTO dto = new ReservationCreateDTO();
        dto.setRoomId(302L);
        dto.setTitle("创建预约");
        dto.setMeetingDate("2026-04-15");
        dto.setStartClock("09:00");
        dto.setEndClock("10:00");
        dto.setAttendees(6);
        dto.setRemark("备注");
        dto.setDeviceRequirements(List.of(
                deviceRequirement(1L, 1),
                deviceRequirement(2L, 2)
        ));

        RoomOptionVO room = new RoomOptionVO();
        room.setId(302L);
        room.setCapacity(8);
        room.setStatus("AVAILABLE");
        when(roomMapper.selectOptionById(302L)).thenReturn(room);
        when(reservationMapper.countConflictByRoomId(eq(302L), any(), any())).thenReturn(0);
        when(roomMapper.selectEnabledRoomDevices(List.of(302L))).thenReturn(List.of(
                roomDeviceRow(302L, 1L, 1),
                roomDeviceRow(302L, 2L, 2)
        ));
        when(reservationMapper.lastInsertId()).thenReturn(999L);
        ReservationCreateVO createVO = new ReservationCreateVO();
        createVO.setId(999L);
        createVO.setReservationNo("RSV999");
        createVO.setTitle("鍒涘缓棰勭害");
        createVO.setStartTime("2026-04-15 09:00:00");
        createVO.setEndTime("2026-04-15 10:00:00");
        when(reservationMapper.selectCreateResultById(999L)).thenReturn(createVO);

        ReservationCreateVO result = reservationService.create(dto, 88L);

        assertEquals(999L, result.getId());
        verify(reservationMapper, times(1)).insertReservation(any(), eq(302L), eq(88L), eq("创建预约"), eq("备注"), eq(6), any(Timestamp.class), any(Timestamp.class));
        verify(reservationMapper, times(1)).insertReservationDevice(999L, 1L, 1);
        verify(reservationMapper, times(1)).insertReservationDevice(999L, 2L, 2);
        verify(notificationService).createReservationCreatedNotification(88L, createVO.getTitle(), null, "2026-04-15 09:00:00", "2026-04-15 10:00:00");
    }

    @Test
    void create_shouldPersistParticipantsWhenParticipantUserIdsProvided() {
        ReservationCreateDTO dto = new ReservationCreateDTO();
        dto.setRoomId(306L);
        dto.setTitle("参会人创建");
        dto.setMeetingDate("2026-04-15");
        dto.setStartClock("09:00");
        dto.setEndClock("10:00");
        dto.setAttendees(6);
        dto.setParticipantUserIds(List.of(101L, 102L, 101L));

        RoomOptionVO room = new RoomOptionVO();
        room.setId(306L);
        room.setCapacity(8);
        room.setStatus("AVAILABLE");
        room.setName("云杉会议室");
        when(roomMapper.selectOptionById(306L)).thenReturn(room);
        when(reservationMapper.countConflictByRoomId(eq(306L), any(), any())).thenReturn(0);
        when(userService.listActiveUsersByIds(List.of(101L, 102L))).thenReturn(List.of(
                userOption(101L, "zhangsan", "张三"),
                userOption(102L, "lisi", "李四")
        ));
        when(reservationMapper.lastInsertId()).thenReturn(1002L);
        ReservationCreateVO createVO = new ReservationCreateVO();
        createVO.setId(1002L);
        createVO.setReservationNo("RSV1002");
        when(reservationMapper.selectCreateResultById(1002L)).thenReturn(createVO);
        when(reservationMapper.selectReservationParticipants(List.of(1002L))).thenReturn(List.of(
                participantRow(1002L, 101L, "zhangsan", "张三"),
                participantRow(1002L, 102L, "lisi", "李四")
        ));

        ReservationCreateVO result = reservationService.create(dto, 88L);

        verify(reservationMapper).insertReservationParticipant(1002L, 101L);
        verify(reservationMapper).insertReservationParticipant(1002L, 102L);
        assertEquals(2, result.getParticipants().size());
    }

    @Test
    void updateMyReservation_shouldRejectWhenDeviceRequirementsCannotBeSatisfied() {
        MyReservationUpdateDTO dto = new MyReservationUpdateDTO();
        dto.setRoomId(401L);
        dto.setTitle("更新设备校验");
        dto.setMeetingDate("2026-04-16");
        dto.setStartClock("10:00");
        dto.setEndClock("11:00");
        dto.setAttendees(4);
        dto.setDeviceRequirements(List.of(deviceRequirement(6L, 2)));

        ReservationMapper.ReservationEditableRow editableRow = editableReservationRow(70L, 9L, 401L);
        when(reservationMapper.selectEditableReservation(70L, 9L)).thenReturn(editableRow);

        RoomOptionVO room = new RoomOptionVO();
        room.setId(401L);
        room.setCapacity(8);
        room.setStatus("AVAILABLE");
        when(roomMapper.selectOptionById(401L)).thenReturn(room);
        when(reservationMapper.countConflictExcludeSelf(eq(70L), eq(401L), any(), any())).thenReturn(0);
        when(roomMapper.selectEnabledRoomDevices(List.of(401L))).thenReturn(List.of(
                roomDeviceRow(401L, 6L, 1)
        ));

        BizException ex = assertThrows(BizException.class, () -> reservationService.updateMyReservation(70L, 9L, dto));

        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("device"));
        verify(reservationMapper, never()).updateMyReservation(any(), any(), any(), any(), any(), any(), any());
        verify(reservationMapper, never()).deleteReservationDevicesByReservationId(any());
    }

    @Test
    void updateMyReservation_shouldRewriteReservationDevicesWhenRequirementsAreSatisfied() {
        MyReservationUpdateDTO dto = new MyReservationUpdateDTO();
        dto.setRoomId(402L);
        dto.setTitle("更新预约");
        dto.setMeetingDate("2026-04-16");
        dto.setStartClock("10:30");
        dto.setEndClock("12:00");
        dto.setAttendees(4);
        dto.setRemark("同步");
        dto.setDeviceRequirements(List.of(
                deviceRequirement(2L, 1),
                deviceRequirement(4L, 1)
        ));

        ReservationMapper.ReservationEditableRow editableRow = editableReservationRow(71L, 9L, 402L);
        when(reservationMapper.selectEditableReservation(71L, 9L)).thenReturn(editableRow);

        RoomOptionVO room = new RoomOptionVO();
        room.setId(402L);
        room.setCapacity(8);
        room.setStatus("AVAILABLE");
        when(roomMapper.selectOptionById(402L)).thenReturn(room);
        when(reservationMapper.countConflictExcludeSelf(eq(71L), eq(402L), any(), any())).thenReturn(0);
        when(roomMapper.selectEnabledRoomDevices(List.of(402L))).thenReturn(List.of(
                roomDeviceRow(402L, 2L, 1),
                roomDeviceRow(402L, 4L, 1)
        ));
        when(reservationMapper.selectMyReservationDetail(71L, 9L)).thenReturn(myReservationDetail(71L));
        when(reservationMapper.selectMyReservationDevices(List.of(71L))).thenReturn(List.of(
                myReservationDeviceRow(71L, 2L, "DEV-2", "投影仪", 1),
                myReservationDeviceRow(71L, 4L, "DEV-4", "白板", 1)
        ));

        MyReservationVO result = reservationService.updateMyReservation(71L, 9L, dto);

        assertEquals(71L, result.getId());
        assertEquals(2, result.getDevices().size());
        verify(reservationMapper, times(1)).updateMyReservation(eq(71L), eq("更新预约"), eq(402L), any(), any(), eq(4), eq("同步"));
        verify(reservationMapper, times(1)).deleteReservationDevicesByReservationId(71L);
        verify(reservationMapper, times(1)).insertReservationDevice(71L, 2L, 1);
        verify(reservationMapper, times(1)).insertReservationDevice(71L, 4L, 1);
        verify(notificationService).createReservationUpdatedNotification(9L, "棰勭害71", "浼氳瀹?1", "2026-04-15 09:00:00", "2026-04-15 10:00:00");
    }

    @Test
    void updateMyReservation_shouldRewriteParticipantsWhenParticipantIdsProvided() {
        MyReservationUpdateDTO dto = new MyReservationUpdateDTO();
        dto.setRoomId(404L);
        dto.setTitle("更新参会人");
        dto.setMeetingDate("2026-04-16");
        dto.setStartClock("10:30");
        dto.setEndClock("12:00");
        dto.setAttendees(4);
        dto.setParticipantUserIds(List.of(201L, 202L, 201L));

        ReservationMapper.ReservationEditableRow editableRow = editableReservationRow(74L, 9L, 404L);
        when(reservationMapper.selectEditableReservation(74L, 9L)).thenReturn(editableRow);

        RoomOptionVO room = new RoomOptionVO();
        room.setId(404L);
        room.setCapacity(8);
        room.setStatus("AVAILABLE");
        room.setName("潮汐会议室");
        when(roomMapper.selectOptionById(404L)).thenReturn(room);
        when(reservationMapper.countConflictExcludeSelf(eq(74L), eq(404L), any(), any())).thenReturn(0);
        when(userService.listActiveUsersByIds(List.of(201L, 202L))).thenReturn(List.of(
                userOption(201L, "wangwu", "王五"),
                userOption(202L, "zhaoliu", "赵六")
        ));
        when(reservationMapper.selectMyReservationDetail(74L, 9L)).thenReturn(myReservationDetail(74L));
        when(reservationMapper.selectMyReservationDevices(List.of(74L))).thenReturn(List.of());
        when(reservationMapper.selectMyReservationReviews(9L, List.of(74L))).thenReturn(List.of());
        when(reservationMapper.selectReservationParticipants(List.of(74L))).thenReturn(List.of(
                participantRow(74L, 201L, "wangwu", "王五"),
                participantRow(74L, 202L, "zhaoliu", "赵六")
        ));

        MyReservationVO result = reservationService.updateMyReservation(74L, 9L, dto);

        verify(reservationMapper).deleteReservationParticipantsByReservationId(74L);
        verify(reservationMapper).insertReservationParticipant(74L, 201L);
        verify(reservationMapper).insertReservationParticipant(74L, 202L);
        assertEquals(2, result.getParticipants().size());
    }

    @Test
    void updateMyReservation_shouldMergeDuplicateDeviceRequirementsBeforePersist() {
        MyReservationUpdateDTO dto = new MyReservationUpdateDTO();
        dto.setRoomId(403L);
        dto.setTitle("更新重复设备");
        dto.setMeetingDate("2026-04-16");
        dto.setStartClock("14:00");
        dto.setEndClock("15:00");
        dto.setAttendees(4);
        dto.setDeviceRequirements(List.of(
                deviceRequirement(8L, 1),
                deviceRequirement(8L, 2)
        ));

        ReservationMapper.ReservationEditableRow editableRow = editableReservationRow(72L, 9L, 403L);
        when(reservationMapper.selectEditableReservation(72L, 9L)).thenReturn(editableRow);

        RoomOptionVO room = new RoomOptionVO();
        room.setId(403L);
        room.setCapacity(8);
        room.setStatus("AVAILABLE");
        when(roomMapper.selectOptionById(403L)).thenReturn(room);
        when(reservationMapper.countConflictExcludeSelf(eq(72L), eq(403L), any(), any())).thenReturn(0);
        when(roomMapper.selectEnabledRoomDevices(List.of(403L))).thenReturn(List.of(
                roomDeviceRow(403L, 8L, 3)
        ));
        when(reservationMapper.selectMyReservationDetail(72L, 9L)).thenReturn(myReservationDetail(72L));
        when(reservationMapper.selectMyReservationDevices(List.of(72L))).thenReturn(List.of(
                myReservationDeviceRow(72L, 8L, "DEV-8", "麦克风", 3)
        ));

        reservationService.updateMyReservation(72L, 9L, dto);

        verify(reservationMapper, times(1)).deleteReservationDevicesByReservationId(72L);
        verify(reservationMapper, times(1)).insertReservationDevice(72L, 8L, 3);
    }

    @Test
    void cancelMyReservation_shouldCreateCancellationNotification() {
        MyReservationCancelDTO dto = new MyReservationCancelDTO();
        dto.setCancelReason("涓存椂鍐插獊");

        ReservationMapper.ReservationEditableRow editableRow = editableReservationRow(73L, 9L, 404L);
        when(reservationMapper.selectEditableReservation(73L, 9L)).thenReturn(editableRow);
        when(reservationMapper.selectMyReservationDetail(73L, 9L)).thenReturn(myReservationDetail(73L));
        when(reservationMapper.selectMyReservationDevices(List.of(73L))).thenReturn(List.of());
        when(reservationMapper.selectMyReservationReviews(9L, List.of(73L))).thenReturn(List.of());

        MyReservationVO result = reservationService.cancelMyReservation(73L, 9L, dto);

        assertEquals(73L, result.getId());
        verify(reservationMapper).cancelMyReservation(73L, "涓存椂鍐插獊");
        verify(notificationService).createReservationCancelledNotification(9L, "棰勭害73", "鍙栨秷鍘熷洜");
    }

    @Test
    void markEnded_shouldCreateReviewTodoNotificationsAfterStatusUpdated() {
        when(reservationMapper.selectReviewTodoTargets(any())).thenReturn(List.of(
                reviewTodoTargetRow(90L, 1L, "棰勭害90"),
                reviewTodoTargetRow(90L, 2L, "棰勭害90")
        ));
        when(reservationMapper.markEnded(any())).thenReturn(1);

        int updated = reservationService.markEnded();

        assertEquals(1, updated);
        verify(notificationService).createReviewTodoNotifications(any());
    }

    @Test
    void submitMyReservationReview_shouldAllowEndedReservation() {
        MyReservationReviewDTO dto = new MyReservationReviewDTO();
        dto.setRating(5);
        dto.setContent("体验很好");

        when(reservationMapper.selectReviewableReservation(80L, 1L))
                .thenReturn(reviewableReservationRow(80L, "ENDED"));
        when(reservationMapper.selectReservationReviewByReservationIdAndUserId(80L, 1L))
                .thenReturn(null)
                .thenReturn(reviewRow(80L, 1L, 5, "体验很好"));

        MyReservationReviewResultVO result = reservationService.submitMyReservationReview(80L, 1L, dto);

        assertTrue(result.getReviewed());
        assertEquals(5, result.getMyReview().getRating());
        assertEquals("体验很好", result.getMyReview().getContent());
        verify(reservationMapper, times(1)).insertReservationReview(80L, 1L, 5, "体验很好");
    }

    @Test
    void submitMyReservationReview_shouldRejectActiveOrCancelledReservation() {
        MyReservationReviewDTO dto = new MyReservationReviewDTO();
        dto.setRating(4);

        when(reservationMapper.selectReviewableReservation(81L, 1L))
                .thenReturn(reviewableReservationRow(81L, "ACTIVE"));

        BizException activeEx = assertThrows(BizException.class, () -> reservationService.submitMyReservationReview(81L, 1L, dto));
        assertEquals(400, activeEx.getCode());

        when(reservationMapper.selectReviewableReservation(82L, 1L))
                .thenReturn(reviewableReservationRow(82L, "CANCELLED"));

        BizException cancelledEx = assertThrows(BizException.class, () -> reservationService.submitMyReservationReview(82L, 1L, dto));
        assertEquals(400, cancelledEx.getCode());
    }

    @Test
    void submitMyReservationReview_shouldRejectWhenReservationIsNotVisibleToCurrentUser() {
        MyReservationReviewDTO dto = new MyReservationReviewDTO();
        dto.setRating(3);

        when(reservationMapper.selectReviewableReservation(83L, 1L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> reservationService.submitMyReservationReview(83L, 1L, dto));

        assertEquals(404, ex.getCode());
    }

    @Test
    void submitMyReservationReview_shouldRejectDuplicateReview() {
        MyReservationReviewDTO dto = new MyReservationReviewDTO();
        dto.setRating(5);

        when(reservationMapper.selectReviewableReservation(84L, 1L))
                .thenReturn(reviewableReservationRow(84L, "ENDED"));
        when(reservationMapper.selectReservationReviewByReservationIdAndUserId(84L, 1L))
                .thenReturn(reviewRow(84L, 1L, 4, "已评价"));

        BizException ex = assertThrows(BizException.class, () -> reservationService.submitMyReservationReview(84L, 1L, dto));

        assertEquals(400, ex.getCode());
        verify(reservationMapper, never()).insertReservationReview(any(), any(), any(), any());
    }

    @Test
    void myReservations_shouldFillReviewedFalseWhenNoReviewExists() {
        MyReservationVO reservation = myReservationDetail(90L);
        when(reservationMapper.selectMyReservations(eq(1L), any(), any(), eq("all"), eq("ACTIVE")))
                .thenReturn(List.of(reservation));
        when(reservationMapper.selectMyReservationDevices(List.of(90L))).thenReturn(List.of());
        when(reservationMapper.selectMyReservationReviews(1L, List.of(90L))).thenReturn(List.of());

        List<MyReservationVO> result = reservationService.myReservations(1L, "2026-04-01 00:00:00", "2026-04-30 23:59:59", "all", "ACTIVE", false);

        assertEquals(1, result.size());
        assertFalse(result.get(0).getReviewed());
        assertEquals(null, result.get(0).getMyReview());
    }

    @Test
    void myReservations_shouldFillReviewedTrueAndMyReviewWhenReviewExists() {
        MyReservationVO reservation = myReservationDetail(91L);
        when(reservationMapper.selectMyReservations(eq(1L), any(), any(), eq("all"), eq("ACTIVE")))
                .thenReturn(List.of(reservation));
        when(reservationMapper.selectMyReservationDevices(List.of(91L))).thenReturn(List.of());
        when(reservationMapper.selectMyReservationReviews(1L, List.of(91L)))
                .thenReturn(List.of(reviewRow(91L, 1L, 5, "很满意")));

        List<MyReservationVO> result = reservationService.myReservations(1L, "2026-04-01 00:00:00", "2026-04-30 23:59:59", "all", "ACTIVE", false);

        assertEquals(1, result.size());
        assertTrue(result.get(0).getReviewed());
        assertEquals(5, result.get(0).getMyReview().getRating());
        assertEquals("很满意", result.get(0).getMyReview().getContent());
    }

    @Test
    void myReservations_shouldFillParticipantsWhenExists() {
        MyReservationVO reservation = myReservationDetail(94L);
        when(reservationMapper.selectMyReservations(eq(1L), any(), any(), eq("all"), eq("ACTIVE")))
                .thenReturn(List.of(reservation));
        when(reservationMapper.selectMyReservationDevices(List.of(94L))).thenReturn(List.of());
        when(reservationMapper.selectMyReservationReviews(1L, List.of(94L))).thenReturn(List.of());
        when(reservationMapper.selectReservationParticipants(List.of(94L))).thenReturn(List.of(
                participantRow(94L, 301L, "zhangsan", "张三")
        ));

        List<MyReservationVO> result = reservationService.myReservations(1L, "2026-04-01 00:00:00", "2026-04-30 23:59:59", "all", "ACTIVE", false);

        assertEquals(1, result.size());
        assertEquals(1, result.get(0).getParticipants().size());
        assertEquals(301L, result.get(0).getParticipants().get(0).getId());
    }

    @Test
    void myReservations_shouldClampStartToTodayWhenFutureOnlyIsTrue() {
        MyReservationVO reservation = myReservationDetail(92L);
        when(reservationMapper.selectMyReservations(eq(1L), any(), any(), eq("all"), eq("ACTIVE")))
                .thenReturn(List.of(reservation));
        when(reservationMapper.selectMyReservationDevices(List.of(92L))).thenReturn(List.of());
        when(reservationMapper.selectMyReservationReviews(1L, List.of(92L))).thenReturn(List.of());

        reservationService.myReservations(1L, "2026-04-01 00:00:00", "2099-04-30 23:59:59", "all", "ACTIVE", true);

        ArgumentCaptor<Timestamp> startCaptor = ArgumentCaptor.forClass(Timestamp.class);
        verify(reservationMapper).selectMyReservations(eq(1L), startCaptor.capture(), any(), eq("all"), eq("ACTIVE"));
        assertEquals(Timestamp.valueOf(LocalDate.now().atStartOfDay()), startCaptor.getValue());
    }

    @Test
    void myReservations_shouldKeepOriginalStartWhenFutureOnlyIsFalse() {
        MyReservationVO reservation = myReservationDetail(93L);
        when(reservationMapper.selectMyReservations(eq(1L), any(), any(), eq("all"), eq("ACTIVE")))
                .thenReturn(List.of(reservation));
        when(reservationMapper.selectMyReservationDevices(List.of(93L))).thenReturn(List.of());
        when(reservationMapper.selectMyReservationReviews(1L, List.of(93L))).thenReturn(List.of());

        reservationService.myReservations(1L, "2099-04-01 00:00:00", "2099-04-30 23:59:59", "all", "ACTIVE", false);

        ArgumentCaptor<Timestamp> startCaptor = ArgumentCaptor.forClass(Timestamp.class);
        verify(reservationMapper).selectMyReservations(eq(1L), startCaptor.capture(), any(), eq("all"), eq("ACTIVE"));
        assertEquals(Timestamp.valueOf(LocalDateTime.parse("2099-04-01T00:00:00")), startCaptor.getValue());
    }

    @Test
    void myEndedReservations_shouldReturnPagedEndedReservations() {
        MyReservationVO first = myReservationDetail(101L);
        MyReservationVO second = myReservationDetail(100L);
        when(reservationMapper.countMyEndedReservations(1L, "all")).thenReturn(2L);
        when(reservationMapper.selectMyEndedReservationsPage(1L, "all", 8, 0))
                .thenReturn(List.of(first, second));
        when(reservationMapper.selectMyReservationDevices(List.of(101L, 100L))).thenReturn(List.of());
        when(reservationMapper.selectMyReservationReviews(1L, List.of(101L, 100L))).thenReturn(List.of());

        PageResultVO<MyReservationVO> result = reservationService.myEndedReservations(1L, null, 1, 8);

        assertEquals(2L, result.getTotal());
        assertEquals(1, result.getPageNum());
        assertEquals(8, result.getPageSize());
        assertEquals(List.of(101L, 100L), result.getList().stream().map(MyReservationVO::getId).toList());
    }

    private ReservationDeviceRequirementDTO deviceRequirement(Long deviceId, Integer quantity) {
        ReservationDeviceRequirementDTO dto = new ReservationDeviceRequirementDTO();
        dto.setDeviceId(deviceId);
        dto.setQuantity(quantity);
        return dto;
    }

    private RoomMapper.RecommendationRoomRow roomCandidate(Long id, String roomCode, String name, String location, Integer capacity) {
        RoomMapper.RecommendationRoomRow row = new RoomMapper.RecommendationRoomRow();
        row.setId(id);
        row.setRoomCode(roomCode);
        row.setName(name);
        row.setLocation(location);
        row.setCapacity(capacity);
        return row;
    }

    private RoomMapper.RoomDeviceAvailabilityRow roomDeviceRow(Long roomId, Long deviceId, Integer quantity) {
        RoomMapper.RoomDeviceAvailabilityRow row = new RoomMapper.RoomDeviceAvailabilityRow();
        row.setRoomId(roomId);
        row.setDeviceId(deviceId);
        row.setQuantity(quantity);
        return row;
    }

    private ReservationMapper.ReservationEditableRow editableReservationRow(Long id, Long organizerId, Long roomId) {
        ReservationMapper.ReservationEditableRow row = new ReservationMapper.ReservationEditableRow();
        row.setId(id);
        row.setOrganizerId(organizerId);
        row.setRoomId(roomId);
        row.setStatus("ACTIVE");
        row.setEndTime(Timestamp.valueOf(LocalDateTime.now().plusDays(1)));
        return row;
    }

    private MyReservationVO myReservationDetail(Long id) {
        MyReservationVO vo = new MyReservationVO();
        vo.setId(id);
        vo.setTitle("棰勭害" + id);
        vo.setRoomName("浼氳瀹?1");
        vo.setStartTime("2026-04-15 09:00:00");
        vo.setEndTime("2026-04-15 10:00:00");
        vo.setCancelReason("鍙栨秷鍘熷洜");
        vo.setDevices(List.of());
        return vo;
    }

    private UserOptionVO userOption(Long id, String username, String nickname) {
        UserOptionVO vo = new UserOptionVO();
        vo.setId(id);
        vo.setUsername(username);
        vo.setNickname(nickname);
        vo.setDisplayName(nickname + "（" + username + "）");
        return vo;
    }

    private ReservationMapper.ReservationParticipantRow participantRow(Long reservationId, Long userId, String username, String displayName) {
        ReservationMapper.ReservationParticipantRow row = new ReservationMapper.ReservationParticipantRow();
        row.setReservationId(reservationId);
        row.setId(userId);
        row.setUserId(userId);
        row.setUsername(username);
        row.setDisplayName(displayName);
        return row;
    }

    private ReservationMapper.ReviewableReservationRow reviewableReservationRow(Long id, String status) {
        ReservationMapper.ReviewableReservationRow row = new ReservationMapper.ReviewableReservationRow();
        row.setId(id);
        row.setStatus(status);
        return row;
    }

    private ReservationMapper.ReservationReviewRow reviewRow(Long reservationId, Long userId, Integer rating, String content) {
        ReservationMapper.ReservationReviewRow row = new ReservationMapper.ReservationReviewRow();
        row.setReservationId(reservationId);
        row.setUserId(userId);
        row.setRating(rating);
        row.setContent(content);
        row.setCreatedAt(Timestamp.valueOf(LocalDateTime.parse("2026-04-15T16:30:00")));
        return row;
    }

    private ReservationMapper.ReviewTodoTargetRow reviewTodoTargetRow(Long reservationId, Long userId, String title) {
        ReservationMapper.ReviewTodoTargetRow row = new ReservationMapper.ReviewTodoTargetRow();
        row.setReservationId(reservationId);
        row.setUserId(userId);
        row.setTitle(title);
        return row;
    }

    private MyReservationVO.DeviceRow myReservationDeviceRow(Long reservationId, Long deviceId, String deviceCode, String name, Integer quantity) {
        MyReservationVO.DeviceRow row = new MyReservationVO.DeviceRow();
        row.setReservationId(reservationId);
        row.setId(deviceId);
        row.setDeviceId(deviceId);
        row.setDeviceCode(deviceCode);
        row.setName(name);
        row.setQuantity(quantity);
        row.setStatus("ENABLED");
        return row;
    }
}
