package com.dqh.nccapa.config;

import com.dqh.nccapa.capa.dto.AddActionRequest;
import com.dqh.nccapa.capa.dto.CompleteActionRequest;
import com.dqh.nccapa.capa.dto.TaoCapaRequest;
import com.dqh.nccapa.capa.dto.ToActionPlanRequest;
import com.dqh.nccapa.capa.dto.ToInvestigatingRequest;
import com.dqh.nccapa.capa.dto.ToMonitoringRequest;
import com.dqh.nccapa.capa.dto.ToProcessingRequest;
import com.dqh.nccapa.capa.entity.Capa;
import com.dqh.nccapa.capa.entity.CapaAction;
import com.dqh.nccapa.capa.service.CapaService;
import com.dqh.nccapa.common.enums.ActionType;
import com.dqh.nccapa.common.enums.LoaiThayDoi;
import com.dqh.nccapa.common.enums.NcSource;
import com.dqh.nccapa.nc.dto.ApproveRequest;
import com.dqh.nccapa.nc.dto.SubmitApprovalRequest;
import com.dqh.nccapa.nc.dto.TaoNcRequest;
import com.dqh.nccapa.nc.entity.Nc;
import com.dqh.nccapa.nc.service.NcService;
import com.dqh.nccapa.pr.dto.PrActionRequest;
import com.dqh.nccapa.pr.dto.TaoPrRequest;
import com.dqh.nccapa.pr.entity.Pr;
import com.dqh.nccapa.pr.service.PrService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Seed dữ liệu demo khi app.seed.enabled=true và bảng còn rỗng.
 * Bộ mã lỗi theo APPX (chuẩn đã chốt) + master data + dữ liệu mô phỏng OOS/MES tối thiểu,
 * + gán vai trò cho user (app_user_role) + phiếu NC/CAPA/PR mẫu đủ các trạng thái.
 * Test chạy với seed.enabled=false (xem src/test/resources/application-test.yml) nên không bị ảnh hưởng.
 *
 * Khi có CSV export từ MockData_NC-CAPA_v1.0.1.xlsx: thay phần INSERT bộ mã lỗi/master bằng nạp CSV.
 */
@Configuration
public class DemoSeeder {

    @Bean
    public ApplicationRunner seedRunner(AppProperties props, JdbcTemplate jdbc,
                                        NcService ncService, CapaService capaService, PrService prService) {
        return (ApplicationArguments args) -> {
            if (!props.getSeed().isEnabled()) return;
            Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM nha_may", Integer.class);
            if (n != null && n > 0) return; // đã seed

            seedMasterData(jdbc);
            seedUserRole(jdbc);
            seedPhieuDemo(jdbc, ncService, capaService, prService);
        };
    }

    /** Master data (nhà máy, role, user, mã hàng/NCC/lô, bộ mã lỗi, MES mô phỏng). */
    private void seedMasterData(JdbcTemplate jdbc) {
        // --- Nhà máy ---
        jdbc.update("INSERT INTO nha_may(ma, ten) VALUES ('MGP','Nha may Mega Phu'),('DNA','Nha may Dong Nai')");

        // --- Role ---
        jdbc.batchUpdate(
            "INSERT INTO role(ma, ten) VALUES ('QA_TAP_DOAN','QA tap doan')",
            "INSERT INTO role(ma, ten) VALUES ('QA_NHA_MAY','QA nha may')",
            "INSERT INTO role(ma, ten) VALUES ('BO_PHAN_SX','Bo phan san xuat')",
            "INSERT INTO role(ma, ten) VALUES ('BO_PHAN_KHO','Bo phan kho')",
            "INSERT INTO role(ma, ten) VALUES ('NGUOI_BAO_CAO','Nguoi bao cao')",
            "INSERT INTO role(ma, ten) VALUES ('R_AND_D','R&D')");

        // --- User demo ---
        jdbc.update("INSERT INTO app_user(username, full_name, nha_may, active) VALUES " +
                "('qatd','QA Tap doan', NULL, 1)," +
                "('qamgp','QA MGP','MGP',1)," +
                "('sxmgp','SX MGP','MGP',1)," +
                "('khomgp','Kho MGP','MGP',1)," +
                "('bcmgp','Nguoi bao cao MGP','MGP',1)," +
                "('rndtd','R&D Tap doan', NULL, 1)");

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
    }

    /**
     * Gán vai trò cho user (app_user_role). Master data — INSERT qua JdbcTemplate như master khác.
     * Idempotent: INSERT IGNORE theo khoá (user_id, role_ma).
     */
    private void seedUserRole(JdbcTemplate jdbc) {
        ganRole(jdbc, "qatd", "QA_TAP_DOAN");
        ganRole(jdbc, "qamgp", "QA_NHA_MAY");
        ganRole(jdbc, "sxmgp", "BO_PHAN_SX");
        ganRole(jdbc, "khomgp", "BO_PHAN_KHO");
        ganRole(jdbc, "bcmgp", "NGUOI_BAO_CAO");
        ganRole(jdbc, "rndtd", "R_AND_D");
    }

    private void ganRole(JdbcTemplate jdbc, String username, String roleMa) {
        jdbc.update("INSERT IGNORE INTO app_user_role(user_id, role_ma) " +
                "SELECT id, ? FROM app_user WHERE username = ?", roleMa, username);
    }

    /**
     * Phiếu NC/CAPA/PR mẫu đủ trạng thái — đi qua service để đúng guard/state machine (quy ước bắt buộc).
     * Mỗi phiếu dùng cặp (mã hàng + mã lỗi) RIÊNG để tránh TH2 fail sớm ăn chéo giữa các CAPA Monitoring.
     * Lưu ý trạng thái Closed của CAPA phụ thuộc mốc X ngày (thời gian thực trôi) nên KHÔNG seed được
     * qua service mà không chờ thực tế — dừng ở Monitoring (đã có action Completed + 2 mốc). Xem GHI CHÚ.
     */
    private void seedPhieuDemo(JdbcTemplate jdbc, NcService ncService,
                               CapaService capaService, PrService prService) {
        // 1) NC Nhẹ ở trạng thái đầu (New) — luồng thủ công.
        taoNc(ncService, "bcmgp", "FG-01", "SP001", NcSource.THU_CONG, "NC Nhe cam quan - de demo luong Nhanh");

        // 2) NC Nặng đã duyệt sang Processing (SoD: người đề xuất ≠ 2 người duyệt).
        Nc ncNang = taoNc(ncService, "bcmgp", "REC-02", "SP001", NcSource.THU_CONG,
                "NVL nhiem tap chat - NC Nang");
        SubmitApprovalRequest sub = new SubmitApprovalRequest();
        sub.nguyenNhanSoBo = "Nghi NVL dau vao nhiem tap chat";
        sub.dispositionDeXuat = "RETURN_NCC";
        sub.chiPhiUocTinh = new BigDecimal("5000000");
        sub.boPhanThucHien = "BO_PHAN_KHO";
        sub.actor = "sxmgp";
        ncService.submitApproval(ncNang.getSoHieu(), sub);
        ncService.approve(ncNang.getSoHieu(), approveReq("qamgp"));   // duyệt cấp 1
        ncService.approve(ncNang.getSoHieu(), approveReq("qatd"));    // duyệt cấp 2 -> Processing

        // 3) CAPA đang Investigating (từ NC Nhẹ để tránh guard RCA của NC Nặng).
        Nc ncCapa1 = taoNc(ncService, "bcmgp", "REC-06", "SP001", NcSource.THU_CONG,
                "Giay CN NCC het han - mo CAPA dieu tra");
        Capa capaInv = capaService.taoTuNc(ncCapa1.getSoHieu(), taoCapaReq("qamgp"));
        capaService.toInvestigating(capaInv.getId(), toInvestigatingReq("qamgp"));

        // 4) CAPA đã vào Monitoring (action Completed, 2 mốc; N=0 để không phụ thuộc MES).
        Nc ncCapa2 = taoNc(ncService, "bcmgp", "FG-01", "SP002", NcSource.THU_CONG,
                "Cam quan SP002 - mo CAPA den Monitoring");
        Capa capaMon = capaService.taoTuNc(ncCapa2.getSoHieu(), taoCapaReq("qamgp"));
        capaService.toInvestigating(capaMon.getId(), toInvestigatingReq("qamgp"));
        capaService.toActionPlan(capaMon.getId(), toActionPlanReq("qamgp"));
        CapaAction act = capaService.themAction(capaMon.getId(), addActionReq("sxmgp"));
        capaService.toProcessing(capaMon.getId(), toProcessingReq("qamgp"));
        capaService.completeAction(act.getId(), completeActionReq("sxmgp"));
        capaService.toMonitoring(capaMon.getId(), toMonitoringReq("qamgp"));

        // 5) PR nhiều cấp ở giữa chuỗi duyệt (CONG_THUC: R_AND_D -> QA_TAP_DOAN; đã duyệt cấp 1, còn cấp 2).
        TaoPrRequest prReq = new TaoPrRequest();
        prReq.loaiThayDoi = LoaiThayDoi.CONG_THUC;
        prReq.maHang = "SP002";
        prReq.maLoiId = "FG-04";
        prReq.actor = "qatd";
        Pr pr = prService.tao(prReq);
        prService.toAnalyzing(pr.getId(), prAction("qatd", null, null));
        prService.submit(pr.getId(), prAction("qatd", null, null));
        prService.approve(pr.getId(), prAction("rndtd", null, "R_AND_D")); // duyệt cấp 1, chờ QA_TAP_DOAN
    }

    // ---- helpers tạo request DTO (giữ seed gọn, dễ đọc) ----

    private Nc taoNc(NcService ncService, String nguoiBaoCao, String maLoi, String maHang,
                     NcSource nguon, String moTa) {
        TaoNcRequest req = new TaoNcRequest();
        req.loaiNc = "NC";
        req.nguonPhatSinh = nguon;
        req.nhaMay = "MGP";
        req.nguoiBaoCao = nguoiBaoCao;
        req.moTaLoi = moTa;
        TaoNcRequest.ChiTieuItem ct = new TaoNcRequest.ChiTieuItem();
        ct.maLoiId = maLoi;
        ct.maHang = maHang;
        req.chiTieu = List.of(ct);
        return ncService.taoNc(req);
    }

    private ApproveRequest approveReq(String actor) {
        ApproveRequest r = new ApproveRequest();
        r.actor = actor;
        return r;
    }

    private TaoCapaRequest taoCapaReq(String actor) {
        TaoCapaRequest r = new TaoCapaRequest();
        r.actor = actor;
        return r;
    }

    private ToInvestigatingRequest toInvestigatingReq(String actor) {
        ToInvestigatingRequest r = new ToInvestigatingRequest();
        r.khMucTieu = "Xac dinh nguyen nhan goc";
        r.actor = actor;
        return r;
    }

    private ToActionPlanRequest toActionPlanReq(String actor) {
        ToActionPlanRequest r = new ToActionPlanRequest();
        r.rcaNoiDung = "Nguyen nhan: thiet lap thong so chua chuan";
        r.actor = actor;
        return r;
    }

    private AddActionRequest addActionReq(String nguoiPhuTrach) {
        AddActionRequest r = new AddActionRequest();
        r.loai = ActionType.NGAN_TAI_DIEN;
        r.moTa = "Cap nhat SOP va dao tao lai";
        r.nguoiPhuTrach = nguoiPhuTrach;
        r.deadline = LocalDate.now().plusDays(7);
        r.actor = nguoiPhuTrach;
        return r;
    }

    private ToProcessingRequest toProcessingReq(String actor) {
        ToProcessingRequest r = new ToProcessingRequest();
        r.actor = actor;
        return r;
    }

    private CompleteActionRequest completeActionReq(String actor) {
        CompleteActionRequest r = new CompleteActionRequest();
        r.bangChung = "Bien ban hoan thanh + SOP ban moi";
        r.actor = actor;
        return r;
    }

    private ToMonitoringRequest toMonitoringReq(String actor) {
        ToMonitoringRequest r = new ToMonitoringRequest();
        r.xNgay = 30;
        r.nLo = 0;      // nhóm N=0: đóng bằng biên bản, không phụ thuộc MES
        r.actor = actor;
        return r;
    }

    private PrActionRequest prAction(String actor, String lyDo, String vaiTro) {
        PrActionRequest r = new PrActionRequest();
        r.actor = actor;
        r.lyDo = lyDo;
        r.vaiTro = vaiTro;
        return r;
    }

    private void seedMaLoi(JdbcTemplate jdbc, String ma, String nhom, String ten,
                           String muc, String nguy, int kv, String nhomN) {
        jdbc.update("INSERT INTO ma_loi(ma_loi_id, nhom_loi_id, ten_ma_loi, muc_nghiem_trong_default, " +
                "moi_nguy_default, bat_buoc_khoanh_vung, nhom_n_kiem_chung, nguoi_so_huu, hieu_luc_tu) " +
                "VALUES (?,?,?,?,?,?,?, 'QA_TAP_DOAN', '2026-01-01')",
                ma, nhom, ten, muc, nguy, kv, nhomN);
    }
}
