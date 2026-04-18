package com.llf.controller;

import com.llf.auth.AuthContext;
import com.llf.auth.AuthUser;
import com.llf.result.R;
import com.llf.service.AuthService;
import com.llf.service.UserService;
import com.llf.vo.auth.UserInfoVO;
import com.llf.vo.user.UserOptionVO;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
public class UsersController {

    @Resource
    private AuthService authService;
    @Resource
    private UserService userService;

    @GetMapping("/me")
    public R<UserInfoVO> me() {
        return R.ok(authService.me());
    }

    @GetMapping("/search")
    public R<List<UserOptionVO>> search(@RequestParam(required = false) String keyword,
                                        @RequestParam(required = false) Integer limit) {
        AuthUser currentUser = AuthContext.get();
        Long excludeUserId = currentUser == null ? null : currentUser.getId();
        return R.ok(userService.searchActiveUsersByDisplayName(keyword, limit, excludeUserId));
    }
}
