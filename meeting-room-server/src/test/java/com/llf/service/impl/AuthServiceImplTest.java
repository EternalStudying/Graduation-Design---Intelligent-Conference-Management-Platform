package com.llf.service.impl;

import com.llf.auth.AuthContext;
import com.llf.auth.AuthUser;
import com.llf.auth.LoginAttemptGuard;
import com.llf.auth.PasswordHasher;
import com.llf.auth.TokenStore;
import com.llf.dto.auth.LoginDTO;
import com.llf.mapper.SysUserMapper;
import com.llf.result.BizException;
import com.llf.service.CaptchaService;
import com.llf.vo.auth.LoginVO;
import com.llf.vo.auth.UserInfoVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthServiceImplTest {

    private final SysUserMapper sysUserMapper = mock(SysUserMapper.class);
    private final TokenStore tokenStore = new TokenStore();
    private final CaptchaService captchaService = mock(CaptchaService.class);
    private final LoginAttemptGuard loginAttemptGuard = new LoginAttemptGuard(
            3,
            Duration.ofMinutes(10),
            Clock.fixed(Instant.parse("2026-05-12T09:00:00Z"), ZoneId.of("UTC"))
    );
    private final AuthServiceImpl authService = new AuthServiceImpl();

    AuthServiceImplTest() {
        inject(authService, "sysUserMapper", sysUserMapper);
        inject(authService, "tokenStore", tokenStore);
        inject(authService, "captchaService", captchaService);
        inject(authService, "loginAttemptGuard", loginAttemptGuard);
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    void login_shouldAcceptNumericEnabledStatus() {
        LoginDTO dto = loginDTO();
        doNothing().when(captchaService).verifyCaptcha("captcha-1", "abcd");
        when(sysUserMapper.findByUsername("admin")).thenReturn(user("admin", "2", "1"));

        LoginVO vo = authService.login(dto);

        assertNotNull(vo.getToken());
        assertEquals("admin", tokenStore.get(vo.getToken()).getUsername());
    }

    @Test
    void login_shouldRejectPlainTextStoredPassword() {
        LoginDTO dto = loginDTO();
        doNothing().when(captchaService).verifyCaptcha("captcha-1", "abcd");
        SysUserMapper.SysUserDO user = user("admin", "2", "1");
        user.passwordHash = "123456";
        when(sysUserMapper.findByUsername("admin")).thenReturn(user);

        BizException ex = assertThrows(BizException.class, () -> authService.login(dto));

        assertEquals(401, ex.getCode());
    }

    @Test
    void login_shouldLockUsernameAfterRepeatedPasswordFailures() {
        doNothing().when(captchaService).verifyCaptcha("captcha-1", "abcd");
        when(sysUserMapper.findByUsername("admin")).thenReturn(user("admin", "2", "1"));

        assertThrows(BizException.class, () -> authService.login(loginDTO("wrong-1")));
        assertThrows(BizException.class, () -> authService.login(loginDTO("wrong-2")));
        assertThrows(BizException.class, () -> authService.login(loginDTO("wrong-3")));
        BizException ex = assertThrows(BizException.class, () -> authService.login(loginDTO("123456")));

        assertEquals(423, ex.getCode());
    }

    @Test
    void me_shouldTreatNumericAdminRoleAsAdmin() {
        AuthUser currentUser = new AuthUser();
        currentUser.setUsername("admin");
        currentUser.setRole("2");
        AuthContext.set(currentUser);

        UserInfoVO vo = authService.me();

        assertEquals("admin", vo.getRoles().get(0));
    }

    private LoginDTO loginDTO() {
        return loginDTO("123456");
    }

    private LoginDTO loginDTO(String password) {
        LoginDTO dto = new LoginDTO();
        dto.setUsername("admin");
        dto.setPassword(password);
        dto.setCode("abcd");
        dto.setCaptchaId("captcha-1");
        return dto;
    }

    private SysUserMapper.SysUserDO user(String username, String role, String status) {
        SysUserMapper.SysUserDO user = new SysUserMapper.SysUserDO();
        user.id = 1L;
        user.username = username;
        user.displayName = "系统管理员";
        user.passwordHash = PasswordHasher.sha256ForStorage("123456");
        user.role = role;
        user.status = status;
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
