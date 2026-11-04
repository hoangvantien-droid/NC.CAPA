package com.dqh.nccapa.nc.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * Body POST /nc/{so}/confirm-done (N8) — chi phí thực tế bắt buộc.
 * Cần ít nhất một trong: ketQuaThucTe (mô tả kết quả) hoặc bangChung (số chứng từ/ghi chú); service kiểm tra.
 */
public class ConfirmDoneRequest {
    public String bangChung;
    public String ketQuaThucTe;   // kết quả thực tế sau xử lý
    @NotNull  public BigDecimal chiPhiThucTe;
    @jakarta.validation.constraints.NotBlank public String actor;
}