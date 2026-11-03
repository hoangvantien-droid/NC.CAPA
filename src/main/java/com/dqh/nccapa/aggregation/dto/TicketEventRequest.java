package com.dqh.nccapa.aggregation.dto;

import jakarta.validation.constraints.NotBlank;

/** Body POST /ingest/ticket — mô phỏng ticket khiếu nại (API §4, REQ mục 5, 6). */
public class TicketEventRequest {
    @NotBlank public String ticketId;
    @NotBlank public String ticketCategory;   // Food_Safety | Quality
    public String productCode;
    public String lotNumber;
    public String defectDescription;
    @NotBlank public String nhaMay;
    /** Mã lỗi gợi ý (với Quality). Food_Safety sẽ ép Nghiêm trọng bất kể. */
    public String maLoiId;
}
