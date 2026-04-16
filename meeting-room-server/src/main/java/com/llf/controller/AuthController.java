package com.llf.controller;

import com.llf.dto.auth.LoginDTO;
import com.llf.result.R;
import com.llf.service.AuthService;
import com.llf.service.CaptchaService;
import com.llf.vo.auth.CaptchaVO;
import com.llf.vo.auth.LoginVO;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    @Resource
    private AuthService authService;
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
}
