package com.dqh.nccapa.capa.dto;

import com.dqh.nccapa.common.enums.ActionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/** POST /capa/{id}/actions — thêm action con. */
public class AddActionRequest {
    @NotNull  public ActionType loai;
    @NotBlank public String moTa;
    @NotBlank public String nguoiPhuTrach;
    @NotNull  public LocalDate deadline;
    @NotBlank public String actor;
}
