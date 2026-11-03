package com.dqh.nccapa.capa;

import com.dqh.nccapa.capa.dto.*;
import com.dqh.nccapa.capa.entity.Capa;
import com.dqh.nccapa.capa.service.CapaService;
import com.dqh.nccapa.common.enums.NhomNguyenNhan6M;
import com.dqh.nccapa.common.enums.NcSource;
import com.dqh.nccapa.common.enums.RcaMethod;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Nhóm 1: RCA có cấu trúc + nhóm nguyên nhân 6M + cờ tái diễn + kế hoạch điều tra tách trường. */
class CapaRcaTest extends MySqlTestBase {

    @Autowired NcService ncService;
    @Autowired CapaService capaService;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setUp() { DefectSeed.seed(jdbc); }

    private Long capaTuNcNang(String lo) {
        TaoNcRequest r = new TaoNcRequest();
        r.loaiNc = "TRONG_SX"; r.nguonPhatSinh = NcSource.THU_CONG;
        r.nhaMay = "MGP"; r.nguoiBaoCao = "u"; r.loKiem = lo;
        TaoNcRequest.ChiTieuItem ct = new TaoNcRequest.ChiTieuItem();
        ct.maLoiId = "PRC-02"; ct.maHang = "SP001";
        r.chiTieu = List.of(ct);
        Nc nc = ncService.taoNc(r);
        TaoCapaRequest t = new TaoCapaRequest(); t.actor = "qa";
        return capaService.taoTuNc(nc.getSoHieu(), t).getId();
    }

    @Test // Kế hoạch điều tra tách 3 trường; NC Nặng cần ít nhất mục tiêu
    void keHoach_tachTruong() {
        Long id = capaTuNcNang("LOT-RCA-1");
        ToInvestigatingRequest inv = new ToInvestigatingRequest();
        inv.khMucTieu = "Tìm nguyên nhân lệch nhiệt"; inv.khPhuongPhap = "5-Why + đo lại"; inv.khNguonLuc = "QA + kỹ thuật";
        inv.actor = "qa";
        capaService.toInvestigating(id, inv);
        Capa c = capaService.layCapa(id);
        assertThat(c.getKhMucTieu()).isEqualTo("Tìm nguyên nhân lệch nhiệt");
        assertThat(c.getKhPhuongPhap()).contains("5-Why");
    }

    @Test // NC Nặng thiếu cả mục tiêu lẫn kế hoạch cũ -> chặn
    void keHoach_thieu_biChan() {
        Long id = capaTuNcNang("LOT-RCA-2");
        ToInvestigatingRequest inv = new ToInvestigatingRequest(); inv.actor = "qa"; // không mục tiêu
        assertThatThrownBy(() -> capaService.toInvestigating(id, inv))
                .isInstanceOf(BusinessException.class);
    }

    @Test // RCA có cấu trúc: method + data JSON + 6M + tái diễn
    void rca_coCauTruc() {
        Long id = capaTuNcNang("LOT-RCA-3");
        ToInvestigatingRequest inv = new ToInvestigatingRequest(); inv.khMucTieu = "x"; inv.actor = "qa";
        capaService.toInvestigating(id, inv);

        ToActionPlanRequest ap = new ToActionPlanRequest();
        ap.rcaNoiDung = "Cảm biến nhiệt lệch chuẩn";
        ap.rcaMethod = RcaMethod.FIVE_WHY;
        ap.rcaData = "{\"w1\":\"nhiệt cao\",\"w2\":\"cảm biến sai\",\"w3\":\"chưa hiệu chuẩn\"}";
        ap.nhomNguyenNhan6m = NhomNguyenNhan6M.MACHINE;
        ap.taiDien = true;
        ap.chiPhiDauTu = new BigDecimal("3000000");
        ap.rpnS = 5; ap.rpnO = 2; ap.rpnD = 5;
        ap.actor = "qa";
        capaService.toActionPlan(id, ap);

        Capa c = capaService.layCapa(id);
        assertThat(c.getRcaMethod()).isEqualTo(RcaMethod.FIVE_WHY);
        assertThat(c.getRcaData()).contains("cảm biến sai");
        assertThat(c.getNhomNguyenNhan6m()).isEqualTo(NhomNguyenNhan6M.MACHINE);
        assertThat(c.isTaiDien()).isTrue();
        assertThat(c.rpn()).isEqualTo(50);   // 5*2*5
    }

    @Test // RPN mỗi yếu tố thang 1-5: giá trị ngoài khoảng bị chặn
    void rpn_ngoaiThang_biChan() {
        Long id = capaTuNcNang("LOT-RCA-4");
        ToInvestigatingRequest inv = new ToInvestigatingRequest(); inv.khMucTieu = "x"; inv.actor = "qa";
        capaService.toInvestigating(id, inv);
        ToActionPlanRequest ap = new ToActionPlanRequest();
        ap.rcaNoiDung = "x"; ap.nhomNguyenNhan = "x"; ap.chiPhiDauTu = BigDecimal.ONE;
        ap.rpnS = 7; ap.rpnO = 1; ap.rpnD = 1;  // 7 > 5
        ap.actor = "qa";
        assertThatThrownBy(() -> capaService.toActionPlan(id, ap))
                .isInstanceOf(BusinessException.class);
    }
}
