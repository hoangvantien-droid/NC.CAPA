package com.dqh.nccapa.nc.service;

import com.dqh.nccapa.nc.entity.Nc;

/**
 * Hook fail sớm TH2 (StateMachine §4.3): gọi sau khi tạo NC mới.
 * Impl thật ở module CAPA (EarlyFailDetector); lát chưa có CAPA dùng no-op.
 */
public interface EarlyFailHook {
    /** @return true nếu NC này đã trở thành NC kế thừa do fail sớm. */
    boolean onNewNc(Nc ncMoi);
}
