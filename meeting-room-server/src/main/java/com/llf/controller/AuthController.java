package com.llf.controller;

import com.llf.auth.TokenStore;
import com.llf.dto.LoginDTO;
import com.llf.result.R;
import com.llf.service.AuthService;
import com.llf.service.CaptchaService;
import com.llf.vo.CaptchaVO;
import com.llf.vo.LoginVO;
import com.llf.vo.UserInfoVO;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    @Resource
    private AuthService authService;
    @Resource
    private TokenStore tokenStore;
    @Resource
    private CaptchaService captchaService;

    @GetMapping("/captcha")
    public R<CaptchaVO> captcha() {
        return R.ok(captchaService.createCaptcha());
    }

    @PostMapping("/login")
    public R<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        return R.ok(authService.login(dto));
    }

    @GetMapping("/me")
    public R<UserInfoVO> me() {
        return R.ok(authService.me());
    }

    @PostMapping("/logout")
    public R<String> logout(@RequestHeader(value = "token", required = false) String token) {
        if (token != null) {
            tokenStore.revoke(token.trim());
        }
        return R.ok("success");
    }
}
