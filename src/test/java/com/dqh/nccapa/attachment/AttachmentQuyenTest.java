package com.dqh.nccapa.attachment;

import com.dqh.nccapa.attachment.service.AttachmentService;
import com.dqh.nccapa.capa.entity.Capa;
import com.dqh.nccapa.capa.entity.CapaAction;
import com.dqh.nccapa.capa.service.CapaService;
import com.dqh.nccapa.common.error.BusinessException;
import com.dqh.nccapa.common.error.ErrorCode;
import com.dqh.nccapa.demo.CurrentUserService;
import com.dqh.nccapa.nc.service.NcService;
import com.dqh.nccapa.support.AttachmentFixture;
import com.dqh.nccapa.support.DefectSeed;
import com.dqh.nccapa.support.MySqlTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Siết quyền file đính kèm Đợt 2: vai trò theo trạng thái NC, người phụ trách action, scope nhà máy,
 * actor bắt buộc, và form upload trên nc-detail. Vai trò THẬT seed qua app_user_role (không dùng DemoRole
 * cho guard backend). Deterministic, không so wall-clock.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.upload.dir=target/test-uploads-quyen")
class AttachmentQuyenTest extends MySqlTestBase {

    private static final String DIR = "target/test-uploads-quyen";

    @Autowired AttachmentService service;
    @Autowired NcService ncService;
    @Autowired CapaService capaService;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mockMvc;
    @Autowired CurrentUserService currentUser;

    private String soNc;
    private Long actionId;

    @BeforeEach
    void seed() {
        DefectSeed.seed(jdbc);
        soNc = AttachmentFixture.taoNc(ncService, "QUYEN-NC").getSoHieu();
        Capa capa = AttachmentFixture.taoCapaActionPlan(ncService, capaService, "QUYEN-CAPA");
        CapaAction action = AttachmentFixture.themAction(capaService, capa.getId()); // phụ trách: sxmgp
        actionId = action.getId();
        // Seed vai trò SAU khi dựng fixture để không đổi hành vi CapaService lúc tạo CAPA/action.
        DefectSeed.seedUserRole(jdbc, "baocao", "NGUOI_BAO_CAO", "MGP");
        DefectSeed.seedUserRole(jdbc, "sxmgp", "BO_PHAN_SX", "MGP");
        DefectSeed.seedUserRole(jdbc, "khomgp", "BO_PHAN_KHO", "MGP");
        DefectSeed.seedUserRole(jdbc, "qamgp", "QA_NHA_MAY", "MGP");
        DefectSeed.seedUserRole(jdbc, "qadna", "QA_NHA_MAY", "DNA");
        DefectSeed.seedUserRole(jdbc, "qatd", "QA_TAP_DOAN");
        DefectSeed.seedUserRole(jdbc, "admin", "ADMIN");
    }

    @AfterEach
    void cleanup() throws Exception {
        Path dir = Paths.get(DIR);
        if (Files.exists(dir)) {
            try (Stream<Path> walk = Files.walk(dir)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (Exception ignore) { }
                });
            }
        }
    }

    private MockMultipartFile txt() {
        return new MockMultipartFile("file", "bb.txt", "text/plain", "noi dung".getBytes(StandardCharsets.UTF_8));
    }

    private void datTrangThai(String tt) {
        jdbc.update("UPDATE nc SET trang_thai = ? WHERE so_hieu = ?", tt, soNc);
    }

    private long soFileTrenDia() throws Exception {
        Path dir = Paths.get(DIR);
        if (!Files.exists(dir)) return 0;
        try (Stream<Path> s = Files.list(dir)) {
            return s.count();
        }
    }

    private long soDong() {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM attachment", Long.class);
        return n == null ? 0 : n;
    }

    /** Upload bị ROLE_DENIED và KHÔNG ghi file, KHÔNG ghi DB. */
    private void phaiBiChan(String loai, String id, String actor, String ctx) throws Exception {
        long files = soFileTrenDia();
        long rows = soDong();
        assertThatThrownBy(() -> service.luu(loai, id, txt(), actor))
                .as(ctx)
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo(ErrorCode.ROLE_DENIED));
        assertThat(soFileTrenDia()).as(ctx + " - file").isEqualTo(files);
        assertThat(soDong()).as(ctx + " - db").isEqualTo(rows);
    }

    private void phaiDuoc(String loai, String id, String actor, String ctx) {
        assertThat(service.luu(loai, id, txt(), actor).getId()).as(ctx).isNotNull();
    }

    // ---- Ma trận theo trạng thái NC ----

    @Test
    void ncNew_moiVaiTroNghiepVuDuoc_adminBiChan() throws Exception {
        for (String u : List.of("baocao", "sxmgp", "khomgp", "qamgp", "qatd")) {
            phaiDuoc("NC", soNc, u, "New/" + u);
        }
        phaiBiChan("NC", soNc, "admin", "New/admin");
    }

    @Test
    void ncContainmentPendingApprovalRejected_chiQa() throws Exception {
        for (String tt : List.of("Containment", "PendingApproval", "Rejected")) {
            datTrangThai(tt);
            phaiDuoc("NC", soNc, "qamgp", tt + "/qamgp");
            phaiDuoc("NC", soNc, "qatd", tt + "/qatd");
            phaiBiChan("NC", soNc, "baocao", tt + "/baocao");
            phaiBiChan("NC", soNc, "sxmgp", tt + "/sxmgp");
            phaiBiChan("NC", soNc, "khomgp", tt + "/khomgp");
            phaiBiChan("NC", soNc, "admin", tt + "/admin");
        }
    }

    @Test
    void ncProcessingPendingVerification_qaSxKhoDuoc_baoCaoVaAdminBiChan() throws Exception {
        for (String tt : List.of("Processing", "PendingVerification")) {
            datTrangThai(tt);
            for (String u : List.of("qamgp", "qatd", "sxmgp", "khomgp")) {
                phaiDuoc("NC", soNc, u, tt + "/" + u);
            }
            phaiBiChan("NC", soNc, "baocao", tt + "/baocao");
            phaiBiChan("NC", soNc, "admin", tt + "/admin");
        }
    }

    // ---- Action ----

    @Test
    void action_qaVaNguoiPhuTrachDuoc_nguoiKhacBiChan() throws Exception {
        String id = String.valueOf(actionId);
        phaiDuoc("ACTION", id, "qamgp", "action/qamgp");
        phaiDuoc("ACTION", id, "qatd", "action/qatd");
        phaiDuoc("ACTION", id, "sxmgp", "action/phu-trach");
        phaiBiChan("ACTION", id, "baocao", "action/baocao");
        phaiBiChan("ACTION", id, "khomgp", "action/khomgp");
        phaiBiChan("ACTION", id, "admin", "action/admin");
    }

    // ---- Scope nhà máy ----

    @Test
    void scope_qaNhaMayKhac_khongUploadXemTai_qaTapDoanVaAdminXemTaiDuoc() throws Exception {
        long id = service.luu("NC", soNc, txt(), "qamgp").getId();

        // QA nhà máy DNA thao tác NC nhà máy MGP: bị chặn cả upload, xem danh sách, tải.
        phaiBiChan("NC", soNc, "qadna", "scope/upload qadna");
        assertThatThrownBy(() -> service.danhSach("NC", soNc, "qadna"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("Ngoài phạm vi nhà máy");
        assertThatThrownBy(() -> service.taiVe(id, "qadna"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("Ngoài phạm vi nhà máy");

        // QA tập đoàn (nhà máy null): upload/xem/tải được.
        phaiDuoc("NC", soNc, "qatd", "scope/upload qatd");
        assertThat(service.danhSach("NC", soNc, "qatd")).hasSize(2);
        assertThat(service.taiVe(id, "qatd").getAttachment().getId()).isEqualTo(id);

        // ADMIN: xem + tải được, nhưng không upload.
        assertThat(service.danhSach("NC", soNc, "admin")).hasSize(2);
        assertThat(service.taiVe(id, "admin").getAttachment().getId()).isEqualTo(id);
        phaiBiChan("NC", soNc, "admin", "scope/upload admin");

        // QA cùng nhà máy xem/tải được.
        assertThat(service.danhSach("NC", soNc, "qamgp")).hasSize(2);
    }

    @Test
    void scope_ncDoiSangNhaMayKhac_qaMgpBiChan() throws Exception {
        jdbc.update("UPDATE nc SET nha_may = 'DNA' WHERE so_hieu = ?", soNc);
        phaiBiChan("NC", soNc, "qamgp", "scope/nc-dna qamgp");
        phaiDuoc("NC", soNc, "qadna", "scope/nc-dna qadna");
        phaiDuoc("NC", soNc, "qatd", "scope/nc-dna qatd");
    }

    @Test
    void scope_action_theoNhaMayCuaNcGoc() throws Exception {
        String id = String.valueOf(actionId);
        long attId = service.luu("ACTION", id, txt(), "qamgp").getId();
        phaiBiChan("ACTION", id, "qadna", "scope/action qadna");
        assertThatThrownBy(() -> service.danhSach("ACTION", id, "qadna"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("Ngoài phạm vi nhà máy");
        assertThatThrownBy(() -> service.taiVe(attId, "qadna"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("Ngoài phạm vi nhà máy");
        assertThat(service.danhSach("ACTION", id, "qatd")).hasSize(1);
        assertThat(service.danhSach("ACTION", id, "admin")).hasSize(1);
    }

    // ---- Đọc: mọi vai trò xem/tải được (không áp ma trận trạng thái) ----

    @Test
    void doc_moiVaiTroCungNhaMayXemTaiDuoc_khiNcDaDong() throws Exception {
        long id = service.luu("NC", soNc, txt(), "qamgp").getId();
        datTrangThai("Closed");
        for (String u : List.of("baocao", "sxmgp", "khomgp", "qamgp", "qatd", "admin")) {
            assertThat(service.danhSach("NC", soNc, u)).as(u).hasSize(1);
            assertThat(service.taiVe(id, u).getAttachment().getId()).as(u).isEqualTo(id);
        }
    }

    // ---- Actor rỗng ----

    @Test
    void actorRong_uploadVaDocDeuBiTuChoi() throws Exception {
        long id = service.luu("NC", soNc, txt(), "qamgp").getId();
        for (String rong : new String[] {null, "", "   "}) {
            phaiBiChan("NC", soNc, rong, "upload actor='" + rong + "'");
            assertThatThrownBy(() -> service.danhSach("NC", soNc, rong))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo(ErrorCode.ROLE_DENIED));
            assertThatThrownBy(() -> service.taiVe(id, rong))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo(ErrorCode.ROLE_DENIED));
        }
    }

    @Test
    void http_getKhongActor_tra403() throws Exception {
        long id = service.luu("NC", soNc, txt(), "qamgp").getId();
        mockMvc.perform(get("/api/v1/attachment").param("doiTuong", "NC").param("doiTuongId", soNc))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/attachment/{id}/download", id))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/attachment").param("doiTuong", "NC").param("doiTuongId", soNc)
                        .param("actor", "qamgp"))
                .andExpect(status().isOk());
    }

    // ---- UI nc-detail ----

    private String html(String url, String username) throws Exception {
        MockHttpSession session = new MockHttpSession();
        currentUser.doiUser(session, username);
        return mockMvc.perform(get(url).session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void ui_formUploadChiHienChoVaiTroDuocPhepTheoTrangThai() throws Exception {
        String url = "/nc/" + soNc;
        // New: mọi vai trò nghiệp vụ có form; admin không.
        for (String u : List.of("baocao", "sxmgp", "qamgp", "qatd")) {
            assertThat(html(url, u)).as("New/" + u).contains("id=\"att-form\"");
        }
        assertThat(html(url, "admin")).doesNotContain("id=\"att-form\"");

        // PendingApproval: chỉ QA.
        datTrangThai("PendingApproval");
        assertThat(html(url, "qamgp")).contains("id=\"att-form\"");
        assertThat(html(url, "qatd")).contains("id=\"att-form\"");
        assertThat(html(url, "baocao")).doesNotContain("id=\"att-form\"");
        assertThat(html(url, "sxmgp")).doesNotContain("id=\"att-form\"");
        assertThat(html(url, "admin")).doesNotContain("id=\"att-form\"");

        // Processing: thêm SX; người báo cáo vẫn không. Danh sách file luôn có.
        datTrangThai("Processing");
        assertThat(html(url, "sxmgp")).contains("id=\"att-form\"");
        String bc = html(url, "baocao");
        assertThat(bc).doesNotContain("id=\"att-form\"");
        assertThat(bc).contains("id=\"att-list\"");


        // Ngoài scope nhà máy: qamgp (MGP) không thấy form khi NC thuộc nhà máy DNA.
        jdbc.update("UPDATE nc SET nha_may = 'DNA' WHERE so_hieu = ?", soNc);
        assertThat(html(url, "qamgp")).doesNotContain("id=\"att-form\"");
        assertThat(html(url, "qatd")).contains("id=\"att-form\"");
    }
}
