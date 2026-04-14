package com.llf.auth;

import lombok.Data;

@Data
public class AuthUser {
    private Long id;
    private String username;
    private String displayName;
    private String role; // USER/ADMIN
}