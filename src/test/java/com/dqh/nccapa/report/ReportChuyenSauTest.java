package com.dqh.nccapa.report;

import com.dqh.nccapa.support.MySqlTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Báo cáo chuyên sâu (REQ mục 8 mở rộng): cost of quality, tuổi NC đang mở,
 * thời gian đóng trung bình (suy từ audit_log), top mã lỗi / top mã hàng.
 *
 * Deterministic: chèn thẳng qua JdbcTemplate với created_at cố định tương đối
 * (DATE_SUB theo số ngày) và mốc Closed cố định trong audit_log → không so wall-clock,
 * chỉ kiểm công thức.
 */
class ReportChuyenSauTest extends MySqlTestBase {

    @Autowired ReportService reportService;
    @Autowired JdbcTemplate jdbc;

    /** Chèn 1 NC với created_at = (hiện tại - ageDays ngày). */
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

    /** Ghi mốc chuyển sang Closed trong audit_log tại thời điểm = (hiện tại - daysAgo ngày). */
    private void chenAuditClosed(String so, int daysAgo) {
        jdbc.update(
                "INSERT INTO audit_log (doi_tuong, doi_tuong_id, hanh_dong, gia_tri_cu, gia_tri_moi, "
                        + "nguoi_thuc_hien, thuc_hien_luc) VALUES ('NC', ?, 'CHUYEN_TRANG_THAI', "
                        + "'PendingVerification', 'Closed', 'u', DATE_SUB(NOW(), INTERVAL ? DAY))",
                so, daysAgo);
    }

    private void chenCapaCost(long id, BigDecimal chiPhi) {
        jdbc.update("INSERT INTO capa (id, trang_thai, chi_phi_dau_tu, created_at, created_by) "
                + "VALUES (?, 'Open', ?, NOW(), 'u')", id, chiPhi);
    }

    @Test
    void costOfQuality_tongDung() {
        chenNc("NC-1", "Processing", "MGP", "FG-01", "SP001", new BigDecimal("100.00"), 2);
        chenNc("NC-2", "Processing", "MGP", "FG-02", "SP002", new BigDecimal("250.50"), 2);
        chenCapaCost(1L, new BigDecimal("1000.00"));
        chenCapaCost(2L, new BigDecimal("500.00"));

        var d = reportService.dashboard(null);
        assertThat(d.cophiNc).isEqualByComparingTo("350.50");
        assertThat(d.cophiCapa).isEqualByComparingTo("1500.00");
        assertThat(d.costOfQuality).isEqualByComparingTo("1850.50");
    }

    @Test
    void topMaLoi_xepDungThuTu_boQuaNull() {
        // FG-01 x3, FG-02 x2, FG-03 x1, và 1 NC không mã lỗi (null) phải bị bỏ qua.
        chenNc("NC-1", "Processing", "MGP", "FG-01", "SP001", null, 1);
        chenNc("NC-2", "Processing", "MGP", "FG-01", "SP001", null, 1);
        chenNc("NC-3", "Processing", "MGP", "FG-01", "SP001", null, 1);
        chenNc("NC-4", "Processing", "MGP", "FG-02", "SP002", null, 1);
        chenNc("NC-5", "Processing", "MGP", "FG-02", "SP002", null, 1);
        chenNc("NC-6", "Processing", "MGP", "FG-03", "SP003", null, 1);
        chenNc("NC-7", "Processing", "MGP", null, null, null, 1);

        var d = reportService.dashboard(null);
        assertThat(d.topMaLoi).containsExactly(
                org.assertj.core.api.Assertions.entry("FG-01", 3L),
                org.assertj.core.api.Assertions.entry("FG-02", 2L),
                org.assertj.core.api.Assertions.entry("FG-03", 1L));
        assertThat(d.topMaLoi).doesNotContainKey("—");
        // Top mã hàng: SP001 x3 đứng đầu.
        List<String> keys = d.topMaHang.keySet().stream().toList();
        assertThat(keys.get(0)).isEqualTo("SP001");
        assertThat(d.topMaHang.get("SP001")).isEqualTo(3L);
    }

    @Test
    void tuoiNcDangMo_phanBoTheoKhoang() {
        chenNc("NC-A", "Processing", "MGP", "FG-01", "SP001", null, 3);   // <= 7
        chenNc("NC-B", "Processing", "MGP", "FG-01", "SP001", null, 15);  // 8–30
        chenNc("NC-C", "Processing", "MGP", "FG-01", "SP001", null, 40);  // > 30
        chenNc("NC-D", "Processing", "MGP", "FG-01", "SP001", null, 50);  // > 30

        var d = reportService.dashboard(null);
        assertThat(d.tuoiMoDuoi7).isEqualTo(1L);
        assertThat(d.tuoiMo8Den30).isEqualTo(1L);
        assertThat(d.tuoiMoTren30).isEqualTo(2L);
        // Tuổi trung bình = (3 + 15 + 40 + 50) / 4 = 27.0
        assertThat(d.tuoiTrungBinhMo).isEqualTo(27.0);
    }

    @Test
    void thoiGianDongTrungBinh_tuAuditLog() {
        // NC-X: tạo 20 ngày trước, đóng 5 ngày trước → đóng mất 15 ngày.
        chenNc("NC-X", "Closed", "MGP", "FG-01", "SP001", null, 20);
        chenAuditClosed("NC-X", 5);
        // NC-Y: tạo 10 ngày trước, đóng 1 ngày trước → đóng mất 9 ngày.
        chenNc("NC-Y", "Closed", "MGP", "FG-02", "SP002", null, 10);
        chenAuditClosed("NC-Y", 1);

        var d = reportService.dashboard(null);
        // Trung bình (15 + 9) / 2 = 12.0
        assertThat(d.thoiGianDongTrungBinh).isEqualTo(12.0);
    }

    @Test
    void thoiGianDongTrungBinh_null_khiChuaCoNcDong() {
        chenNc("NC-1", "Processing", "MGP", "FG-01", "SP001", null, 2);
        var d = reportService.dashboard(null);
        assertThat(d.thoiGianDongTrungBinh).isNull();
    }

    @Test
    void costOfQuality_locTheoNhaMay() {
        chenNc("NC-1", "Processing", "MGP", "FG-01", "SP001", new BigDecimal("100.00"), 2);
        chenNc("NC-2", "Processing", "DNA", "FG-02", "SP002", new BigDecimal("999.00"), 2);

        var mgp = reportService.dashboard("MGP");
        assertThat(mgp.cophiNc).isEqualByComparingTo("100.00");
    }
}
