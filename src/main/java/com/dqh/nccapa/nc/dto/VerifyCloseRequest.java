package com.dqh.nccapa.nc.dto;

import jakarta.validation.constraints.NotBlank;

/** Body POST /nc/{so}/verify-close (N9). */
public class VerifyCloseRequest {
    @NotBlank public String ketLuanThamTra;
    @NotBlank public String actor;
}
