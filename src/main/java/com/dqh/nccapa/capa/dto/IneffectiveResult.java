package com.dqh.nccapa.capa.dto;

/** Kết quả mark-ineffective: CAPA → Ineffective + NC kế thừa (+ cờ PR nếu chạm trần). */
public class IneffectiveResult {
    public Long capaId;
    public String ncKeThuaSoHieu;
    public String ncKeThuaMuc;
    public boolean moPrNgay;        // chạm trần Nghiêm trọng hoặc fail lần 2
    public boolean failLan2;

    public IneffectiveResult(Long capaId, String ncKeThuaSoHieu, String ncKeThuaMuc,
                             boolean moPrNgay, boolean failLan2) {
        this.capaId = capaId;
        this.ncKeThuaSoHieu = ncKeThuaSoHieu;
        this.ncKeThuaMuc = ncKeThuaMuc;
        this.moPrNgay = moPrNgay;
        this.failLan2 = failLan2;
    }
}
