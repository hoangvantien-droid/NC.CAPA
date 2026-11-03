package com.dqh.nccapa.attachment.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** attachment — file/ảnh đính kèm (bằng chứng) cho NC hoặc CAPA action (Nhóm 2, migration V11). */
@Entity
@Table(name = "attachment")
public class Attachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "doi_tuong", nullable = false, length = 16)
    private String doiTuong;      // NC | ACTION

    @Column(name = "doi_tuong_id", nullable = false, length = 64)
    private String doiTuongId;    // so_hieu NC hoặc id action

    @Column(name = "ten_file", nullable = false, length = 255)
    private String tenFile;       // tên gốc

    @Column(name = "ten_luu", nullable = false, length = 255)
    private String tenLuu;        // tên file trên disk (duy nhất)

    @Column(name = "content_type", length = 128)
    private String contentType;

    @Column(name = "kich_thuoc", nullable = false)
    private long kichThuoc;

    @Column(name = "nguoi_tai", length = 64)
    private String nguoiTai;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected Attachment() { }

    public Attachment(String doiTuong, String doiTuongId, String tenFile, String tenLuu,
                      String contentType, long kichThuoc, String nguoiTai, LocalDateTime createdAt) {
        this.doiTuong = doiTuong;
        this.doiTuongId = doiTuongId;
        this.tenFile = tenFile;
        this.tenLuu = tenLuu;
        this.contentType = contentType;
        this.kichThuoc = kichThuoc;
        this.nguoiTai = nguoiTai;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public String getDoiTuong() { return doiTuong; }
    public String getDoiTuongId() { return doiTuongId; }
    public String getTenFile() { return tenFile; }
    public String getTenLuu() { return tenLuu; }
    public String getContentType() { return contentType; }
    public long getKichThuoc() { return kichThuoc; }
    public String getNguoiTai() { return nguoiTai; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
