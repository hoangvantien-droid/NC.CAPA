package com.dqh.nccapa.ticket;

import com.dqh.nccapa.common.enums.NcStatus;
import com.dqh.nccapa.nc.dto.*;
import com.dqh.nccapa.nc.service.NcService;
import com.dqh.nccapa.support.DefectSeed;
import com.dqh.nccapa.support.MySqlTestBase;
import com.dqh.nccapa.ticket.entity.TicketCallbackOutbox;
import com.dqh.nccapa.ticket.repository.TicketCallbackOutboxRepository;
import com.dqh.nccapa.ticket.service.TicketCallbackService;
import com.dqh.nccapa.ticket.service.TicketSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/** Đợt c — callback ticket 2 chiều: idempotency, retry/dead-letter, thứ tự mốc, hủy. */
class TicketCallbackTest extends MySqlTestBase {

    /** Thay hẳn bean TicketSender bằng mock (chỉ 1 instance, deterministic). */
    @MockBean
    TicketSender sender;

    @Autowired NcService ncService;
    @Autowired TicketCallbackService callbackService;
    @Autowired TicketCallbackOutboxRepository outboxRepo;
    @Autowired JdbcTemplate jdbc;

    /** Ghi lại payload khi send trả true (mô phỏng StubTicketSender.sent). */
    private final List<String> daGui = new ArrayList<>();

    @BeforeEach
    void setUp() {
        DefectSeed.seed(jdbc);
        daGui.clear();
        // mặc định: gửi thành công, ghi lại payload
        when(sender.send(anyString(), anyString())).thenAnswer(inv -> {
            daGui.add(inv.getArgument(1));
            return true;
        });
    }

    @Test // Idempotent: enqueue 2 lần cùng event_id -> 1 dòng
    void enqueue_idempotent() {
        insertNc("KPH-X", "PendingVerification");
        jdbc.update("INSERT INTO nc_ticket(nc_so_hieu,ticket_id,created_at) VALUES ('KPH-X','TK-1',NOW())");

        callbackService.enqueueChoNc("KPH-X", "early_response", "KPH-X:early", Map.of());
        callbackService.enqueueChoNc("KPH-X", "early_response", "KPH-X:early", Map.of());
        assertThat(outboxRepo.findByTicketIdOrderByIdAsc("TK-1")).hasSize(1);
    }

    @Test // flush gửi thành công -> SENT
    void flush_thanhCong_sent() {
        callbackService.enqueue("TK-2", "early_response", "e1", "KPH-SEED", Map.of());
        int n = callbackService.flush();
        assertThat(n).isEqualTo(1);
        assertThat(outboxRepo.findByTicketIdOrderByIdAsc("TK-2").get(0).getTrangThai()).isEqualTo("SENT");
    }

    @Test // thất bại nhiều lần -> DEAD_LETTER (maxRetry=3)
    void flush_thatBai_deadLetter() {
        callbackService.enqueue("TK-3", "early_response", "e2", "KPH-SEED", Map.of());
        when(sender.send(anyString(), anyString())).thenReturn(false);   // luôn fail
        for (int i = 0; i < 3; i++) {
            callbackService.flush();
            jdbc.update("UPDATE ticket_callback_outbox SET thu_ke_tiep_luc = DATE_SUB(NOW(), INTERVAL 1 MINUTE) WHERE ticket_id='TK-3'");
        }
        assertThat(outboxRepo.findByTicketIdOrderByIdAsc("TK-3").get(0).getTrangThai()).isEqualTo("DEAD_LETTER");
    }

    @Test // confirm-done -> early_response; verify-close -> case_closed; đúng thứ tự khi flush
    void ncKhieuNai_early_truoc_closed() {
        String so = "KPH-CB";
        insertNcProcessing(so);
        jdbc.update("INSERT INTO nc_ticket(nc_so_hieu,ticket_id,created_at) VALUES (?, 'TK-CB', NOW())", so);

        ConfirmDoneRequest c = new ConfirmDoneRequest(); c.bangChung="bb"; c.chiPhiThucTe=new BigDecimal("1"); c.actor="sx";
        ncService.confirmDone(so, c);
        VerifyCloseRequest v = new VerifyCloseRequest(); v.ketLuanThamTra="đạt"; v.actor="qa";
        ncService.verifyClose(so, v);

        List<TicketCallbackOutbox> rows = outboxRepo.findByTicketIdOrderByIdAsc("TK-CB");
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).getCallbackType()).isEqualTo("early_response");
        assertThat(rows.get(1).getCallbackType()).isEqualTo("case_closed");

        callbackService.flush();
        assertThat(daGui).hasSize(2);
        assertThat(daGui.get(0)).contains("early_response");   // early gửi trước
        assertThat(daGui.get(1)).contains("case_closed");
    }

    @Test // NC khiếu nại bị Cancelled -> callback case_closed = Cancelled
    void ncKhieuNai_cancelled_callbackCancelled() {
        String so = "KPH-CL";
        insertNc(so, "New");
        jdbc.update("INSERT INTO nc_ticket(nc_so_hieu,ticket_id,created_at) VALUES (?, 'TK-CL', NOW())", so);

        RejectRequest r = new RejectRequest(); r.lyDo="trùng phiếu"; r.actor="qatd";
        ncService.cancel(so, r);
        assertThat(ncService.layNc(so).getTrangThai()).isEqualTo(NcStatus.Cancelled);

        var rows = outboxRepo.findByTicketIdOrderByIdAsc("TK-CL");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getCallbackType()).isEqualTo("case_closed");
        assertThat(rows.get(0).getPayload()).contains("Cancelled");
    }

    private void insertNc(String so, String trangThai) {
        jdbc.update("INSERT INTO nc(so_hieu,loai_nc,muc_nghiem_trong,nguon_phat_sinh,trang_thai,is_escalated,is_food_safety,nha_may,nguoi_bao_cao,created_at,created_by,da_duyet_cap_1) " +
                "VALUES (?,'KHIEU_NAI','NANG','TICKET',?,0,0,'MGP','u',NOW(),'u',0)", so, trangThai);
    }

    private void insertNcProcessing(String so) {
        jdbc.update("INSERT INTO nc(so_hieu,loai_nc,muc_nghiem_trong,nguon_phat_sinh,trang_thai,is_escalated,is_food_safety,nha_may,nguoi_bao_cao,created_at,created_by,da_duyet_cap_1,chi_phi_thuc_te) " +
                "VALUES (?,'KHIEU_NAI','NANG','TICKET','Processing',0,0,'MGP','u',NOW(),'u',0,NULL)", so);
    }
}
