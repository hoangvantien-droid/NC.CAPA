package com.dqh.nccapa.ticket.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Tự động flush callback ticket định kỳ (thay cho gọi /ticket/flush tay).
 * Tắt ở profile test (app.ticket.scheduler.enabled=false trong application-test.yml)
 * để test điều khiển flush thủ công, deterministic.
 */
@Component
@ConditionalOnProperty(name = "app.ticket.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class TicketCallbackScheduler {

    private final TicketCallbackService callbackService;

    public TicketCallbackScheduler(TicketCallbackService callbackService) {
        this.callbackService = callbackService;
    }

    /** Mỗi 30 giây gửi các callback PENDING. */
    @Scheduled(fixedDelayString = "${app.ticket.scheduler.interval-ms:30000}")
    public void flushDinhKy() {
        callbackService.flush();
    }
}
