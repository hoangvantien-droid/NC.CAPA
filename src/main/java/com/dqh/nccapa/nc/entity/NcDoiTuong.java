package com.dqh.nccapa.nc.entity;

import jakarta.persistence.*;

/** nc_doi_tuong — đối tượng ảnh hưởng, line-item khoanh vùng (DataModel §3.3). */
@Entity
@Table(name = "nc_doi_tuong")
public class NcDoiTuong {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nc_so_hieu", nullable = false, length = 32)
    private String ncSoHieu;

    @Column(name = "loai_doi_tuong", nullable = false, length = 16)
    private String loaiDoiTuong;   // LO|MA_HANG|LENH_SX|THIET_BI|NCC|DIEM_LAY_MAU

    @Column(name = "gia_tri", nullable = false, length = 64)
    private String giaTri;

    @Column(name = "khoanh_vung", nullable = false)
    private boolean khoanhVung;

    @Column(name = "trang_thai_dong_lo", length = 16)
    private String trangThaiDongLo;   // Held|Released|Scrapped|Reworked

    @Column(name = "chung_tu_hold", length = 255)
    private String chungTuHold;

    @Column(name = "chung_tu_unhold", length = 255)
    private String chungTuUnhold;

    protected NcDoiTuong() { }

    public NcDoiTuong(String ncSoHieu, String loaiDoiTuong, String giaTri) {
        this.ncSoHieu = ncSoHieu;
        this.loaiDoiTuong = loaiDoiTuong;
        this.giaTri = giaTri;
    }

    public Long getId() { return id; }
    public String getNcSoHieu() { return ncSoHieu; }
    public String getLoaiDoiTuong() { return loaiDoiTuong; }
    public String getGiaTri() { return giaTri; }
    public boolean isKhoanhVung() { return khoanhVung; }
    public String getTrangThaiDongLo() { return trangThaiDongLo; }
    public String getChungTuHold() { return chungTuHold; }
    public String getChungTuUnhold() { return chungTuUnhold; }

    public void setKhoanhVung(boolean v) { this.khoanhVung = v; }
    public void setTrangThaiDongLo(String v) { this.trangThaiDongLo = v; }
    public void setChungTuHold(String v) { this.chungTuHold = v; }
    public void setChungTuUnhold(String v) { this.chungTuUnhold = v; }

    /** Trạng thái cuối hợp lệ cho dòng lô (guard N9). */
    public boolean coTrangThaiCuoi() {
        return "Released".equals(trangThaiDongLo)
                || "Scrapped".equals(trangThaiDongLo)
                || "Reworked".equals(trangThaiDongLo);
    }
}
