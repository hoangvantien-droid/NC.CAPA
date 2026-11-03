package com.dqh.nccapa.pr.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.List;

/** Liên kết pr_nc / pr_capa + kiểm "1 PR đang mở / vấn đề" (REQ mục 5, quy tắc #13). */
@Repository
public class PrLinkDao {

    private final JdbcTemplate jdbc;

    public PrLinkDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void linkNc(Long prId, String ncSoHieu) {
        jdbc.update("INSERT IGNORE INTO pr_nc(pr_id, nc_so_hieu) VALUES (?, ?)", prId, ncSoHieu);
    }

    public void linkCapa(Long prId, Long capaId) {
        jdbc.update("INSERT IGNORE INTO pr_capa(pr_id, capa_id) VALUES (?, ?)", prId, capaId);
    }

    public List<String> ncsOfPr(Long prId) {
        return jdbc.queryForList("SELECT nc_so_hieu FROM pr_nc WHERE pr_id = ?", String.class, prId);
    }

    /** Có PR đang mở nào cho cùng (mã hàng + mã lỗi) không? (trừ prId hiện tại nếu có). */
    public Long prDangMoChoVanDe(String maHang, String maLoiId, Long truPrId) {
        List<Long> ids = jdbc.queryForList(
            "SELECT id FROM pr WHERE ma_hang <=> ? AND ma_loi_id <=> ? " +
            "AND trang_thai NOT IN ('Closed','Rejected') AND id <> ?",
            Long.class, maHang, maLoiId, truPrId == null ? -1L : truPrId);
        return ids.isEmpty() ? null : ids.get(0);
    }
}
