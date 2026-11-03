package com.dqh.nccapa.capa;

import com.dqh.nccapa.capa.dto.*;
import com.dqh.nccapa.capa.entity.Capa;
import com.dqh.nccapa.capa.entity.CapaAction;
import com.dqh.nccapa.capa.entity.CapaActionExtension;
import com.dqh.nccapa.capa.repository.CapaActionRepository;
import com.dqh.nccapa.capa.service.CapaService;
import com.dqh.nccapa.common.enums.ActionType;
import com.dqh.nccapa.common.enums.CapaStatus;
import com.dqh.nccapa.common.enums.NcSource;
import com.dqh.nccapa.common.error.BusinessException;
import com.dqh.nccapa.nc.dto.TaoNcRequest;
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

/** Lát 5: CAPA + action con + gia hạn có duyệt. */
class CapaFlowTest extends MySqlTestBase {

    @Autowired NcService ncService;
    @Autowired CapaService capaService;
    @Autowired CapaActionRepository actionRepo;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setUp() { DefectSeed.seed(jdbc); }

    private Nc taoNc(String maLoi, String lo) {
        TaoNcRequest r = new TaoNcRequest();
        r.loaiNc = "TRONG_SX"; r.nguonPhatSinh = NcSource.THU_CONG;
        r.nhaMay = "MGP"; r.nguoiBaoCao = "user01"; r.loKiem = lo;
        TaoNcRequest.ChiTieuItem ct = new TaoNcRequest.ChiTieuItem();
        ct.maLoiId = maLoi; ct.maHang = "SP001";
        r.chiTieu = List.of(ct);
        return ncService.taoNc(r);
    }

    private Capa taoCapa(String ncSo) {
        TaoCapaRequest t = new TaoCapaRequest(); t.actor = "qa";
        return capaService.taoTuNc(ncSo, t);
    }

    @Test // AC14: CAPA từ NC Nhẹ qua Investigating -> ActionPlan không kẹt
    void ncNhe_quaInvestigating_khongKet() {
        Nc nc = taoNc("FG-01", "LOT-CP1"); // Nhẹ
        Capa capa = taoCapa(nc.getSoHieu());
        assertThat(ncService.layNc(nc.getSoHieu()).isEscalated()).isTrue(); // tạo CAPA bật is_escalated

        ToInvestigatingRequest inv = new ToInvestigatingRequest();
        inv.keHoachDieuTra = null; inv.actor = "qa";  // Nhẹ: không bắt buộc
        capaService.toInvestigating(capa.getId(), inv);
        assertThat(capaService.layCapa(capa.getId()).getTrangThai()).isEqualTo(CapaStatus.Investigating);

        ToActionPlanRequest ap = new ToActionPlanRequest(); ap.actor = "qa"; // Nhẹ: không bắt buộc RCA
        capaService.toActionPlan(capa.getId(), ap);
        assertThat(capaService.layCapa(capa.getId()).getTrangThai()).isEqualTo(CapaStatus.ActionPlan);
    }

    @Test // NC Nặng: thiếu kế hoạch điều tra bị chặn ở C1
    void ncNang_thieuKeHoach_biChan() {
        Nc nc = taoNc("PRC-02", "LOT-CP2"); // Nặng
        Capa capa = taoCapa(nc.getSoHieu());
        ToInvestigatingRequest inv = new ToInvestigatingRequest();
        inv.keHoachDieuTra = "  "; inv.actor = "qa";
        assertThatThrownBy(() -> capaService.toInvestigating(capa.getId(), inv))
                .isInstanceOf(BusinessException.class);
    }

    @Test // Luồng đầy đủ tới Monitoring (NC Nặng)
    void luongDayDuToiMonitoring() {
        Nc nc = taoNc("PRC-02", "LOT-CP3");
        Capa capa = taoCapa(nc.getSoHieu());
        Long id = capa.getId();

        ToInvestigatingRequest inv = new ToInvestigatingRequest();
        inv.keHoachDieuTra = "điều tra X"; inv.actor = "qa";
        capaService.toInvestigating(id, inv);

        ToActionPlanRequest ap = new ToActionPlanRequest();
        ap.rcaNoiDung = "5why"; ap.nhomNguyenNhan = "con người"; ap.chiPhiDauTu = new BigDecimal("2000000");
        ap.actor = "qa";
        capaService.toActionPlan(id, ap);

        AddActionRequest a1 = new AddActionRequest();
        a1.loai = ActionType.TUC_THOI; a1.moTa = "cô lập"; a1.nguoiPhuTrach = "sx"; a1.deadline = LocalDate.now().plusDays(3); a1.actor = "qa";
        CapaAction act = capaService.themAction(id, a1);

        ToProcessingRequest pr = new ToProcessingRequest();
        pr.khacPhucTucThoi = "cô lập lô"; pr.nganTaiDien = "đào tạo lại"; pr.actor = "qa";
        capaService.toProcessing(id, pr);
        assertThat(capaService.layCapa(id).getTrangThai()).isEqualTo(CapaStatus.Processing);

        CompleteActionRequest comp = new CompleteActionRequest(); comp.bangChung = "bb"; comp.actor = "sx";
        capaService.completeAction(act.getId(), comp);

        ToMonitoringRequest mon = new ToMonitoringRequest();
        mon.xNgay = 30; mon.nLo = 5; mon.actor = "qa";
        capaService.toMonitoring(id, mon);
        assertThat(capaService.layCapa(id).getTrangThai()).isEqualTo(CapaStatus.Monitoring);
    }

    @Test // AC10: gia hạn phải được duyệt mới đổi deadline; người duyệt != người yêu cầu
    void giaHan_canDuyet_moiDoiDeadline() {
        Nc nc = taoNc("PRC-02", "LOT-CP4");
        Capa capa = taoCapa(nc.getSoHieu());
        Long id = capa.getId();
        ToInvestigatingRequest inv = new ToInvestigatingRequest(); inv.keHoachDieuTra = "x"; inv.actor = "qa";
        capaService.toInvestigating(id, inv);
        ToActionPlanRequest ap = new ToActionPlanRequest();
        ap.rcaNoiDung = "x"; ap.nhomNguyenNhan = "x"; ap.chiPhiDauTu = BigDecimal.ONE; ap.actor = "qa";
        capaService.toActionPlan(id, ap);
        AddActionRequest a1 = new AddActionRequest();
        a1.loai = ActionType.NGAN_TAI_DIEN; a1.moTa = "x"; a1.nguoiPhuTrach = "sx";
        a1.deadline = LocalDate.now().plusDays(2); a1.actor = "qa";
        CapaAction act = capaService.themAction(id, a1);

        LocalDate hanCu = act.getDeadline();
        ExtensionRequest ext = new ExtensionRequest();
        ext.deadlineMoi = hanCu.plusDays(7); ext.lyDo = "thiếu nguồn lực"; ext.actor = "sx";
        CapaActionExtension e = capaService.yeuCauGiaHan(act.getId(), ext);

        // Chưa duyệt -> deadline chưa đổi
        assertThat(actionRepo.findById(act.getId()).get().getDeadline()).isEqualTo(hanCu);

        // Người duyệt trùng người yêu cầu -> chặn
        ApproveExtensionRequest selfApprove = new ApproveExtensionRequest(); selfApprove.actor = "sx";
        assertThatThrownBy(() -> capaService.duyetGiaHan(e.getId(), selfApprove))
                .isInstanceOf(BusinessException.class);

        // Người khác duyệt -> đổi deadline
        ApproveExtensionRequest ok = new ApproveExtensionRequest(); ok.actor = "manager";
        capaService.duyetGiaHan(e.getId(), ok);
        assertThat(actionRepo.findById(act.getId()).get().getDeadline()).isEqualTo(hanCu.plusDays(7));
    }
}
