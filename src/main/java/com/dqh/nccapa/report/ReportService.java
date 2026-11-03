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
        return d;
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

        /** Giá trị lớn nhất trong một nhóm (để tính bề rộng thanh bar); tối thiểu 1 tránh chia 0. */
        public long max(Map<String, Long> m) {
            long mx = 0;
            for (Long v : m.values()) if (v != null && v > mx) mx = v;
            return Math.max(mx, 1);
        }
    }
}
