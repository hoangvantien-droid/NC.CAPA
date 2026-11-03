package com.dqh.nccapa.common.audit;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** audit_log (DataModel §5.1). Append-only. */
@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "doi_tuong", nullable = false, length = 16)
    private String doiTuong;      // NC|CAPA|ACTION

    @Column(name = "doi_tuong_id", nullable = false, length = 64)
    private String doiTuongId;

    @Column(name = "hanh_dong", nullable = false, length = 32)
    private String hanhDong;      // CHUYEN_TRANG_THAI|DOI_MUC|NANG_BAC|DOI_HAN|HUY|...

    @Column(name = "gia_tri_cu", length = 255)
    private String giaTriCu;

    @Column(name = "gia_tri_moi", length = 255)
    private String giaTriMoi;

    @Column(name = "ly_do", length = 512)
    private String lyDo;

    @Column(name = "nguoi_thuc_hien", nullable = false, length = 64)
    private String nguoiThucHien;

    @Column(name = "thuc_hien_luc", nullable = false)
    private LocalDateTime thucHienLuc;

    protected AuditLog() { }

    public AuditLog(String doiTuong, String doiTuongId, String hanhDong,
                    String giaTriCu, String giaTriMoi, String lyDo, String nguoiThucHien) {
        this.doiTuong = doiTuong;
        this.doiTuongId = doiTuongId;
        this.hanhDong = hanhDong;
        this.giaTriCu = giaTriCu;
        this.giaTriMoi = giaTriMoi;
        this.lyDo = lyDo;
        this.nguoiThucHien = nguoiThucHien;
        this.thucHienLuc = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getDoiTuong() { return doiTuong; }
    public String getDoiTuongId() { return doiTuongId; }
    public String getHanhDong() { return hanhDong; }
    public String getGiaTriCu() { return giaTriCu; }
    public String getGiaTriMoi() { return giaTriMoi; }
    public String getLyDo() { return lyDo; }
    public String getNguoiThucHien() { return nguoiThucHien; }
    public LocalDateTime getThucHienLuc() { return thucHienLuc; }
}
