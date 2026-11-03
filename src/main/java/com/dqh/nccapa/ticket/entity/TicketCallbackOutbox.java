package com.dqh.nccapa.ticket.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** ticket_callback_outbox (V9) — hàng đợi callback ra hệ ticket, có retry + dead-letter. */
@Entity
@Table(name = "ticket_callback_outbox")
public class TicketCallbackOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_id", nullable = false, length = 64)
    private String ticketId;

    @Column(name = "callback_type", nullable = false, length = 16)
    private String callbackType;   // early_response | case_closed

    @Column(name = "event_id", nullable = false, length = 64)
    private String eventId;

    @Column(name = "nc_so_hieu", length = 32)
    private String ncSoHieu;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "trang_thai", nullable = false, length = 16)
    private String trangThai;      // PENDING | SENT | DEAD_LETTER

    @Column(name = "so_lan_thu", nullable = false)
    private int soLanThu;

    @Column(name = "thu_ke_tiep_luc")
    private LocalDateTime thuKeTiepLuc;

    @Column(name = "gui_luc")
    private LocalDateTime guiLuc;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected TicketCallbackOutbox() { }

    public TicketCallbackOutbox(String ticketId, String callbackType, String eventId,
                                String ncSoHieu, String payload) {
        this.ticketId = ticketId;
        this.callbackType = callbackType;
        this.eventId = eventId;
        this.ncSoHieu = ncSoHieu;
        this.payload = payload;
        this.trangThai = "PENDING";
        this.soLanThu = 0;
        this.thuKeTiepLuc = LocalDateTime.now();
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getTicketId() { return ticketId; }
    public String getCallbackType() { return callbackType; }
    public String getEventId() { return eventId; }
    public String getPayload() { return payload; }
    public String getTrangThai() { return trangThai; }
    public int getSoLanThu() { return soLanThu; }
    public LocalDateTime getThuKeTiepLuc() { return thuKeTiepLuc; }

    public void danhDauGui() {
        this.trangThai = "SENT";
        this.guiLuc = LocalDateTime.now();
    }

    public void ghiNhanThatBai(int delaySeconds, int maxRetry) {
        this.soLanThu++;
        if (this.soLanThu >= maxRetry) {
            this.trangThai = "DEAD_LETTER";
        } else {
            this.thuKeTiepLuc = LocalDateTime.now().plusSeconds(delaySeconds);
        }
    }
}
