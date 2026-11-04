package com.dqh.nccapa.demo;

import com.dqh.nccapa.capa.dto.AddActionRequest;
import com.dqh.nccapa.capa.dto.CompleteActionRequest;
import com.dqh.nccapa.capa.dto.CompleteStepRequest;
import com.dqh.nccapa.capa.dto.ConfirmEffectiveRequest;
import com.dqh.nccapa.capa.dto.MarkIneffectiveRequest;
import com.dqh.nccapa.capa.dto.TaoCapaRequest;
import com.dqh.nccapa.capa.dto.ToActionPlanRequest;
import com.dqh.nccapa.capa.dto.ToInvestigatingRequest;
import com.dqh.nccapa.capa.dto.ToMonitoringRequest;
import com.dqh.nccapa.capa.dto.ToProcessingRequest;
import com.dqh.nccapa.capa.entity.Capa;
import com.dqh.nccapa.capa.entity.CapaAction;
import com.dqh.nccapa.capa.service.CapaService;
import com.dqh.nccapa.common.enums.ActionType;
import com.dqh.nccapa.common.enums.LoaiThayDoi;
import com.dqh.nccapa.common.enums.NcSource;
import com.dqh.nccapa.common.enums.NhomNguyenNhan6M;
import com.dqh.nccapa.common.enums.RcaMethod;
import com.dqh.nccapa.common.security.RoleChecker;
import com.dqh.nccapa.nc.dto.TaoNcRequest;
import com.dqh.nccapa.nc.entity.Nc;
import com.dqh.nccapa.nc.service.NcService;
import com.dqh.nccapa.pr.dto.PrActionRequest;
import com.dqh.nccapa.pr.dto.TaoPrRequest;
import com.dqh.nccapa.pr.entity.Pr;
import com.dqh.nccapa.pr.service.PrService;
import com.dqh.nccapa.support.DefectSeed;
import com.dqh.nccapa.support.MySqlTestBase;
import com.dqh.nccapa.ticket.entity.TicketCallbackOutbox;
import com.dqh.nccapa.ticket.repository.TicketCallbackOutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TÁI HIỆN báo lỗi "quyền quản trị hệ thống vào mục capa báo lỗi" (Whitelabel 500).
 *
 * <p>Nguyên nhân gốc: {@code templates/capa-detail.html} đọc {@code a.moTa}, {@code a.nguoiPhuTrach}
 * (SpEL trên {@link CapaAction}) và {@code s.noiDung} (trên {@code Capa8dStep}) nhưng hai entity này
 * KHÔNG có getter tương ứng -> SpEL ném lỗi "Property or field cannot be found" -> 500. Lỗi xảy ra với
 * MỌI vai trò khi CAPA có action (không riêng admin); test cũ chạy trên DB rỗng / CAPA không action
 * nên không bắt được.
 *
 * <p>Test dựng dữ liệu thật: NC Nặng -> CAPA ở nhiều trạng thái (Open, Monitoring có action + 8D + RPN +
 * chi phí + mốc X/N, Closed có kết quả kiểm chứng, Ineffective), rồi mở {@code /capa} và
 * {@code /capa/{id}} dưới cả 5 vai trò. KHÔNG seed {@code app_user} cho {@code admin} (mô phỏng
 * production thiếu user) và KHÔNG so wall-clock.
 */
@AutoConfigureMockMvc
class CapaViewRenderTest extends MySqlTestBase {

    private static final List<String> VAI_TRO = List.of("baocao", "sxmgp", "qamgp", "qatd", "admin");

    @Autowired MockMvc mockMvc;
    @Autowired CurrentUserService currentUser;
    @Autowired NcService ncService;
    @Autowired CapaService capaService;
    @Autowired PrService prService;
    @Autowired TicketCallbackOutboxRepository outboxRepo;
    @Autowired RoleChecker roleChecker;
    @Autowired JdbcTemplate jdbc;

    private Long capaOpen;
    private Long capaMonitoring;
    private Long capaClosed;
    private Long capaIneffective;
    private String soNc;
    private Long prId;

    @BeforeEach
    void setUp() {
        DefectSeed.seed(jdbc);
        // Chỉ seed role cho actor dời hạn/kiểm chứng; TUYỆT ĐỐI không seed app_user "admin".
        DefectSeed.seedUserRole(jdbc, "qatd", "QA_TAP_DOAN");

        DefectSeed.seedUserRole(jdbc, "rd1", "R_AND_D");
        capaOpen = taoCapa("LOT-CV-OPEN").getId();

        capaMonitoring = capaToMonitoring("LOT-CV-MON", 5, true);

        Long closed = capaToMonitoring("LOT-CV-CLOSED", 0, false);
        jdbc.update("UPDATE capa SET monitoring_bat_dau = '2020-01-01 00:00:00' WHERE id = ?", closed);
        jdbc.update("UPDATE capa_verification SET han_kiem_chung_tiep_theo = '2020-02-29 00:00:00' WHERE capa_id = ?", closed);
        ConfirmEffectiveRequest cf = new ConfirmEffectiveRequest();
        cf.bienBanKiemTra = "BB kiểm tra thực tế"; cf.actor = "qa";
        capaService.confirmEffective(closed, cf);
        capaClosed = closed;

        Long ineff = capaToMonitoring("LOT-CV-INEFF", 3, false);
        MarkIneffectiveRequest mi = new MarkIneffectiveRequest();
        mi.lyDo = "không hiệu quả tại mốc"; mi.actor = "qa";
        capaService.markIneffective(ineff, mi);
        capaIneffective = ineff;

        // NC riêng (không CAPA) để phủ nc-list / nc-detail / nc-queue (chỉ còn NEW -> PendingApproval).
        Nc nc = taoNc("PRC-02", "LOT-CV-NC");
        this.soNc = nc.getSoHieu();

        // PR (loại CONG_THUC: chuỗi duyệt R_AND_D -> QA_TAP_DOAN) để phủ pr-list / pr-detail.
        TaoPrRequest pr = new TaoPrRequest();
        pr.loaiThayDoi = LoaiThayDoi.CONG_THUC; pr.maHang = "SP-PR-CV"; pr.maLoiId = "FG-01"; pr.actor = "qa"; // cặp riêng: tránh PR tự mở của PRC-02/SP001
        Pr created = prService.tao(pr);
        PrActionRequest act = new PrActionRequest(); act.actor = "qa";
        prService.toAnalyzing(created.getId(), act);
        prService.submit(created.getId(), act);
        PrActionRequest approve = new PrActionRequest(); approve.actor = "rd1"; approve.vaiTro = "R_AND_D";
        prService.approve(created.getId(), approve);
        this.prId = created.getId();

        // Ticket outbox: một callback đã gửi để ticket-outbox có dữ liệu.
        TicketCallbackOutbox cb = new TicketCallbackOutbox("TCK-CV-1", "early_response", "EVT-CV-1", soNc, "{}");
        outboxRepo.save(cb);
    }

    private Nc taoNc(String maLoi, String lo) {
        TaoNcRequest r = new TaoNcRequest();
        r.loaiNc = "TRONG_SX"; r.nguonPhatSinh = NcSource.THU_CONG;
        r.nhaMay = "MGP"; r.nguoiBaoCao = "user01"; r.loKiem = lo;
        TaoNcRequest.ChiTieuItem ct = new TaoNcRequest.ChiTieuItem();
        ct.maLoiId = maLoi; ct.maHang = "SP001";
        r.chiTieu = List.of(ct);
        return ncService.taoNc(r);
    }

    private Capa taoCapa(String lo) {
        TaoNcRequest r = new TaoNcRequest();
        r.loaiNc = "TRONG_SX"; r.nguonPhatSinh = NcSource.THU_CONG;
        r.nhaMay = "MGP"; r.nguoiBaoCao = "user01"; r.loKiem = lo;
        TaoNcRequest.ChiTieuItem ct = new TaoNcRequest.ChiTieuItem();
        ct.maLoiId = "PRC-02"; ct.maHang = "SP001"; // Nặng
        r.chiTieu = List.of(ct);
        Nc nc = ncService.taoNc(r);
        TaoCapaRequest t = new TaoCapaRequest(); t.actor = "qa";
        return capaService.taoTuNc(nc.getSoHieu(), t);
    }

    /** NC Nặng -> CAPA đủ RCA/RPN/chi phí/action/(8D) -> Monitoring với n_lo cho trước. */
    private Long capaToMonitoring(String lo, int nLo, boolean bat8d) {
        Long id = taoCapa(lo).getId();
        ToInvestigatingRequest inv = new ToInvestigatingRequest();
        inv.keHoachDieuTra = "điều tra nguyên nhân"; inv.actor = "qa";
        capaService.toInvestigating(id, inv);

        if (bat8d) {
            capaService.bat8d(id, "qa");
            CompleteStepRequest d1 = new CompleteStepRequest();
            d1.noiDung = "Đã lập nhóm 8D"; d1.actor = "qa";
            capaService.hoanThanhBuoc8d(id, "D1", d1);
        }

        ToActionPlanRequest ap = new ToActionPlanRequest();
        ap.rcaNoiDung = "thiếu kiểm soát công đoạn"; ap.rcaMethod = RcaMethod.FIVE_WHY;
        ap.rcaData = "{\"w1\":\"vì sao\"}"; ap.nhomNguyenNhan6m = NhomNguyenNhan6M.METHOD;
        ap.chiPhiDauTu = new BigDecimal("5000000");
        ap.rpnS = 5; ap.rpnO = 4; ap.rpnD = 3;
        ap.actor = "qa";
        capaService.toActionPlan(id, ap);

        AddActionRequest a1 = new AddActionRequest();
        a1.loai = ActionType.TUC_THOI; a1.moTa = "Cô lập lô hàng lỗi " + lo;
        a1.nguoiPhuTrach = "sxmgp"; a1.deadline = LocalDate.of(2099, 1, 3); a1.actor = "qa";
        CapaAction act = capaService.themAction(id, a1);

        AddActionRequest a2 = new AddActionRequest();
        a2.loai = ActionType.NGAN_TAI_DIEN; a2.moTa = "Đào tạo lại nhân sự " + lo;
        a2.nguoiPhuTrach = "sxmgp"; a2.deadline = LocalDate.of(2099, 1, 10); a2.actor = "qa";
        capaService.themAction(id, a2);

        ToProcessingRequest pp = new ToProcessingRequest();
        pp.khacPhucTucThoi = "cô lập lô"; pp.nganTaiDien = "đào tạo lại"; pp.actor = "qa";
        capaService.toProcessing(id, pp);

        // Hoàn thành mọi action (toMonitoring yêu cầu) -> có cả action Completed (bằng chứng).
        for (CapaAction a : capaService.actionsCua(id)) {
            CompleteActionRequest comp = new CompleteActionRequest();
            comp.bangChung = "biên bản xử lý"; comp.actor = "sxmgp";
            capaService.completeAction(a.getId(), comp);
        }

        ToMonitoringRequest mon = new ToMonitoringRequest();
        mon.xNgay = 30; mon.nLo = nLo; mon.actor = "qa";
        capaService.toMonitoring(id, mon);
        return id;
    }

    private MockHttpSession sessionCua(String username) {
        MockHttpSession session = new MockHttpSession();
        currentUser.doiUser(session, username);
        return session;
    }

    private String html(String url, String username) throws Exception {
        MvcResult r = mockMvc.perform(get(url).session(sessionCua(username)))
                .andExpect(status().isOk())
                .andReturn();
        return r.getResponse().getContentAsString();
    }

    @Test
    void nhaMayCua_userKhongCoTrongDb_traNull_khongNem() {
        // Mô phỏng production thiếu user admin trong app_user: tra cứu phải an toàn.
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM app_user WHERE username = 'admin'", Integer.class);
        assertThat(n).isZero();
        assertThat(roleChecker.nhaMayCua("admin")).isNull();
        assertThat(roleChecker.coRole("admin", "ADMIN")).isFalse();
    }

    @Test
    void capaList_moiVaiTro_200_coDuLieu() throws Exception {
        for (String u : VAI_TRO) {
            String body = html("/capa", u);
            for (Long id : List.of(capaOpen, capaMonitoring, capaClosed, capaIneffective)) {
                assertThat(body).as("role=%s capa=%s", u, id).contains("CAPA#<span>" + id + "</span>");
            }
            assertThat(body).as("role=%s chi phí", u).contains("5,000,000 ₫");
            assertThat(body).as("role=%s RPN 5x4x3", u).contains(">60<");
        }
    }

    @Test
    void capaDetail_moiVaiTro_moiTrangThai_200() throws Exception {
        for (String u : VAI_TRO) {
            for (Long id : List.of(capaOpen, capaMonitoring, capaClosed, capaIneffective)) {
                String body = html("/capa/" + id, u);
                assertThat(body).as("role=%s capa=%s", u, id).contains("Thông tin CAPA");
            }
            // CAPA có action: mô tả + người phụ trách (getter còn thiếu trước đây) phải hiện.
            String mon = html("/capa/" + capaMonitoring, u);
            assertThat(mon).as("role=%s moTa action", u).contains("Cô lập lô hàng lỗi LOT-CV-MON");
            assertThat(mon).as("role=%s nguoiPhuTrach", u).contains("sxmgp");
            assertThat(mon).as("role=%s 8D", u).contains("Đã lập nhóm 8D");
            assertThat(mon).as("role=%s chi phí", u).contains("5,000,000 ₫");
        }
    }

    @Test
    void capaDetail_admin_chiXem_khongThayNutThaoTac() throws Exception {
        String body = html("/capa/" + capaMonitoring, "admin");
        assertThat(body).doesNotContain("data-action=\"confirm-effective\"");
        assertThat(body).contains("Không có thao tác khả dụng.");
        // QA tập đoàn vẫn thấy nút thao tác (giữ nguyên gating cũ).
        assertThat(html("/capa/" + capaMonitoring, "qatd")).contains("data-action=\"confirm-effective\"");
    }

    // ---- Phủ thêm các trang khác có dữ liệu thật (mục 3 của yêu cầu) ----

    @Test
    void ncList_ncDetail_ncQueue_moiVaiTro_200() throws Exception {
        for (String u : VAI_TRO) {
            assertThat(html("/", u)).as("nc-list role=%s", u).contains(soNc);
            String detail = html("/nc/" + soNc, u);
            assertThat(detail).as("nc-detail role=%s", u).contains(soNc);
            if (u.equals("qamgp") || u.equals("qatd")) {
                assertThat(html("/cho-duyet", u)).as("nc-queue role=%s", u).contains(soNc);
            }
        }
    }

    @Test
    void prList_prDetail_moiVaiTroCoQuyen_200() throws Exception {
        for (String u : List.of("sxmgp", "qamgp", "qatd", "admin")) {
            assertThat(html("/pr", u)).as("pr-list role=%s", u).contains("PR#" + prId);
            assertThat(html("/pr/" + prId, u)).as("pr-detail role=%s", u).contains("PR#" + prId);
        }
    }

    @Test
    void ticketOutbox_dashboard_admin_moiVaiTroCoQuyen_200() throws Exception {
        for (String u : List.of("qatd", "admin")) {
            assertThat(html("/ticket", u)).as("ticket-outbox role=%s", u).contains("TCK-CV-1");
        }
        for (String u : List.of("qamgp", "qatd", "admin")) {
            assertThat(html("/dashboard", u)).as("dashboard role=%s", u).contains("Phiếu đang mở");
        }
        assertThat(html("/quan-tri", "admin")).as("admin page").isNotBlank();
    }
}
