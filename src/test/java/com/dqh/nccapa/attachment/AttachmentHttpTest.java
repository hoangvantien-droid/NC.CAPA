package com.dqh.nccapa.attachment;

import com.dqh.nccapa.capa.entity.Capa;
import com.dqh.nccapa.capa.entity.CapaAction;
import com.dqh.nccapa.capa.service.CapaService;
import com.dqh.nccapa.nc.service.NcService;
import com.dqh.nccapa.support.AttachmentFixture;
import com.dqh.nccapa.support.DefectSeed;
import com.dqh.nccapa.support.MySqlTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Nhóm 2: test tầng HTTP (MockMvc) cho AttachmentController — upload, whitelist, download, list.
 * Không so wall-clock; dọn thư mục upload riêng sau mỗi test.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.upload.dir=target/test-uploads-http")
class AttachmentHttpTest extends MySqlTestBase {

    private static final String UPLOAD_DIR = "target/test-uploads-http";

    @Autowired MockMvc mockMvc;
    @Autowired NcService ncService;
    @Autowired CapaService capaService;
    @Autowired JdbcTemplate jdbc;

    private String soNc;
    private String actionId;

    @BeforeEach
    void seed() {
        DefectSeed.seed(jdbc);
        soNc = AttachmentFixture.taoNc(ncService, "ATT-HTTP").getSoHieu();
        Capa capa = AttachmentFixture.taoCapaActionPlan(ncService, capaService, "ATT-HTTP-CAPA");
        CapaAction action = AttachmentFixture.themAction(capaService, capa.getId());
        actionId = String.valueOf(action.getId());
    }

    @AfterEach
    void cleanup() throws Exception {
        Path dir = Paths.get(UPLOAD_DIR);
        if (Files.exists(dir)) {
            try (Stream<Path> walk = Files.walk(dir)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (Exception ignore) { }
                });
            }
        }
    }

    @Test
    void upload_png_hopLe_tra200VaMetadata() throws Exception {
        // Byte PNG thật (magic 89 50 4E 47 0D 0A 1A 0A) để qua bước sniff magic-number.
        byte[] png = new byte[] {
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52
        };
        MockMultipartFile file = new MockMultipartFile(
                "file", "anh.png", "image/png", png);
        mockMvc.perform(multipart("/api/v1/attachment")
                        .file(file)
                        .param("doiTuong", "NC")
                        .param("doiTuongId", soNc)
                        .param("actor", "qa"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.tenFile").value("anh.png"))
                .andExpect(jsonPath("$.data.doiTuong").value("NC"))
                .andExpect(jsonPath("$.data.contentType").value("image/png"));
    }

    @Test
    void upload_contentTypeNgoaiWhitelist_traLoiNghiepVu() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "virus.exe", "application/x-msdownload", new byte[] {9, 9, 9});
        mockMvc.perform(multipart("/api/v1/attachment")
                        .file(file)
                        .param("doiTuong", "NC")
                        .param("doiTuongId", soNc))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("BUSINESS_RULE"))
                .andExpect(jsonPath("$.error.field").value("file"));
    }

    @Test
    void upload_ncKhongTonTai_tra404_khongGhiDb() throws Exception {
        mockMvc.perform(multipart("/api/v1/attachment")
                        .file(new MockMultipartFile("file", "a.txt", "text/plain", "a".getBytes(StandardCharsets.UTF_8)))
                        .param("doiTuong", "NC")
                        .param("doiTuongId", "KPH-KHONG-CO"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM attachment", Integer.class);
        org.assertj.core.api.Assertions.assertThat(n).isZero();
    }

    @Test
    void upload_ncClosed_tra422() throws Exception {
        jdbc.update("UPDATE nc SET trang_thai = ? WHERE so_hieu = ?", "Closed", soNc);
        mockMvc.perform(multipart("/api/v1/attachment")
                        .file(new MockMultipartFile("file", "a.txt", "text/plain", "a".getBytes(StandardCharsets.UTF_8)))
                        .param("doiTuong", "NC")
                        .param("doiTuongId", soNc))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("BUSINESS_RULE"));
    }

    @Test
    void download_tra200VaHeaderDung() throws Exception {
        byte[] data = "tai ve http".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile(
                "file", "note.txt", "text/plain", data);
        MvcResult uploaded = mockMvc.perform(multipart("/api/v1/attachment")
                        .file(file)
                        .param("doiTuong", "NC")
                        .param("doiTuongId", soNc))
                .andExpect(status().isOk())
                .andReturn();
        Number idNum = com.jayway.jsonpath.JsonPath.read(
                uploaded.getResponse().getContentAsString(), "$.data.id");
        long id = idNum.longValue();

        mockMvc.perform(get("/api/v1/attachment/{id}/download", id))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN));
    }

    @Test
    void danhSach_traDungSoBanGhi() throws Exception {
        MockMultipartFile f1 = new MockMultipartFile(
                "file", "a.txt", "text/plain", "a".getBytes(StandardCharsets.UTF_8));
        MockMultipartFile f2 = new MockMultipartFile(
                "file", "b.txt", "text/plain", "bb".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/v1/attachment").file(f1)
                        .param("doiTuong", "ACTION").param("doiTuongId", actionId))
                .andExpect(status().isOk());
        mockMvc.perform(multipart("/api/v1/attachment").file(f2)
                        .param("doiTuong", "ACTION").param("doiTuongId", actionId))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/attachment")
                        .param("doiTuong", "ACTION").param("doiTuongId", actionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(2)));
    }
}
