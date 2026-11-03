package com.dqh.nccapa.demo;

import com.dqh.nccapa.common.enums.NcSource;
import com.dqh.nccapa.common.enums.NcStatus;
import com.dqh.nccapa.common.error.BusinessException;
import com.dqh.nccapa.nc.dto.ApproveRequest;
import com.dqh.nccapa.nc.dto.SubmitApprovalRequest;
import com.dqh.nccapa.nc.dto.TaoNcRequest;
import com.dqh.nccapa.nc.entity.Nc;
import com.dqh.nccapa.nc.service.NcService;
import com.dqh.nccapa.support.DefectSeed;
import com.dqh.nccapa.support.MySqlTestBase;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tính năng chuyển tài khoản giả lập (demo, KHÔNG login thật):
 * - {@link CurrentUserService}: user mặc định, đổi user, danh sách.
 * - {@code POST /demo/switch-user} đổi user trong session rồi redirect về Referer.
 * - SoD vẫn hoạt động khi actor lấy từ user demo hiện tại: A đề xuất + A tự duyệt bị chặn, B duyệt được.
 */
@AutoConfigureMockMvc
class DemoUserSwitchTest extends MySqlTestBase {

    @Autowired MockMvc mockMvc;
    @Autowired CurrentUserService currentUser;
    @Autowired NcService ncService;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setUp() { DefectSeed.seed(jdbc); }

    @Test
    void danhSach_dung5TaiKhoan() {
        assertThat(currentUser.danhSach()).hasSize(5);
    }

    @Test
    void userMacDinh_laTaiKhoanDauTien() {
        HttpSession session = new MockHttpSession();
        DemoUser u = currentUser.hienTai(session);
        assertThat(u.username()).isEqualTo("baocao");
    }

    @Test
    void doiUser_hopLe_thayDoiUserHienTai() {
        HttpSession session = new MockHttpSession();
        boolean ok = currentUser.doiUser(session, "qamgp");
        assertThat(ok).isTrue();
        assertThat(currentUser.hienTai(session).username()).isEqualTo("qamgp");
        assertThat(currentUser.hienTai(session).vaiTro()).isEqualTo(DemoRole.QA_NHA_MAY);
    }

    @Test
    void doiUser_usernameKhongHopLe_khongDoi() {
        HttpSession session = new MockHttpSession();
        boolean ok = currentUser.doiUser(session, "khong-ton-tai");
        assertThat(ok).isFalse();
        assertThat(currentUser.hienTai(session).username()).isEqualTo("baocao"); // vẫn mặc định
    }

    @Test
    void getDemoUsers_traJsonDung5() throws Exception {
        mockMvc.perform(get("/demo/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", org.hamcrest.Matchers.hasSize(5)))
                .andExpect(jsonPath("$.data[0].username").value("baocao"));
    }

    @Test
    void postSwitchUser_doiSessionVaRedirect() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/demo/switch-user")
                        .param("username", "qatd")
                        .header("Referer", "/nc/KPH-0001")
                        .session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/nc/KPH-0001"));
        // session phản ánh user mới
        assertThat(currentUser.hienTai(session).username()).isEqualTo("qatd");
    }

    @Test
    void postSwitchUser_khongCoReferer_redirectVeGoc() throws Exception {
        mockMvc.perform(post("/demo/switch-user").param("username", "admin"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/"));
    }

    @Test // SoD chạy trên actor lấy từ user demo: A đề xuất không được tự duyệt, B duyệt được
    void soD_theoUserDemo_AkhongTuDuyet_Bduyet() {
        // A = qamgp đề xuất
        MockHttpSession sessionA = new MockHttpSession();
        currentUser.doiUser(sessionA, "qamgp");
        String actorA = currentUser.hienTai(sessionA).username();

        Nc nc = taoNcNang("LOT-DEMO-1");
        SubmitApprovalRequest s = new SubmitApprovalRequest();
        s.nguyenNhanSoBo = "rc"; s.dispositionDeXuat = "REWORK";
        s.chiPhiUocTinh = new BigDecimal("1000000"); s.boPhanThucHien = "SX";
        s.actor = actorA;
        ncService.submitApproval(nc.getSoHieu(), s);

        // A tự duyệt -> bị chặn (SoD)
        ApproveRequest aSelf = new ApproveRequest(); aSelf.actor = actorA;
        assertThatThrownBy(() -> ncService.approve(nc.getSoHieu(), aSelf))
                .isInstanceOf(BusinessException.class);

        // Đổi sang B = qatd, B duyệt (cấp 1 cho NC Nặng -> vẫn PendingApproval)
        MockHttpSession sessionB = new MockHttpSession();
        currentUser.doiUser(sessionB, "qatd");
        ApproveRequest aB = new ApproveRequest(); aB.actor = currentUser.hienTai(sessionB).username();
        ncService.approve(nc.getSoHieu(), aB);
        assertThat(ncService.layNc(nc.getSoHieu()).getTrangThai()).isEqualTo(NcStatus.PendingApproval);
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
