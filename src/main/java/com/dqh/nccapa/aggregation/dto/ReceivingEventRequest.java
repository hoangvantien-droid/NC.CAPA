package com.dqh.nccapa.aggregation.dto;

import jakarta.validation.constraints.NotBlank;

/** Body POST /ingest/receiving — NVL không đạt khi kiểm nhận hàng (REQ mục 5). */
public class ReceivingEventRequest {
    @NotBlank public String loNvl;          // lô NVL (khóa aggregation)
    @NotBlank public String maLoiId;        // vd REC-02/REC-03
    public String ncc;                      // nhà cung cấp
    @NotBlank public String nhaMay;
    public String moTa;
}
