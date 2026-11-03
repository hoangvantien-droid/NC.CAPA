package com.dqh.nccapa.aggregation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sinh số hiệu NC an toàn concurrency (DataModel §3.5, vá vòng 3 LAST_INSERT_ID(1)).
 * Format: KPH-{nam}-{nhaMay}-{no:%05d}. Reset theo năm.
 */
@Component
public class SequenceGenerator {

    private final JdbcTemplate jdbc;

    public SequenceGenerator(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public String nextSoHieu(int nam, String nhaMay) {
        // Nguyên tử: dòng đầu (INSERT) và dòng sau (UPDATE) đều set LAST_INSERT_ID.
        jdbc.update(
            "INSERT INTO nc_sequence (nam, nha_may, last_no) VALUES (?, ?, LAST_INSERT_ID(1)) " +
            "ON DUPLICATE KEY UPDATE last_no = LAST_INSERT_ID(last_no + 1)",
            nam, nhaMay);
        Long no = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return String.format("KPH-%d-%s-%05d", nam, nhaMay, no);
    }
}
