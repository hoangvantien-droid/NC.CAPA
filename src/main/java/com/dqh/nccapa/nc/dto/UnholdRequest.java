package com.dqh.nccapa.nc.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Body POST /nc/{so}/doi-tuong/{id}/unhold — gỡ khoanh dòng lô trong Processing (REQ mục 4.1 line-item).
 */
public class UnholdRequest {
    @NotBlank public String chungTuUnhold;
    @NotBlank
    @Pattern(regexp = "Released|Scrapped|Reworked",
             message = "trangThaiDongLo phải là Released|Scrapped|Reworked")
    public String trangThaiDongLo;
    @NotBlank public String actor;
}
