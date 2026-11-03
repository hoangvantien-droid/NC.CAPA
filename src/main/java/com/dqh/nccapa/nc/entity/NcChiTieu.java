package com.dqh.nccapa.nc.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;

/** nc_chi_tieu — chỉ tiêu vi phạm (append khi auto-aggregation; DataModel §3.2). */
@Entity
@Table(name = "nc_chi_tieu")
public class NcChiTieu {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nc_so_hieu", nullable = false, length = 32)
    private String ncSoHieu;

    @Column(name = "ma_loi_id", nullable = false, length = 16)
    private String maLoiId;

    @Column(name = "ma_hang", length = 32)
    private String maHang;

    @Column(name = "gia_tri_do", length = 64)
    private String giaTriDo;

    @Column(name = "spec_min", length = 64)
    private String specMin;

    @Column(name = "spec_max", length = 64)
    private String specMax;

    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(name = "in_out_spec")
    private Integer inOutSpec;

    @Column(name = "nguon_su_kien", length = 24)
    private String nguonSuKien;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected NcChiTieu() { }

    public NcChiTieu(String ncSoHieu, String maLoiId, String maHang, Integer inOutSpec, String nguonSuKien) {
        this.ncSoHieu = ncSoHieu;
        this.maLoiId = maLoiId;
        this.maHang = maHang;
        this.inOutSpec = inOutSpec;
        this.nguonSuKien = nguonSuKien;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getNcSoHieu() { return ncSoHieu; }
    public String getMaLoiId() { return maLoiId; }
    public String getMaHang() { return maHang; }
    public Integer getInOutSpec() { return inOutSpec; }
    public String getNguonSuKien() { return nguonSuKien; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setGiaTriDo(String v) { this.giaTriDo = v; }
    public void setSpecMin(String v) { this.specMin = v; }
    public void setSpecMax(String v) { this.specMax = v; }
}
