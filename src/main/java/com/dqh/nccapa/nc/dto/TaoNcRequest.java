package com.dqh.nccapa.nc.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Body POST /nc (API §3.1). */
public class TaoNcRequest {

    @NotBlank public String loaiNc;
    @NotNull  public com.dqh.nccapa.common.enums.NcSource nguonPhatSinh;
    @NotBlank public String nhaMay;
    @NotBlank public String nguoiBaoCao;

    public LocalDate ngaySx;
    public String loKiem;
    public String lenhSx;
    public String congDoan;
    public String khuVuc;
    public String moTaLoi;
    public BigDecimal soLuong;
    public String nguyenNhanSoBo;
    public List<String> ticketIds;

    @NotEmpty public List<ChiTieuItem> chiTieu;
    public List<DoiTuongItem> doiTuong;

    public static class ChiTieuItem {
        @NotBlank public String maLoiId;
        public String maHang;
        public Integer inOutSpec;
    }

    public static class DoiTuongItem {
        @NotBlank public String loaiDoiTuong;  // LO|MA_HANG|...
        @NotBlank public String giaTri;
    }
}
