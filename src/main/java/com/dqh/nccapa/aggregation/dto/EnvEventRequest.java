package com.dqh.nccapa.aggregation.dto;

import jakarta.validation.constraints.NotBlank;

/** Body POST /ingest/env — sự kiện giám sát môi trường (REQ mục 5: bootswab, bề mặt, nước/khí). */
public class EnvEventRequest {
    @NotBlank public String diemLayMau;    // điểm lấy mẫu (khóa aggregation)
    @NotBlank public String maLoiId;        // vd ENV-01
    @NotBlank public String nhaMay;
    public String ketQua;                   // mô tả kết quả (vd "Listeria +")
}
