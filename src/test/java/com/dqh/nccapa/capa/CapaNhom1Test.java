package com.dqh.nccapa.capa;

import com.dqh.nccapa.capa.dto.*;
import com.dqh.nccapa.capa.entity.Capa;
import com.dqh.nccapa.capa.entity.CapaAction;
import com.dqh.nccapa.capa.entity.CapaActionExtension;
import com.dqh.nccapa.capa.service.CapaService;
import com.dqh.nccapa.common.enums.ActionType;
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

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Nhóm 1 (CAPA): RPN S/O/D, cờ pháp lý, người điều tra, guard gia hạn action. */
class CapaNhom1Test extends MySqlTestBase {

    @Autowired NcService ncService;
    @Autowired CapaService capaService;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setUp() { DefectSeed.seed(jdbc); }

    private Capa capaOpen(String lo) {
        TaoNcRequest r = new TaoNcRequest();
        r.loaiNc = "TRONG_SX"; r.nguonPhatSinh = NcSource.THU_CONG;
        r.nhaMay = "MGP"; r.nguoiBaoCao = "user01"; r.loKiem = lo;
        TaoNcRequest.ChiTieuItem ct = new TaoNcRequest.ChiTieuItem();
        ct.maLoiId = "FG-01"; ct.maHang = "SP001"; // Nhẹ: không bắt buộc kế hoạch/RCA
        r.chiTieu = List.of(ct);
        Nc nc = ncService.taoNc(r);
        TaoCapaRequest t = new TaoCapaRequest(); t.actor = "qa";
        return capaService.taoTuNc(nc.getSoHieu(), t);
    }

    private Long capaInvestigating(String lo, String nguoiDieuTra) {
        Capa c = capaOpen(lo);
        ToInvestigatingRequest inv = new ToInvestigatingRequest();
        inv.nguoiDieuTra = nguoiDieuTra; inv.actor = "qa";
        capaService.toInvestigating(c.getId(), inv);
        return c.getId();
    }

    private ToActionPlanRequest ap(Integer s, Integer o, Integer d) {
        ToActionPlanRequest ap = new ToActionPlanRequest();
        ap.rpnS = s; ap.rpnO = o; ap.rpnD = d; ap.actor = "qa";
        return ap;
    }

    @Test
    void nguoiDieuTra_ghiDeKhiCoGuiVaGiuNeuKhong() {
        Long a = capaInvestigating("LOT-C1-1", "qa.dieutra");
        assertThat(capaService.layCapa(a).getNguoiDieuTra()).isEqualTo("qa.dieutra");
        Long b = capaInvestigating("LOT-C1-2", null);
        assertThat(capaService.layCapa(b).getNguoiDieuTra()).isEqualTo("qa"); // mặc định = người tạo
    }

    @Test
    void rpn_nhapDuSOD_luuVaPhanMuc() {
        Long id = capaInvestigating("LOT-C1-3", null);
        ToActionPlanRequest req = ap(3, 3, 3);
        req.coYeuCauPhapLy = true;
        capaService.toActionPlan(id, req);
        Capa c = capaService.layCapa(id);
        assertThat(c.rpn()).isEqualTo(27);
        assertThat(c.rpnMuc()).isEqualTo("Medium");
        assertThat(c.isCoYeuCauPhapLy()).isTrue();
    }

    @Test
    void rpn_muc_LowHighVaS5() {
        Long low = capaInvestigating("LOT-C1-4", null);
        capaService.toActionPlan(low, ap(2, 2, 2));
        assertThat(capaService.layCapa(low).rpnMuc()).isEqualTo("Low");        // 8

        Long s5 = capaInvestigating("LOT-C1-5", null);
        capaService.toActionPlan(s5, ap(5, 1, 1));
        assertThat(capaService.layCapa(s5).rpnMuc()).isEqualTo("High");        // S=5 => High dù RPN=5

        Long cao = capaInvestigating("LOT-C1-6", null);
        capaService.toActionPlan(cao, ap(5, 2, 5));
        assertThat(capaService.layCapa(cao).rpnMuc()).isEqualTo("High");       // 50
    }

    @Test
    void rpn_chiNhapMotPhan_biChan() {
        Long id = capaInvestigating("LOT-C1-7", null);
        assertThatThrownBy(() -> capaService.toActionPlan(id, ap(3, null, 2)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void rpn_ngoaiThang1den5_biChan() {
        Long id = capaInvestigating("LOT-C1-8", null);
        assertThatThrownBy(() -> capaService.toActionPlan(id, ap(6, 1, 1)))
                .isInstanceOf(BusinessException.class);
    }

    private CapaAction themAction(Long id, LocalDate han) {
        AddActionRequest a = new AddActionRequest();
        a.loai = ActionType.NGAN_TAI_DIEN; a.moTa = "x"; a.nguoiPhuTrach = "sx"; a.deadline = han; a.actor = "qa";
        return capaService.themAction(id, a);
    }

    private Long capaActionPlan(String lo) {
        Long id = capaInvestigating(lo, null);
        capaService.toActionPlan(id, ap(null, null, null));
        return id;
    }

    @Test
    void giaHan_hanMoiKhongSauHanHienTai_biChan() {
        Long id = capaActionPlan("LOT-C1-9");
        CapaAction act = themAction(id, LocalDate.now().plusDays(5));
        ExtensionRequest ext = new ExtensionRequest();
        ext.deadlineMoi = act.getDeadline(); ext.lyDo = "x"; ext.actor = "sx"; // bằng hạn hiện tại
        assertThatThrownBy(() -> capaService.yeuCauGiaHan(act.getId(), ext)).isInstanceOf(BusinessException.class);
    }

    @Test
    void giaHan_actionDaHoanThanh_biChan() {
        Long id = capaActionPlan("LOT-C1-10");
        CapaAction act = themAction(id, LocalDate.now().plusDays(5));
        CompleteActionRequest comp = new CompleteActionRequest(); comp.bangChung = "bb"; comp.actor = "sx";
        capaService.completeAction(act.getId(), comp);
        ExtensionRequest ext = new ExtensionRequest();
        ext.deadlineMoi = act.getDeadline().plusDays(3); ext.lyDo = "x"; ext.actor = "sx";
        assertThatThrownBy(() -> capaService.yeuCauGiaHan(act.getId(), ext)).isInstanceOf(BusinessException.class);
    }

    @Test // giaHanCua trả yêu cầu gia hạn của các action thuộc CAPA (dùng cho giao diện)
    void giaHanCua_lietKeYeuCauCuaCapa() {
        Long id = capaActionPlan("LOT-C1-11");
        CapaAction act = themAction(id, LocalDate.now().plusDays(5));
        ExtensionRequest ext = new ExtensionRequest();
        ext.deadlineMoi = act.getDeadline().plusDays(7); ext.lyDo = "thiếu nguồn lực"; ext.actor = "sx";
        capaService.yeuCauGiaHan(act.getId(), ext);
        List<CapaActionExtension> ds = capaService.giaHanCua(id);
        assertThat(ds).hasSize(1);
        assertThat(ds.get(0).getActionId()).isEqualTo(act.getId());
        assertThat(ds.get(0).getLyDo()).isEqualTo("thiếu nguồn lực");
        assertThat(capaService.giaHanCua(capaActionPlan("LOT-C1-12"))).isEmpty();
    }
}
