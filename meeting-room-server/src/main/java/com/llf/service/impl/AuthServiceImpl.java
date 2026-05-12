package com.llf.service.impl;

import com.llf.auth.AuthContext;
import com.llf.auth.AuthUser;
import com.llf.auth.LoginAttemptGuard;
import com.llf.auth.PasswordHasher;
import com.llf.auth.TokenStore;
import com.llf.dto.auth.LoginDTO;
import com.llf.mapper.SysUserMapper;
import com.llf.result.BizException;
import com.llf.service.AuthService;
import com.llf.service.CaptchaService;
import com.llf.vo.auth.LoginVO;
import com.llf.vo.auth.UserInfoVO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AuthServiceImpl implements AuthService {

    @Resource
    private SysUserMapper sysUserMapper;
    @Resource
    private TokenStore tokenStore;
    @Resource
    private CaptchaService captchaService;
    @Resource
    private LoginAttemptGuard loginAttemptGuard;

    @Override
    public LoginVO login(LoginDTO dto) {
        if (dto == null) {
            throw new BizException(400, "request body must not be null");
        }

        captchaService.verifyCaptcha(dto.getCaptchaId(), dto.getCode());
        loginAttemptGuard.assertNotLocked(dto.getUsername());

        SysUserMapper.SysUserDO u = sysUserMapper.findByUsername(dto.getUsername());
        if (u == null) {
            loginAttemptGuard.recordFailure(dto.getUsername());
            throw new BizException(401, "username or password is incorrect");
        }
        if (!isActiveStatus(u.status)) {
            throw new BizException(403, "account is disabled");
        }
        if (!PasswordHasher.matches(dto.getPassword(), u.passwordHash)) {
            loginAttemptGuard.recordFailure(dto.getUsername());
            throw new BizException(401, "username or password is incorrect");
        }
        loginAttemptGuard.recordSuccess(dto.getUsername());

        AuthUser au = new AuthUser();
        au.setId(u.id);
        au.setUsername(u.username);
        au.setDisplayName(u.displayName);
        au.setRole(u.role);

        LoginVO vo = new LoginVO();
        vo.setToken(tokenStore.issue(au));
        return vo;
    }

    @Override
    public UserInfoVO me() {
        AuthUser u = AuthContext.get();
        if (u == null) {
            throw new BizException(401, "not logged in");
        }

        UserInfoVO vo = new UserInfoVO();
        vo.setUsername(u.getUsername());
        vo.setRoles(List.of(normalizeRole(u.getRole())));
        return vo;
    }

    private String normalizeRole(String role) {
        if (role == null || role.isBlank()) {
            return "user";
        }
        if ("ADMIN".equalsIgnoreCase(role) || "2".equals(role.trim())) {
            return "admin";
        }
        return "user";
    }

    private boolean isActiveStatus(String status) {
        if (status == null || status.isBlank()) {
            return false;
        }
        String value = status.trim();
        return "ACTIVE".equalsIgnoreCase(value) || "1".equals(value);
    }
}
