package com.dqh.nccapa.common.enums;

/** Trạng thái PR (REQ mục 4.3). */
public enum PrStatus {
    Draft,
    Analyzing,
    PendingApproval,
    Approved,
    Implementing,
    Closed,
    Rejected;

    public boolean laDangMo() {
        return this != Closed && this != Rejected;
    }
}
