package com.dqh.nccapa.capa.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** capa_8d_step — một bước D0..D8 của một CAPA ở chế độ 8D (V8). */
@Entity
@Table(name = "capa_8d_step")
public class Capa8dStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "capa_id", nullable = false)
    private Long capaId;

    @Column(name = "buoc", nullable = false, length = 4)
    private String buoc;   // D0..D8

    @Column(name = "noi_dung", columnDefinition = "TEXT")
    private String noiDung;

    @Column(name = "hoan_thanh", nullable = false)
    private boolean hoanThanh;

    @Column(name = "nguoi_thuc_hien", length = 64)
    private String nguoiThucHien;

    @Column(name = "hoan_thanh_luc")
    private LocalDateTime hoanThanhLuc;

    protected Capa8dStep() { }

    public Capa8dStep(Long capaId, String buoc) {
        this.capaId = capaId;
        this.buoc = buoc;
        this.hoanThanh = false;
    }

    public Long getId() { return id; }
    public Long getCapaId() { return capaId; }
    public String getBuoc() { return buoc; }
    public boolean isHoanThanh() { return hoanThanh; }

    public void hoanThanh(String noiDung, String nguoi) {
        this.noiDung = noiDung;
        this.hoanThanh = true;
        this.nguoiThucHien = nguoi;
        this.hoanThanhLuc = LocalDateTime.now();
    }
}
