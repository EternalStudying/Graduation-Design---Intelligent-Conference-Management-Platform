package com.llf.controller;

import com.llf.auth.AuthContext;
import com.llf.auth.AuthUser;
import com.llf.config.GlobalExceptionHandler;
import com.llf.service.ReservationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminReservationControllerTest {

    private final ReservationService reservationService = mock(ReservationService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AuthContext.set(currentUser("USER"));
        AdminReservationController controller = new AdminReservationController();
        inject(controller, "reservationService", reservationService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    void page_shouldRejectNonAdminUser() throws Exception {
        mockMvc.perform(get("/api/v1/admin/reservations")
                        .param("currentPage", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));

        verifyNoInteractions(reservationService);
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
