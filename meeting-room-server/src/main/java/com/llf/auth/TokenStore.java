package com.llf.auth;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TokenStore {
    private final Map<String, AuthUser> map = new ConcurrentHashMap<>();

    public String issue(AuthUser user) {
        String token = UUID.randomUUID().toString().replace("-", "");
        map.put(token, user);
        return token;
    }

    public AuthUser get(String token) {
        if (token == null || token.isBlank()) return null;
        return map.get(token);
    }

    public void revoke(String token) {
        if (token == null) return;
        map.remove(token);
    }
}