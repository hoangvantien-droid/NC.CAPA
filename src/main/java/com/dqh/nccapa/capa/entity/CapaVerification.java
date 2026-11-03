package com.dqh.nccapa.capa.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** capa_verification — kiểm chứng 2 mốc + đếm số lần dời hạn (DataModel §4.4). */
@Entity
@Table(name = "capa_verification")
public class CapaVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "capa_id", nullable = false)
    private Long capaId;

    @Column(name = "moc_x_dat_luc")
    private LocalDateTime mocXDatLuc;

    @Column(name = "moc_n_dat_luc")
    private LocalDateTime mocNDatLuc;

    @Column(name = "han_kiem_chung_tiep_theo")
    private LocalDateTime hanKiemChungTiepTheo;

    @Column(name = "so_lan_doi_han", nullable = false)
    private int soLanDoiHan;

    @Column(name = "bien_ban_kiem_tra", length = 255)
    private String bienBanKiemTra;

    @Column(name = "ket_qua", length = 16)
    private String ketQua;   // HIEU_QUA | KHONG_HIEU_QUA

    @Column(name = "xac_nhan_boi", length = 64)
    private String xacNhanBoi;

    @Column(name = "xac_nhan_luc")
    private LocalDateTime xacNhanLuc;

    protected CapaVerification() { }

    public CapaVerification(Long capaId) {
        this.capaId = capaId;
        this.soLanDoiHan = 0;
    }

    public Long getId() { return id; }
    public Long getCapaId() { return capaId; }
    public LocalDateTime getHanKiemChungTiepTheo() { return hanKiemChungTiepTheo; }
    public int getSoLanDoiHan() { return soLanDoiHan; }
    public String getBienBanKiemTra() { return bienBanKiemTra; }
    public String getKetQua() { return ketQua; }

    public void setHanKiemChungTiepTheo(LocalDateTime v) { this.hanKiemChungTiepTheo = v; }
    public void setSoLanDoiHan(int v) { this.soLanDoiHan = v; }
    public void setBienBanKiemTra(String v) { this.bienBanKiemTra = v; }
    public void setMocXDatLuc(LocalDateTime v) { this.mocXDatLuc = v; }
    public void setMocNDatLuc(LocalDateTime v) { this.mocNDatLuc = v; }
    public void setKetQua(String v) { this.ketQua = v; }
    public void setXacNhanBoi(String v) { this.xacNhanBoi = v; }
    public void setXacNhanLuc(LocalDateTime v) { this.xacNhanLuc = v; }
}
