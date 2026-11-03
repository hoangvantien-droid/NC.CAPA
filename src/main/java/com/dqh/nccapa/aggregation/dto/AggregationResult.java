package com.dqh.nccapa.aggregation.dto;

/** Kết quả auto-aggregation (API §4): APPENDED vào NC hiện hữu hay CREATED mới. */
public class AggregationResult {
    public enum Action { APPENDED, CREATED }

    public Action action;
    public String soHieu;
    public String mucNghiemTrong;

    public AggregationResult(Action action, String soHieu, String mucNghiemTrong) {
        this.action = action;
        this.soHieu = soHieu;
        this.mucNghiemTrong = mucNghiemTrong;
    }
}
