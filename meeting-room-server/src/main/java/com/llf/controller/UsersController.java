package com.llf.controller;

import com.llf.result.R;
import com.llf.service.AuthService;
import com.llf.vo.auth.UserInfoVO;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UsersController {

    @Resource
    private AuthService authService;

    @GetMapping("/me")
    public R<UserInfoVO> me() {
        return R.ok(authService.me());
    }
}
