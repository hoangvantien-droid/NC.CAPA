package com.dqh.nccapa.nc.dto;

import jakarta.validation.constraints.NotBlank;

/** Body POST /nc/{so}/close-fast (N1). */
public class CloseFastRequest {
    @NotBlank public String ketQuaXuLy;
    @NotBlank public String actor;   // demo: người thực hiện (thay cho SecurityContext)
}
