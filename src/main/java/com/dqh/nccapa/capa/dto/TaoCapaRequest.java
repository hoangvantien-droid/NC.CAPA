package com.dqh.nccapa.capa.dto;

import jakarta.validation.constraints.NotBlank;

/** Body POST /nc/{so}/capa — tạo CAPA từ NC. */
public class TaoCapaRequest {
    @NotBlank public String actor;
}
