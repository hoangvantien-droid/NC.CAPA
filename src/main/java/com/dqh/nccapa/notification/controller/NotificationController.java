package com.dqh.nccapa.notification.controller;

import com.dqh.nccapa.common.error.ApiResponse;
import com.dqh.nccapa.notification.entity.Notification;
import com.dqh.nccapa.notification.service.NotificationService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Điều khiển/kiểm hệ thống thông báo (demo). Liệt kê thông báo gần đây,
 * và cho phép kích hoạt quét sinh + flush thủ công (thực tế do scheduler chạy định kỳ).
 */
@RestController
@RequestMapping("/api/v1/notification")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ApiResponse<List<Notification>> ganDay() {
        return ApiResponse.ok(notificationService.ganDay());
    }

    @PostMapping("/scan")
    public ApiResponse<Integer> quet() {
        return ApiResponse.ok(notificationService.quetVaSinh());
    }

    @PostMapping("/flush")
    public ApiResponse<Integer> flush() {
        return ApiResponse.ok(notificationService.flush());
    }
}
