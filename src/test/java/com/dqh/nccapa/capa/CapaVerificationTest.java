package com.dqh.nccapa.capa;

import com.dqh.nccapa.capa.dto.*;
import com.dqh.nccapa.capa.entity.Capa;
import com.dqh.nccapa.capa.service.CapaService;
import com.dqh.nccapa.common.enums.ActionType;
import com.dqh.nccapa.common.enums.CapaStatus;
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

/** Lát 6: kiểm chứng 2 mốc, mark-ineffective (TH1), guard N9 thật (AC9). */
class CapaVerificationTest extends MySqlTestBase {

    @Autowired NcService ncService;
    @Autowired CapaService capaService;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        DefectSeed.seed(jdbc);
        // "qatd" là QA TẬP ĐOÀN để qua guard dời hạn (điểm mở #7): seed demo tắt ở profile test
        // nên app_user_role rỗng — phải seed trực tiếp vai trò cho actor dời hạn.
        DefectSeed.seedUserRole(jdbc, "qatd", "QA_TAP_DOAN");
    }

    private Nc taoNc(String maLoi, String lo) {
        TaoNcRequest r = new TaoNcRequest();
        r.loaiNc = "TRONG_SX"; r.nguonPhatSinh = NcSource.THU_CONG;
        r.nhaMay = "MGP"; r.nguoiBaoCao = "user01"; r.loKiem = lo;
        TaoNcRequest.ChiTieuItem ct = new TaoNcRequest.ChiTieuItem();
        ct.maLoiId = maLoi; ct.maHang = "SP001";
        r.chiTieu = List.of(ct);
        return ncService.taoNc(r);
    }

    /** Tạo CAPA (từ NC Nặng) đi tới Monitoring với n_lo cho trước. */
    private Long capaToMonitoring(String ncSo, int nLo) {
        TaoCapaRequest t = new TaoCapaRequest(); t.actor = "qa";
        Capa capa = capaService.taoTuNc(ncSo, t);
        Long id = capa.getId();
        ToInvestigatingRequest inv = new ToInvestigatingRequest(); inv.keHoachDieuTra = "x"; inv.actor = "qa";
        capaService.toInvestigating(id, inv);
        ToActionPlanRequest ap = new ToActionPlanRequest();
        ap.rcaNoiDung = "x"; ap.nhomNguyenNhan = "x"; ap.chiPhiDauTu = BigDecimal.ONE; ap.actor = "qa";
        capaService.toActionPlan(id, ap);
        AddActionRequest a = new AddActionRequest();
        a.loai = ActionType.NGAN_TAI_DIEN; a.moTa = "x"; a.nguoiPhuTrach = "sx";
        a.deadline = LocalDate.now().plusDays(1); a.actor = "qa";
        var act = capaService.themAction(id, a);
        ToProcessingRequest pr = new ToProcessingRequest();
        pr.khacPhucTucThoi = "x"; pr.nganTaiDien = "y"; pr.actor = "qa";
        capaService.toProcessing(id, pr);
        CompleteActionRequest c = new CompleteActionRequest(); c.bangChung = "bb"; c.actor = "sx";
        capaService.completeAction(act.getId(), c);
        ToMonitoringRequest mon = new ToMonitoringRequest(); mon.xNgay = 30; mon.nLo = nLo; mon.actor = "qa";
        capaService.toMonitoring(id, mon);
        return id;
    }

    /** Lùi mốc + monitoring_bat_dau về quá khứ để mô phỏng "đã đủ X ngày". */
    private void lamXDat(Long capaId) {
        jdbc.update("UPDATE capa SET monitoring_bat_dau = DATE_SUB(NOW(), INTERVAL 60 DAY) WHERE id = ?", capaId);
        jdbc.update("UPDATE capa_verification SET han_kiem_chung_tiep_theo = DATE_SUB(NOW(), INTERVAL 1 DAY) WHERE capa_id = ?", capaId);
    }

    @Test // AC11: mốc nhắc chưa đủ -> không confirm; đủ 2 mốc (N=0 + biên bản) -> confirm -> Closed
    void nhomN0_bienBan_dongDuoc() {
        Nc nc = taoNc("PRC-02", "LOT-V1");
        Long id = capaToMonitoring(nc.getSoHieu(), 0);

        // chưa đủ X ngày -> confirm bị chặn
        ConfirmEffectiveRequest cf = new ConfirmEffectiveRequest();
        cf.bienBanKiemTra = "BB kiểm tra thực tế"; cf.actor = "qa";
        assertThatThrownBy(() -> capaService.confirmEffective(id, cf))
                .isInstanceOf(BusinessException.class);

        lamXDat(id);
        capaService.confirmEffective(id, cf);
        assertThat(capaService.layCapa(id).getTrangThai()).isEqualTo(CapaStatus.Closed);
    }

    @Test // AC11: N>=1 nhưng chưa đủ lô MES -> không confirm
    void chuaDuLo_khongConfirm() {
        Nc nc = taoNc("PRC-02", "LOT-V2");
        Long id = capaToMonitoring(nc.getSoHieu(), 3); // cần 3 lô, chưa seed lô nào
        lamXDat(id);
        ConfirmEffectiveRequest cf = new ConfirmEffectiveRequest(); cf.actor = "qa";
        assertThatThrownBy(() -> capaService.confirmEffective(id, cf))
                .isInstanceOf(BusinessException.class);  // N chưa đạt
    }

    @Test // AC12: quá M lần dời hạn -> tự Ineffective
    void quaMLanDoiHan_tuIneffective() {
        Nc nc = taoNc("PRC-02", "LOT-V3");
        Long id = capaToMonitoring(nc.getSoHieu(), 3);
        PostponeRequest p = new PostponeRequest(); p.soNgayDoiThem = 7; p.lyDo = "thiếu lô"; p.actor = "qatd";
        capaService.postponeReminder(id, p);   // lần 1 (so_lan_doi_han: 0 -> 1)
        capaService.postponeReminder(id, p);   // lần 2 (1 -> 2)
        // lần 3: so_lan_doi_han(2) >= M(2) -> tự Ineffective, không ném
        capaService.postponeReminder(id, p);
        assertThat(capaService.layCapa(id).getTrangThai()).isEqualTo(CapaStatus.Ineffective);
    }

    @Test // Điểm mở #7/#14: NC Nặng -> actor KHÔNG phải QA_TAP_DOAN bị chặn dời hạn
    void ncNang_actorThuong_khongDoiHanDuoc() {
        Nc nc = taoNc("PRC-02", "LOT-V5"); // PRC-02 = Nặng
        Long id = capaToMonitoring(nc.getSoHieu(), 3);
        DefectSeed.seedUserRole(jdbc, "sxmgp", "BO_PHAN_SX"); // actor có user nhưng không phải QA_TAP_DOAN
        PostponeRequest p = new PostponeRequest(); p.soNgayDoiThem = 7; p.lyDo = "thiếu lô"; p.actor = "sxmgp";
        assertThatThrownBy(() -> capaService.postponeReminder(id, p))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("QA tập đoàn");
    }

    @Test // Điểm mở #7/#14: NC Nặng -> actor QA_TAP_DOAN dời hạn được
    void ncNang_qaTapDoan_doiHanDuoc() {
        Nc nc = taoNc("PRC-02", "LOT-V6"); // PRC-02 = Nặng
        Long id = capaToMonitoring(nc.getSoHieu(), 3);
        PostponeRequest p = new PostponeRequest(); p.soNgayDoiThem = 7; p.lyDo = "thiếu lô"; p.actor = "qatd";
        capaService.postponeReminder(id, p);
        VerificationStatus st = capaService.verificationStatus(id);
        assertThat(st.soLanDoiHan).isEqualTo(1);
    }

    @Test // Điểm mở #7/#14: NC Nhẹ -> guard không áp, actor thường vẫn dời hạn được
    void ncNhe_actorThuong_doiHanDuoc() {
        Nc nc = taoNc("REC-06", "LOT-V7"); // REC-06 = Nhẹ (N=0)
        Long id = capaToMonitoring(nc.getSoHieu(), 3);
        PostponeRequest p = new PostponeRequest(); p.soNgayDoiThem = 7; p.lyDo = "chưa đủ bằng chứng"; p.actor = "sxmgp";
        capaService.postponeReminder(id, p); // không bị chặn dù actor không phải QA_TAP_DOAN
        VerificationStatus st = capaService.verificationStatus(id);
        assertThat(st.soLanDoiHan).isEqualTo(1);
    }

    @Test // AC9: CAPA Ineffective + handover -> NC gốc đóng được (không treo)
    void markIneffective_sinhNcKeThua_ncGocDongDuoc() {
        Nc nc = taoNc("PRC-02", "LOT-V4");
        String ncSo = nc.getSoHieu();
        Long id = capaToMonitoring(ncSo, 3);

        MarkIneffectiveRequest mi = new MarkIneffectiveRequest();
        mi.lyDo = "không hiệu quả tại mốc"; mi.actor = "qa";
        IneffectiveResult r = capaService.markIneffective(id, mi);

        assertThat(capaService.layCapa(id).getTrangThai()).isEqualTo(CapaStatus.Ineffective);
        assertThat(r.ncKeThuaSoHieu).isNotNull();
        assertThat(r.ncKeThuaMuc).isEqualTo("NGHIEM_TRONG");  // Nặng nâng 1 bậc
        assertThat(r.moPrNgay).isTrue();                       // trần Nghiêm trọng -> PR

        // Đưa NC gốc tới PendingVerification rồi đóng — guard N9 không treo vì CAPA đã handover
        submitApproveConfirm(ncSo);
        VerifyCloseRequest v = new VerifyCloseRequest(); v.ketLuanThamTra = "đạt"; v.actor = "qaNM1";
        ncService.verifyClose(ncSo, v);
        assertThat(ncService.layNc(ncSo).getTrangThai()).isEqualTo(NcStatus.Closed);
    }

    private void submitApproveConfirm(String ncSo) {
        SubmitApprovalRequest s = new SubmitApprovalRequest();
        s.nguyenNhanSoBo = "x"; s.dispositionDeXuat = "REWORK";
        s.chiPhiUocTinh = new BigDecimal("1"); s.boPhanThucHien = "SX"; s.actor = "proposer";
        ncService.submitApproval(ncSo, s);
        ApproveRequest a1 = new ApproveRequest(); a1.actor = "qaNM1"; ncService.approve(ncSo, a1);
        ApproveRequest a2 = new ApproveRequest(); a2.actor = "qaNM2"; ncService.approve(ncSo, a2);
        ConfirmDoneRequest c = new ConfirmDoneRequest();
        c.bangChung = "bb"; c.chiPhiThucTe = new BigDecimal("1"); c.actor = "SX";
        ncService.confirmDone(ncSo, c);
    }
}
