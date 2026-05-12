package com.llf.auth;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class TokenStoreTest {

    @Test
    void get_shouldReturnNullAfterTokenExpires() {
        MutableClock clock = new MutableClock(Instant.parse("2026-05-12T09:00:00Z"));
        TokenStore tokenStore = new TokenStore(Duration.ofMinutes(30), clock);

        String token = tokenStore.issue(currentUser());
        assertNotNull(tokenStore.get(token));

        clock.advance(Duration.ofMinutes(31));

        assertNull(tokenStore.get(token));
    }

    private AuthUser currentUser() {
        AuthUser user = new AuthUser();
        user.setId(1L);
        user.setUsername("admin");
        user.setRole("ADMIN");
        return user;
    }

    private static class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
