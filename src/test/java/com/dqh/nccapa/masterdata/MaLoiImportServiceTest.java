package com.dqh.nccapa.masterdata;

import com.dqh.nccapa.masterdata.service.ImportResult;
import com.dqh.nccapa.masterdata.service.MaLoiImportService;
import com.dqh.nccapa.support.MySqlTestBase;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Import bộ mã lỗi từ xlsx (POI): tạo workbook IN-MEMORY trong test, đưa InputStream vào
 * {@link MaLoiImportService#importXlsx}. Kiểm all-or-nothing + idempotent qua JdbcTemplate.
 * Deterministic, KHÔNG so wall-clock, KHÔNG dùng file trên đĩa.
 */
class MaLoiImportServiceTest extends MySqlTestBase {

    @Autowired MaLoiImportService service;
    @Autowired JdbcTemplate jdbc;

    /** Dựng workbook hợp lệ: 1 nhóm (REC) + 2 mã lỗi hợp lệ. */
    private byte[] workbookHopLe() {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet nhom = wb.createSheet("NhomLoi");
            dong(nhom, 0, "nhom_loi_id", "ten_nhom", "theo_nguon");
            dong(nhom, 1, "REC", "Loi nhan hang/NVL", "Nhan hang");

            Sheet ma = wb.createSheet("MaLoi");
            dong(ma, 0, "ma_loi_id", "nhom_loi_id", "ten_ma_loi", "muc_nghiem_trong_default",
                    "moi_nguy_default", "bat_buoc_khoanh_vung", "nhom_n_kiem_chung");
            dong(ma, 1, "REC-02", "REC", "NVL nhiem tap chat la", "NANG", "VAT_LY", "0", "SX_TP_NVL");
            dong(ma, 2, "REC-03", "REC", "NVL vuot nguong vi sinh", "NGHIEM_TRONG", "VI_SINH", "1", "SX_TP_NVL");
            return toBytes(wb);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void dong(Sheet sheet, int r, String... vals) {
        Row row = sheet.createRow(r);
        for (int c = 0; c < vals.length; c++) {
            row.createCell(c).setCellValue(vals[c]);
        }
    }

    private static byte[] toBytes(Workbook wb) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            wb.write(out);
            return out.toByteArray();
        }
    }

    private int demMaLoi() {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM ma_loi", Integer.class);
        return n == null ? 0 : n;
    }

    @Test
    void import_hopLe_ghiDungBanGhi() {
        ImportResult kq = service.importXlsx(new ByteArrayInputStream(workbookHopLe()));

        assertThat(kq.coLoi()).isFalse();
        assertThat(kq.getSoNhomImport()).isEqualTo(1);
        assertThat(kq.getSoMaLoiImport()).isEqualTo(2);

        assertThat(demMaLoi()).isEqualTo(2);
        String ten = jdbc.queryForObject(
                "SELECT ten_ma_loi FROM ma_loi WHERE ma_loi_id = 'REC-02'", String.class);
        assertThat(ten).isEqualTo("NVL nhiem tap chat la");
        Integer kv = jdbc.queryForObject(
                "SELECT bat_buoc_khoanh_vung FROM ma_loi WHERE ma_loi_id = 'REC-03'", Integer.class);
        assertThat(kv).isEqualTo(1);
    }

    @Test
    void import_coDongLoi_allOrNothing_khongGhiGi() {
        byte[] bytes;
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet nhom = wb.createSheet("NhomLoi");
            dong(nhom, 0, "nhom_loi_id", "ten_nhom", "theo_nguon");
            dong(nhom, 1, "REC", "Loi nhan hang/NVL", "Nhan hang");

            Sheet ma = wb.createSheet("MaLoi");
            dong(ma, 0, "ma_loi_id", "nhom_loi_id", "ten_ma_loi", "muc_nghiem_trong_default",
                    "moi_nguy_default", "bat_buoc_khoanh_vung", "nhom_n_kiem_chung");
            dong(ma, 1, "REC-02", "REC", "NVL nhiem tap chat la", "NANG", "VAT_LY", "0", "SX_TP_NVL");
            // Dòng 3: muc_nghiem_trong_default sai -> lỗi -> cả import bị từ chối
            dong(ma, 2, "REC-99", "REC", "Dong loi", "SAI_MUC", "VAT_LY", "0", "SX_TP_NVL");
            bytes = toBytes(wb);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        ImportResult kq = service.importXlsx(new ByteArrayInputStream(bytes));

        assertThat(kq.coLoi()).isTrue();
        assertThat(kq.getLoi()).hasSize(1);
        ImportResult.LoiDong loi = kq.getLoi().get(0);
        assertThat(loi.getSheet()).isEqualTo("MaLoi");
        assertThat(loi.getDong()).isEqualTo(3); // dòng Excel 1-based
        assertThat(loi.getLyDo()).contains("muc_nghiem_trong_default");

        // all-or-nothing: KHÔNG ghi gì (kể cả dòng REC-02 hợp lệ)
        assertThat(demMaLoi()).isZero();
        Integer soNhom = jdbc.queryForObject("SELECT COUNT(*) FROM nhom_loi", Integer.class);
        assertThat(soNhom).isZero();
    }

    @Test
    void import_hai_lan_idempotent_khongNhanDoi() {
        service.importXlsx(new ByteArrayInputStream(workbookHopLe()));
        assertThat(demMaLoi()).isEqualTo(2);

        // Import lại cùng nội dung: upsert theo khoá -> không nhân đôi
        ImportResult kq2 = service.importXlsx(new ByteArrayInputStream(workbookHopLe()));
        assertThat(kq2.coLoi()).isFalse();
        assertThat(demMaLoi()).isEqualTo(2);
    }

    @Test
    void import_nhomLoiKhongTonTai_baoLoiFk() {
        byte[] bytes;
        try (Workbook wb = new XSSFWorkbook()) {
            // KHÔNG có sheet NhomLoi, và DB rỗng -> FK nhom_loi_id không thoả
            Sheet ma = wb.createSheet("MaLoi");
            dong(ma, 0, "ma_loi_id", "nhom_loi_id", "ten_ma_loi", "muc_nghiem_trong_default",
                    "moi_nguy_default", "bat_buoc_khoanh_vung", "nhom_n_kiem_chung");
            dong(ma, 1, "XXX-01", "KHONGCO", "Ten", "NHE", "KHONG", "0", "SX_TP_NVL");
            bytes = toBytes(wb);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        ImportResult kq = service.importXlsx(new ByteArrayInputStream(bytes));
        assertThat(kq.coLoi()).isTrue();
        assertThat(kq.getLoi().get(0).getLyDo()).contains("nhom_loi_id");
        assertThat(demMaLoi()).isZero();
    }

    @Test
    void import_boQuaDongTrong() {
        byte[] bytes;
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet nhom = wb.createSheet("NhomLoi");
            dong(nhom, 0, "nhom_loi_id", "ten_nhom", "theo_nguon");
            dong(nhom, 1, "REC", "Loi nhan hang/NVL", "Nhan hang");

            Sheet ma = wb.createSheet("MaLoi");
            dong(ma, 0, "ma_loi_id", "nhom_loi_id", "ten_ma_loi", "muc_nghiem_trong_default",
                    "moi_nguy_default", "bat_buoc_khoanh_vung", "nhom_n_kiem_chung");
            dong(ma, 1, "REC-02", "REC", "NVL nhiem tap chat la", "NANG", "VAT_LY", "0", "SX_TP_NVL");
            ma.createRow(2); // dòng trống -> bỏ qua
            dong(ma, 3, "REC-03", "REC", "NVL vuot nguong vi sinh", "NGHIEM_TRONG", "VI_SINH", "1", "SX_TP_NVL");
            bytes = toBytes(wb);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        ImportResult kq = service.importXlsx(new ByteArrayInputStream(bytes));
        assertThat(kq.coLoi()).isFalse();
        assertThat(kq.getSoMaLoiImport()).isEqualTo(2);
        assertThat(demMaLoi()).isEqualTo(2);
    }

    @Test
    void import_khongCoSheetMaLoi_baoLoi() {
        byte[] bytes;
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet nhom = wb.createSheet("NhomLoi");
            dong(nhom, 0, "nhom_loi_id", "ten_nhom", "theo_nguon");
            dong(nhom, 1, "REC", "Loi nhan hang/NVL", "Nhan hang");
            bytes = toBytes(wb);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        ImportResult kq = service.importXlsx(new ByteArrayInputStream(bytes));
        assertThat(kq.coLoi()).isTrue();
        List<ImportResult.LoiDong> loi = kq.getLoi();
        assertThat(loi).anyMatch(l -> l.getLyDo().contains("MaLoi"));
    }
}
