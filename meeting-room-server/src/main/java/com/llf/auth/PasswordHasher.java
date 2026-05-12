package com.llf.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class PasswordHasher {

    private static final String PREFIX = "sha256:";

    private PasswordHasher() {
    }

    public static String sha256ForStorage(String rawPassword) {
        return PREFIX + sha256(rawPassword);
    }

    public static boolean matches(String rawPassword, String storedHash) {
        if (rawPassword == null || storedHash == null || storedHash.isBlank()) {
            return false;
        }
        String normalizedHash = normalizeStoredHash(storedHash);
        if (normalizedHash == null) {
            return false;
        }
        return MessageDigest.isEqual(
                sha256(rawPassword).getBytes(StandardCharsets.UTF_8),
                normalizedHash.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static String normalizeStoredHash(String storedHash) {
        String value = storedHash.trim();
        if (value.regionMatches(true, 0, PREFIX, 0, PREFIX.length())) {
            value = value.substring(PREFIX.length());
        }
        if (!value.matches("(?i)[0-9a-f]{64}")) {
            return null;
        }
        return value.toLowerCase();
    }

    private static String sha256(String rawPassword) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawPassword.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is not available", e);
        }
    }
}
