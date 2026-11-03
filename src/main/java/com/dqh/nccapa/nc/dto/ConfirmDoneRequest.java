package com.dqh.nccapa.nc.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** Body POST /nc/{so}/confirm-done (N8) — chi phí thực tế bắt buộc. */
public class ConfirmDoneRequest {
    @NotBlank public String bangChung;
    @NotNull  public BigDecimal chiPhiThucTe;
    @NotBlank public String actor;
}
