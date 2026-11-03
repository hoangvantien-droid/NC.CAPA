package com.dqh.nccapa.ticket.service;

import com.dqh.nccapa.nc.entity.Nc;
import com.dqh.nccapa.nc.service.TicketCallbackHook;
import org.springframework.stereotype.Service;

import java.util.Map;

/** Impl thật (Đợt c): ghi callback vào outbox khi NC nguồn khiếu nại đạt mốc. */
@Service
public class TicketCallbackHookImpl implements TicketCallbackHook {

    private final TicketCallbackService callbackService;

    public TicketCallbackHookImpl(TicketCallbackService callbackService) {
        this.callbackService = callbackService;
    }

    @Override
    public void mocSom(Nc nc) {
        callbackService.enqueueChoNc(nc.getSoHieu(), "early_response",
                nc.getSoHieu() + ":early",
                Map.of("nc_status", nc.getTrangThai().name(),
                       "severity", nc.getMucNghiemTrong().name(),
                       "root_cause_summary", "Đang điều tra — kết luận sơ bộ sẽ cập nhật"));
    }

    @Override
    public void mocDong(Nc nc, String ketLuan) {
        callbackService.enqueueChoNc(nc.getSoHieu(), "case_closed",
                nc.getSoHieu() + ":closed",
                Map.of("nc_status", nc.getTrangThai().name(),
                       "severity", nc.getMucNghiemTrong().name(),
                       "investigation_conclusion", ketLuan));
    }
}
