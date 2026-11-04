package com.dqh.nccapa.support;

import com.dqh.nccapa.capa.dto.AddActionRequest;
import com.dqh.nccapa.capa.dto.TaoCapaRequest;
import com.dqh.nccapa.capa.dto.ToActionPlanRequest;
import com.dqh.nccapa.capa.dto.ToInvestigatingRequest;
import com.dqh.nccapa.capa.entity.Capa;
import com.dqh.nccapa.capa.entity.CapaAction;
import com.dqh.nccapa.capa.service.CapaService;
import com.dqh.nccapa.common.enums.ActionType;
import com.dqh.nccapa.common.enums.NcSource;
import com.dqh.nccapa.common.enums.NhomNguyenNhan6M;
import com.dqh.nccapa.common.enums.RcaMethod;
import com.dqh.nccapa.nc.dto.TaoNcRequest;
import com.dqh.nccapa.nc.entity.Nc;
import com.dqh.nccapa.nc.service.NcService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Dựng NC / CAPA action THẬT (qua service) cho test đính kèm. Mỗi lô dùng mã hàng riêng để không
 * chạm guard chống trùng PR / TH2. Gọi {@link DefectSeed#seed} trước.
 */
public final class AttachmentFixture {

    private AttachmentFixture() { }

    /** NC mới (trạng thái New, mức Nặng) với mã hàng riêng theo lô. */
    public static Nc taoNc(NcService ncService, String lo) {
        TaoNcRequest r = new TaoNcRequest();
        r.loaiNc = "TRONG_SX"; r.nguonPhatSinh = NcSource.THU_CONG;
        r.nhaMay = "MGP"; r.nguoiBaoCao = "user01"; r.loKiem = lo;
        TaoNcRequest.ChiTieuItem ct = new TaoNcRequest.ChiTieuItem();
        ct.maLoiId = "PRC-02"; ct.maHang = "SP-" + lo;
        r.chiTieu = List.of(ct);
        return ncService.taoNc(r);
    }

    /** CAPA ở trạng thái ActionPlan (đủ điều kiện thêm action) từ một NC mới. */
    public static Capa taoCapaActionPlan(NcService ncService, CapaService capaService, String lo) {
        Nc nc = taoNc(ncService, lo);
        TaoCapaRequest t = new TaoCapaRequest(); t.actor = "qa";
        Capa capa = capaService.taoTuNc(nc.getSoHieu(), t);
        Long id = capa.getId();
        ToInvestigatingRequest inv = new ToInvestigatingRequest();
        inv.keHoachDieuTra = "điều tra nguyên nhân"; inv.actor = "qa";
        capaService.toInvestigating(id, inv);
        ToActionPlanRequest ap = new ToActionPlanRequest();
        ap.rcaNoiDung = "thiếu kiểm soát công đoạn"; ap.rcaMethod = RcaMethod.FIVE_WHY;
        ap.rcaData = "{\"w1\":\"vì sao\"}"; ap.nhomNguyenNhan6m = NhomNguyenNhan6M.METHOD;
        ap.chiPhiDauTu = new BigDecimal("5000000");
        ap.rpnS = 5; ap.rpnO = 4; ap.rpnD = 3;
        ap.actor = "qa";
        capaService.toActionPlan(id, ap);
        return capa;
    }

    /** Một action thật thuộc CAPA đã cho (deadline cố định, không dùng đồng hồ). */
    public static CapaAction themAction(CapaService capaService, Long capaId) {
        AddActionRequest a = new AddActionRequest();
        a.loai = ActionType.TUC_THOI; a.moTa = "Cô lập lô hàng lỗi";
        a.nguoiPhuTrach = "sxmgp"; a.deadline = LocalDate.of(2099, 1, 3); a.actor = "qa";
        return capaService.themAction(capaId, a);
    }
}
