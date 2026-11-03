package com.dqh.nccapa.capa.dto;

import jakarta.validation.constraints.NotBlank;

/** C3: ActionPlan -> Processing. 2 ô (tức thời + ngăn tái diễn) bắt buộc nếu NC >= Nặng. */
public class ToProcessingRequest {
    public String khacPhucTucThoi;
    public String nganTaiDien;
    @NotBlank public String actor;
}
