package com.dqh.nccapa.ticket;

import com.dqh.nccapa.common.enums.NcStatus;
import com.dqh.nccapa.nc.dto.*;
import com.dqh.nccapa.nc.entity.Nc;
import com.dqh.nccapa.nc.service.NcService;
import com.dqh.nccapa.support.DefectSeed;
import com.dqh.nccapa.support.MySqlTestBase;
import com.dqh.nccapa.support.StubTicketSender;
import com.dqh.nccapa.ticket.entity.TicketCallbackOutbox;
import com.dqh.nccapa.ticket.repository.TicketCallbackOutboxRepository;
import com.dqh.nccapa.ticket.service.TicketCallbackService;
import com.dqh.nccapa.ticket.service.TicketSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Đợt c — callback ticket 2 chiều: idempotency, retry/dead-letter, thứ tự mốc, hủy. */
@Import(TicketCallbackTest.StubConfig.class)
class TicketCallbackTest extends MySqlTestBase {

    @TestConfiguration
    static class StubConfig {
        @Bean
        @org.springframework.context.annotation.Primary
        TicketSender ticketSender() { return new StubTicketSender(); }
    }

    @Autowired NcService ncService;
    @Autowired TicketCallbackService callbackService;
    @Autowired TicketCallbackOutboxRepository outboxRepo;
    @Autowired TicketSender sender;   // = StubTicketSender
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        DefectSeed.seed(jdbc);
        ((StubTicketSender) sender).failNext = false;
        ((StubTicketSender) sender).sent.clear();
    }

    @Test // Idempotent: enqueue 2 lần cùng event_id -> 1 dòng
    void enqueue_idempotent() {
        jdbc.update("INSERT INTO nc(so_hieu,loai_nc,muc_nghiem_trong,nguon_phat_sinh,trang_thai,is_escalated,is_food_safety,nha_may,nguoi_bao_cao,created_at,created_by,da_duyet_cap_1) " +
                "VALUES ('KPH-X','KHIEU_NAI','NANG','TICKET','PendingVerification',0,0,'MGP','u',NOW(),'u',0)");
        jdbc.update("INSERT INTO nc_ticket(nc_so_hieu,ticket_id,created_at) VALUES ('KPH-X','TK-1',NOW())");

        callbackService.enqueueChoNc("KPH-X", "early_response", "KPH-X:early", Map.of());
        callbackService.enqueueChoNc("KPH-X", "early_response", "KPH-X:early", Map.of()); // lần 2 trùng
        List<TicketCallbackOutbox> rows = outboxRepo.findByTicketIdOrderByIdAsc("TK-1");
        assertThat(rows).hasSize(1);
    }

    @Test // flush gửi thành công -> SENT
    void flush_thanhCong_sent() {
        seedOutbox("TK-2", "early_response", "e1");
        int n = callbackService.flush();
        assertThat(n).isEqualTo(1);
        assertThat(outboxRepo.findByTicketIdOrderByIdAsc("TK-2").get(0).getTrangThai()).isEqualTo("SENT");
    }

    @Test // thất bại nhiều lần -> DEAD_LETTER (maxRetry=3)
    void flush_thatBai_deadLetter() {
        seedOutbox("TK-3", "early_response", "e2");
        ((StubTicketSender) sender).failNext = true;
        // mỗi flush: 1 lần thử; thu_ke_tiep đẩy về tương lai nên cần reset để thử tiếp
        for (int i = 0; i < 3; i++) {
            callbackService.flush();
            jdbc.update("UPDATE ticket_callback_outbox SET thu_ke_tiep_luc = NOW() WHERE ticket_id='TK-3'");
        }
        var row = outboxRepo.findByTicketIdOrderByIdAsc("TK-3").get(0);
        assertThat(row.getTrangThai()).isEqualTo("DEAD_LETTER");
    }

    @Test // NC khiếu nại: confirm-done -> early_response; verify-close -> case_closed Confirmed; đúng thứ tự
    void ncKhieuNai_early_truoc_closed() {
        // tạo NC nguồn TICKET qua insert trực tiếp (ingest ticket đã test ở Đợt a)
        String so = "KPH-CB";
        jdbc.update("INSERT INTO nc(so_hieu,loai_nc,muc_nghiem_trong,nguon_phat_sinh,trang_thai,is_escalated,is_food_safety,nha_may,nguoi_bao_cao,created_at,created_by,da_duyet_cap_1,chi_phi_thuc_te) " +
                "VALUES (?,'KHIEU_NAI','NANG','TICKET','Processing',0,0,'MGP','u',NOW(),'u',0,NULL)", so);
        jdbc.update("INSERT INTO nc_ticket(nc_so_hieu,ticket_id,created_at) VALUES (?, 'TK-CB', NOW())", so);

        ConfirmDoneRequest c = new ConfirmDoneRequest(); c.bangChung="bb"; c.chiPhiThucTe=new BigDecimal("1"); c.actor="sx";
        ncService.confirmDone(so, c);   // -> PendingVerification + early_response enqueue
        VerifyCloseRequest v = new VerifyCloseRequest(); v.ketLuanThamTra="đạt"; v.actor="qa";
        ncService.verifyClose(so, v);   // -> Closed + case_closed enqueue

        List<TicketCallbackOutbox> rows = outboxRepo.findByTicketIdOrderByIdAsc("TK-CB");
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).getCallbackType()).isEqualTo("early_response"); // enqueue trước -> id nhỏ hơn
        assertThat(rows.get(1).getCallbackType()).isEqualTo("case_closed");

        callbackService.flush();
        // Stub ghi theo thứ tự id -> early gửi trước closed
        List<String> sent = ((StubTicketSender) sender).sent;
        assertThat(sent.get(0)).contains("early_response");
        assertThat(sent.get(1)).contains("case_closed");
    }

    @Test // NC khiếu nại bị Cancelled -> callback case_closed = Cancelled
    void ncKhieuNai_cancelled_callbackCancelled() {
        String so = "KPH-CL";
        jdbc.update("INSERT INTO nc(so_hieu,loai_nc,muc_nghiem_trong,nguon_phat_sinh,trang_thai,is_escalated,is_food_safety,nha_may,nguoi_bao_cao,created_at,created_by,da_duyet_cap_1) " +
                "VALUES (?,'KHIEU_NAI','NANG','TICKET','New',0,0,'MGP','u',NOW(),'u',0)", so);
        jdbc.update("INSERT INTO nc_ticket(nc_so_hieu,ticket_id,created_at) VALUES (?, 'TK-CL', NOW())", so);

        RejectRequest r = new RejectRequest(); r.lyDo="trùng phiếu"; r.actor="qatd";
        ncService.cancel(so, r);
        assertThat(ncService.layNc(so).getTrangThai()).isEqualTo(NcStatus.Cancelled);

        var rows = outboxRepo.findByTicketIdOrderByIdAsc("TK-CL");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getCallbackType()).isEqualTo("case_closed");
        assertThat(rows.get(0).getPayload()).contains("Cancelled");
    }

    private void seedOutbox(String ticketId, String type, String eventId) {
        callbackService.enqueue(ticketId, type, eventId, "KPH-SEED", Map.of());
    }
}
