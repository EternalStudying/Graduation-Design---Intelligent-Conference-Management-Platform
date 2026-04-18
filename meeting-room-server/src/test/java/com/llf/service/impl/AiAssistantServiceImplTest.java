package com.llf.service.impl;

import com.llf.assistant.AiAssistantActionRegistry;
import com.llf.assistant.AiAssistantSessionStore;
import com.llf.assistant.handler.CalendarAssistantActionHandler;
import com.llf.assistant.handler.OverviewAssistantActionHandler;
import com.llf.assistant.handler.ReservationAssistantActionHandler;
import com.llf.assistant.handler.RoomAssistantActionHandler;
import com.llf.assistant.semantic.AiAssistantIntentParser;
import com.llf.assistant.semantic.AiAssistantReferenceResolver;
import com.llf.assistant.semantic.AiAssistantIntentSchemaValidator;
import com.llf.assistant.semantic.AiAssistantSemanticService;
import com.llf.assistant.semantic.AiAssistantTextNormalizer;
import com.llf.assistant.semantic.AiAssistantTimeResolver;
import com.llf.auth.AuthContext;
import com.llf.auth.AuthUser;
import com.llf.dto.assistant.AiAssistantMessageRequestDTO;
import com.llf.dto.reservation.ReservationCreateDTO;
import com.llf.service.DashboardService;
import com.llf.service.ReservationService;
import com.llf.service.RoomService;
import com.llf.service.UserService;
import com.llf.vo.assistant.AiAssistantTurnVO;
import com.llf.vo.reservation.MyReservationVO;
import com.llf.vo.reservation.ReservationCreateVO;
import com.llf.vo.user.UserOptionVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiAssistantServiceImplTest {

    @Mock
    private ReservationService reservationService;

    @Mock
    private RoomService roomService;

    @Mock
    private DashboardService dashboardService;

    @Mock
    private UserService userService;

    private AiAssistantServiceImpl aiAssistantService;

    @BeforeEach
    void setUp() {
        AiAssistantActionRegistry registry = new AiAssistantActionRegistry(List.of(
                new ReservationAssistantActionHandler(reservationService, userService),
                new RoomAssistantActionHandler(reservationService, roomService),
                new OverviewAssistantActionHandler(dashboardService),
                new CalendarAssistantActionHandler(reservationService)
        ));
        AiAssistantIntentSchemaValidator schemaValidator = new AiAssistantIntentSchemaValidator();
        AiAssistantSemanticService semanticService = new AiAssistantSemanticService(
                new AiAssistantTextNormalizer(),
                new AiAssistantTimeResolver(),
                new AiAssistantIntentParser(registry, schemaValidator, request -> null),
                schemaValidator,
                new AiAssistantReferenceResolver()
        );
        aiAssistantService = new AiAssistantServiceImpl(new AiAssistantSessionStore(), registry, semanticService);
        AuthContext.set(currentUser(1L));
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    void createSession_shouldReturnReplyStage() {
        AiAssistantTurnVO result = aiAssistantService.createSession(currentUser(1L));

        assertNotNull(result.getSessionId());
        assertTrue(result.getSessionId().startsWith("asst-"));
        assertEquals("reply", result.getStage());
        assertEquals(0, result.getMissingFields().size());
        assertEquals(null, result.getPendingAction());
        assertEquals(null, result.getResult());
        assertTrue(result.getSuggestions().size() >= 3);
    }

    @Test
    void message_textCreate_shouldReturnCollect() {
        AiAssistantTurnVO session = aiAssistantService.createSession(currentUser(1L));

        AiAssistantTurnVO result = aiAssistantService.message(currentUser(1L), messageRequest(session.getSessionId(), "帮我创建一个预约"));

        assertEquals("collect", result.getStage());
        assertTrue(result.getMissingFields().stream().anyMatch(field -> "title".equals(field.getKey())));
        assertTrue(result.getMissingFields().stream().anyMatch(field -> "meetingDate".equals(field.getKey())));
        assertTrue(result.getMissingFields().stream().anyMatch(field -> "startClock".equals(field.getKey())));
        assertTrue(result.getMissingFields().stream().anyMatch(field -> "endClock".equals(field.getKey())));
        assertTrue(result.getMissingFields().stream().anyMatch(field -> "attendees".equals(field.getKey())));
        assertTrue(result.getMissingFields().stream().anyMatch(field ->
                "participantUserIds".equals(field.getKey())
                        && "user-select".equals(field.getInputType())
                        && !field.isRequired()));
    }

    @Test
    void message_collectFieldsForCreate_shouldEnterConfirm() {
        AiAssistantTurnVO session = aiAssistantService.createSession(currentUser(1L));
        aiAssistantService.message(currentUser(1L), messageRequest(session.getSessionId(), "帮我创建一个预约"));

        AiAssistantMessageRequestDTO request = new AiAssistantMessageRequestDTO();
        request.setSessionId(session.getSessionId());
        request.setFieldValues(Map.of(
                "title", "周会复盘",
                "meetingDate", "2026-04-20",
                "startClock", "14:00",
                "endClock", "15:00",
                "attendees", 10,
                "roomId", 101
        ));

        AiAssistantTurnVO result = aiAssistantService.message(currentUser(1L), request);

        assertEquals("confirm", result.getStage());
        assertNotNull(result.getPendingAction());
        assertEquals("reservations.create", result.getPendingAction().getActionType());
        assertTrue(result.getPendingAction().getConfirmRequired());
    }

    @Test
    void confirm_afterCreate_shouldExecuteAndReturnResult() {
        AiAssistantTurnVO session = aiAssistantService.createSession(currentUser(1L));
        aiAssistantService.message(currentUser(1L), messageRequest(session.getSessionId(), "帮我创建一个预约"));

        AiAssistantMessageRequestDTO request = new AiAssistantMessageRequestDTO();
        request.setSessionId(session.getSessionId());
        request.setFieldValues(Map.of(
                "title", "周会复盘",
                "meetingDate", "2026-04-20",
                "startClock", "14:00",
                "endClock", "15:00",
                "attendees", 10,
                "roomId", 101
        ));
        AiAssistantTurnVO confirmTurn = aiAssistantService.message(currentUser(1L), request);

        ReservationCreateVO created = new ReservationCreateVO();
        created.setId(2001L);
        created.setReservationNo("R20260418001");
        created.setRoomId(101L);
        created.setTitle("周会复盘");
        created.setStatus("ACTIVE");
        when(reservationService.create(any(ReservationCreateDTO.class), eq(1L))).thenReturn(created);

        AiAssistantTurnVO result = aiAssistantService.confirm(currentUser(1L), confirmTurn.getPendingAction().getExecutionId());

        assertEquals("result", result.getStage());
        assertNotNull(result.getResult());
        assertEquals("success", result.getResult().getStatus());
        assertEquals("/reservations/index", result.getResult().getDeepLink());

        ArgumentCaptor<ReservationCreateDTO> captor = ArgumentCaptor.forClass(ReservationCreateDTO.class);
        verify(reservationService).create(captor.capture(), eq(1L));
        assertEquals("周会复盘", captor.getValue().getTitle());
        assertEquals(101L, captor.getValue().getRoomId());
    }

    @Test
    void message_createWithAmbiguousParticipant_shouldReturnCollectUserSelect() {
        AiAssistantTurnVO session = aiAssistantService.createSession(currentUser(1L));
        when(userService.searchActiveUsersByDisplayName("张三", 10, 1L)).thenReturn(List.of(
                userOption(101L, "zhangsan", "张三"),
                userOption(102L, "zhangsan2", "张三")
        ));

        AiAssistantTurnVO result = aiAssistantService.message(currentUser(1L), messageRequest(session.getSessionId(), "帮我约一个我和张三的会"));

        assertEquals("collect", result.getStage());
        assertTrue(result.getAssistantText().contains("参会人"));
        assertTrue(result.getMissingFields().stream().anyMatch(field ->
                "participantUserIds".equals(field.getKey())
                        && "user-select".equals(field.getInputType())));
        verify(userService).searchActiveUsersByDisplayName("张三", 10, 1L);
    }

    @Test
    void confirm_afterCreateWithRecognizedParticipants_shouldPassParticipantIds() {
        AiAssistantTurnVO session = aiAssistantService.createSession(currentUser(1L));
        when(userService.searchActiveUsersByDisplayName("张三", 10, 1L)).thenReturn(List.of(
                userOption(101L, "zhangsan", "张三")
        ));

        AiAssistantTurnVO firstTurn = aiAssistantService.message(currentUser(1L), messageRequest(session.getSessionId(), "帮我约一个我和张三的会"));
        assertEquals("collect", firstTurn.getStage());

        AiAssistantMessageRequestDTO request = new AiAssistantMessageRequestDTO();
        request.setSessionId(session.getSessionId());
        request.setFieldValues(Map.of(
                "title", "周会复盘",
                "meetingDate", "2026-04-20",
                "startClock", "14:00",
                "endClock", "15:00",
                "attendees", 10,
                "roomId", 101
        ));
        AiAssistantTurnVO confirmTurn = aiAssistantService.message(currentUser(1L), request);

        ReservationCreateVO created = new ReservationCreateVO();
        created.setId(2002L);
        created.setReservationNo("R20260418002");
        created.setRoomId(101L);
        created.setTitle("周会复盘");
        created.setStatus("ACTIVE");
        when(reservationService.create(any(ReservationCreateDTO.class), eq(1L))).thenReturn(created);

        AiAssistantTurnVO result = aiAssistantService.confirm(currentUser(1L), confirmTurn.getPendingAction().getExecutionId());

        assertEquals("result", result.getStage());
        ArgumentCaptor<ReservationCreateDTO> captor = ArgumentCaptor.forClass(ReservationCreateDTO.class);
        verify(reservationService).create(captor.capture(), eq(1L));
        assertEquals(List.of(101L), captor.getValue().getParticipantUserIds());
    }

    @Test
    void cancel_shouldReturnCancelledResult() {
        AiAssistantTurnVO session = aiAssistantService.createSession(currentUser(1L));
        aiAssistantService.message(currentUser(1L), messageRequest(session.getSessionId(), "帮我创建一个预约"));

        AiAssistantMessageRequestDTO request = new AiAssistantMessageRequestDTO();
        request.setSessionId(session.getSessionId());
        request.setFieldValues(Map.of(
                "title", "周会复盘",
                "meetingDate", "2026-04-20",
                "startClock", "14:00",
                "endClock", "15:00",
                "attendees", 10,
                "roomId", 101
        ));
        AiAssistantTurnVO confirmTurn = aiAssistantService.message(currentUser(1L), request);

        AiAssistantTurnVO result = aiAssistantService.cancel(currentUser(1L), confirmTurn.getPendingAction().getExecutionId());

        assertEquals("result", result.getStage());
        assertNotNull(result.getResult());
        assertEquals("cancelled", result.getResult().getStatus());
    }

    @Test
    void message_deleteReservation_shouldMapToCancel() {
        AiAssistantTurnVO session = aiAssistantService.createSession(currentUser(1L));
        when(reservationService.myReservations(eq(1L), any(), any(), eq("all"), eq("ACTIVE"), eq(false)))
                .thenReturn(List.of(activeReservation(9001L, "项目周会", "2026-04-19 14:00:00", "2026-04-19 15:00:00", "云杉会议室")));

        AiAssistantTurnVO confirmTurn = aiAssistantService.message(currentUser(1L), messageRequest(session.getSessionId(), "删除我明天下午的预约"));

        assertEquals("confirm", confirmTurn.getStage());
        assertEquals("reservations.cancel", confirmTurn.getPendingAction().getActionType());
    }

    @Test
    void message_cancelWhenMultipleMatches_shouldReturnReservationSelect() {
        AiAssistantTurnVO session = aiAssistantService.createSession(currentUser(1L));
        when(reservationService.myReservations(eq(1L), any(), any(), eq("all"), eq("ACTIVE"), eq(false)))
                .thenReturn(List.of(
                        activeReservation(9001L, "项目周会", "2026-04-19 14:00:00", "2026-04-19 15:00:00", "云杉会议室"),
                        activeReservation(9002L, "客户沟通会", "2026-04-19 15:30:00", "2026-04-19 16:00:00", "潮汐会议室")
                ));

        AiAssistantTurnVO result = aiAssistantService.message(currentUser(1L), messageRequest(session.getSessionId(), "取消我明天下午的预约"));

        assertEquals("collect", result.getStage());
        assertTrue(result.getMissingFields().stream().anyMatch(field -> "reservationId".equals(field.getKey()) && "select".equals(field.getInputType())));
        assertFalse(result.getMissingFields().stream().filter(field -> "reservationId".equals(field.getKey())).findFirst().orElseThrow().getOptions().isEmpty());
    }

    @Test
    void confirm_withInvalidExecutionId_shouldReturnErrorStage() {
        AiAssistantTurnVO result = aiAssistantService.confirm(currentUser(1L), "exec-invalid");

        assertEquals("error", result.getStage());
        assertNotNull(result.getResult());
        assertEquals("error", result.getResult().getStatus());
    }

    @Test
    void message_colloquialTomorrowMeetings_shouldReturnReply() {
        AiAssistantTurnVO session = aiAssistantService.createSession(currentUser(1L));
        when(reservationService.myReservations(eq(1L), any(), any(), eq("all"), eq(null), eq(false)))
                .thenReturn(List.of(activeReservation(9001L, "项目周会", "2026-04-19 14:00:00", "2026-04-19 15:00:00", "云杉会议室")));

        AiAssistantTurnVO result = aiAssistantService.message(currentUser(1L), messageRequest(session.getSessionId(), "我明天有会吗"));

        assertEquals("reply", result.getStage());
    }

    @Test
    void message_colloquialTomorrowMeetings_shouldIgnoreCancelledReservations() {
        AiAssistantTurnVO session = aiAssistantService.createSession(currentUser(1L));
        when(reservationService.myReservations(eq(1L), any(), any(), eq("all"), eq(null), eq(false)))
                .thenReturn(List.of(
                        reservationWithStatus(9001L, "项目周会", "2026-04-19 14:00:00", "2026-04-19 15:00:00", "云杉会议室", "ACTIVE"),
                        reservationWithStatus(9002L, "客户沟通会", "2026-04-19 16:00:00", "2026-04-19 17:00:00", "潮汐会议室", "CANCELLED")
                ));

        AiAssistantTurnVO result = aiAssistantService.message(currentUser(1L), messageRequest(session.getSessionId(), "我明天有会吗"));

        assertEquals("reply", result.getStage());
        assertTrue(result.getAssistantText().contains("共有 1 条预约"));
        assertTrue(result.getAssistantText().contains("项目周会"));
        assertFalse(result.getAssistantText().contains("客户沟通会"));
    }

    @Test
    void message_listShouldLabelNonActiveReservations() {
        AiAssistantTurnVO session = aiAssistantService.createSession(currentUser(1L));
        when(reservationService.myReservations(eq(1L), any(), any(), eq("all"), eq(null), eq(false)))
                .thenReturn(List.of(
                        reservationWithStatus(9001L, "复盘会", "2026-04-19 14:00:00", "2026-04-19 15:00:00", "云杉会议室", "ENDED")
                ));

        AiAssistantTurnVO result = aiAssistantService.message(currentUser(1L), messageRequest(session.getSessionId(), "我明天有会吗"));

        assertEquals("reply", result.getStage());
        assertTrue(result.getAssistantText().contains("已结束"));
    }

    @Test
    void message_tomorrowReservationQuery_shouldUseReservationListIntent() {
        AiAssistantTurnVO session = aiAssistantService.createSession(currentUser(1L));
        when(reservationService.myReservations(eq(1L), any(), any(), eq("all"), eq(null), eq(false)))
                .thenReturn(List.of(
                        activeReservation(9001L, "项目周会", "2026-04-19 14:00:00", "2026-04-19 15:00:00", "云杉会议室")
                ));

        AiAssistantTurnVO result = aiAssistantService.message(currentUser(1L), messageRequest(session.getSessionId(), "我明天有预约吗"));

        assertEquals("reply", result.getStage());
        assertTrue(result.getAssistantText().contains("共有 1 条预约"));
        assertTrue(result.getAssistantText().contains("项目周会"));
    }

    @Test
    void message_vagueRequest_shouldReturnCollectClarification() {
        AiAssistantTurnVO session = aiAssistantService.createSession(currentUser(1L));

        AiAssistantTurnVO result = aiAssistantService.message(currentUser(1L), messageRequest(session.getSessionId(), "帮我看看明天的"));

        assertEquals("collect", result.getStage());
        assertTrue(result.getAssistantText().contains("具体"));
    }

    @Test
    void message_contextReservationReference_shouldReuseLastReservation() {
        AiAssistantTurnVO session = aiAssistantService.createSession(currentUser(1L));
        MyReservationVO reservation = activeReservation(9001L, "项目周会", "2026-04-19 14:00:00", "2026-04-19 15:00:00", "云杉会议室");
        when(reservationService.myReservations(eq(1L), any(), any(), eq("all"), eq(null), eq(false)))
                .thenReturn(List.of(reservation));
        when(reservationService.myReservationDetail(9001L, 1L)).thenReturn(reservation);

        AiAssistantTurnVO detailTurn = aiAssistantService.message(currentUser(1L), messageRequest(session.getSessionId(), "查看我明天下午那个会的详情"));
        assertEquals("reply", detailTurn.getStage());

        AiAssistantTurnVO cancelTurn = aiAssistantService.message(currentUser(1L), messageRequest(session.getSessionId(), "把那个会撤了"));
        assertEquals("confirm", cancelTurn.getStage());
        assertEquals("reservations.cancel", cancelTurn.getPendingAction().getActionType());
    }

    @Test
    void message_weekendQuery_shouldUseResolvedWeekendWindow() {
        AiAssistantTurnVO session = aiAssistantService.createSession(currentUser(1L));
        when(reservationService.myReservations(eq(1L), any(), any(), eq("all"), eq(null), eq(false)))
                .thenReturn(List.of(activeReservation(9001L, "周末复盘", "2026-04-18 14:00:00", "2026-04-18 15:00:00", "云杉会议室")));

        AiAssistantTurnVO result = aiAssistantService.message(currentUser(1L), messageRequest(session.getSessionId(), "这周末我有会吗"));

        assertEquals("reply", result.getStage());
        ArgumentCaptor<String> startCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> endCaptor = ArgumentCaptor.forClass(String.class);
        verify(reservationService, times(1)).myReservations(eq(1L), startCaptor.capture(), endCaptor.capture(), eq("all"), eq(null), eq(false));
        assertEquals("2026-04-18 00:00:00", startCaptor.getValue());
        assertEquals("2026-04-20 00:00:00", endCaptor.getValue());
    }

    private AiAssistantMessageRequestDTO messageRequest(String sessionId, String message) {
        AiAssistantMessageRequestDTO dto = new AiAssistantMessageRequestDTO();
        dto.setSessionId(sessionId);
        dto.setMessage(message);
        return dto;
    }

    private AuthUser currentUser(Long id) {
        AuthUser user = new AuthUser();
        user.setId(id);
        user.setUsername("user-" + id);
        user.setDisplayName("用户" + id);
        user.setRole("USER");
        return user;
    }

    private MyReservationVO activeReservation(Long id, String title, String startTime, String endTime, String roomName) {
        return reservationWithStatus(id, title, startTime, endTime, roomName, "ACTIVE");
    }

    private MyReservationVO reservationWithStatus(Long id, String title, String startTime, String endTime, String roomName, String status) {
        MyReservationVO vo = new MyReservationVO();
        vo.setId(id);
        vo.setTitle(title);
        vo.setStartTime(startTime);
        vo.setEndTime(endTime);
        vo.setRoomName(roomName);
        vo.setRoomId(101L);
        vo.setStatus(status);
        vo.setCanCancel(Boolean.TRUE);
        vo.setCanEdit(Boolean.TRUE);
        return vo;
    }

    private UserOptionVO userOption(Long id, String username, String displayName) {
        UserOptionVO vo = new UserOptionVO();
        vo.setId(id);
        vo.setUsername(username);
        vo.setNickname(displayName);
        vo.setDisplayName(displayName + " (" + username + ")");
        return vo;
    }
}
