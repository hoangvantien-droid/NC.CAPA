package com.dqh.nccapa.aggregation;

import com.dqh.nccapa.aggregation.dto.AggregationResult;
import com.dqh.nccapa.aggregation.dto.OosEventRequest;
import com.dqh.nccapa.common.error.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/** Endpoint mô phỏng nguồn QLone/MES bắn sự kiện (API §4). */
@RestController
@RequestMapping("/api/v1/ingest")
public class IngestController {

    private final AutoAggregationService aggregation;

    public IngestController(AutoAggregationService aggregation) {
        this.aggregation = aggregation;
    }

    @PostMapping("/oos")
    public ApiResponse<AggregationResult> oos(@Valid @RequestBody OosEventRequest req) {
        return ApiResponse.ok(aggregation.onOos(req));
    }

    @PostMapping("/ticket")
    public ApiResponse<AggregationResult> ticket(@Valid @RequestBody com.dqh.nccapa.aggregation.dto.TicketEventRequest req) {
        return ApiResponse.ok(aggregation.onTicket(req));
    }
}
