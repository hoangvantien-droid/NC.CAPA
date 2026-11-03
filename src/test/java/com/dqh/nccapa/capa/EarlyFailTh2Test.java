package com.dqh.nccapa.capa;

import com.dqh.nccapa.capa.dto.*;
import com.dqh.nccapa.capa.entity.Capa;
import com.dqh.nccapa.capa.service.CapaService;
import com.dqh.nccapa.common.enums.ActionType;
import com.dqh.nccapa.common.enums.CapaStatus;
import com.dqh.nccapa.common.enums.NcSource;
import com.dqh.nccapa.common.enums.Severity;
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

/** TH2 — fail sớm tự động khi NC mới cùng (mã hàng+mã lỗi) phát sinh trong lúc CAPA Monitoring. */
class EarlyFailTh2Test extends MySqlTestBase {

    @Autowired NcService ncService;
    @Autowired CapaService capaService;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setUp() { DefectSeed.seed(jdbc); }

    private Nc taoNc(String maLoi, String lo) {
        TaoNcRequest r = new TaoNcRequest();
        r.loaiNc = "TRONG_SX"; r.nguonPhatSinh = NcSource.THU_CONG;
        r.nhaMay = "MGP"; r.nguoiBaoCao = "u"; r.loKiem = lo;
        TaoNcRequest.ChiTieuItem ct = new TaoNcRequest.ChiTieuItem();
        ct.maLoiId = maLoi; ct.maHang = "SP001";
        r.chiTieu = List.of(ct);
        return ncService.taoNc(r);
    }

    private Long capaToMonitoring(String ncSo) {
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
        ToMonitoringRequest mon = new ToMonitoringRequest(); mon.xNgay = 30; mon.nLo = 3; mon.actor = "qa";
        capaService.toMonitoring(id, mon);
        return id;
    }

    @Test // AC15: NC mới cùng (mã hàng+mã lỗi) trong Monitoring -> CAPA Ineffective, NC mới là kế thừa (không đẻ NC thứ 3)
    void th2_failSom_khongTaoNcThu3() {
        Nc ncGoc = taoNc("PRC-02", "LOT-TH2-A");      // Nặng, SP001 + PRC-02
        Long capaId = capaToMonitoring(ncGoc.getSoHieu());
        assertThat(capaService.layCapa(capaId).getTrangThai()).isEqualTo(CapaStatus.Monitoring);

        long ncTruoc = jdbc.queryForObject("SELECT COUNT(*) FROM nc", Long.class);

        // NC mới cùng SP001 + PRC-02 (lô khác) trong lúc Monitoring
        Nc ncMoi = taoNc("PRC-02", "LOT-TH2-B");

        // CAPA bị Ineffective
        assertThat(capaService.layCapa(capaId).getTrangThai()).isEqualTo(CapaStatus.Ineffective);
        assertThat(capaService.layCapa(capaId).isHandoverDone()).isTrue();

        // NC mới trở thành kế thừa: nâng 1 bậc (Nặng -> Nghiêm trọng), link CAPA
        Nc ncMoiReload = ncService.layNc(ncMoi.getSoHieu());
        assertThat(ncMoiReload.getMucNghiemTrong()).isEqualTo(Severity.NGHIEM_TRONG);

        // KHÔNG đẻ NC thứ 3: chỉ tăng đúng 1 NC (chính là ncMoi)
        long ncSau = jdbc.queryForObject("SELECT COUNT(*) FROM nc", Long.class);
        assertThat(ncSau - ncTruoc).isEqualTo(1);
    }

    @Test // Không fail sớm nếu khác mã lỗi
    void khacMaLoi_khongFailSom() {
        Nc ncGoc = taoNc("PRC-02", "LOT-TH2-C");
        Long capaId = capaToMonitoring(ncGoc.getSoHieu());
        taoNc("FG-01", "LOT-TH2-D");   // khác mã lỗi
        assertThat(capaService.layCapa(capaId).getTrangThai()).isEqualTo(CapaStatus.Monitoring);
    }
}
