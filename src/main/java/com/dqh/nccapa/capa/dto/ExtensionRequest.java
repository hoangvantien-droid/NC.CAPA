package com.dqh.nccapa.capa.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/** POST /capa/actions/{aid}/extension — yêu cầu gia hạn action. */
public class ExtensionRequest {
    @NotNull  public LocalDate deadlineMoi;
    @NotBlank public String lyDo;
    @NotBlank public String actor;
}
