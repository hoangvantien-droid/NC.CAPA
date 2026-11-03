package com.dqh.nccapa.capa.dto;

import jakarta.validation.constraints.NotBlank;

/** POST /capa/actions/{aid}/complete. */
public class CompleteActionRequest {
    @NotBlank public String bangChung;
    @NotBlank public String actor;
}
