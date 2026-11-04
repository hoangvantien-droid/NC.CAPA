package com.dqh.nccapa.nc.entity;

import com.dqh.nccapa.common.enums.NcSource;
import com.dqh.nccapa.common.enums.NcStatus;
import com.dqh.nccapa.common.enums.Severity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** nc — bảng chính (DataModel §3.1). so_hieu là khóa nghiệp vụ. */
@Entity
@Table(name = "nc")
public class Nc {

    @Id
    @Column(name = "so_hieu", length = 32)
    private String soHieu;

    @Column(name = "loai_nc", nullable = false, length = 24)
    private String loaiNc;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "muc_nghiem_trong", nullable = false, length = 16)
    private Severity mucNghiemTrong;

    @Column(name = "ma_hang_chinh", length = 32)
    private String maHangChinh;

    @Column(name = "ma_loi_chinh", length = 16)
    private String maLoiChinh;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "nguon_phat_sinh", nullable = false, length = 24)
    private NcSource nguonPhatSinh;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "trang_thai", nullable = false, length = 24)
    private NcStatus trangThai;

    @Column(name = "is_escalated", nullable = false)
    private boolean isEscalated;

    @Column(name = "is_food_safety", nullable = false)
    private boolean isFoodSafety;

    @Column(name = "nha_may", nullable = false, length = 16)
    private String nhaMay;

    @Column(name = "lo_kiem", length = 64)
    private String loKiem;

    @Column(name = "lenh_sx", length = 64)
    private String lenhSx;

    @Column(name = "cong_doan", length = 64)
    private String congDoan;

    @Column(name = "ngay_sx")
    private LocalDate ngaySx;

    @Column(name = "khu_vuc", length = 64)
    private String khuVuc;

    @Column(name = "mo_ta_loi", columnDefinition = "TEXT")
    private String moTaLoi;

    @Column(name = "so_luong")
    private BigDecimal soLuong;

    @Column(name = "nguyen_nhan_so_bo", columnDefinition = "TEXT")
    private String nguyenNhanSoBo;

    @Column(name = "disposition_de_xuat", length = 24)
    private String dispositionDeXuat;

    @Column(name = "chi_phi_uoc_tinh")
    private BigDecimal chiPhiUocTinh;

    @Column(name = "bo_phan_thuc_hien", length = 64)
    private String boPhanThucHien;

    @Column(name = "nguoi_de_xuat_disposition", length = 64)
    private String nguoiDeXuatDisposition;

    @Column(name = "ket_qua_xu_ly", columnDefinition = "TEXT")
    private String ketQuaXuLy;

    @Column(name = "bang_chung", length = 255)
    private String bangChung;

    @Column(name = "chi_phi_thuc_te")
    private BigDecimal chiPhiThucTe;

    @Column(name = "ket_luan_tham_tra", columnDefinition = "TEXT")
    private String ketLuanThamTra;

    @Column(name = "da_duyet_cap_1", nullable = false)
    private boolean daDuyetCap1;

    @Column(name = "nguoi_duyet_cap_1", length = 64)
    private String nguoiDuyetCap1;

    @Column(name = "nc_ke_thua_tu_capa_id")
    private Long ncKeThuaTuCapaId;

    @Column(name = "nc_gop_vao_id", length = 32)
    private String ncGopVaoId;

    @Column(name = "ly_do_huy", length = 512)
    private String lyDoHuy;

    @Column(name = "nguoi_bao_cao", nullable = false, length = 64)
    private String nguoiBaoCao;

    @Column(name = "nguoi_phu_trach", length = 64)
    private String nguoiPhuTrach;

    @Column(name = "han_xu_ly")
    private LocalDate hanXuLy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by", nullable = false, length = 64)
    private String createdBy;

    protected Nc() { }

    public Nc(String soHieu, String loaiNc, Severity mucNghiemTrong, NcSource nguonPhatSinh,
              String nhaMay, String nguoiBaoCao, String createdBy) {
        this.soHieu = soHieu;
        this.loaiNc = loaiNc;
        this.mucNghiemTrong = mucNghiemTrong;
        this.nguonPhatSinh = nguonPhatSinh;
        this.nhaMay = nhaMay;
        this.nguoiBaoCao = nguoiBaoCao;
        this.createdBy = createdBy;
        this.trangThai = NcStatus.New;
        this.createdAt = LocalDateTime.now();
    }

    // getters
    public String getSoHieu() { return soHieu; }
    public String getLoaiNc() { return loaiNc; }
    public Severity getMucNghiemTrong() { return mucNghiemTrong; }
    public String getMaHangChinh() { return maHangChinh; }
    public String getMaLoiChinh() { return maLoiChinh; }
    public NcSource getNguonPhatSinh() { return nguonPhatSinh; }
    public NcStatus getTrangThai() { return trangThai; }
    public boolean isEscalated() { return isEscalated; }
    public boolean isFoodSafety() { return isFoodSafety; }
    public String getNhaMay() { return nhaMay; }
    public String getLoKiem() { return loKiem; }
    public BigDecimal getChiPhiUocTinh() { return chiPhiUocTinh; }
    public BigDecimal getChiPhiThucTe() { return chiPhiThucTe; }
    public boolean isDaDuyetCap1() { return daDuyetCap1; }
    public String getNguoiDuyetCap1() { return nguoiDuyetCap1; }
    public String getKetQuaXuLy() { return ketQuaXuLy; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    // mutators dùng bởi service/state machine
    public void setTrangThai(NcStatus t) { this.trangThai = t; }
    public void setMucNghiemTrong(Severity s) { this.mucNghiemTrong = s; }
    public void setEscalated(boolean v) { this.isEscalated = v; }
    public void setFoodSafety(boolean v) { this.isFoodSafety = v; }
    public void setMaHangChinh(String v) { this.maHangChinh = v; }
    public void setMaLoiChinh(String v) { this.maLoiChinh = v; }
    public void setLoKiem(String v) { this.loKiem = v; }
    public void setLenhSx(String v) { this.lenhSx = v; }
    public void setCongDoan(String v) { this.congDoan = v; }
    public void setNgaySx(LocalDate v) { this.ngaySx = v; }
    public void setKhuVuc(String v) { this.khuVuc = v; }
    public void setMoTaLoi(String v) { this.moTaLoi = v; }
    public void setSoLuong(BigDecimal v) { this.soLuong = v; }
    public void setNguyenNhanSoBo(String v) { this.nguyenNhanSoBo = v; }
    public void setDispositionDeXuat(String v) { this.dispositionDeXuat = v; }
    public void setChiPhiUocTinh(BigDecimal v) { this.chiPhiUocTinh = v; }
    public void setBoPhanThucHien(String v) { this.boPhanThucHien = v; }
    public String getNguoiDeXuatDisposition() { return nguoiDeXuatDisposition; }
    public void setNguoiDeXuatDisposition(String v) { this.nguoiDeXuatDisposition = v; }
    public void setKetQuaXuLy(String v) { this.ketQuaXuLy = v; }
    public void setBangChung(String v) { this.bangChung = v; }
    public void setChiPhiThucTe(BigDecimal v) { this.chiPhiThucTe = v; }
    public void setKetLuanThamTra(String v) { this.ketLuanThamTra = v; }
    public void setDaDuyetCap1(boolean v) { this.daDuyetCap1 = v; }
    public void setNguoiDuyetCap1(String v) { this.nguoiDuyetCap1 = v; }
    public Long getNcKeThuaTuCapaId() { return ncKeThuaTuCapaId; }
    public void setNcKeThuaTuCapaId(Long v) { this.ncKeThuaTuCapaId = v; }
    public void setLyDoHuy(String v) { this.lyDoHuy = v; }
    public void setNguoiPhuTrach(String v) { this.nguoiPhuTrach = v; }
    public void setHanXuLy(LocalDate v) { this.hanXuLy = v; }

    /** reset duyệt cấp 1 khi reject/resubmit/nâng bậc (StateMachine N6/N7/§2.2). */
    public void resetDuyetCap1() {
        this.daDuyetCap1 = false;
        this.nguoiDuyetCap1 = null;
    }
}
