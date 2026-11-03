package com.dqh.nccapa.aggregation.dto;

import jakarta.validation.constraints.NotBlank;

/** Body POST /ingest/oos — mô phỏng QLone bắn kết quả OOS (API §4). */
public class OosEventRequest {
    @NotBlank public String loKiem;
    @NotBlank public String maLoiId;
    public String maHang;
    public Integer inOutSpec;    // 2 = OOS
    public String giaTriDo;
    @NotBlank public String nhaMay;
}
