package com.dqh.nccapa.capa.dto;

/** GET /capa/{id}/verification-status (API §5.2). */
public class VerificationStatus {
    public boolean xDat;
    public boolean nDat;
    public int soLanDoiHan;
    public boolean coTheXacNhan;   // xDat && nDat

    public VerificationStatus(boolean xDat, boolean nDat, int soLanDoiHan) {
        this.xDat = xDat;
        this.nDat = nDat;
        this.soLanDoiHan = soLanDoiHan;
        this.coTheXacNhan = xDat && nDat;
    }
}
