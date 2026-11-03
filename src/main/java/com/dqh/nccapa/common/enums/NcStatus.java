package com.dqh.nccapa.common.enums;

/** Trạng thái NC (REQ mục 4.1, DataModel §8). */
public enum NcStatus {
    New,
    Containment,
    PendingApproval,
    Rejected,
    Processing,
    PendingVerification,
    Closed,
    Cancelled;

    public boolean laKetThuc() {
        return this == Closed || this == Cancelled;
    }
}
