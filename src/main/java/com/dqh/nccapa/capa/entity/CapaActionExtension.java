package com.dqh.nccapa.capa.entity;

import com.dqh.nccapa.common.enums.ExtensionStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** capa_action_extension — yêu cầu gia hạn action, cần duyệt (DataModel §4.3). */
@Entity
@Table(name = "capa_action_extension")
public class CapaActionExtension {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "action_id", nullable = false)
    private Long actionId;

    @Column(name = "deadline_moi", nullable = false)
    private LocalDate deadlineMoi;

    @Column(name = "ly_do", nullable = false, length = 512)
    private String lyDo;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "trang_thai", nullable = false, length = 16)
    private ExtensionStatus trangThai;

    @Column(name = "nguoi_yeu_cau", nullable = false, length = 64)
    private String nguoiYeuCau;

    @Column(name = "nguoi_duyet", length = 64)
    private String nguoiDuyet;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected CapaActionExtension() { }

    public CapaActionExtension(Long actionId, LocalDate deadlineMoi, String lyDo, String nguoiYeuCau) {
        this.actionId = actionId;
        this.deadlineMoi = deadlineMoi;
        this.lyDo = lyDo;
        this.nguoiYeuCau = nguoiYeuCau;
        this.trangThai = ExtensionStatus.PENDING;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getActionId() { return actionId; }
    public String getLyDo() { return lyDo; }
    public String getNguoiDuyet() { return nguoiDuyet; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDate getDeadlineMoi() { return deadlineMoi; }
    public ExtensionStatus getTrangThai() { return trangThai; }
    public String getNguoiYeuCau() { return nguoiYeuCau; }

    public void setTrangThai(ExtensionStatus v) { this.trangThai = v; }
    public void setNguoiDuyet(String v) { this.nguoiDuyet = v; }
}
