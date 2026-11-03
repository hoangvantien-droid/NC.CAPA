package com.dqh.nccapa.capa.dto;

import jakarta.validation.constraints.NotBlank;

/** POST /capa/{id}/confirm-effective — chỉ khi đủ cả 2 mốc (hoặc N=0 + biên bản). */
public class ConfirmEffectiveRequest {
    public String bienBanKiemTra;   // bắt buộc nếu nhóm N=0
    @NotBlank public String actor;
}
