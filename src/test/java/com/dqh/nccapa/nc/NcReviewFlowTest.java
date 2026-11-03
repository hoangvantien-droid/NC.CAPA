package com.dqh.nccapa.nc;

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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Lát 2: luồng Có xem xét (NC Nặng) 5 bước + duyệt 2 cấp + SoD + guard N9. */
class NcReviewFlowTest extends MySqlTestBase {

    @Autowired NcService ncService;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        DefectSeed.seed(jdbc);
        // NC Nặng cần người duyệt thuộc nhóm QA (REQ 6.2) — seed role THẬT cho các actor duyệt
        // (seed demo tắt ở profile test → app_user_role rỗng).
        DefectSeed.seedUserRole(jdbc, "qaNM1", "QA_NHA_MAY");
        DefectSeed.seedUserRole(jdbc, "qaNM2", "QA_NHA_MAY");
        DefectSeed.seedUserRole(jdbc, "qaX", "QA_NHA_MAY");
    }

    private Nc taoNcNang(String lo) {
        TaoNcRequest r = new TaoNcRequest();
        r.loaiNc = "TRONG_SX"; r.nguonPhatSinh = NcSource.THU_CONG;
        r.nhaMay = "MGP"; r.nguoiBaoCao = "user01"; r.loKiem = lo;
        TaoNcRequest.ChiTieuItem ct = new TaoNcRequest.ChiTieuItem();
        ct.maLoiId = "PRC-02"; ct.maHang = "SP001"; // Nặng
        r.chiTieu = List.of(ct);
        return ncService.taoNc(r);
    }

    private void submit(Nc nc, String actor) {
        SubmitApprovalRequest s = new SubmitApprovalRequest();
        s.nguyenNhanSoBo = "rc sơ bộ"; s.dispositionDeXuat = "REWORK";
        s.chiPhiUocTinh = new BigDecimal("1000000"); s.boPhanThucHien = "SX";
        s.actor = actor;
        ncService.submitApproval(nc.getSoHieu(), s);
    }

    private void approve(Nc nc, String actor) {
        ApproveRequest a = new ApproveRequest(); a.actor = actor;
        ncService.approve(nc.getSoHieu(), a);
    }

    @Test // AC2: NC Nặng đi đủ 5 bước, cần 2 người duyệt (người đề xuất != người duyệt — SoD)
    void luongNang5Buoc() {
        Nc nc = taoNcNang("LOT-N1");
        submit(nc, "proposer");
        approve(nc, "qaNM1");      // cấp 1 (khác người đề xuất)
        assertThat(reload(nc).getTrangThai()).isEqualTo(NcStatus.PendingApproval); // chờ người 2
        approve(nc, "qaNM2");      // cấp 2 (khác cấp 1)
        assertThat(reload(nc).getTrangThai()).isEqualTo(NcStatus.Processing);

        ConfirmDoneRequest c = new ConfirmDoneRequest();
        c.bangChung = "bien ban"; c.chiPhiThucTe = new BigDecimal("950000"); c.actor = "SX";
        ncService.confirmDone(nc.getSoHieu(), c);
        assertThat(reload(nc).getTrangThai()).isEqualTo(NcStatus.PendingVerification);

        VerifyCloseRequest v = new VerifyCloseRequest();
        v.ketLuanThamTra = "đạt"; v.actor = "qaNM1";
        ncService.verifyClose(nc.getSoHieu(), v);
        assertThat(reload(nc).getTrangThai()).isEqualTo(NcStatus.Closed);
    }

    @Test // Siết SoD theo role THẬT: NC Nặng -> actor KHÔNG thuộc nhóm QA bị chặn duyệt
    void ncNang_actorKhongPhaiQa_biChan() {
        Nc nc = taoNcNang("LOT-N6");
        submit(nc, "proposer");
        DefectSeed.seedUserRole(jdbc, "sx1", "BO_PHAN_SX"); // có user nhưng không phải QA
        ApproveRequest a = new ApproveRequest(); a.actor = "sx1";
        assertThatThrownBy(() -> ncService.approve(nc.getSoHieu(), a))
                .isInstanceOf(BusinessException.class);
        assertThat(reload(nc).getTrangThai()).isEqualTo(NcStatus.PendingApproval); // chưa duyệt
    }

    @Test // NC Nặng -> actor QA (QA_NHA_MAY) duyệt được cấp 1
    void ncNang_actorQa_duyetDuoc() {
        Nc nc = taoNcNang("LOT-N7");
        submit(nc, "proposer");
        approve(nc, "qaNM1"); // qaNM1 đã seed QA_NHA_MAY
        assertThat(reload(nc).getTrangThai()).isEqualTo(NcStatus.PendingApproval); // cấp 1 xong, chờ người 2
    }

    @Test // AC25: cùng user duyệt 2 lần bị chặn
    void cungUserDuyet2Lan_biChan() {
        Nc nc = taoNcNang("LOT-N2");
        submit(nc, "proposer");
        approve(nc, "qaX");  // cấp 1
        ApproveRequest a2 = new ApproveRequest(); a2.actor = "qaX";
        assertThatThrownBy(() -> ncService.approve(nc.getSoHieu(), a2))
                .isInstanceOf(BusinessException.class);
    }

    @Test // SoD: người đề xuất không được duyệt
    void soD_nguoiDeXuatKhongDuocDuyet() {
        Nc nc = taoNcNang("LOT-N3");
        submit(nc, "qaProposer");
        ApproveRequest a = new ApproveRequest(); a.actor = "qaProposer";
        assertThatThrownBy(() -> ncService.approve(nc.getSoHieu(), a))
                .isInstanceOf(BusinessException.class);
    }

    @Test // AC8: confirm-done thiếu chi phí thực tế bị chặn (validation NotNull chặn ở DTO; service cũng chặn)
    void confirmDone_thieuChiPhi_biChan() {
        Nc nc = taoNcNang("LOT-N4");
        submit(nc, "proposer");
        approve(nc, "qaNM1"); approve(nc, "qaNM2");
        ConfirmDoneRequest c = new ConfirmDoneRequest();
        c.bangChung = "x"; c.chiPhiThucTe = null; c.actor = "SX";
        assertThatThrownBy(() -> ncService.confirmDone(nc.getSoHieu(), c))
                .isInstanceOf(BusinessException.class);
    }

    @Test // AC21: guard N9 KHÔNG chặn NC Nặng không khoanh vùng (doiTuong trạng thái NULL)
    void ncNangKhongKhoanhVung_dongDuoc() {
        Nc nc = taoNcNang("LOT-N5");
        submit(nc, "proposer");
        approve(nc, "qaNM1"); approve(nc, "qaNM2");
        ConfirmDoneRequest c = new ConfirmDoneRequest();
        c.bangChung = "bb"; c.chiPhiThucTe = new BigDecimal("1"); c.actor = "SX";
        ncService.confirmDone(nc.getSoHieu(), c);
        VerifyCloseRequest v = new VerifyCloseRequest();
        v.ketLuanThamTra = "đạt"; v.actor = "qaNM1";
        Nc closed = ncService.verifyClose(nc.getSoHieu(), v);
        assertThat(closed.getTrangThai()).isEqualTo(NcStatus.Closed);  // không bị treo
    }

    private Nc reload(Nc nc) {
        return ncService.layNc(nc.getSoHieu());
    }
}
