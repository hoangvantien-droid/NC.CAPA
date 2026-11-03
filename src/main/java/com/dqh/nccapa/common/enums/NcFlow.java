package com.dqh.nccapa.common.enums;

/** Luồng NC quyết định từ bộ mã lỗi (StateMachine §6). */
public enum NcFlow {
    NHANH,          // NC Nhẹ: New -> Closed
    CO_XEM_XET,     // NC Nặng: 5 bước
    CO_KHOANH_VUNG  // NC Nghiêm trọng / an toàn TP
}
