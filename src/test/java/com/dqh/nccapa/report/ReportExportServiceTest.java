package com.dqh.nccapa.report;

import com.dqh.nccapa.support.MySqlTestBase;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Xuất báo cáo Excel/PDF: dựng vài NC/CAPA qua JdbcTemplate (deterministic, không so wall-clock),
 * gọi service xuất file rồi kiểm nội dung byte[].
 */
class ReportExportServiceTest extends MySqlTestBase {

    @Autowired ReportExportService exportService;
    @Autowired JdbcTemplate jdbc;

    private void chenNc(String so, String trangThai, String nhaMay,
                        String maLoi, String maHang, BigDecimal chiPhi, int ageDays) {
        jdbc.update(
                "INSERT INTO nc (so_hieu, loai_nc, muc_nghiem_trong, ma_hang_chinh, ma_loi_chinh, "
                        + "nguon_phat_sinh, trang_thai, nha_may, chi_phi_thuc_te, nguoi_bao_cao, "
                        + "created_at, created_by) VALUES (?,?,?,?,?,?,?,?,?,?, "
                        + "DATE_SUB(NOW(), INTERVAL ? DAY), ?)",
                so, "THANH_PHAM", "NHE", maHang, maLoi, "THU_CONG", trangThai, nhaMay,
                chiPhi, "u", ageDays, "u");
    }

    private void seed() {
        chenNc("NC-1", "Processing", "MGP", "FG-01", "SP001", new BigDecimal("100.00"), 3);
        chenNc("NC-2", "Processing", "MGP", "FG-01", "SP001", new BigDecimal("250.00"), 15);
        chenNc("NC-3", "Containment", "DNA", "FG-02", "SP002", new BigDecimal("50.00"), 40);
    }

    @Test
    void excel_khongRong_vaMoDuocBangPoi() throws Exception {
        seed();
        byte[] xlsx = exportService.excel(null);
        assertThat(xlsx).isNotEmpty();

        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            // Có các sheet mong đợi
            assertThat(wb.getSheet("Tổng quan")).isNotNull();
            assertThat(wb.getSheet("NC theo mức")).isNotNull();
            assertThat(wb.getSheet("Cost of quality")).isNotNull();
            // Ô tiêu đề trên sheet Tổng quan (A1) chứa chữ "Báo cáo NC/CAPA"
            String a1 = wb.getSheet("Tổng quan").getRow(0).getCell(0).getStringCellValue();
            assertThat(a1).contains("Báo cáo NC/CAPA");
        }
    }

    @Test
    void pdf_khongRong_vaBatDauBangMagicPdf() {
        seed();
        byte[] pdf = exportService.pdf(null);
        assertThat(pdf).isNotEmpty();
        // Magic number "%PDF" = 0x25 0x50 0x44 0x46
        assertThat(pdf[0]).isEqualTo((byte) 0x25);
        assertThat(pdf[1]).isEqualTo((byte) 0x50);
        assertThat(pdf[2]).isEqualTo((byte) 0x44);
        assertThat(pdf[3]).isEqualTo((byte) 0x46);
    }

    @Test
    void excel_locTheoNhaMay_vanDungDuoc() throws Exception {
        seed();
        byte[] xlsx = exportService.excel("MGP");
        assertThat(xlsx).isNotEmpty();
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            assertThat(wb.getSheet("Tổng quan")).isNotNull();
        }
    }
}
