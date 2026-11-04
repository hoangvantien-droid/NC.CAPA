package com.dqh.nccapa.nc.dto;

import jakarta.validation.constraints.NotBlank;

/** Body POST /nc/{so}/verify-close (N9). ketQuaThamTra: DAT (mặc định) -> Closed; KHONG_DAT -> trả về Processing. */
public class VerifyCloseRequest {
    @NotBlank public String ketLuanThamTra;
    public String ketQuaThamTra;   // DAT | KHONG_DAT (null = DAT)
    @NotBlank public String actor;
}