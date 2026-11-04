package com.dqh.nccapa.masterdata.entity;

import com.dqh.nccapa.common.enums.HazardType;
import com.dqh.nccapa.common.enums.NKiemChungGroup;
import com.dqh.nccapa.common.enums.Severity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDate;

/** ma_loi — bộ mã lỗi cấp 2 (APPX; DataModel §2.4). */
@Entity
@Table(name = "ma_loi")
public class MaLoi {

    @Id
    @Column(name = "ma_loi_id", length = 16)
    private String maLoiId;

    @Column(name = "nhom_loi_id", nullable = false, length = 8)
    private String nhomLoiId;

    @Column(name = "ten_ma_loi", nullable = false)
    private String tenMaLoi;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "muc_nghiem_trong_default", nullable = false, length = 16)
    private Severity mucNghiemTrongDefault;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "moi_nguy_default", nullable = false, length = 16)
    private HazardType moiNguyDefault;

    @Column(name = "bat_buoc_khoanh_vung", nullable = false)
    private boolean batBuocKhoanhVung;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "nhom_n_kiem_chung", nullable = false, length = 24)
    private NKiemChungGroup nhomNKiemChung;

    @Column(name = "nguoi_so_huu", nullable = false, length = 64)
    private String nguoiSoHuu = "QA_TAP_DOAN";

    @Column(name = "hieu_luc_tu", nullable = false)
    private LocalDate hieuLucTu;

    @Column(name = "hieu_luc_den")
    private LocalDate hieuLucDen;

    protected MaLoi() { }

    public String getMaLoiId() { return maLoiId; }
    public String getNhomLoiId() { return nhomLoiId; }
    public String getTenMaLoi() { return tenMaLoi; }
    public Severity getMucNghiemTrongDefault() { return mucNghiemTrongDefault; }
    public HazardType getMoiNguyDefault() { return moiNguyDefault; }
    public boolean isBatBuocKhoanhVung() { return batBuocKhoanhVung; }
    public NKiemChungGroup getNhomNKiemChung() { return nhomNKiemChung; }
    public String getNguoiSoHuu() { return nguoiSoHuu; }
    public LocalDate getHieuLucTu() { return hieuLucTu; }
    public LocalDate getHieuLucDen() { return hieuLucDen; }

    public boolean conHieuLuc(LocalDate at) {
        return !at.isBefore(hieuLucTu) && (hieuLucDen == null || !at.isAfter(hieuLucDen));
    }
}
