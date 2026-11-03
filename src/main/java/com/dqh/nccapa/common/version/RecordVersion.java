package com.dqh.nccapa.common.version;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * record_version (V13) — snapshot bản ghi đã xác nhận trước khi bị sửa (REQ mục 7, invariant I7).
 * Cơ chế TỔNG QUÁT: mỗi lần sửa một bản ghi đã xác nhận, lưu một dòng chứa JSON của giá trị CŨ,
 * liên kết qua (doi_tuong_loai, doi_tuong_id, phien_ban). BỔ SUNG cho audit_log, không thay thế.
 */
@Entity
@Table(name = "record_version")
public class RecordVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "doi_tuong_loai", nullable = false, length = 24)
    private String doiTuongLoai;   // ví dụ CAPA_VERIFICATION

    @Column(name = "doi_tuong_id", nullable = false, length = 64)
    private String doiTuongId;

    @Column(name = "phien_ban", nullable = false)
    private Integer phienBan;      // bắt đầu 1, tăng dần

    @Column(name = "snapshot_json", nullable = false, columnDefinition = "TEXT")
    private String snapshotJson;   // giá trị bản CŨ dạng JSON

    @Column(name = "ly_do", length = 512)
    private String lyDo;

    @Column(name = "nguoi_thuc_hien", nullable = false, length = 64)
    private String nguoiThucHien;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected RecordVersion() { }

    public RecordVersion(String doiTuongLoai, String doiTuongId, Integer phienBan,
                         String snapshotJson, String lyDo, String nguoiThucHien) {
        this.doiTuongLoai = doiTuongLoai;
        this.doiTuongId = doiTuongId;
        this.phienBan = phienBan;
        this.snapshotJson = snapshotJson;
        this.lyDo = lyDo;
        this.nguoiThucHien = nguoiThucHien;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getDoiTuongLoai() { return doiTuongLoai; }
    public String getDoiTuongId() { return doiTuongId; }
    public Integer getPhienBan() { return phienBan; }
    public String getSnapshotJson() { return snapshotJson; }
    public String getLyDo() { return lyDo; }
    public String getNguoiThucHien() { return nguoiThucHien; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
