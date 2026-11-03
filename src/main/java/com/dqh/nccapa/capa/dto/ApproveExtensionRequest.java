package com.dqh.nccapa.capa.dto;

import jakarta.validation.constraints.NotBlank;

/** POST /capa/actions/ext/{eid}/approve — duyệt gia hạn (quản lý người phụ trách). */
public class ApproveExtensionRequest {
    @NotBlank public String actor;
}
