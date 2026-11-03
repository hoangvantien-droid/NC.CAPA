package com.dqh.nccapa.common.enums;

/**
 * Loại hành động CAPA theo trục Corrective/Preventive (Nhóm 2, phần F).
 * Trục này KHÁC với {@link ActionType} (TUC_THOI/NGAN_TAI_DIEN) — ánh xạ cột loai_hanh_dong.
 */
public enum ActionCategory {
    CORRECTIVE,   // khắc phục
    PREVENTIVE    // phòng ngừa
}
