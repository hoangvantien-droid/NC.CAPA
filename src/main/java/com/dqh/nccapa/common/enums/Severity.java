package com.dqh.nccapa.common.enums;

/**
 * Mức nghiêm trọng NC. Thứ tự ordinal từ nhẹ → nặng để so sánh & nâng bậc (StateMachine §2.2).
 */
public enum Severity {
    NHE,          // 0
    NANG,         // 1
    NGHIEM_TRONG; // 2

    public boolean caoHon(Severity khac) {
        return this.ordinal() > khac.ordinal();
    }

    public boolean ordinalGte(Severity khac) {
        return this.ordinal() >= khac.ordinal();
    }

    /** Nâng 1 bậc, trần ở NGHIEM_TRONG (TH1/TH2 — StateMachine §4.4). */
    public Severity nangMotBac() {
        return this == NGHIEM_TRONG ? NGHIEM_TRONG : values()[this.ordinal() + 1];
    }

    public static Severity nangNhat(Severity a, Severity b) {
        return a.ordinal() >= b.ordinal() ? a : b;
    }
}
