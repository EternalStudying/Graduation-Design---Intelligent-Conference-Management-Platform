package com.llf.service;

import com.llf.dto.LoginDTO;
import com.llf.vo.LoginVO;
import com.llf.vo.UserInfoVO;

public interface AuthService {
    LoginVO login(LoginDTO dto);

    UserInfoVO me();

    void logout();
}
