package com.dqh.nccapa.capa;

import com.dqh.nccapa.capa.dto.AddActionRequest;
import com.dqh.nccapa.capa.dto.TaoCapaRequest;
import com.dqh.nccapa.capa.dto.ToActionPlanRequest;
import com.dqh.nccapa.capa.dto.ToInvestigatingRequest;
import com.dqh.nccapa.capa.entity.CapaAction;
import com.dqh.nccapa.capa.service.CapaService;
import com.dqh.nccapa.common.enums.ActionCategory;
import com.dqh.nccapa.common.enums.ActionType;
import com.dqh.nccapa.common.enums.NcSource;
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

/** Nhóm 2 phần F: cột loai_hanh_dong (CORRECTIVE/PREVENTIVE). */
class ActionCategoryTest extends MySqlTestBase {

    @Autowired NcService ncService;
    @Autowired CapaService capaService;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setUp() { DefectSeed.seed(jdbc); }

    private Long capaTaiActionPlan(String lo) {
        TaoNcRequest r = new TaoNcRequest();
        r.loaiNc = "TRONG_SX"; r.nguonPhatSinh = NcSource.THU_CONG;
        r.nhaMay = "MGP"; r.nguoiBaoCao = "u"; r.loKiem = lo;
        TaoNcRequest.ChiTieuItem ct = new TaoNcRequest.ChiTieuItem();
        ct.maLoiId = "PRC-02"; ct.maHang = "SP001";
        r.chiTieu = List.of(ct);
        Nc nc = ncService.taoNc(r);
        TaoCapaRequest t = new TaoCapaRequest(); t.actor = "qa";
        Long id = capaService.taoTuNc(nc.getSoHieu(), t).getId();

        ToInvestigatingRequest inv = new ToInvestigatingRequest();
        inv.khMucTieu = "x"; inv.actor = "qa";
        capaService.toInvestigating(id, inv);
        ToActionPlanRequest ap = new ToActionPlanRequest();
        ap.rcaNoiDung = "x"; ap.nhomNguyenNhan = "x";
        ap.chiPhiDauTu = java.math.BigDecimal.ONE; ap.actor = "qa";
        capaService.toActionPlan(id, ap);
        return id;
    }

    @Test
    void themAction_preventive_luuDungCot() {
        Long id = capaTaiActionPlan("LOT-CAT-1");
        AddActionRequest req = new AddActionRequest();
        req.loai = ActionType.NGAN_TAI_DIEN;
        req.moTa = "Thêm kiểm soát phòng ngừa";
        req.nguoiPhuTrach = "sx";
        req.deadline = LocalDate.now().plusDays(7);
        req.actor = "qa";
        req.loaiHanhDong = ActionCategory.PREVENTIVE;

        CapaAction a = capaService.themAction(id, req);
        assertThat(a.getLoaiHanhDong()).isEqualTo(ActionCategory.PREVENTIVE);

        String col = jdbc.queryForObject(
                "SELECT loai_hanh_dong FROM capa_action WHERE id = ?", String.class, a.getId());
        assertThat(col).isEqualTo("PREVENTIVE");
    }

    @Test
    void themAction_khongTruyen_macDinhCorrective() {
        Long id = capaTaiActionPlan("LOT-CAT-2");
        AddActionRequest req = new AddActionRequest();
        req.loai = ActionType.TUC_THOI;
        req.moTa = "Khắc phục tức thời";
        req.nguoiPhuTrach = "sx";
        req.deadline = LocalDate.now().plusDays(3);
        req.actor = "qa";
        // loaiHanhDong null -> CORRECTIVE

        CapaAction a = capaService.themAction(id, req);
        assertThat(a.getLoaiHanhDong()).isEqualTo(ActionCategory.CORRECTIVE);
    }
}
