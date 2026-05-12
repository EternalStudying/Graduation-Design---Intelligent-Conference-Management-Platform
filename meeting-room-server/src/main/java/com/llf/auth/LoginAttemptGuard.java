package com.llf.auth;

import com.llf.result.BizException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LoginAttemptGuard {

    private static final int DEFAULT_MAX_ATTEMPTS = 5;
    private static final Duration DEFAULT_LOCK_DURATION = Duration.ofMinutes(10);

    private final int maxAttempts;
    private final Duration lockDuration;
    private final Clock clock;
    private final Map<String, AttemptState> attempts = new ConcurrentHashMap<>();

    public LoginAttemptGuard() {
        this(DEFAULT_MAX_ATTEMPTS, DEFAULT_LOCK_DURATION, Clock.systemDefaultZone());
    }

    public LoginAttemptGuard(int maxAttempts, Duration lockDuration, Clock clock) {
        this.maxAttempts = maxAttempts;
        this.lockDuration = lockDuration;
        this.clock = clock;
    }

    public void assertNotLocked(String username) {
        String key = keyOf(username);
        AttemptState state = attempts.get(key);
        if (state == null || state.lockedUntil == null) {
            return;
        }
        Instant now = clock.instant();
        if (now.isBefore(state.lockedUntil)) {
            throw new BizException(423, "account temporarily locked");
        }
        attempts.remove(key);
    }

    public void recordFailure(String username) {
        String key = keyOf(username);
        attempts.compute(key, (_ignored, state) -> {
            AttemptState next = state == null ? new AttemptState() : state;
            next.failedCount++;
            if (next.failedCount >= maxAttempts) {
                next.lockedUntil = clock.instant().plus(lockDuration);
            }
            return next;
        });
    }

    public void recordSuccess(String username) {
        attempts.remove(keyOf(username));
    }

    private String keyOf(String username) {
        return username == null ? "" : username.trim().toLowerCase();
    }

    private static class AttemptState {
        private int failedCount;
        private Instant lockedUntil;
    }
}
