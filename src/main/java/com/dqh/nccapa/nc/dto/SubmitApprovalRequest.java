package com.dqh.nccapa.nc.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * Body POST /nc/{so}/submit-approval (N3) — đủ trường luồng Nặng.
 * Cần ít nhất MỘT trong hai: nguyenNhanDieuTra (nguyên nhân sau điều tra, có thể khác sơ bộ)
 * hoặc nguyenNhanSoBo (tương thích cũ); service kiểm tra.
 */
public class SubmitApprovalRequest {
    public String nguyenNhanSoBo;                // tuỳ chọn: không gửi thì giữ giá trị lúc tạo NC
    public String nguyenNhanDieuTra;             // nguyên nhân sau điều tra của QA
    public String giaiPhapXuLy;                  // giải pháp / ghi chú xử lý (tuỳ chọn)
    @NotBlank public String dispositionDeXuat;   // REWORK|RETURN_NCC|HA_CAP|TAI_CHE|TIEU_HUY
    @NotNull  public BigDecimal chiPhiUocTinh;
    @NotBlank public String boPhanThucHien;
    @NotBlank public String actor;               // người đề xuất disposition (SoD §2.2)
}