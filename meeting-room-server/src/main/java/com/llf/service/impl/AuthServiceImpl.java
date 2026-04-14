package com.llf.service.impl;

import com.llf.auth.AuthContext;
import com.llf.auth.AuthUser;
import com.llf.auth.TokenStore;
import com.llf.dto.LoginDTO;
import com.llf.mapper.SysUserMapper;
import com.llf.result.BizException;
import com.llf.service.AuthService;
import com.llf.service.CaptchaService;
import com.llf.vo.LoginVO;
import com.llf.vo.UserInfoVO;
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

    @Override
    public LoginVO login(LoginDTO dto) {
        if (dto == null) {
            throw new BizException(400, "request body must not be null");
        }

        captchaService.verifyCaptcha(dto.getCaptchaId(), dto.getCode());

        SysUserMapper.SysUserDO u = sysUserMapper.findByUsername(dto.getUsername());
        if (u == null) {
            throw new BizException(401, "username or password is incorrect");
        }
        if (!"ACTIVE".equalsIgnoreCase(u.status)) {
            throw new BizException(403, "account is disabled");
        }
        if (!dto.getPassword().equals(u.passwordHash)) {
            throw new BizException(401, "username or password is incorrect");
        }

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

    @Override
    public void logout() {
    }

    private String normalizeRole(String role) {
        if (role == null || role.isBlank()) {
            return "user";
        }
        if ("ADMIN".equalsIgnoreCase(role)) {
            return "admin";
        }
        return "user";
    }
}
