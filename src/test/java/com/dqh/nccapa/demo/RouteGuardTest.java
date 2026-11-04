package com.dqh.nccapa.demo;

import com.dqh.nccapa.capa.entity.Capa;
import com.dqh.nccapa.capa.entity.CapaAction;
import com.dqh.nccapa.capa.service.CapaService;
import com.dqh.nccapa.common.enums.LoaiThayDoi;
import com.dqh.nccapa.nc.service.NcService;
import com.dqh.nccapa.pr.dto.PrActionRequest;
import com.dqh.nccapa.pr.dto.TaoPrRequest;
import com.dqh.nccapa.pr.entity.Pr;
import com.dqh.nccapa.pr.service.PrService;
import com.dqh.nccapa.support.AttachmentFixture;
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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Guard server cho route VIEW theo ma trận menu (không chỉ ẩn menu) + gating form/nút đính kèm theo
 * trạng thái cuối. Dữ liệu THẬT dựng qua service; không dùng đồng hồ hệ thống.
 */
@AutoConfigureMockMvc
class RouteGuardTest extends MySqlTestBase {

    private static final List<String> TAT_CA = List.of("bcmgp", "sxmgp", "qamgp", "qatd", "admin");

    /** Vai trò ĐƯỢC vào từng route (ma trận menu đã chốt). */
    private static final Map<String, List<String>> DUOC = Map.of(
            "/cho-duyet", List.of("qamgp", "qatd"),
            "/capa", List.of("sxmgp", "qamgp", "qatd", "admin"),
            "/pr", List.of("qamgp", "qatd", "admin"),
            "/dashboard", List.of("qamgp", "qatd", "admin"),
            "/ticket", List.of("qatd", "admin"));

    @Autowired MockMvc mockMvc;
    @Autowired CurrentUserService currentUser;
    @Autowired NcService ncService;
    @Autowired CapaService capaService;
    @Autowired PrService prService;
    @Autowired TicketCallbackOutboxRepository outboxRepo;
    @Autowired JdbcTemplate jdbc;

    private String soNcMo;
    private String soNcDong;
    private Long capaId;
    private Long actionId;
    private Long capaDongId;
    private Long actionDongId;
    private Long prId;

    @BeforeEach
    void setUp() {
        DefectSeed.seed(jdbc);
        DefectSeed.seedUserRole(jdbc, "qamgp", "QA_NHA_MAY", "MGP");
        soNcMo = AttachmentFixture.taoNc(ncService, "RG-MO").getSoHieu();
        soNcDong = AttachmentFixture.taoNc(ncService, "RG-DONG").getSoHieu();
        jdbc.update("UPDATE nc SET trang_thai = 'Closed' WHERE so_hieu = ?", soNcDong);

        Capa capa = AttachmentFixture.taoCapaActionPlan(ncService, capaService, "RG-CAPA");
        capaId = capa.getId();
        CapaAction action = AttachmentFixture.themAction(capaService, capaId);
        actionId = action.getId();

        Capa capaDong = AttachmentFixture.taoCapaActionPlan(ncService, capaService, "RG-CAPA-DONG");
        capaDongId = capaDong.getId();
        actionDongId = AttachmentFixture.themAction(capaService, capaDongId).getId();
        jdbc.update("UPDATE capa SET trang_thai = 'Closed' WHERE id = ?", capaDongId);

        // PR riêng (cặp mã hàng + mã lỗi khác các NC ở trên) để render pr-detail.
        TaoPrRequest pr = new TaoPrRequest();
        pr.loaiThayDoi = LoaiThayDoi.CONG_THUC; pr.maHang = "SP-RG-PR"; pr.maLoiId = "FG-01"; pr.actor = "qa";
        Pr created = prService.tao(pr);
        PrActionRequest act = new PrActionRequest(); act.actor = "qa";
        prService.toAnalyzing(created.getId(), act);
        this.prId = created.getId();

        outboxRepo.save(new TicketCallbackOutbox("TCK-RG-1", "early_response", "EVT-RG-1", soNcMo, "{}"));
    }

    private MockHttpSession sessionCua(String username) {
        MockHttpSession session = new MockHttpSession();
        currentUser.doiUser(session, username);
        return session;
    }

    private String html(String url, String username) throws Exception {
        return mockMvc.perform(get(url).session(sessionCua(username)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private void expectChan(String url, String username) throws Exception {
        mockMvc.perform(get(url).session(sessionCua(username)))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/home"));
    }

    @Test
    void routeList_theoMaTran_chanRedirectHome_duocThi200() throws Exception {
        for (Map.Entry<String, List<String>> e : DUOC.entrySet()) {
            for (String u : TAT_CA) {
                if (e.getValue().contains(u)) {
                    html(e.getKey(), u); // 200
                } else {
                    expectChan(e.getKey(), u);
                }
            }
        }
    }

    @Test
    void routeChiTiet_capaVaPr_theoMaTran() throws Exception {
        for (String u : TAT_CA) {
            if (DUOC.get("/capa").contains(u)) {
                assertThat(html("/capa/" + capaId, u)).as("capa-detail %s", u).contains("Thông tin CAPA");
            } else {
                expectChan("/capa/" + capaId, u);
            }
            if (DUOC.get("/pr").contains(u)) {
                assertThat(html("/pr/" + prId, u)).as("pr-detail %s", u).contains("PR#" + prId);
            } else {
                expectChan("/pr/" + prId, u);
            }
        }
    }

    @Test
    void sidebarVaRoute_khop_menuChiHienMucMaRouteChoVao() throws Exception {
        for (String u : TAT_CA) {
            String body = html("/nc/" + soNcMo, u);
            assertThat(body.contains("href=\"/pr\"")).as("menu /pr %s", u).isEqualTo(DUOC.get("/pr").contains(u));
            assertThat(body.contains("href=\"/ticket\"")).as("menu /ticket %s", u).isEqualTo(DUOC.get("/ticket").contains(u));
            assertThat(body.contains("href=\"/dashboard\"")).as("menu /dashboard %s", u).isEqualTo(DUOC.get("/dashboard").contains(u));
            assertThat(body.contains("href=\"/cho-duyet\"")).as("menu /cho-duyet %s", u).isEqualTo(DUOC.get("/cho-duyet").contains(u));
            assertThat(body.contains("href=\"/capa\"")).as("menu /capa %s", u).isEqualTo(DUOC.get("/capa").contains(u));
        }
    }

    @Test
    void home_landingMoiVaiTro_laRouteVaiTroDoDuocVao_khongVongLap() throws Exception {
        Map<String, String> landing = Map.of(
                "bcmgp", "/nc/tao", "sxmgp", "/", "qamgp", "/cho-duyet", "qatd", "/dashboard", "admin", "/dashboard");
        for (Map.Entry<String, String> e : landing.entrySet()) {
            mockMvc.perform(get("/home").session(sessionCua(e.getKey())))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(header().string("Location", e.getValue()));
            html(e.getValue(), e.getKey()); // landing phải là 200, không redirect tiếp
        }
    }

    @Test
    void ncDetail_ncDangMo_coFormUpload_ncClosed_khongCoForm() throws Exception {
        String mo = html("/nc/" + soNcMo, "qamgp");
        assertThat(mo).contains("id=\"att-form\"");
        String dong = html("/nc/" + soNcDong, "qamgp");
        assertThat(dong).doesNotContain("id=\"att-form\"");
        assertThat(dong).contains("id=\"att-list\""); // vẫn giữ danh sách file để xem/tải
        jdbc.update("UPDATE nc SET trang_thai = 'Cancelled' WHERE so_hieu = ?", soNcMo);
        assertThat(html("/nc/" + soNcMo, "qamgp")).doesNotContain("id=\"att-form\"");
    }

    @Test
    void capaDetail_nutDinhKemAction_anKhiCapaTrangThaiCuoi() throws Exception {
        assertThat(html("/capa/" + capaId, "qatd")).contains("data-att-action=\"" + actionId + "\"");
        assertThat(html("/capa/" + capaDongId, "qatd")).doesNotContain("data-att-action=\"" + actionDongId + "\"");
        jdbc.update("UPDATE capa SET trang_thai = 'Ineffective' WHERE id = ?", capaId);
        assertThat(html("/capa/" + capaId, "qatd")).doesNotContain("data-att-action=\"" + actionId + "\"");
    }
}
