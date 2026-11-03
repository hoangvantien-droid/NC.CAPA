package com.dqh.nccapa.config;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Seed dữ liệu demo khi app.seed.enabled=true và bảng còn rỗng.
 * Bộ mã lỗi theo APPX (chuẩn đã chốt) + master data + dữ liệu mô phỏng OOS/MES tối thiểu.
 * Test chạy với seed.enabled=false nên không bị ảnh hưởng.
 *
 * Khi có CSV export từ MockData_NC-CAPA_v1.0.1.xlsx: thay phần INSERT bộ mã lỗi/master bằng nạp CSV.
 */
@Configuration
public class DemoSeeder {

    @Bean
    public ApplicationRunner seedRunner(AppProperties props, JdbcTemplate jdbc) {
        return (ApplicationArguments args) -> {
            if (!props.getSeed().isEnabled()) return;
            Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM nha_may", Integer.class);
            if (n != null && n > 0) return; // đã seed

            // --- Nhà máy ---
            jdbc.update("INSERT INTO nha_may(ma, ten) VALUES ('MGP','Nha may Mega Phu'),('DNA','Nha may Dong Nai')");

            // --- Role ---
            jdbc.batchUpdate(
                "INSERT INTO role(ma, ten) VALUES ('QA_TAP_DOAN','QA tap doan')",
                "INSERT INTO role(ma, ten) VALUES ('QA_NHA_MAY','QA nha may')",
                "INSERT INTO role(ma, ten) VALUES ('BO_PHAN_SX','Bo phan san xuat')",
                "INSERT INTO role(ma, ten) VALUES ('BO_PHAN_KHO','Bo phan kho')",
                "INSERT INTO role(ma, ten) VALUES ('NGUOI_BAO_CAO','Nguoi bao cao')");

            // --- User demo ---
            jdbc.update("INSERT INTO app_user(username, full_name, nha_may, active) VALUES " +
                    "('qatd','QA Tap doan', NULL, 1)," +
                    "('qamgp','QA MGP','MGP',1)," +
                    "('sxmgp','SX MGP','MGP',1)");

            // --- Mã hàng / NCC / Lô ---
            jdbc.update("INSERT INTO ma_hang(ma, ten, nha_may) VALUES ('SP001','San pham A','MGP'),('SP002','San pham B','MGP')");
            jdbc.update("INSERT INTO ncc(ma, ten, co_giay_cn, cn_het_han_ngay) VALUES ('NCC01','Nha cung cap 1',1,'2026-12-31')");
            jdbc.update("INSERT INTO lo(so_lo, ma_hang, nha_may, ngay_sx, trang_thai_lo) VALUES " +
                    "('LOT-2026-00001','SP001','MGP','2026-10-01','Available')," +
                    "('LOT-2026-00002','SP001','MGP','2026-10-05','Available')");

            // --- Nhom loi (APPX) ---
            jdbc.update("INSERT INTO nhom_loi(nhom_loi_id, ten_nhom, theo_nguon) VALUES " +
                    "('REC','Loi nhan hang/NVL','Nhan hang')," +
                    "('PRC','Loi trong san xuat','San xuat')," +
                    "('FG','Loi thanh pham','Thanh pham')," +
                    "('ENV','Loi moi truong','Moi truong')," +
                    "('CAL','Loi thiet bi do','Hieu chuan')," +
                    "('CMP','Loi khieu nai','Ticket')");

            // --- Ma loi (APPX mục 3, đại diện) ---
            seedMaLoi(jdbc, "REC-02","REC","NVL nhiem tap chat la","NANG","VAT_LY",0,"SX_TP_NVL");
            seedMaLoi(jdbc, "REC-03","REC","NVL vuot nguong vi sinh","NGHIEM_TRONG","VI_SINH",1,"SX_TP_NVL");
            seedMaLoi(jdbc, "REC-06","REC","Giay CN NCC het han","NHE","KHONG",0,"HETHONG_TBDO_NCC_MT");
            seedMaLoi(jdbc, "PRC-02","PRC","MES chan cong doan","NANG","KHONG",0,"SX_TP_NVL");
            seedMaLoi(jdbc, "PRC-03","PRC","CCP mat kiem soat","NGHIEM_TRONG","VI_SINH",1,"SX_TP_NVL");
            seedMaLoi(jdbc, "FG-01","FG","Cam quan khong dat","NHE","CAM_QUAN",0,"SX_TP_NVL");
            seedMaLoi(jdbc, "FG-03","FG","Vi sinh thanh pham OOS","NGHIEM_TRONG","VI_SINH",1,"SX_TP_NVL");
            seedMaLoi(jdbc, "FG-04","FG","Hoa ly OOS vuot nguong an toan","NGHIEM_TRONG","HOA_HOC",1,"SX_TP_NVL");
            seedMaLoi(jdbc, "ENV-01","ENV","Bootswab duong mam benh vung 1","NGHIEM_TRONG","VI_SINH",1,"HETHONG_TBDO_NCC_MT");
            seedMaLoi(jdbc, "CAL-02","CAL","Thiet bi CCP qua han hieu chuan","NANG","KHONG",0,"HETHONG_TBDO_NCC_MT");
            seedMaLoi(jdbc, "CMP-01","CMP","Khieu nai an toan TP","NGHIEM_TRONG","VI_SINH",1,"SX_TP_NVL");
            seedMaLoi(jdbc, "CMP-02","CMP","Khieu nai chat luong","NANG","CAM_QUAN",0,"SX_TP_NVL");

            // --- MES mô phỏng: vài lô cho mốc kiểm chứng ---
            jdbc.update("INSERT INTO mes_lot_event(ma_hang, so_lo, san_xuat_luc, nha_may) VALUES " +
                    "('SP001','MES-L1', NOW(), 'MGP')," +
                    "('SP001','MES-L2', NOW(), 'MGP')");
        };
    }

    private void seedMaLoi(JdbcTemplate jdbc, String ma, String nhom, String ten,
                           String muc, String nguy, int kv, String nhomN) {
        jdbc.update("INSERT INTO ma_loi(ma_loi_id, nhom_loi_id, ten_ma_loi, muc_nghiem_trong_default, " +
                "moi_nguy_default, bat_buoc_khoanh_vung, nhom_n_kiem_chung, nguoi_so_huu, hieu_luc_tu) " +
                "VALUES (?,?,?,?,?,?,?, 'QA_TAP_DOAN', '2026-01-01')",
                ma, nhom, ten, muc, nguy, kv, nhomN);
    }
}
