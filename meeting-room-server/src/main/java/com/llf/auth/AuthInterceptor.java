package com.llf.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.llf.result.R;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final TokenStore tokenStore;
    private final ObjectMapper om = new ObjectMapper();

    public AuthInterceptor(TokenStore tokenStore) {
        this.tokenStore = tokenStore;
    }

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse resp, Object handler) throws Exception {
        String path = req.getRequestURI();

        if (isPublicAuthPath(path) || "OPTIONS".equalsIgnoreCase(req.getMethod())) {
            return true;
        }

        String token = extract(req);
        AuthUser u = tokenStore.get(token);

        if (u == null) {
            return reject(resp, HttpStatus.UNAUTHORIZED.value(), "not logged in or token expired");
        }

        if (isAdminApiPath(path) && !isAdminRole(u.getRole())) {
            return reject(resp, HttpStatus.FORBIDDEN.value(), "admin permission required");
        }

        AuthContext.set(u);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest req, HttpServletResponse resp, Object handler, Exception ex) {
        AuthContext.clear();
    }

    private boolean isPublicAuthPath(String path) {
        return path.startsWith("/api/v1/auth/login")
                || path.startsWith("/api/v1/auth/captcha");
    }

    private boolean isAdminApiPath(String path) {
        return "/api/v1/admin".equals(path) || path.startsWith("/api/v1/admin/");
    }

    private boolean isAdminRole(String role) {
        if (role == null || role.isBlank()) {
            return false;
        }
        String value = role.trim();
        return "ADMIN".equalsIgnoreCase(value) || "2".equals(value) || "admin".equalsIgnoreCase(value);
    }

    private String extract(HttpServletRequest req) {
        String token = req.getHeader("token");
        if (token == null || token.isBlank()) {
            return null;
        }
        return token.trim();
    }

    private boolean reject(HttpServletResponse resp, int code, String message) throws Exception {
        resp.setStatus(code);
        resp.setContentType("application/json;charset=UTF-8");
        resp.getWriter().write(om.writeValueAsString(R.fail(code, message)));
        return false;
    }
}
