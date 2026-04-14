package com.llf.auth;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class MemoryCaptchaStore implements CaptchaStore {

    private final Map<String, CaptchaEntry> cache = new ConcurrentHashMap<>();

    @Override
    public void save(String captchaId, String code, Duration ttl) {
        purgeExpired();
        cache.put(captchaId, new CaptchaEntry(code, System.currentTimeMillis() + ttl.toMillis()));
    }

    @Override
    public VerifyResult verifyAndConsume(String captchaId, String inputCode) {
        if (captchaId == null || captchaId.isBlank() || inputCode == null || inputCode.isBlank()) {
            return VerifyResult.invalid();
        }

        CaptchaEntry entry = cache.remove(captchaId);
        if (entry == null) {
            return VerifyResult.expiredResult();
        }
        if (entry.expireAt < System.currentTimeMillis()) {
            return VerifyResult.expiredResult();
        }
        if (!entry.code.equalsIgnoreCase(inputCode.trim())) {
            return VerifyResult.invalid();
        }
        return VerifyResult.ok();
    }

    private void purgeExpired() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, CaptchaEntry>> iterator = cache.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, CaptchaEntry> entry = iterator.next();
            if (entry.getValue().expireAt < now) {
                iterator.remove();
            }
        }
    }

    private record CaptchaEntry(String code, long expireAt) {
    }
}
