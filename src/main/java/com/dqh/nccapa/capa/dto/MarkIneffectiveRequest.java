package com.dqh.nccapa.capa.dto;

import jakarta.validation.constraints.NotBlank;

/** POST /capa/{id}/mark-ineffective (TH1 — fail tại mốc, QA đánh giá). */
public class MarkIneffectiveRequest {
    @NotBlank public String lyDo;
    @NotBlank public String actor;
}
