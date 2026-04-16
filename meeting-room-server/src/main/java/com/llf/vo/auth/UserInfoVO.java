package com.llf.vo.auth;

import lombok.Data;

import java.util.List;

@Data
public class UserInfoVO {
    private String username;
    private List<String> roles;
}

