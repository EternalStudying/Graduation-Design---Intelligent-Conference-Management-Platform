package com.llf.auth;

import java.time.Duration;

public interface CaptchaStore {

    void save(String captchaId, String code, Duration ttl);

    VerifyResult verifyAndConsume(String captchaId, String inputCode);

    record VerifyResult(boolean success, boolean expired) {
        public static VerifyResult ok() {
            return new VerifyResult(true, false);
        }

        public static VerifyResult invalid() {
            return new VerifyResult(false, false);
        }

        public static VerifyResult expiredResult() {
            return new VerifyResult(false, true);
        }
    }
}
