package com.llf.service;

import com.llf.dto.auth.LoginDTO;
import com.llf.vo.auth.LoginVO;
import com.llf.vo.auth.UserInfoVO;

public interface AuthService {
    LoginVO login(LoginDTO dto);

    UserInfoVO me();
}
