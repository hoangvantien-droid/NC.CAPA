package com.dqh.nccapa.nc;

import com.dqh.nccapa.common.audit.AuditService;
import com.dqh.nccapa.common.enums.NcSource;
import com.dqh.nccapa.common.enums.NcStatus;
import com.dqh.nccapa.common.error.BusinessException;
import com.dqh.nccapa.nc.dto.*;
import com.dqh.nccapa.nc.entity.Nc;
import com.dqh.nccapa.nc.service.NcService;
import com.dqh.nccapa.support.DefectSeed;
import com.dqh.nccapa.support.MySqlTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Nhóm 1: trường bổ sung ở tạo NC, nguyên nhân sau điều tra, ý kiến duyệt, kết quả thực tế, thẩm tra Đạt/Không đạt. */
class NcNhom1Test extends MySqlTestBase {

    @Autowired NcService ncService;
    @Autowired AuditService audit;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        DefectSeed.seed(jdbc);
        DefectSeed.seedUserRole(jdbc, "qaNM1", "QA_NHA_MAY");
        DefectSeed.seedUserRole(jdbc, "qaNM2", "QA_NHA_MAY");
    }

    private Nc taoNcNang(String lo) {
        TaoNcRequest r = new TaoNcRequest();
        r.loaiNc = "TRONG_SX"; r.nguonPhatSinh = NcSource.THU_CONG;
        r.nhaMay = "MGP"; r.nguoiBaoCao = "user01"; r.loKiem = lo;
        r.lenhSx = "LSX-01"; r.congDoan = "Đóng gói"; r.khuVuc = "Line 2";
        r.ngaySx = LocalDate.of(2026, 11, 1); r.soLuong = new BigDecimal("120");
        r.nguyenNhanSoBo = "nghi do may";
        TaoNcRequest.ChiTieuItem ct = new TaoNcRequest.ChiTieuItem();
        ct.maLoiId = "PRC-02"; ct.maHang = "SP001"; // Nặng
        r.chiTieu = List.of(ct);
        return ncService.taoNc(r);
    }

    private void submit(Nc nc, String actor, String nnDieuTra, String giaiPhap) {
        SubmitApprovalRequest s = new SubmitApprovalRequest();
        s.nguyenNhanDieuTra = nnDieuTra; s.giaiPhapXuLy = giaiPhap;
        s.dispositionDeXuat = "REWORK"; s.chiPhiUocTinh = new BigDecimal("1000");
        s.boPhanThucHien = "SX"; s.actor = actor;
        ncService.submitApproval(nc.getSoHieu(), s);
    }

    private Nc daToiProcessing(String lo) {
        Nc nc = taoNcNang(lo);
        submit(nc, "proposer", "nguyên nhân thật", "sửa lại khuôn");
        ApproveRequest a1 = new ApproveRequest(); a1.actor = "qaNM1"; ncService.approve(nc.getSoHieu(), a1);
        ApproveRequest a2 = new ApproveRequest(); a2.actor = "qaNM2"; ncService.approve(nc.getSoHieu(), a2);
        return ncService.layNc(nc.getSoHieu());
    }

    private void confirmDone(Nc nc) {
        ConfirmDoneRequest c = new ConfirmDoneRequest();
        c.ketQuaThucTe = "đã làm lại 120 sp"; c.chiPhiThucTe = new BigDecimal("900"); c.actor = "SX";
        ncService.confirmDone(nc.getSoHieu(), c);
    }

    @Test
    void taoNc_luuLenhSxCongDoanVaCacTruongBoSung() {
        Nc nc = ncService.layNc(taoNcNang("LOT-G1-1").getSoHieu());
        assertThat(nc.getLenhSx()).isEqualTo("LSX-01");
        assertThat(nc.getCongDoan()).isEqualTo("Đóng gói");
        assertThat(nc.getKhuVuc()).isEqualTo("Line 2");
        assertThat(nc.getNgaySx()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(nc.getSoLuong()).isEqualByComparingTo("120");
        assertThat(nc.getNguyenNhanSoBo()).isEqualTo("nghi do may");
    }

    @Test // nguyên nhân sau điều tra lưu riêng, nguyên nhân sơ bộ giữ nguyên
    void trinhDuyet_nguyenNhanDieuTraKhacSoBo() {
        Nc nc = taoNcNang("LOT-G1-2");
        submit(nc, "proposer", "khuôn mòn", "thay khuôn");
        Nc r = ncService.layNc(nc.getSoHieu());
        assertThat(r.getNguyenNhanSoBo()).isEqualTo("nghi do may");
        assertThat(r.getNguyenNhanDieuTra()).isEqualTo("khuôn mòn");
        assertThat(r.getGiaiPhapXuLy()).isEqualTo("thay khuôn");
    }

    @Test // thiếu cả nguyên nhân sau điều tra lẫn sơ bộ trong request -> chặn
    void trinhDuyet_thieuNguyenNhan_biChan() {
        Nc nc = taoNcNang("LOT-G1-3");
        assertThatThrownBy(() -> submit(nc, "proposer", " ", null)).isInstanceOf(BusinessException.class);
        assertThat(ncService.layNc(nc.getSoHieu()).getTrangThai()).isEqualTo(NcStatus.New);
    }

    @Test
    void duyet_yKienDuocGhiAudit() {
        Nc nc = taoNcNang("LOT-G1-4");
        submit(nc, "proposer", "khuôn mòn", null);
        ApproveRequest a = new ApproveRequest(); a.actor = "qaNM1"; a.yKien = "đồng ý, ưu tiên làm gấp";
        ncService.approve(nc.getSoHieu(), a);
        assertThat(audit.timTheo("NC", nc.getSoHieu()))
                .anyMatch(l -> "Y_KIEN_DUYET".equals(l.getHanhDong()) && "đồng ý, ưu tiên làm gấp".equals(l.getLyDo()));
    }

    @Test // thiếu cả kết quả thực tế lẫn bằng chứng -> chặn
    void confirmDone_thieuKetQuaVaBangChung_biChan() {
        Nc nc = daToiProcessing("LOT-G1-5");
        ConfirmDoneRequest c = new ConfirmDoneRequest();
        c.chiPhiThucTe = BigDecimal.ONE; c.actor = "SX";
        assertThatThrownBy(() -> ncService.confirmDone(nc.getSoHieu(), c)).isInstanceOf(BusinessException.class);
        assertThat(ncService.layNc(nc.getSoHieu()).getTrangThai()).isEqualTo(NcStatus.Processing);
    }

    @Test
    void confirmDone_chiCoKetQuaThucTe_duocVaLuuKetQua() {
        Nc nc = daToiProcessing("LOT-G1-6");
        confirmDone(nc);
        Nc r = ncService.layNc(nc.getSoHieu());
        assertThat(r.getTrangThai()).isEqualTo(NcStatus.PendingVerification);
        assertThat(r.getKetQuaXuLy()).isEqualTo("đã làm lại 120 sp");
    }

    @Test // thẩm tra KHÔNG ĐẠT -> về Processing, rồi xử lý lại và thẩm tra ĐẠT -> Closed
    void thamTra_khongDat_veProcessing_roiDatThiDong() {
        Nc nc = daToiProcessing("LOT-G1-7");
        confirmDone(nc);

        VerifyCloseRequest khong = new VerifyCloseRequest();
        khong.ketQuaThamTra = "KHONG_DAT"; khong.ketLuanThamTra = "còn 5 sp lỗi"; khong.actor = "qaNM1";
        ncService.verifyClose(nc.getSoHieu(), khong);
        Nc r1 = ncService.layNc(nc.getSoHieu());
        assertThat(r1.getTrangThai()).isEqualTo(NcStatus.Processing);
        assertThat(r1.getKetQuaThamTra()).isEqualTo("KHONG_DAT");
        assertThat(audit.timTheo("NC", nc.getSoHieu()))
                .anyMatch(l -> "THAM_TRA_KHONG_DAT".equals(l.getHanhDong()));

        confirmDone(nc);
        VerifyCloseRequest dat = new VerifyCloseRequest();
        dat.ketLuanThamTra = "đạt"; dat.actor = "qaNM1"; // mặc định = DAT
        ncService.verifyClose(nc.getSoHieu(), dat);
        Nc r2 = ncService.layNc(nc.getSoHieu());
        assertThat(r2.getTrangThai()).isEqualTo(NcStatus.Closed);
        assertThat(r2.getKetQuaThamTra()).isEqualTo("DAT");
    }

    @Test
    void thamTra_giaTriLa_biChan() {
        Nc nc = daToiProcessing("LOT-G1-8");
        confirmDone(nc);
        VerifyCloseRequest v = new VerifyCloseRequest();
        v.ketQuaThamTra = "XYZ"; v.ketLuanThamTra = "x"; v.actor = "qaNM1";
        assertThatThrownBy(() -> ncService.verifyClose(nc.getSoHieu(), v)).isInstanceOf(BusinessException.class);
        assertThat(ncService.layNc(nc.getSoHieu()).getTrangThai()).isEqualTo(NcStatus.PendingVerification);
    }

    @Test // N7: bị từ chối -> nộp lại đề xuất đã sửa -> PendingApproval, duyệt cấp 1 bị reset
    void resubmit_sauKhiBiTuChoi_veChoDuyet() {
        Nc nc = taoNcNang("LOT-G1-9");
        submit(nc, "proposer", "nguyên nhân A", null);
        ApproveRequest a1 = new ApproveRequest(); a1.actor = "qaNM1"; ncService.approve(nc.getSoHieu(), a1);
        assertThat(ncService.layNc(nc.getSoHieu()).isDaDuyetCap1()).isTrue();

        RejectRequest rj = new RejectRequest(); rj.lyDo = "chi phí chưa hợp lý"; rj.actor = "qaNM2";
        ncService.reject(nc.getSoHieu(), rj);
        assertThat(ncService.layNc(nc.getSoHieu()).getTrangThai()).isEqualTo(NcStatus.Rejected);

        SubmitApprovalRequest s = new SubmitApprovalRequest();
        s.nguyenNhanDieuTra = "nguyên nhân A (đã bổ sung)"; s.giaiPhapXuLy = "giải pháp mới";
        s.dispositionDeXuat = "TAI_CHE"; s.chiPhiUocTinh = new BigDecimal("500");
        s.boPhanThucHien = "SX"; s.actor = "proposer";
        ncService.resubmit(nc.getSoHieu(), s);

        Nc r = ncService.layNc(nc.getSoHieu());
        assertThat(r.getTrangThai()).isEqualTo(NcStatus.PendingApproval);
        assertThat(r.isDaDuyetCap1()).isFalse();
        assertThat(r.getDispositionDeXuat()).isEqualTo("TAI_CHE");
        assertThat(r.getNguyenNhanDieuTra()).isEqualTo("nguyên nhân A (đã bổ sung)");
    }

    @Test // resubmit chỉ hợp lệ từ Rejected
    void resubmit_khongPhaiRejected_biChan() {
        Nc nc = taoNcNang("LOT-G1-10");
        SubmitApprovalRequest s = new SubmitApprovalRequest();
        s.nguyenNhanDieuTra = "x"; s.dispositionDeXuat = "REWORK"; s.chiPhiUocTinh = BigDecimal.ONE;
        s.boPhanThucHien = "SX"; s.actor = "proposer";
        assertThatThrownBy(() -> ncService.resubmit(nc.getSoHieu(), s)).isInstanceOf(BusinessException.class);
        assertThat(ncService.layNc(nc.getSoHieu()).getTrangThai()).isEqualTo(NcStatus.New);
    }}
