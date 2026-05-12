package com.llf.auth;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthInterceptorTest {

    private TokenStore tokenStore;
    private AuthInterceptor authInterceptor;

    @BeforeEach
    void setUp() {
        tokenStore = new TokenStore();
        authInterceptor = new AuthInterceptor(tokenStore);
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    void preHandle_shouldRejectMissingTokenForAdminApi() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = authInterceptor.preHandle(
                new MockHttpServletRequest("GET", "/api/v1/admin/rooms"),
                response,
                new Object()
        );

        assertFalse(allowed);
        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
    }

    @Test
    void preHandle_shouldRejectNonAdminUserForAdminApi() throws Exception {
        String token = tokenStore.issue(currentUser("USER"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/admin/rooms");
        request.addHeader("token", token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = authInterceptor.preHandle(request, response, new Object());

        assertFalse(allowed);
        assertEquals(HttpServletResponse.SC_FORBIDDEN, response.getStatus());
    }

    @Test
    void preHandle_shouldAllowAdminUserForAdminApi() throws Exception {
        String token = tokenStore.issue(currentUser("ADMIN"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/admin/rooms");
        request.addHeader("token", token);

        boolean allowed = authInterceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        assertTrue(allowed);
        assertEquals("ADMIN", AuthContext.get().getRole());
    }

    @Test
    void preHandle_shouldAllowNormalApiForSignedInUser() throws Exception {
        String token = tokenStore.issue(currentUser("USER"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/rooms");
        request.addHeader("token", token);

        boolean allowed = authInterceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        assertTrue(allowed);
        assertEquals("USER", AuthContext.get().getRole());
    }

    private AuthUser currentUser(String role) {
        AuthUser user = new AuthUser();
        user.setId(1L);
        user.setUsername("tester");
        user.setDisplayName("测试用户");
        user.setRole(role);
        return user;
    }
}
