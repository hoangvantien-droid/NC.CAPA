package com.dqh.nccapa.pr.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** pr_approval — lịch sử duyệt từng cấp (V7). */
@Entity
@Table(name = "pr_approval")
public class PrApproval {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pr_id", nullable = false)
    private Long prId;

    @Column(name = "cap", nullable = false)
    private int cap;

    @Column(name = "vai_tro", nullable = false, length = 32)
    private String vaiTro;

    @Column(name = "ket_qua", nullable = false, length = 16)
    private String ketQua;   // APPROVED | REJECTED

    @Column(name = "nguoi_duyet", nullable = false, length = 64)
    private String nguoiDuyet;

    @Column(name = "ly_do", length = 512)
    private String lyDo;

    @Column(name = "duyet_luc", nullable = false)
    private LocalDateTime duyetLuc;

    protected PrApproval() { }

    public PrApproval(Long prId, int cap, String vaiTro, String ketQua, String nguoiDuyet, String lyDo) {
        this.prId = prId;
        this.cap = cap;
        this.vaiTro = vaiTro;
        this.ketQua = ketQua;
        this.nguoiDuyet = nguoiDuyet;
        this.lyDo = lyDo;
        this.duyetLuc = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public int getCap() { return cap; }
    public String getVaiTro() { return vaiTro; }
    public String getKetQua() { return ketQua; }
    public String getNguoiDuyet() { return nguoiDuyet; }
}
