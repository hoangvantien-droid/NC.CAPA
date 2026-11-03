package com.dqh.nccapa.masterdata.service;

import java.util.ArrayList;
import java.util.List;

/**
 * Kết quả import bộ mã lỗi từ file xlsx.
 *
 * <p>Quy ước: import theo chính sách <b>all-or-nothing</b> trong 1 transaction — nếu có bất kỳ
 * dòng lỗi nào, service KHÔNG ghi gì vào DB và trả về danh sách {@link #getLoi()} mô tả từng
 * dòng sai (kèm sheet + số dòng Excel 1-based + lý do). Khi không có lỗi, {@link #getSoNhomImport()}
 * và {@link #getSoMaLoiImport()} là số bản ghi đã upsert.
 */
public class ImportResult {

    private int soNhomImport;
    private int soMaLoiImport;
    private final List<LoiDong> loi = new ArrayList<>();

    public int getSoNhomImport() { return soNhomImport; }
    public void setSoNhomImport(int soNhomImport) { this.soNhomImport = soNhomImport; }

    public int getSoMaLoiImport() { return soMaLoiImport; }
    public void setSoMaLoiImport(int soMaLoiImport) { this.soMaLoiImport = soMaLoiImport; }

    public List<LoiDong> getLoi() { return loi; }

    public boolean coLoi() { return !loi.isEmpty(); }

    public void themLoi(String sheet, int dongExcel, String lyDo) {
        loi.add(new LoiDong(sheet, dongExcel, lyDo));
    }

    /** Một dòng lỗi: tên sheet + số dòng Excel (1-based) + lý do. */
    public static final class LoiDong {
        private final String sheet;
        private final int dong;
        private final String lyDo;

        public LoiDong(String sheet, int dong, String lyDo) {
            this.sheet = sheet;
            this.dong = dong;
            this.lyDo = lyDo;
        }

        public String getSheet() { return sheet; }
        public int getDong() { return dong; }
        public String getLyDo() { return lyDo; }
    }
}
