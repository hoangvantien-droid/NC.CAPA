package com.dqh.nccapa.capa.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.NotBlank;

/** C4: Processing -> Monitoring. Đặt 2 mốc X ngày + N lô. */
public class ToMonitoringRequest {
    @NotNull @Positive public Integer xNgay;
    @NotNull @PositiveOrZero public Integer nLo;   // 0 = nhóm N=0
    @NotBlank public String actor;
}
