package com.llf.controller;

import com.llf.auth.AuthContext;
import com.llf.auth.AuthUser;
import com.llf.config.GlobalExceptionHandler;
import com.llf.service.AiChatService;
import com.llf.vo.assistant.AiChatResponseVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AiChatControllerTest {

    private final AiChatService aiChatService = mock(AiChatService.class);

    private MockMvc mockMvc;
    private AuthUser authUser;

    @BeforeEach
    void setUp() {
        AiChatController controller = new AiChatController();
        injectAiChatService(controller, aiChatService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        authUser = currentUser();
        AuthContext.set(authUser);
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    void chat_shouldKeepJsonContractUnchanged() throws Exception {
        AiChatResponseVO response = new AiChatResponseVO();
        response.setSessionId("assistant-1");
        response.setAnswer("今天下午可优先看远景会议室。");
        response.setSuggestions(List.of("优先推荐带投影的会议室", "查看14:00到16:00的空闲时段", "只看当前楼层的会议室"));
        when(aiChatService.chat(eq(authUser), eq("assistant-1"), eq("帮我找今天下午可用的10人会议室"), eq("reservation")))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sessionId":"assistant-1","message":"帮我找今天下午可用的10人会议室","scene":"reservation"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.sessionId").value("assistant-1"))
                .andExpect(jsonPath("$.data.answer").value("今天下午可优先看远景会议室。"))
                .andExpect(jsonPath("$.data.suggestions[0]").value("优先推荐带投影的会议室"));
    }

    @Test
    void stream_shouldReturnStartDeltaDoneEvents() throws Exception {
        when(aiChatService.streamChat(eq(authUser), eq("assistant-1"), eq("帮我找今天下午可用的10人会议室"), eq("reservation")))
                .thenReturn(buildEmitter());

        MvcResult mvcResult = mockMvc.perform(post("/api/v1/ai/chat/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sessionId":"assistant-1","message":"帮我找今天下午可用的10人会议室","scene":"reservation"}
                                """))
                .andExpect(request().asyncStarted())
                .andReturn();

        MvcResult dispatched = mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(content().string(containsString("event:start")))
                .andExpect(content().string(containsString("event:delta")))
                .andExpect(content().string(containsString("event:done")))
                .andExpect(content().string(containsString("\"sessionId\":\"assistant-1\"")))
                .andExpect(content().string(containsString("\"delta\":")))
                .andExpect(content().string(containsString("\"suggestions\":")))
                .andReturn();

        String body = dispatched.getResponse().getContentAsString();
        int startIndex = body.indexOf("event:start");
        int deltaIndex = body.indexOf("event:delta");
        int doneIndex = body.indexOf("event:done");
        org.junit.jupiter.api.Assertions.assertTrue(startIndex >= 0 && deltaIndex > startIndex && doneIndex > deltaIndex);

        verify(aiChatService).streamChat(eq(authUser), eq("assistant-1"), eq("帮我找今天下午可用的10人会议室"), eq("reservation"));
    }

    private void injectAiChatService(AiChatController controller, AiChatService service) {
        try {
            var field = AiChatController.class.getDeclaredField("aiChatService");
            field.setAccessible(true);
            field.set(controller, service);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private AuthUser currentUser() {
        AuthUser user = new AuthUser();
        user.setId(1L);
        user.setUsername("user-1");
        user.setDisplayName("用户1");
        user.setRole("USER");
        return user;
    }

    private SseEmitter buildEmitter() {
        SseEmitter emitter = new SseEmitter();
        CompletableFuture.runAsync(() -> {
            try {
                emitter.send(SseEmitter.event()
                        .name("start")
                        .data(Map.of("sessionId", "assistant-1"), MediaType.APPLICATION_JSON));
                emitter.send(SseEmitter.event()
                        .name("delta")
                        .data(Map.of("delta", "今天下午"), MediaType.APPLICATION_JSON));
                Map<String, Object> donePayload = new LinkedHashMap<>();
                donePayload.put("sessionId", "assistant-1");
                donePayload.put("suggestions", List.of("优先推荐带投影的会议室", "查看14:00到16:00的空闲时段", "只看当前楼层的会议室"));
                emitter.send(SseEmitter.event()
                        .name("done")
                        .data(donePayload, MediaType.APPLICATION_JSON));
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }
}
