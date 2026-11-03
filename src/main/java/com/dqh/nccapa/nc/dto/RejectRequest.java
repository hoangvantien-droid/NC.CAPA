package com.dqh.nccapa.nc.dto;

import jakarta.validation.constraints.NotBlank;

/** Body POST /nc/{so}/reject (N6). */
public class RejectRequest {
    @NotBlank public String lyDo;
    @NotBlank public String actor;
}
