package com.dqh.nccapa.common.enums;

/** Trạng thái CAPA (REQ mục 4.2). Ineffective là trạng thái kết thúc riêng, không gộp Closed. */
public enum CapaStatus {
    Open,
    Investigating,
    ActionPlan,
    Processing,
    Monitoring,
    Closed,
    Ineffective
}
