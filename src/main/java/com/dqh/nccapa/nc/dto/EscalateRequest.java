package com.dqh.nccapa.nc.dto;

import com.dqh.nccapa.common.enums.Severity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Body POST /nc/{so}/escalate. */
public class EscalateRequest {
    @NotNull  public Severity mucMoi;
    @NotBlank public String lyDo;    // bắt buộc (REQ mục 7 #4)
    @NotBlank public String actor;
}
