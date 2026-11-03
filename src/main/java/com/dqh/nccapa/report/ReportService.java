package com.dqh.nccapa.report;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Báo cáo/dashboard Đợt b (REQ mục 8): NC theo mức/trạng thái/nguồn/nhà máy,
 * NC quá hạn, tỷ lệ CAPA hiệu quả. Dùng JdbcTemplate (truy vấn gom nhóm).
 */
@Service
public class ReportService {

    private final JdbcTemplate jdbc;

    public ReportService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Các trạng thái "đang mở" (chưa kết thúc). */
    private static final String OPEN = "'New','Containment','PendingApproval','Rejected','Processing','PendingVerification'";

    public DashboardData dashboard(String nhaMay) {
        boolean loc = nhaMay != null && !nhaMay.isBlank();
        String where = loc ? " WHERE nha_may = ? " : " ";
        Object[] args = loc ? new Object[]{nhaMay} : new Object[]{};

        DashboardData d = new DashboardData();
        d.tongMo = count("SELECT COUNT(*) FROM nc" + where + (loc ? "AND" : "WHERE") + " trang_thai IN (" + OPEN + ")", args);
        d.quaHan = count("SELECT COUNT(*) FROM nc" + where + (loc ? "AND" : "WHERE")
                + " han_xu_ly IS NOT NULL AND han_xu_ly < CURDATE() AND trang_thai IN (" + OPEN + ")", args);
        d.khoanhVung = count("SELECT COUNT(*) FROM nc" + where + (loc ? "AND" : "WHERE") + " trang_thai = 'Containment'", args);

        d.theoMuc = nhom("muc_nghiem_trong", where, args, loc);
        d.theoTrangThai = nhom("trang_thai", where, args, loc);
        d.theoNguon = nhom("nguon_phat_sinh", where, args, loc);
        d.theoLoai = nhom("loai_nc", where, args, loc);
        d.theoNhaMay = loc ? Map.of() : nhom("nha_may", " ", new Object[]{}, false);

        // Tỷ lệ CAPA hiệu quả (trên các CAPA đã có kết quả kiểm chứng)
        Long hieuQua = count("SELECT COUNT(*) FROM capa_verification WHERE ket_qua = 'HIEU_QUA'", new Object[]{});
        Long khong = count("SELECT COUNT(*) FROM capa_verification WHERE ket_qua = 'KHONG_HIEU_QUA'", new Object[]{});
        d.capaHieuQua = hieuQua;
        d.capaKhongHieuQua = khong;
        long tong = hieuQua + khong;
        d.tyLeHieuQua = tong == 0 ? null : Math.round(hieuQua * 1000.0 / tong) / 10.0; // %, 1 chữ số thập phân

        // ---- Báo cáo chuyên sâu (REQ mục 8 mở rộng) ----
        napChuyenSau(d, where, args, loc, nhaMay);
        return d;
    }

    /**
     * Bổ sung các chỉ số chuyên sâu: tuổi NC đang mở, thời gian đóng trung bình,
     * cost of quality, top mã lỗi / top mã hàng tái diễn. Chỉ thêm truy vấn đọc,
     * không đổi schema.
     */
    private void napChuyenSau(DashboardData d, String where, Object[] args, boolean loc, String nhaMay) {
        // (1) Tuổi NC đang mở — số ngày từ created_at tới hiện tại, phân bố theo khoảng.
        String ageSql = "SELECT "
                + "AVG(DATEDIFF(CURDATE(), DATE(created_at))) avg_age, "
                + "SUM(CASE WHEN DATEDIFF(CURDATE(), DATE(created_at)) <= 7 THEN 1 ELSE 0 END) le7, "
                + "SUM(CASE WHEN DATEDIFF(CURDATE(), DATE(created_at)) BETWEEN 8 AND 30 THEN 1 ELSE 0 END) m8_30, "
                + "SUM(CASE WHEN DATEDIFF(CURDATE(), DATE(created_at)) > 30 THEN 1 ELSE 0 END) gt30 "
                + "FROM nc" + where + (loc ? "AND" : "WHERE") + " trang_thai IN (" + OPEN + ")";
        Map<String, Object> ageRow = jdbc.queryForMap(ageSql, args);
        Object avgAge = ageRow.get("avg_age");
        d.tuoiTrungBinhMo = avgAge == null ? null : Math.round(((Number) avgAge).doubleValue() * 10.0) / 10.0;
        d.tuoiMoDuoi7 = soLong(ageRow.get("le7"));
        d.tuoiMo8Den30 = soLong(ageRow.get("m8_30"));
        d.tuoiMoTren30 = soLong(ageRow.get("gt30"));

        // (2) Thời gian đóng trung bình — từ created_at tới thời điểm chuyển sang 'Closed'
        // (lấy từ audit_log, vì nc không có cột ngày đóng riêng). Lấy lần chuyển Closed sớm nhất.
        String closeWhere = loc ? " AND n.nha_may = ? " : " ";
        Object[] closeArgs = loc ? new Object[]{nhaMay} : new Object[]{};
        String closeSql = "SELECT AVG(DATEDIFF(DATE(a.closed_at), DATE(n.created_at))) avg_close FROM nc n "
                + "JOIN (SELECT doi_tuong_id, MIN(thuc_hien_luc) closed_at FROM audit_log "
                + "      WHERE doi_tuong = 'NC' AND gia_tri_moi = 'Closed' GROUP BY doi_tuong_id) a "
                + "  ON a.doi_tuong_id = n.so_hieu "
                + "WHERE n.trang_thai = 'Closed'" + closeWhere;
        Object avgClose = jdbc.queryForMap(closeSql, closeArgs).get("avg_close");
        d.thoiGianDongTrungBinh = avgClose == null ? null : Math.round(((Number) avgClose).doubleValue() * 10.0) / 10.0;

        // (3) Cost of quality — tổng chi_phi_thuc_te (NC) + tổng chi_phi_dau_tu (CAPA liên kết NC).
        d.cophiNc = tien("SELECT COALESCE(SUM(chi_phi_thuc_te),0) FROM nc" + where, args);
        String capaCostSql = loc
                ? "SELECT COALESCE(SUM(c.chi_phi_dau_tu),0) FROM capa c "
                + "JOIN nc_capa l ON l.capa_id = c.id JOIN nc n ON n.so_hieu = l.nc_so_hieu WHERE n.nha_may = ?"
                : "SELECT COALESCE(SUM(chi_phi_dau_tu),0) FROM capa";
        d.cophiCapa = tien(capaCostSql, loc ? new Object[]{nhaMay} : new Object[]{});
        d.costOfQuality = d.cophiNc.add(d.cophiCapa);

        // (4) Top mã lỗi / Top mã hàng (bỏ qua null), lấy top 10 desc.
        d.topMaLoi = topN("ma_loi_chinh", where, args, loc);
        d.topMaHang = topN("ma_hang_chinh", where, args, loc);
    }

    /** Top 10 giá trị của một cột (bỏ null/rỗng), đếm số NC giảm dần. */
    private Map<String, Long> topN(String cot, String where, Object[] args, boolean loc) {
        String sql = "SELECT " + cot + " k, COUNT(*) c FROM nc" + where + (loc ? "AND" : "WHERE")
                + " " + cot + " IS NOT NULL AND " + cot + " <> '' GROUP BY " + cot
                + " ORDER BY c DESC, k ASC LIMIT 10";
        Map<String, Long> m = new LinkedHashMap<>();
        for (Map<String, Object> row : jdbc.queryForList(sql, args)) {
            m.put(row.get("k").toString(), ((Number) row.get("c")).longValue());
        }
        return m;
    }

    private java.math.BigDecimal tien(String sql, Object[] args) {
        java.math.BigDecimal v = jdbc.queryForObject(sql, java.math.BigDecimal.class, args);
        return v == null ? java.math.BigDecimal.ZERO : v;
    }

    private long soLong(Object o) {
        return o == null ? 0L : ((Number) o).longValue();
    }

    private Map<String, Long> nhom(String cot, String where, Object[] args, boolean loc) {
        String sql = "SELECT " + cot + " k, COUNT(*) c FROM nc" + where + "GROUP BY " + cot + " ORDER BY c DESC";
        Map<String, Long> m = new LinkedHashMap<>();
        for (Map<String, Object> row : jdbc.queryForList(sql, args)) {
            Object k = row.get("k");
            m.put(k == null ? "—" : k.toString(), ((Number) row.get("c")).longValue());
        }
        return m;
    }

    private Long count(String sql, Object[] args) {
        Long n = jdbc.queryForObject(sql, Long.class, args);
        return n == null ? 0L : n;
    }

    /** Dữ liệu dashboard. */
    public static class DashboardData {
        public long tongMo;
        public long quaHan;
        public long khoanhVung;
        public Map<String, Long> theoMuc;
        public Map<String, Long> theoTrangThai;
        public Map<String, Long> theoNguon;
        public Map<String, Long> theoLoai;
        public Map<String, Long> theoNhaMay;
        public long capaHieuQua;
        public long capaKhongHieuQua;
        public Double tyLeHieuQua; // null nếu chưa có CAPA kiểm chứng

        // ---- Chuyên sâu (REQ mục 8 mở rộng) ----
        /** Tuổi trung bình (ngày) của NC đang mở; null nếu không có NC mở. */
        public Double tuoiTrungBinhMo;
        public long tuoiMoDuoi7;   // NC mở <= 7 ngày
        public long tuoiMo8Den30;  // NC mở 8–30 ngày
        public long tuoiMoTren30;  // NC mở > 30 ngày
        /** Thời gian đóng trung bình (ngày) của NC đã Closed; null nếu chưa có / không suy được. */
        public Double thoiGianDongTrungBinh;
        /** Cost of quality: chi phí NC + chi phí CAPA. */
        public java.math.BigDecimal cophiNc = java.math.BigDecimal.ZERO;
        public java.math.BigDecimal cophiCapa = java.math.BigDecimal.ZERO;
        public java.math.BigDecimal costOfQuality = java.math.BigDecimal.ZERO;
        public Map<String, Long> topMaLoi = new LinkedHashMap<>();
        public Map<String, Long> topMaHang = new LinkedHashMap<>();

        /** Giá trị lớn nhất trong một nhóm (để tính bề rộng thanh bar); tối thiểu 1 tránh chia 0. */
        public long max(Map<String, Long> m) {
            long mx = 0;
            for (Long v : m.values()) if (v != null && v > mx) mx = v;
            return Math.max(mx, 1);
        }

        /** Giá trị lớn nhất trong 3 khoảng tuổi NC đang mở (bề rộng bar); tối thiểu 1 tránh chia 0. */
        public long maxAge() {
            return Math.max(Math.max(tuoiMoDuoi7, tuoiMo8Den30), Math.max(tuoiMoTren30, 1));
        }
    }
}
