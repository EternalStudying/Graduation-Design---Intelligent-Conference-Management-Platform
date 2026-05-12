package com.llf.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.llf.auth.AuthContext;
import com.llf.auth.AuthUser;
import com.llf.config.GlobalExceptionHandler;
import com.llf.service.NotificationService;
import com.llf.vo.notification.AdminNotificationPublishVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminNotificationControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final NotificationService notificationService = mock(NotificationService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AuthContext.set(currentUser("ADMIN"));
        AdminNotificationController controller = new AdminNotificationController();
        inject(controller, "notificationService", notificationService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    void publish_shouldDelegateForAdminUser() throws Exception {
        AdminNotificationPublishVO vo = new AdminNotificationPublishVO();
        vo.setType("ANNOUNCEMENT");
        vo.setCategory("NOTICE");
        vo.setRecipientScope("ALL");
        vo.setTitle("系统公告");
        vo.setPublishedCount(6);
        when(notificationService.publishAdminNotification(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(vo);

        mockMvc.perform(post("/api/v1/admin/notifications")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "type", "ANNOUNCEMENT",
                                "recipientScope", "ALL",
                                "title", "系统公告",
                                "content", "明天上午系统升级。"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.publishedCount").value(6));

        verify(notificationService).publishAdminNotification(
                "ANNOUNCEMENT",
                "系统公告",
                "明天上午系统升级。",
                "ALL"
        );
    }

    @Test
    void publish_shouldRejectNonAdminUser() throws Exception {
        AuthContext.set(currentUser("USER"));

        mockMvc.perform(post("/api/v1/admin/notifications")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "type", "ANNOUNCEMENT",
                                "recipientScope", "ALL",
                                "title", "系统公告",
                                "content", "明天上午系统升级。"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));

        verifyNoInteractions(notificationService);
    }

    private AuthUser currentUser(String role) {
        AuthUser user = new AuthUser();
        user.setId(1L);
        user.setUsername("tester");
        user.setDisplayName("测试用户");
        user.setRole(role);
        return user;
    }

    private void inject(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
