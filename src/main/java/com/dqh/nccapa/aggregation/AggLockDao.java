package com.dqh.nccapa.aggregation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mutex mức dòng cho auto-aggregation (StateMachine §5.1, DataModel §3.6).
 * Upsert ở ĐẦU onEvent → tuần tự hóa mọi luồng cùng lô_kiem; KHÔNG ném DuplicateKey, KHÔNG retry đệ quy.
 */
@Component
public class AggLockDao {

    private final JdbcTemplate jdbc;

    public AggLockDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Giành/giữ khóa cho key. Luồng sau bị chặn tại đây tới khi luồng trước commit. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void acquire(String key) {
        jdbc.update(
            "INSERT INTO nc_agg_lock(lo_kiem, created_at) VALUES (?, NOW()) " +
            "ON DUPLICATE KEY UPDATE created_at = NOW()",
            key);
    }
}
