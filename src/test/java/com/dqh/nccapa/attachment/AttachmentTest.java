package com.dqh.nccapa.attachment;

import com.dqh.nccapa.attachment.entity.Attachment;
import com.dqh.nccapa.attachment.service.AttachmentService;
import com.dqh.nccapa.capa.entity.Capa;
import com.dqh.nccapa.capa.entity.CapaAction;
import com.dqh.nccapa.capa.service.CapaService;
import com.dqh.nccapa.common.audit.AuditLog;
import com.dqh.nccapa.common.audit.AuditService;
import com.dqh.nccapa.common.error.BusinessException;
import com.dqh.nccapa.nc.service.NcService;
import com.dqh.nccapa.support.AttachmentFixture;
import com.dqh.nccapa.support.DefectSeed;
import com.dqh.nccapa.support.MySqlTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Nhóm 2: upload/lưu/đọc file đính kèm qua AttachmentService (deterministic, không so wall-clock). */
@TestPropertySource(properties = "app.upload.dir=target/test-uploads")
class AttachmentTest extends MySqlTestBase {

    @Autowired AttachmentService service;
    @Autowired NcService ncService;
    @Autowired CapaService capaService;
    @Autowired AuditService auditService;
    @Autowired JdbcTemplate jdbc;

    private String soNc;
    private Long actionId;

    @BeforeEach
    void seed() {
        DefectSeed.seed(jdbc);
        soNc = AttachmentFixture.taoNc(ncService, "ATT-NC").getSoHieu();
        Capa capa = AttachmentFixture.taoCapaActionPlan(ncService, capaService, "ATT-CAPA");
        CapaAction action = AttachmentFixture.themAction(capaService, capa.getId());
        actionId = action.getId();
    }

    private long soFileTrenDia() throws Exception {
        Path dir = Paths.get("target/test-uploads");
        if (!Files.exists(dir)) return 0;
        try (Stream<Path> s = Files.list(dir)) {
            return s.count();
        }
    }

    private Long capaIdCuaAction() {
        return jdbc.queryForObject("SELECT capa_id FROM capa_action WHERE id = ?", Long.class, actionId);
    }

    @AfterEach
    void cleanup() throws Exception {
        Path dir = Paths.get("target/test-uploads");
        if (Files.exists(dir)) {
            try (Stream<Path> walk = Files.walk(dir)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (Exception ignore) { }
                });
            }
        }
    }

    @Test
    void upload_nc_taoBanGhiVaFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "bangchung.txt", "text/plain", "noi dung NC".getBytes(StandardCharsets.UTF_8));
        Attachment att = service.luu("NC", soNc, file, "qa");

        assertThat(att.getId()).isNotNull();
        assertThat(att.getDoiTuong()).isEqualTo("NC");
        assertThat(att.getDoiTuongId()).isEqualTo(soNc);
        assertThat(att.getTenFile()).isEqualTo("bangchung.txt");
        assertThat(att.getTenLuu()).endsWith(".txt");
        assertThat(att.getContentType()).isEqualTo("text/plain");
        assertThat(att.getKichThuoc()).isEqualTo(file.getSize());
        assertThat(Files.exists(Paths.get("target/test-uploads", att.getTenLuu()))).isTrue();
    }

    @Test
    void upload_action_vaDanhSach() {
        MockMultipartFile f1 = new MockMultipartFile(
                "file", "a.txt", "text/plain", "a".getBytes(StandardCharsets.UTF_8));
        MockMultipartFile f2 = new MockMultipartFile(
                "file", "b.txt", "text/plain", "bb".getBytes(StandardCharsets.UTF_8));
        service.luu("ACTION", "10", f1, "sx");
        service.luu("ACTION", "10", f2, "sx");

        List<Attachment> ds = service.danhSach("ACTION", "10");
        assertThat(ds).hasSize(2);
        assertThat(ds).extracting(Attachment::getTenFile)
                .containsExactlyInAnyOrder("a.txt", "b.txt");
        assertThat(service.danhSach("ACTION", "999999")).isEmpty();
    }

    @Test
    void taiVe_traNoiDungDung() throws Exception {
        byte[] data = "tai ve dung noi dung".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "x.txt", "text/plain", data);
        Attachment att = service.luu("NC", soNc, file, "qa");

        AttachmentService.TaiVeResult kq = service.taiVe(att.getId());
        Resource res = kq.getResource();
        assertThat(res.exists()).isTrue();
        try (InputStream in = res.getInputStream()) {
            assertThat(in.readAllBytes()).isEqualTo(data);
        }
        assertThat(kq.getAttachment().getTenFile()).isEqualTo("x.txt");
    }

    @Test
    void upload_fileRong_biChan() {
        MockMultipartFile empty = new MockMultipartFile("file", "e.txt", "text/plain", new byte[0]);
        assertThatThrownBy(() -> service.luu("NC", soNc, empty, "qa"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void upload_doiTuongSai_biChan() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "x.txt", "text/plain", "x".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> service.luu("KHAC", "1", file, "qa"))
                .isInstanceOf(BusinessException.class);
    }

    /** Byte PNG thật (magic 89 50 4E 47 0D 0A 1A 0A) khai image/png → OK, lưu content-type image/png. */
    @Test
    void upload_pngHopLe_sniffRaImagePng() {
        byte[] png = pngBytes();
        MockMultipartFile file = new MockMultipartFile("file", "anh.png", "image/png", png);
        Attachment att = service.luu("NC", soNc, file, "qa");

        assertThat(att.getContentType()).isEqualTo("image/png");
        assertThat(att.getKichThuoc()).isEqualTo(png.length);
    }

    /** Byte .exe (magic 4D 5A "MZ") nhưng khai image/png → từ chối (nội dung không khớp loại khai). */
    @Test
    void upload_exeGiaDangPng_biTuChoi() {
        byte[] exe = new byte[]{0x4D, 0x5A, (byte) 0x90, 0x00, 0x03, 0x00, 0x00, 0x00};
        MockMultipartFile file = new MockMultipartFile("file", "virus.png", "image/png", exe);
        assertThatThrownBy(() -> service.luu("NC", soNc, file, "qa"))
                .isInstanceOf(BusinessException.class);
    }

    // ---- Siết quyền Đợt 1: tồn tại + trạng thái + audit ----

    private MockMultipartFile txt() {
        return new MockMultipartFile("file", "bb.txt", "text/plain", "noi dung".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void upload_ncKhongTonTai_notFound_khongGhiFileKhongGhiDb() throws Exception {
        assertThatThrownBy(() -> service.luu("NC", "KPH-KHONG-CO", txt(), "qa"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Không tìm thấy NC");
        assertThat(soFileTrenDia()).isZero();
        assertThat(service.danhSach("NC", "KPH-KHONG-CO")).isEmpty();
        assertThat(auditService.timTheo("NC", "KPH-KHONG-CO")).isEmpty();
    }

    @Test
    void upload_actionKhongTonTai_notFound_khongGhiFile() throws Exception {
        assertThatThrownBy(() -> service.luu("ACTION", "999999", txt(), "qa"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Không tìm thấy action");
        assertThatThrownBy(() -> service.luu("ACTION", "khong-phai-so", txt(), "qa"))
                .isInstanceOf(BusinessException.class);
        assertThat(soFileTrenDia()).isZero();
    }

    @Test
    void upload_ncClosedHoacCancelled_biChan_khongGhiFile() throws Exception {
        for (String tt : List.of("Closed", "Cancelled")) {
            jdbc.update("UPDATE nc SET trang_thai = ? WHERE so_hieu = ?", tt, soNc);
            assertThatThrownBy(() -> service.luu("NC", soNc, txt(), "qa"))
                    .as("trang thai %s", tt)
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Phiếu đã đóng/huỷ");
        }
        assertThat(soFileTrenDia()).isZero();
        assertThat(service.danhSach("NC", soNc)).isEmpty();
    }

    @Test
    void upload_actionCuaCapaClosedHoacIneffective_biChan_khongGhiFile() throws Exception {
        Long capaId = capaIdCuaAction();
        for (String tt : List.of("Closed", "Ineffective")) {
            jdbc.update("UPDATE capa SET trang_thai = ? WHERE id = ?", tt, capaId);
            assertThatThrownBy(() -> service.luu("ACTION", String.valueOf(actionId), txt(), "qa"))
                    .as("capa %s", tt)
                    .isInstanceOf(BusinessException.class);
        }
        assertThat(soFileTrenDia()).isZero();
        assertThat(service.danhSach("ACTION", String.valueOf(actionId))).isEmpty();
    }

    @Test
    void upload_ncDangMo_thanhCong_vaCoDongAudit() {
        service.luu("NC", soNc, txt(), "qa");
        List<AuditLog> logs = auditService.timTheo("NC", soNc).stream()
                .filter(a -> "UPLOAD_ATTACHMENT".equals(a.getHanhDong())).toList();
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getGiaTriMoi()).isEqualTo("bb.txt");
        assertThat(logs.get(0).getNguoiThucHien()).isEqualTo("qa");
    }

    @Test
    void upload_actionCuaCapaDangMo_thanhCong_vaAuditTrenCapa() {
        service.luu("ACTION", String.valueOf(actionId), txt(), "sx");
        List<AuditLog> logs = auditService.timTheo("CAPA", String.valueOf(capaIdCuaAction())).stream()
                .filter(a -> "UPLOAD_ATTACHMENT".equals(a.getHanhDong())).toList();
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getLyDo()).isEqualTo("action #" + actionId);
    }

    /** PNG hợp lệ: header gồm 8-byte signature + tối thiểu để test đủ dài. */
    static byte[] pngBytes() {
        return new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52
        };
    }
}
