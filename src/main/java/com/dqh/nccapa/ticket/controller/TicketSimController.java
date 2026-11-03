package com.dqh.nccapa.ticket.controller;

import com.dqh.nccapa.common.error.ApiResponse;
import com.dqh.nccapa.ticket.entity.TicketCallbackOutbox;
import com.dqh.nccapa.ticket.repository.TicketCallbackOutboxRepository;
import com.dqh.nccapa.ticket.service.TicketCallbackService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Mô phỏng hệ ticket + điều khiển outbox (demo/test Đợt c).
 * /ticket-sim/callback đóng vai endpoint hệ ticket nhận callback.
 * /ticket/flush kích hoạt gửi các callback PENDING (thực tế sẽ là job định kỳ).
 */
@RestController
@RequestMapping("/api/v1")
public class TicketSimController {

    private final TicketCallbackService callbackService;
    private final TicketCallbackOutboxRepository outboxRepo;

    /** Bộ nhớ ghi nhận callback đã nhận (demo). */
    public static final ConcurrentLinkedQueue<Map<String, Object>> DA_NHAN = new ConcurrentLinkedQueue<>();

    public TicketSimController(TicketCallbackService callbackService,
                               TicketCallbackOutboxRepository outboxRepo) {
        this.callbackService = callbackService;
        this.outboxRepo = outboxRepo;
    }

    @PostMapping("/ticket-sim/callback")
    public ApiResponse<String> nhanCallback(@RequestBody Map<String, Object> body) {
        DA_NHAN.add(body);
        return ApiResponse.ok("received");
    }

    @PostMapping("/ticket/flush")
    public ApiResponse<Integer> flush() {
        return ApiResponse.ok(callbackService.flush());
    }

    @GetMapping("/ticket/outbox")
    public ApiResponse<List<TicketCallbackOutbox>> outbox(@RequestParam("ticketId") String ticketId) {
        return ApiResponse.ok(outboxRepo.findByTicketIdOrderByIdAsc(ticketId));
    }
}
