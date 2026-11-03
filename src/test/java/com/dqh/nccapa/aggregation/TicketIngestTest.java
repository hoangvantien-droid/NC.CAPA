package com.dqh.nccapa.aggregation;

import com.dqh.nccapa.aggregation.dto.AggregationResult;
import com.dqh.nccapa.aggregation.dto.TicketEventRequest;
import com.dqh.nccapa.common.enums.NcStatus;
import com.dqh.nccapa.common.enums.Severity;
import com.dqh.nccapa.nc.service.NcService;
import com.dqh.nccapa.support.DefectSeed;
import com.dqh.nccapa.support.MySqlTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/** Ticket ingest + food-safety override (REQ mục 5, 7 #5). */
class TicketIngestTest extends MySqlTestBase {

    @Autowired AutoAggregationService aggregation;
    @Autowired NcService ncService;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setUp() { DefectSeed.seed(jdbc); }

    @Test // Food_Safety -> ép Nghiêm trọng + Containment + link ticket, không cho tự phân loại
    void foodSafety_epNghiemTrong() {
        TicketEventRequest t = new TicketEventRequest();
        t.ticketId = "TK-001"; t.ticketCategory = "Food_Safety";
        t.productCode = "SP001"; t.lotNumber = "LOT-TK-1";
        t.defectDescription = "dị vật"; t.nhaMay = "MGP";
        t.maLoiId = "FG-01"; // gợi ý Nhẹ — nhưng Food_Safety phải ép Nghiêm trọng

        AggregationResult r = aggregation.onTicket(t);
        assertThat(r.action).isEqualTo(AggregationResult.Action.CREATED);
        assertThat(r.mucNghiemTrong).isEqualTo("NGHIEM_TRONG");

        var nc = ncService.layNc(r.soHieu);
        assertThat(nc.getMucNghiemTrong()).isEqualTo(Severity.NGHIEM_TRONG);
        assertThat(nc.getTrangThai()).isEqualTo(NcStatus.Containment);
        assertThat(nc.isFoodSafety()).isTrue();

        Long linked = jdbc.queryForObject(
                "SELECT COUNT(*) FROM nc_ticket WHERE nc_so_hieu = ? AND ticket_id = 'TK-001'",
                Long.class, r.soHieu);
        assertThat(linked).isEqualTo(1);
    }

    @Test // Nhiều ticket cùng lô -> gom vào 1 NC (nhiều-một)
    void nhieuTicketCungLo_gomVao1Nc() {
        TicketEventRequest t1 = new TicketEventRequest();
        t1.ticketId = "TK-010"; t1.ticketCategory = "Quality";
        t1.productCode = "SP001"; t1.lotNumber = "LOT-TK-2"; t1.nhaMay = "MGP"; t1.maLoiId = "CMP-02";
        AggregationResult r1 = aggregation.onTicket(t1);

        TicketEventRequest t2 = new TicketEventRequest();
        t2.ticketId = "TK-011"; t2.ticketCategory = "Quality";
        t2.productCode = "SP001"; t2.lotNumber = "LOT-TK-2"; t2.nhaMay = "MGP"; t2.maLoiId = "CMP-02";
        AggregationResult r2 = aggregation.onTicket(t2);

        assertThat(r2.action).isEqualTo(AggregationResult.Action.APPENDED);
        assertThat(r2.soHieu).isEqualTo(r1.soHieu);  // cùng NC

        Long linked = jdbc.queryForObject(
                "SELECT COUNT(*) FROM nc_ticket WHERE nc_so_hieu = ?", Long.class, r1.soHieu);
        assertThat(linked).isEqualTo(2);  // 2 ticket cùng NC
    }
}
