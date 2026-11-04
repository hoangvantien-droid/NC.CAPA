package com.dqh.nccapa.demo;

import com.dqh.nccapa.common.enums.NcSource;
import com.dqh.nccapa.nc.dto.SubmitApprovalRequest;
import com.dqh.nccapa.nc.dto.TaoNcRequest;
import com.dqh.nccapa.nc.entity.Nc;
import com.dqh.nccapa.nc.service.NcService;
import com.dqh.nccapa.support.DefectSeed;
import com.dqh.nccapa.support.MySqlTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phân quyền UI mức cao (demo, KHÔNG Spring Security): kiểm menu sidebar, huy hiệu quyền,
 * landing redirect theo vai trò, và ẩn/hiện nút duyệt NC theo ma trận. KHÔNG so wall-clock.
 */
@AutoConfigureMockMvc
class ViewAuthorizationTest extends MySqlTestBase {

    @Autowired MockMvc mockMvc;
    @Autowired CurrentUserService currentUser;
    @Autowired NcService ncService;
    @Autowired JdbcTemplate jdbc;

    private String soNcNang;

    @BeforeEach
    void setUp() {
        DefectSeed.seed(jdbc);
        // Seed role THẬT cho người đề xuất + người duyệt (guard backend tra app_user_role).
        DefectSeed.seedUserRole(jdbc, "qamgp", "QA_NHA_MAY", "MGP");
        DefectSeed.seedUserRole(jdbc, "qatd", "QA_TAP_DOAN");

        // Tạo 1 NC Nặng và đưa về PendingApproval để bước kế tiếp hiện nút approve/reject.
        Nc nc = taoNcNang("LOT-AUTHZ-1");
        SubmitApprovalRequest s = new SubmitApprovalRequest();
        s.nguyenNhanSoBo = "rc"; s.dispositionDeXuat = "REWORK";
        s.chiPhiUocTinh = new BigDecimal("1000000"); s.boPhanThucHien = "SX";
        s.actor = "qamgp";
        ncService.submitApproval(nc.getSoHieu(), s);
        this.soNcNang = nc.getSoHieu();
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

    // ---- Nút duyệt NC Nặng theo vai trò ----

    @Test
    void baocao_khongThayNutDuyet_khongThayMenuHanCheQuyen() throws Exception {
        String body = html("/nc/" + soNcNang, "baocao");
        assertThat(body).doesNotContain("data-action=\"approve\"");
        assertThat(body).doesNotContain("data-action=\"reject\"");
        // sidebar không có các menu ngoài quyền người báo cáo
        assertThat(body).doesNotContain("/cho-duyet");
        assertThat(body).doesNotContain("/pr\"");
        assertThat(body).doesNotContain("/ticket");
        assertThat(body).doesNotContain("/quan-tri");
        // huy hiệu quyền hiển thị
        assertThat(body).contains("Đang xem với quyền");
    }

    @Test
    void qaNhaMay_thayNutDuyetNcNang_coMenuChoDuyet_khongCoQuanTri() throws Exception {
        String body = html("/nc/" + soNcNang, "qamgp");
        assertThat(body).contains("data-action=\"approve\"");
        assertThat(body).contains("/cho-duyet");
        assertThat(body).doesNotContain("/quan-tri");
    }

    @Test
    void qaTapDoan_thayNutDuyet_coMenuDashboardTicketPr() throws Exception {
        String body = html("/nc/" + soNcNang, "qatd");
        assertThat(body).contains("data-action=\"approve\"");
        assertThat(body).contains("/dashboard");
        assertThat(body).contains("/ticket");
        assertThat(body).contains("/pr\"");
    }

    @Test
    void admin_coMenuQuanTri_nhungKhongThayNutDuyet() throws Exception {
        String body = html("/nc/" + soNcNang, "admin");
        assertThat(body).contains("/quan-tri");
        assertThat(body).doesNotContain("data-action=\"approve\"");
        assertThat(body).doesNotContain("data-action=\"reject\"");
    }

    // ---- Landing theo vai trò ----

    @Test
    void home_baocao_redirectToTaoNc() throws Exception {
        mockMvc.perform(get("/home").session(sessionCua("baocao")))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/nc/tao"));
    }

    @Test
    void home_qaNhaMay_redirectToChoDuyet() throws Exception {
        mockMvc.perform(get("/home").session(sessionCua("qamgp")))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/cho-duyet"));
    }

    @Test
    void home_qaTapDoan_redirectToDashboard() throws Exception {
        mockMvc.perform(get("/home").session(sessionCua("qatd")))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/dashboard"));
    }

    @Test
    void home_sx_redirectToDanhSach() throws Exception {
        mockMvc.perform(get("/home").session(sessionCua("sxmgp")))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/"));
    }

    // ---- Gating trang quản trị + hàng chờ duyệt ----

    @Test
    void quanTri_admin200_nonAdminRedirectHome() throws Exception {
        mockMvc.perform(get("/quan-tri").session(sessionCua("admin")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/quan-tri").session(sessionCua("baocao")))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/home"));
    }

    @Test
    void choDuyet_qaNhaMay_200() throws Exception {
        mockMvc.perform(get("/cho-duyet").session(sessionCua("qamgp")))
                .andExpect(status().isOk());
    }

    private Nc taoNcNang(String lo) {
        TaoNcRequest r = new TaoNcRequest();
        r.loaiNc = "TRONG_SX"; r.nguonPhatSinh = NcSource.THU_CONG;
        r.nhaMay = "MGP"; r.nguoiBaoCao = "user01"; r.loKiem = lo;
        TaoNcRequest.ChiTieuItem ct = new TaoNcRequest.ChiTieuItem();
        ct.maLoiId = "PRC-02"; ct.maHang = "SP001"; // Nặng
        r.chiTieu = List.of(ct);
        return ncService.taoNc(r);
    }
}
