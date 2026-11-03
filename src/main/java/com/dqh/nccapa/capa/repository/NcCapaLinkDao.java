package com.dqh.nccapa.capa.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.List;

/** Bảng liên kết nhiều-nhiều nc_capa (DataModel §4.2) qua JdbcTemplate. */
@Repository
public class NcCapaLinkDao {

    private final JdbcTemplate jdbc;

    public NcCapaLinkDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void link(String ncSoHieu, Long capaId) {
        jdbc.update("INSERT IGNORE INTO nc_capa(nc_so_hieu, capa_id) VALUES (?, ?)", ncSoHieu, capaId);
    }

    public List<Long> capaIdsOfNc(String ncSoHieu) {
        return jdbc.queryForList("SELECT capa_id FROM nc_capa WHERE nc_so_hieu = ?", Long.class, ncSoHieu);
    }

    public List<String> ncSoHieuOfCapa(Long capaId) {
        return jdbc.queryForList("SELECT nc_so_hieu FROM nc_capa WHERE capa_id = ?", String.class, capaId);
    }

    /** Phạm vi mã hàng của CAPA — lấy từ nc_chi_tieu (StateMachine §3.4, vá 4.2). */
    public List<String> maHangsOfCapa(Long capaId) {
        return jdbc.queryForList(
                "SELECT DISTINCT ct.ma_hang FROM nc_capa nc " +
                "JOIN nc_chi_tieu ct ON ct.nc_so_hieu = nc.nc_so_hieu " +
                "WHERE nc.capa_id = ? AND ct.ma_hang IS NOT NULL",
                String.class, capaId);
    }
}
