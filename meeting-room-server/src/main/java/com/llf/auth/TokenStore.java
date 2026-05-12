package com.llf.auth;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TokenStore {
    private static final Duration DEFAULT_TOKEN_TTL = Duration.ofHours(2);

    private final Map<String, TokenEntry> map = new ConcurrentHashMap<>();
    private final Duration tokenTtl;
    private final Clock clock;

    public TokenStore() {
        this(DEFAULT_TOKEN_TTL, Clock.systemDefaultZone());
    }

    TokenStore(Duration tokenTtl, Clock clock) {
        this.tokenTtl = tokenTtl;
        this.clock = clock;
    }

    public String issue(AuthUser user) {
        String token = UUID.randomUUID().toString().replace("-", "");
        map.put(token, new TokenEntry(user, clock.instant().plus(tokenTtl)));
        return token;
    }

    public AuthUser get(String token) {
        if (token == null || token.isBlank()) return null;
        TokenEntry entry = map.get(token);
        if (entry == null) {
            return null;
        }
        if (!clock.instant().isBefore(entry.expiresAt)) {
            map.remove(token);
            return null;
        }
        return entry.user;
    }

    public void revoke(String token) {
        if (token == null) return;
        map.remove(token);
    }

    private record TokenEntry(AuthUser user, Instant expiresAt) {
    }
}
