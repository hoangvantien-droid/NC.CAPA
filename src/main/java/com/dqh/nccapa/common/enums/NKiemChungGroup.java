package com.dqh.nccapa.common.enums;

/** Nhóm điều kiện kiểm chứng N lô (REQ mục 4.2). */
public enum NKiemChungGroup {
    SX_TP_NVL,            // N >= 1
    HETHONG_TBDO_NCC_MT;  // N = 0 (đóng bằng biên bản)

    public boolean dungN0() {
        return this == HETHONG_TBDO_NCC_MT;
    }
}
