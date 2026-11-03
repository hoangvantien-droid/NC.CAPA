package com.dqh.nccapa.nc.service;

import com.dqh.nccapa.nc.entity.Nc;

/**
 * Hook callback ticket (REQ mục 6): gọi khi NC nguồn khiếu nại đạt mốc.
 * Impl thật ở module ticket; lát chưa có dùng no-op.
 */
public interface TicketCallbackHook {
    /** Mốc phản hồi sớm (NC xong PendingVerification, không mở CAPA). */
    void mocSom(Nc nc);
    /** Mốc đóng hồ sơ (NC Closed/Cancelled). ketLuan: Confirmed/Unconfirmed/Cancelled. */
    void mocDong(Nc nc, String ketLuan);
}
