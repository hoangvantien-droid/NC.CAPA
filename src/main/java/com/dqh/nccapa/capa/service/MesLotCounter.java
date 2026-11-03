package com.dqh.nccapa.capa.service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Đếm lô MES cho mốc kiểm chứng (StateMachine §3.4). Interface để sau thay tích hợp MES thật.
 */
public interface MesLotCounter {
    /** Số lô của maHang sản xuất sau thời điểm 'from'. */
    int countLots(String maHang, LocalDateTime from);

    /** Mọi mã hàng trong tập đạt n_lo? (n_lo=0 → true). */
    default boolean allReached(List<String> maHangs, int nLo, LocalDateTime from) {
        if (nLo <= 0) return true;
        for (String mh : maHangs) {
            if (countLots(mh, from) < nLo) return false;
        }
        return true;
    }
}
