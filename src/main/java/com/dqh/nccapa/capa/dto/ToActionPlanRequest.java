package com.dqh.nccapa.capa.dto;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;

/** C2: Investigating -> ActionPlan. RCA + nhóm nguyên nhân + chi phí (bắt buộc nếu NC >= Nặng). */
public class ToActionPlanRequest {
    public String rcaNoiDung;                 // kết luận nguyên nhân gốc
    public com.dqh.nccapa.common.enums.RcaMethod rcaMethod;   // 5WHY|FISHBONE|IS_IS_NOT
    public String rcaData;                    // JSON các bước
    public String nhomNguyenNhan;             // (cũ, tự do — tuỳ chọn)
    public com.dqh.nccapa.common.enums.NhomNguyenNhan6M nhomNguyenNhan6m;  // 6M
    public Boolean taiDien;                   // cờ tái diễn
    public BigDecimal chiPhiDauTu;
    public Integer rpnS;
    public Integer rpnO;
    public Integer rpnD;
    @NotBlank public String actor;
}
