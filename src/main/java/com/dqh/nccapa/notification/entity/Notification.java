package com.dqh.nccapa.notification.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * notification (V12) — outbox thông báo nhắc mốc kiểm chứng CAPA + cảnh báo action quá hạn.
 * App mẫu không có SMTP thật: thông báo lưu PENDING rồi flush() đánh dấu SENT + log
 * (mô phỏng gửi email), giống pattern ticket_callback_outbox. Dùng String cho các cột
 * phân loại cho đơn giản (loai/doi_tuong_loai/trang_thai) — khớp CHÍNH XÁC V12 (ddl-auto validate).
 */
@Entity
@Table(name = "notification")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "loai", nullable = false, length = 32)
    private String loai;            // ACTION_QUA_HAN | MOC_SAP_DEN | MOC_QUA_HAN

    @Column(name = "doi_tuong_loai", nullable = false, length = 16)
    private String doiTuongLoai;    // ACTION | CAPA

    @Column(name = "doi_tuong_id", nullable = false, length = 64)
    private String doiTuongId;

    @Column(name = "nguoi_nhan", length = 64)
    private String nguoiNhan;

    @Column(name = "tieu_de", nullable = false, length = 255)
    private String tieuDe;

    @Column(name = "noi_dung", nullable = false, columnDefinition = "TEXT")
    private String noiDung;

    @Column(name = "trang_thai", nullable = false, length = 16)
    private String trangThai;       // PENDING | SENT

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    protected Notification() { }

    public Notification(String loai, String doiTuongLoai, String doiTuongId,
                        String nguoiNhan, String tieuDe, String noiDung) {
        this.loai = loai;
        this.doiTuongLoai = doiTuongLoai;
        this.doiTuongId = doiTuongId;
        this.nguoiNhan = nguoiNhan;
        this.tieuDe = tieuDe;
        this.noiDung = noiDung;
        this.trangThai = "PENDING";
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getLoai() { return loai; }
    public String getDoiTuongLoai() { return doiTuongLoai; }
    public String getDoiTuongId() { return doiTuongId; }
    public String getNguoiNhan() { return nguoiNhan; }
    public String getTieuDe() { return tieuDe; }
    public String getNoiDung() { return noiDung; }
    public String getTrangThai() { return trangThai; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getSentAt() { return sentAt; }

    /** Đánh dấu đã gửi (mô phỏng gửi email). */
    public void danhDauGui() {
        this.trangThai = "SENT";
        this.sentAt = LocalDateTime.now();
    }
}
