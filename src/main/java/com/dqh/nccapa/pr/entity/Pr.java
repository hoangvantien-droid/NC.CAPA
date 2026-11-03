package com.dqh.nccapa.pr.entity;

import com.dqh.nccapa.common.enums.LoaiThayDoi;
import com.dqh.nccapa.common.enums.PrStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;

/** pr (DataModel §6 + V7). */
@Entity
@Table(name = "pr")
public class Pr {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "loai_thay_doi", nullable = false, length = 32)
    private LoaiThayDoi loaiThayDoi;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "trang_thai", nullable = false, length = 16)
    private PrStatus trangThai;

    @Column(name = "ma_hang", length = 32)
    private String maHang;

    @Column(name = "ma_loi_id", length = 16)
    private String maLoiId;

    @Column(name = "cap_hien_tai", nullable = false)
    private int capHienTai;

    @Column(name = "ly_do_tu_choi", length = 512)
    private String lyDoTuChoi;

    @Column(name = "created_by", length = 64)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected Pr() { }

    public Pr(LoaiThayDoi loaiThayDoi, String maHang, String maLoiId, String createdBy) {
        this.loaiThayDoi = loaiThayDoi;
        this.maHang = maHang;
        this.maLoiId = maLoiId;
        this.createdBy = createdBy;
        this.trangThai = PrStatus.Draft;
        this.capHienTai = 0;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public LoaiThayDoi getLoaiThayDoi() { return loaiThayDoi; }
    public PrStatus getTrangThai() { return trangThai; }
    public String getMaHang() { return maHang; }
    public String getMaLoiId() { return maLoiId; }
    public int getCapHienTai() { return capHienTai; }
    public String getLyDoTuChoi() { return lyDoTuChoi; }

    public void setTrangThai(PrStatus t) { this.trangThai = t; }
    public void setCapHienTai(int c) { this.capHienTai = c; }
    public void setLyDoTuChoi(String v) { this.lyDoTuChoi = v; }
}
