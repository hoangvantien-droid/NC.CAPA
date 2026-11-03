package com.dqh.nccapa.capa;

import com.dqh.nccapa.capa.dto.*;
import com.dqh.nccapa.capa.entity.Capa;
import com.dqh.nccapa.capa.service.CapaService;
import com.dqh.nccapa.common.enums.ActionType;
import com.dqh.nccapa.common.enums.CapaStatus;
import com.dqh.nccapa.common.enums.LoaiThayDoi;
import com.dqh.nccapa.common.enums.NcSource;
import com.dqh.nccapa.nc.dto.TaoNcRequest;
import com.dqh.nccapa.nc.entity.Nc;
import com.dqh.nccapa.nc.service.NcService;
import com.dqh.nccapa.pr.dto.TaoPrRequest;
import com.dqh.nccapa.pr.service.PrService;
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

/**
 * TỰ ĐỘNG MỞ PR (REQ mục 4.2, 5, quy tắc #13): CAPA fail lần 2 / trần Nghiêm trọng → mở PR THẬT
 * (không chỉ audit), chống trùng 1 PR/vấn đề. Phủ cả hai điểm nối dây:
 * {@code CapaService.markIneffectiveInternal} và {@code EarlyFailDetector.onNewNc}.
 */
class AutoOpenPrTest extends MySqlTestBase {

    @Autowired NcService ncService;
    @Autowired CapaService capaService;
    @Autowired PrService prService;
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

    /** Dựng CAPA (từ NC Nặng) tới Monitoring (theo pattern CapaVerificationTest / EarlyFailTh2Test). */
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

    private long demPr() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM pr", Long.class);
    }

    private long demPrChoVanDe(String maHang, String maLoi) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM pr WHERE ma_hang = ? AND ma_loi_id = ?",
                Long.class, maHang, maLoi);
    }

    @Test // (a) markIneffective (fail lần 2 / trần Nghiêm trọng) -> có PR được tạo thật
    void markIneffective_moPrThat() {
        Nc nc = taoNc("PRC-02", "LOT-PR-A");                 // Nặng, SP001 + PRC-02
        Long id = capaToMonitoring(nc.getSoHieu());
        long prTruoc = demPr();

        MarkIneffectiveRequest mi = new MarkIneffectiveRequest();
        mi.lyDo = "không hiệu quả tại mốc"; mi.actor = "qa";
        IneffectiveResult r = capaService.markIneffective(id, mi);

        assertThat(capaService.layCapa(id).getTrangThai()).isEqualTo(CapaStatus.Ineffective);
        assertThat(r.moPrNgay).isTrue();
        // PR THẬT được tạo (không chỉ audit): đúng 1 PR mới cho vấn đề SP001 + PRC-02
        assertThat(demPr() - prTruoc).isEqualTo(1);
        assertThat(demPrChoVanDe("SP001", "PRC-02")).isEqualTo(1);
        // PR được link về CAPA fail
        Long prCapa = jdbc.queryForObject("SELECT COUNT(*) FROM pr_capa WHERE capa_id = ?", Long.class, id);
        assertThat(prCapa).isEqualTo(1);
    }

    @Test // (b) trần Nghiêm trọng qua TH2 (EarlyFailDetector.onNewNc) -> có PR
    void tranNghiemTrong_th2_moPr() {
        Nc ncGoc = taoNc("PRC-02", "LOT-PR-B1");             // Nặng
        Long capaId = capaToMonitoring(ncGoc.getSoHieu());
        assertThat(capaService.layCapa(capaId).getTrangThai()).isEqualTo(CapaStatus.Monitoring);
        long prTruoc = demPr();

        // NC mới cùng SP001 + PRC-02 trong Monitoring -> fail sớm, nâng lên Nghiêm trọng -> mở PR
        taoNc("PRC-02", "LOT-PR-B2");

        assertThat(capaService.layCapa(capaId).getTrangThai()).isEqualTo(CapaStatus.Ineffective);
        assertThat(demPr() - prTruoc).isEqualTo(1);
        assertThat(demPrChoVanDe("SP001", "PRC-02")).isEqualTo(1);
    }

    @Test // (c) chống trùng: đã có PR đang mở cho vấn đề -> KHÔNG tạo PR thứ 2
    void chongTrung_khongTaoPrThu2() {
        // PR thủ công đang mở sẵn cho SP001 + PRC-02
        TaoPrRequest tp = new TaoPrRequest();
        tp.loaiThayDoi = LoaiThayDoi.QUY_CACH; tp.maHang = "SP001"; tp.maLoiId = "PRC-02"; tp.actor = "qa";
        prService.tao(tp);
        assertThat(demPrChoVanDe("SP001", "PRC-02")).isEqualTo(1);

        Nc nc = taoNc("PRC-02", "LOT-PR-C");                 // cùng vấn đề SP001 + PRC-02
        Long id = capaToMonitoring(nc.getSoHieu());

        MarkIneffectiveRequest mi = new MarkIneffectiveRequest();
        mi.lyDo = "fail"; mi.actor = "qa";
        capaService.markIneffective(id, mi);

        // Vẫn chỉ 1 PR cho vấn đề này (không mở PR thứ 2)
        assertThat(demPrChoVanDe("SP001", "PRC-02")).isEqualTo(1);
    }
}
