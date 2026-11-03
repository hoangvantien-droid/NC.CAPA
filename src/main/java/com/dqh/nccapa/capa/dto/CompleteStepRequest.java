package com.dqh.nccapa.capa.dto;

import jakarta.validation.constraints.NotBlank;

/** POST /capa/{id}/8d/{buoc}/complete — hoàn thành một bước 8D. */
public class CompleteStepRequest {
    public String noiDung;
    @NotBlank public String actor;
}
