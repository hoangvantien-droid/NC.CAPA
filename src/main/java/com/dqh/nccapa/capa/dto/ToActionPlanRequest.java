package com.dqh.nccapa.capa.dto;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;

/** C2: Investigating -> ActionPlan. RCA + nhóm nguyên nhân + chi phí (bắt buộc nếu NC >= Nặng). */
public class ToActionPlanRequest {
    public String rcaNoiDung;
    public String nhomNguyenNhan;
    public BigDecimal chiPhiDauTu;
    public Integer rpnS;
    public Integer rpnO;
    public Integer rpnD;
    @NotBlank public String actor;
}
