package com.dqh.nccapa.support;

import org.springframework.jdbc.core.JdbcTemplate;

/** Nạp bộ mã lỗi tối thiểu cho test (TestPlan §1): FG-01 Nhẹ, PRC-02 Nặng, FG-03 Nghiêm trọng+KV, REC-06 Nhẹ N=0. */
public final class DefectSeed {
    private DefectSeed() { }

    public static void seed(JdbcTemplate jdbc) {
        jdbc.update("INSERT IGNORE INTO nha_may(ma, ten) VALUES ('MGP','Nha may MGP')");
        jdbc.update("INSERT IGNORE INTO nhom_loi(nhom_loi_id, ten_nhom, theo_nguon) VALUES " +
                "('FG','Thanh pham','FG'),('PRC','Trong SX','PRC'),('REC','Nhan hang','REC'),('CMP','Khieu nai','CMP')");
        // ma_loi_id, nhom, ten, muc_default, moi_nguy, KV, nhom_N, nguoi_so_huu, hieu_luc_tu
        insert(jdbc, "FG-01", "FG", "Cam quan khong dat", "NHE", "CAM_QUAN", 0, "SX_TP_NVL");
        insert(jdbc, "PRC-02", "PRC", "MES chan cong doan", "NANG", "KHONG", 0, "SX_TP_NVL");
        insert(jdbc, "FG-03", "FG", "Vi sinh thanh pham OOS", "NGHIEM_TRONG", "VI_SINH", 1, "SX_TP_NVL");
        insert(jdbc, "REC-06", "REC", "Giay CN NCC het han", "NHE", "KHONG", 0, "HETHONG_TBDO_NCC_MT");
        insert(jdbc, "CMP-01", "CMP", "Khieu nai an toan TP", "NGHIEM_TRONG", "VI_SINH", 1, "SX_TP_NVL");
        insert(jdbc, "CMP-02", "CMP", "Khieu nai chat luong", "NANG", "CAM_QUAN", 0, "SX_TP_NVL");
    }

    private static void insert(JdbcTemplate jdbc, String ma, String nhom, String ten,
                               String muc, String nguy, int kv, String nhomN) {
        jdbc.update("INSERT IGNORE INTO ma_loi(ma_loi_id, nhom_loi_id, ten_ma_loi, " +
                "muc_nghiem_trong_default, moi_nguy_default, bat_buoc_khoanh_vung, nhom_n_kiem_chung, " +
                "nguoi_so_huu, hieu_luc_tu) VALUES (?,?,?,?,?,?,?, 'QA_TAP_DOAN', '2026-01-01')",
                ma, nhom, ten, muc, nguy, kv, nhomN);
    }
}
