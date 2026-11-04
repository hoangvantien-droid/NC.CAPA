package com.dqh.nccapa.attachment;

import com.dqh.nccapa.attachment.service.AttachmentService;
import com.dqh.nccapa.common.error.BusinessException;
import com.dqh.nccapa.nc.service.NcService;
import com.dqh.nccapa.support.AttachmentFixture;
import com.dqh.nccapa.support.DefectSeed;
import com.dqh.nccapa.support.MySqlTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Giới hạn TỔNG dung lượng cho mỗi đối tượng: đặt max-total nhỏ (100 byte) để dễ vượt.
 * Tách riêng class vì @TestPropertySource cấu hình ở cấp lớp. (deterministic, không so wall-clock)
 */
@TestPropertySource(properties = {
        "app.upload.dir=target/test-uploads",
        "app.upload.max-total-bytes-per-object=100"
})
class AttachmentTongDungLuongTest extends MySqlTestBase {

    @Autowired AttachmentService service;
    @Autowired NcService ncService;
    @Autowired JdbcTemplate jdbc;

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
    void upload_vuotTongDungLuong_biTuChoi() {
        byte[] data = "x".repeat(60).getBytes(StandardCharsets.UTF_8); // 60 byte/file
        MockMultipartFile f1 = new MockMultipartFile("file", "a.txt", "text/plain", data);
        MockMultipartFile f2 = new MockMultipartFile("file", "b.txt", "text/plain", data);

        DefectSeed.seed(jdbc);
        String so = AttachmentFixture.taoNc(ncService, "ATT-DL").getSoHieu();
        service.luu("NC", so, f1, "qa"); // 60 <= 100 → OK
        // 60 (đã có) + 60 = 120 > 100 → từ chối
        assertThatThrownBy(() -> service.luu("NC", so, f2, "qa"))
                .isInstanceOf(BusinessException.class);
    }
}
