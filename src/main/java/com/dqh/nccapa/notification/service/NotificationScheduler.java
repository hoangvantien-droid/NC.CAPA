package com.dqh.nccapa.notification.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Định kỳ quét sinh thông báo rồi flush (mô phỏng gửi email).
 * Tắt ở profile test (app.notification.scheduler.enabled=false trong application-test.yml)
 * để test gọi NotificationService trực tiếp, deterministic, không bị flaky vì wall-clock.
 */
@Component
@ConditionalOnProperty(name = "app.notification.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class NotificationScheduler {

    private final NotificationService notificationService;

    public NotificationScheduler(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Scheduled(fixedDelayString = "${app.notification.scheduler.interval-ms:60000}")
    public void chayDinhKy() {
        notificationService.quetVaSinh();
        notificationService.flush();
    }
}
