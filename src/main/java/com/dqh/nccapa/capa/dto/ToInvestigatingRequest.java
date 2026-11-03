package com.dqh.nccapa.capa.dto;

import jakarta.validation.constraints.NotBlank;

/** C1: Open -> Investigating. keHoachDieuTra bắt buộc nếu NC gốc >= Nặng (bản vá #3). */
public class ToInvestigatingRequest {
    public String keHoachDieuTra;   // optional với NC Nhẹ
    @NotBlank public String actor;
}
