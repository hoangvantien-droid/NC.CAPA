package com.dqh.nccapa.nc.service;

import com.dqh.nccapa.nc.entity.Nc;

/**
 * Hook tái diễn (StateMachine §4.2 / REQ mục 5): gọi SAU khi tạo NC mới, SAU hook fail sớm TH2.
 * Đếm số NC cùng tổ hợp (mã hàng + mã lỗi) trong rolling window; đủ ngưỡng "tái diễn lần 3"
 * → bắt buộc mở PR. Impl thật ở module CAPA (RecurrenceDetector); lát chưa có CAPA dùng no-op.
 */
public interface RecurrenceHook {
    /** @return true nếu NC này đạt ngưỡng tái diễn và đã kích hoạt mở PR. */
    boolean onNewNc(Nc ncMoi);
}
