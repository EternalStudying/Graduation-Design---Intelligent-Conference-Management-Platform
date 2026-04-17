package com.llf.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.llf.ai.AiChatSessionStore;
import com.llf.ai.ReservationAssistantLlmClient;
import com.llf.ai.ReservationAssistantLlmRequest;
import com.llf.ai.ReservationKnowledgeService;
import com.llf.auth.AuthUser;
import com.llf.mapper.ReservationMapper;
import com.llf.mapper.RoomMapper;
import com.llf.service.ReservationService;
import com.llf.vo.assistant.AiChatResponseVO;
import com.llf.vo.assistant.AiChatSessionVO;
import com.llf.vo.reservation.MyReservationVO;
import com.llf.vo.reservation.ReservationRecommendationItemVO;
import com.llf.vo.reservation.ReservationRecommendationVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiChatServiceImplTest {

    @Mock
    private ReservationService reservationService;

    @Mock
    private RoomMapper roomMapper;

    @Mock
    private ReservationMapper reservationMapper;

    @Mock
    private ReservationAssistantLlmClient llmClient;

    private AiChatServiceImpl aiChatService;

    @BeforeEach
    void setUp() {
        aiChatService = new AiChatServiceImpl(
                new AiChatSessionStore(),
                reservationService,
                roomMapper,
                reservationMapper,
                llmClient,
                new ReservationKnowledgeService(new ObjectMapper())
        );
        lenient().when(llmClient.generate(any())).thenReturn(null);
    }

    @Test
    void createSession_shouldReturnWelcomeAndSuggestions() {
        AiChatSessionVO result = aiChatService.createSession(currentUser(1L));

        assertNotNull(result.getSessionId());
        assertTrue(result.getSessionId().startsWith("assistant-"));
        assertTrue(result.getWelcome().contains("智能预约助手"));
        assertEquals(3, result.getSuggestions().size());
    }

    @Test
    void chat_shouldSummarizeMyReservationsWithinThisWeek() {
        AiChatSessionVO session = aiChatService.createSession(currentUser(1L));
        when(reservationService.myReservations(eq(1L), anyString(), anyString(), eq("all"), eq(null), eq(false)))
                .thenReturn(List.of(
                        reservation(101L, "产品周会", "晨光会议室", LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusHours(1)),
                        reservation(102L, "技术评审会", "远景会议室", LocalDateTime.now().plusDays(2), LocalDateTime.now().plusDays(2).plusHours(2))
                ));
        when(llmClient.generate(any())).thenReturn("本周你有 2 场预约，包括产品周会和技术评审会。");

        AiChatResponseVO result = aiChatService.chat(currentUser(1L), session.getSessionId(), "查看我本周的预约", "reservation");

        assertEquals(session.getSessionId(), result.getSessionId());
        assertEquals("本周你有 2 场预约，包括产品周会和技术评审会。", result.getAnswer());
        verify(reservationService).myReservations(eq(1L), anyString(), anyString(), eq("all"), eq(null), eq(false));
    }

    @Test
    void chat_shouldRecommendAvailableRoomsForThisAfternoon() {
        AiChatSessionVO session = aiChatService.createSession(currentUser(2L));
        when(reservationService.recommend(any())).thenReturn(recommendation("远景会议室", "晨光会议室"));
        when(llmClient.generate(any())).thenReturn("今天下午可优先看远景会议室和晨光会议室。");

        AiChatResponseVO result = aiChatService.chat(currentUser(2L), session.getSessionId(), "帮我找今天下午可用的10人会议室", "reservation");

        assertEquals("今天下午可优先看远景会议室和晨光会议室。", result.getAnswer());

        ArgumentCaptor<com.llf.dto.reservation.ReservationRecommendationDTO> captor =
                ArgumentCaptor.forClass(com.llf.dto.reservation.ReservationRecommendationDTO.class);
        verify(reservationService).recommend(captor.capture());
        assertEquals(10, captor.getValue().getAttendees());
        assertTrue(captor.getValue().getStartTime().contains(LocalDate.now().toString()));
    }

    @Test
    void chat_shouldPassFactsAndKnowledgeIntoLlmRequest() {
        AiChatSessionVO session = aiChatService.createSession(currentUser(3L));
        when(llmClient.generate(any())).thenReturn("我是会议室预约助手，专门处理会议室和预约相关问题。");

        AiChatResponseVO result = aiChatService.chat(currentUser(3L), session.getSessionId(), "你是谁", "reservation");

        assertTrue(result.getAnswer().contains("会议室预约助手"));

        ArgumentCaptor<ReservationAssistantLlmRequest> captor = ArgumentCaptor.forClass(ReservationAssistantLlmRequest.class);
        verify(llmClient).generate(captor.capture());
        assertEquals("你是谁", captor.getValue().question());
        assertTrue(captor.getValue().facts().contains("身份"));
        assertTrue(captor.getValue().knowledgeSnippets().stream().anyMatch(item -> item.contains("会议室预约助手")));
    }

    @Test
    void chat_shouldFallBackToRuleAnswerWhenLlmUnavailable() {
        AiChatSessionVO session = aiChatService.createSession(currentUser(4L));
        when(llmClient.generate(any())).thenReturn(null);

        AiChatResponseVO result = aiChatService.chat(currentUser(4L), session.getSessionId(), "介绍一个 Java 21 新特性", "reservation");

        assertTrue(result.getAnswer().contains("只支持会议室和预约相关问题"));
        assertEquals(3, result.getSuggestions().size());
    }

    @Test
    void chat_shouldDetectReservationConflictAtTomorrowThreePm() {
        AiChatSessionVO session = aiChatService.createSession(currentUser(5L));
        LocalDateTime start = LocalDate.now().plusDays(1).atTime(14, 30);
        LocalDateTime end = LocalDate.now().plusDays(1).atTime(15, 30);
        when(reservationService.myReservations(eq(5L), anyString(), anyString(), eq("all"), eq("ACTIVE"), eq(false)))
                .thenReturn(List.of(reservation(201L, "客户沟通会", "青禾会议室", start, end)));
        when(roomMapper.selectRooms(null, null, true)).thenReturn(List.of());
        when(llmClient.generate(any())).thenReturn("明天下午 3 点与你现有的客户沟通会冲突。");

        AiChatResponseVO result = aiChatService.chat(currentUser(5L), session.getSessionId(), "判断明天下午3点是否容易冲突", "reservation");

        assertTrue(result.getAnswer().contains("客户沟通会"));
    }

    private AuthUser currentUser(Long id) {
        AuthUser user = new AuthUser();
        user.setId(id);
        user.setUsername("user-" + id);
        user.setDisplayName("用户" + id);
        user.setRole("USER");
        return user;
    }

    private MyReservationVO reservation(Long id,
                                        String title,
                                        String roomName,
                                        LocalDateTime start,
                                        LocalDateTime end) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        MyReservationVO vo = new MyReservationVO();
        vo.setId(id);
        vo.setTitle(title);
        vo.setRoomName(roomName);
        vo.setStartTime(start.format(formatter));
        vo.setEndTime(end.format(formatter));
        vo.setStatus("ACTIVE");
        return vo;
    }

    private ReservationRecommendationVO recommendation(String... roomNames) {
        ReservationRecommendationVO vo = new ReservationRecommendationVO();
        vo.setRecommendations(List.of(roomNames).stream().map(this::recommendationItem).toList());
        return vo;
    }

    private ReservationRecommendationItemVO recommendationItem(String roomName) {
        ReservationRecommendationItemVO item = new ReservationRecommendationItemVO();
        item.setRoomId((long) roomName.hashCode());
        item.setRoomName(roomName);
        item.setLocation("1号楼A区2层");
        item.setCapacity(12);
        item.setScore(90);
        item.setTags(List.of("容量匹配好"));
        return item;
    }
}
