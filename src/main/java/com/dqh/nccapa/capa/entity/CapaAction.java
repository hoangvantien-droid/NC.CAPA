package com.dqh.nccapa.capa.entity;

import com.dqh.nccapa.common.enums.ActionCategory;
import com.dqh.nccapa.common.enums.ActionStatus;
import com.dqh.nccapa.common.enums.ActionType;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDate;

/** capa_action (DataModel §4.3). */
@Entity
@Table(name = "capa_action")
public class CapaAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "capa_id", nullable = false)
    private Long capaId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "loai", nullable = false, length = 16)
    private ActionType loai;

    @Column(name = "mo_ta", nullable = false, columnDefinition = "TEXT")
    private String moTa;

    @Column(name = "nguoi_phu_trach", nullable = false, length = 64)
    private String nguoiPhuTrach;

    @Column(name = "deadline", nullable = false)
    private LocalDate deadline;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "trang_thai", nullable = false, length = 16)
    private ActionStatus trangThai;

    @Column(name = "bang_chung", length = 255)
    private String bangChung;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "loai_hanh_dong", nullable = false, length = 16)
    private ActionCategory loaiHanhDong;

    protected CapaAction() { }

    public CapaAction(Long capaId, ActionType loai, String moTa, String nguoiPhuTrach, LocalDate deadline) {
        this.capaId = capaId;
        this.loai = loai;
        this.moTa = moTa;
        this.nguoiPhuTrach = nguoiPhuTrach;
        this.deadline = deadline;
        this.trangThai = ActionStatus.Open;
        this.loaiHanhDong = ActionCategory.CORRECTIVE;
    }

    public Long getId() { return id; }
    public Long getCapaId() { return capaId; }
    public ActionType getLoai() { return loai; }
    public String getMoTa() { return moTa; }
    public String getNguoiPhuTrach() { return nguoiPhuTrach; }
    public LocalDate getDeadline() { return deadline; }
    public ActionStatus getTrangThai() { return trangThai; }
    public String getBangChung() { return bangChung; }
    public ActionCategory getLoaiHanhDong() { return loaiHanhDong; }

    public void setDeadline(LocalDate v) { this.deadline = v; }
    public void setTrangThai(ActionStatus v) { this.trangThai = v; }
    public void setBangChung(String v) { this.bangChung = v; }
    public void setLoaiHanhDong(ActionCategory v) { this.loaiHanhDong = v; }
}
