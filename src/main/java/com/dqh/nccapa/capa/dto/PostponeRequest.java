package com.dqh.nccapa.capa.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** POST /capa/{id}/postpone-reminder — dời hạn mốc chưa đạt (vá 2.4). */
public class PostponeRequest {
    @NotNull @Positive public Integer soNgayDoiThem;
    @NotBlank public String lyDo;
    @NotBlank public String actor;
}
