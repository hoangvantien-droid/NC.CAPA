package com.dqh.nccapa.ticket.service;

import com.dqh.nccapa.common.audit.AuditService;
import com.dqh.nccapa.config.AppProperties;
import com.dqh.nccapa.ticket.entity.TicketCallbackOutbox;
import com.dqh.nccapa.ticket.repository.TicketCallbackOutboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Callback ticket 2 chiều (REQ mục 6, Đợt c).
 * - Ghi outbox idempotent theo (ticket_id + callback_type + event_id).
 * - Gửi cho MỌI ticket gắn NC; early_response trước case_closed (bảo đảm thứ tự khi flush theo id tăng dần).
 * - Retry theo lịch, quá maxRetry → DEAD_LETTER.
 */
@Service
public class TicketCallbackService {

    private final TicketCallbackOutboxRepository outboxRepo;
    private final JdbcTemplate jdbc;
    private final TicketSender sender;
    private final AppProperties appProps;
    private final AuditService audit;
    private final ObjectMapper om = new ObjectMapper();

    public TicketCallbackService(TicketCallbackOutboxRepository outboxRepo, JdbcTemplate jdbc,
                                 TicketSender sender, AppProperties appProps, AuditService audit) {
        this.outboxRepo = outboxRepo;
        this.jdbc = jdbc;
        this.sender = sender;
        this.appProps = appProps;
        this.audit = audit;
    }

    /** Lên lịch callback cho mọi ticket gắn với NC. Idempotent theo event_id. */
    @Transactional
    public void enqueueChoNc(String ncSoHieu, String callbackType, String eventId, Map<String, Object> extra) {
        List<String> ticketIds = jdbc.queryForList(
                "SELECT ticket_id FROM nc_ticket WHERE nc_so_hieu = ?", String.class, ncSoHieu);
        for (String ticketId : ticketIds) {
            enqueue(ticketId, callbackType, eventId, ncSoHieu, extra);
        }
    }

    @Transactional
    public TicketCallbackOutbox enqueue(String ticketId, String callbackType, String eventId,
                                        String ncSoHieu, Map<String, Object> extra) {
        var existing = outboxRepo.findByTicketIdAndCallbackTypeAndEventId(ticketId, callbackType, eventId);
        if (existing.isPresent()) return existing.get();  // idempotent: không tạo trùng

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ticket_id", ticketId);
        body.put("callback_type", callbackType);
        body.put("event_id", eventId);
        body.put("nc_number", ncSoHieu);
        body.put("sent_at", LocalDateTime.now().toString());
        if (extra != null) body.putAll(extra);

        TicketCallbackOutbox o = new TicketCallbackOutbox(ticketId, callbackType, eventId, ncSoHieu, toJson(body));
        outboxRepo.save(o);
        audit.log("TICKET", ticketId, "ENQUEUE_CALLBACK", null, callbackType, "event " + eventId, "SYSTEM");
        return o;
    }

    /**
     * Gửi các callback PENDING tới hạn. Lấy theo id tăng dần → early_response (enqueue trước)
     * luôn gửi trước case_closed của cùng NC.
     * @return số callback gửi thành công trong lần flush này.
     */
    @Transactional
    public int flush() {
        List<TicketCallbackOutbox> pending = outboxRepo.findByTrangThaiOrderByIdAsc("PENDING");
        int sent = 0;
        LocalDateTime now = LocalDateTime.now();
        for (TicketCallbackOutbox o : pending) {
            if (o.getThuKeTiepLuc() != null && o.getThuKeTiepLuc().isAfter(now)) continue; // chưa tới hạn
            boolean ok = sender.send(appProps.getTicket().getCallbackUrl(), o.getPayload());
            if (ok) {
                o.danhDauGui();
                audit.log("TICKET", o.getTicketId(), "CALLBACK_SENT", null, o.getCallbackType(), null, "SYSTEM");
                sent++;
            } else {
                o.ghiNhanThatBai(appProps.getTicket().getRetryDelaySeconds(), appProps.getTicket().getMaxRetry());
                if ("DEAD_LETTER".equals(o.getTrangThai())) {
                    audit.log("TICKET", o.getTicketId(), "CALLBACK_DEAD_LETTER", null, o.getCallbackType(),
                            "quá " + appProps.getTicket().getMaxRetry() + " lần", "SYSTEM");
                }
            }
            outboxRepo.save(o);
        }
        return sent;
    }

    private String toJson(Map<String, Object> m) {
        try { return om.writeValueAsString(m); }
        catch (Exception e) { return "{}"; }
    }
}
