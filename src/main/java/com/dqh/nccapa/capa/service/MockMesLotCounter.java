package com.dqh.nccapa.capa.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** Impl mô phỏng: đếm trên bảng mes_lot_event được seed (DataModel §4.5). */
@Component
public class MockMesLotCounter implements MesLotCounter {

    private final JdbcTemplate jdbc;

    public MockMesLotCounter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public int countLots(String maHang, LocalDateTime from) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM mes_lot_event WHERE ma_hang = ? AND san_xuat_luc > ?",
                Integer.class, maHang, from);
        return n == null ? 0 : n;
    }
}
