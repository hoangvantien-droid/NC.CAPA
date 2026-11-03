package com.dqh.nccapa.nc.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** Body POST /nc/{so}/submit-approval (N3) — đủ trường luồng Nặng. */
public class SubmitApprovalRequest {
    @NotBlank public String nguyenNhanSoBo;
    @NotBlank public String dispositionDeXuat;   // REWORK|RETURN_NCC|HA_CAP|TAI_CHE|TIEU_HUY
    @NotNull  public BigDecimal chiPhiUocTinh;
    @NotBlank public String boPhanThucHien;
    @NotBlank public String actor;               // người đề xuất disposition (SoD §2.2)
}
