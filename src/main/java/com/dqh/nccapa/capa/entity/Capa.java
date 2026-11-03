package com.dqh.nccapa.capa.entity;

import com.dqh.nccapa.common.enums.CapaStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** capa (DataModel §4.1). */
@Entity
@Table(name = "capa")
public class Capa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "trang_thai", nullable = false, length = 16)
    private CapaStatus trangThai;

    @Column(name = "khac_phuc_tuc_thoi", columnDefinition = "TEXT")
    private String khacPhucTucThoi;

    @Column(name = "ngan_tai_dien", columnDefinition = "TEXT")
    private String nganTaiDien;

    @JdbcTypeCode(SqlTypes.TINYINT) @Column(name = "rpn_s") private Integer rpnS;
    @JdbcTypeCode(SqlTypes.TINYINT) @Column(name = "rpn_o") private Integer rpnO;
    @JdbcTypeCode(SqlTypes.TINYINT) @Column(name = "rpn_d") private Integer rpnD;

    @Column(name = "rca_noi_dung", columnDefinition = "TEXT")
    private String rcaNoiDung;

    @Column(name = "nhom_nguyen_nhan", length = 64)
    private String nhomNguyenNhan;

    @Column(name = "ke_hoach_dieu_tra", columnDefinition = "TEXT")
    private String keHoachDieuTra;

    @Column(name = "chi_phi_dau_tu")
    private BigDecimal chiPhiDauTu;

    @Column(name = "x_ngay") private Integer xNgay;
    @Column(name = "n_lo")   private Integer nLo;

    @Column(name = "dem_gop_nlo", nullable = false)
    private boolean demGopNlo;

    @Column(name = "is_8d", nullable = false)
    private boolean is8d;

    @Column(name = "co_yeu_cau_phap_ly", nullable = false)
    private boolean coYeuCauPhapLy;

    @Column(name = "handover_done", nullable = false)
    private boolean handoverDone;

    @Column(name = "monitoring_bat_dau")
    private LocalDateTime monitoringBatDau;

    @Column(name = "nguoi_dieu_tra", length = 64)
    private String nguoiDieuTra;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by", nullable = false, length = 64)
    private String createdBy;

    protected Capa() { }

    public Capa(String createdBy) {
        this.trangThai = CapaStatus.Open;
        this.createdBy = createdBy;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public CapaStatus getTrangThai() { return trangThai; }
    public String getKhacPhucTucThoi() { return khacPhucTucThoi; }
    public String getNganTaiDien() { return nganTaiDien; }
    public String getRcaNoiDung() { return rcaNoiDung; }
    public String getNhomNguyenNhan() { return nhomNguyenNhan; }
    public String getKeHoachDieuTra() { return keHoachDieuTra; }
    public BigDecimal getChiPhiDauTu() { return chiPhiDauTu; }
    public Integer getXNgay() { return xNgay; }
    public Integer getNLo() { return nLo; }
    public boolean isDemGopNlo() { return demGopNlo; }
    public boolean isHandoverDone() { return handoverDone; }
    public LocalDateTime getMonitoringBatDau() { return monitoringBatDau; }
    public Integer getRpnS() { return rpnS; }
    public Integer getRpnO() { return rpnO; }
    public Integer getRpnD() { return rpnD; }

    public void setTrangThai(CapaStatus t) { this.trangThai = t; }
    public void setKhacPhucTucThoi(String v) { this.khacPhucTucThoi = v; }
    public void setNganTaiDien(String v) { this.nganTaiDien = v; }
    public void setRpn(Integer s, Integer o, Integer d) { this.rpnS = s; this.rpnO = o; this.rpnD = d; }
    public void setRcaNoiDung(String v) { this.rcaNoiDung = v; }
    public void setNhomNguyenNhan(String v) { this.nhomNguyenNhan = v; }
    public void setKeHoachDieuTra(String v) { this.keHoachDieuTra = v; }
    public void setChiPhiDauTu(BigDecimal v) { this.chiPhiDauTu = v; }
    public void setXNgay(Integer v) { this.xNgay = v; }
    public void setNLo(Integer v) { this.nLo = v; }
    public void setDemGopNlo(boolean v) { this.demGopNlo = v; }
    public void setIs8d(boolean v) { this.is8d = v; }
    public void setCoYeuCauPhapLy(boolean v) { this.coYeuCauPhapLy = v; }
    public void setHandoverDone(boolean v) { this.handoverDone = v; }
    public void setMonitoringBatDau(LocalDateTime v) { this.monitoringBatDau = v; }
    public void setNguoiDieuTra(String v) { this.nguoiDieuTra = v; }

    /** RPN = s*o*d; phân loại ở service (REQ mục 3.2). */
    public Integer rpn() {
        if (rpnS == null || rpnO == null || rpnD == null) return null;
        return rpnS * rpnO * rpnD;
    }
}
