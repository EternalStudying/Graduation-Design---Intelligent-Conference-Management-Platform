package com.llf.job;

import com.llf.service.ReservationService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
public class ReservationStatusScheduler {

    @Resource
    private ReservationService reservationService;

    @Transactional
    @Scheduled(cron = "0 * * * * *") // Spring 的 cron 默认 6 段：秒 分 时 日 月 周
    public void markEnded() {
        int updated = reservationService.markEnded();
        if (updated > 0) {
            log.info("[scheduler] markEnded updated rows={}", updated);
        }
    }
}