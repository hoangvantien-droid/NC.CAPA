package com.dqh.nccapa.capa.controller;

import com.dqh.nccapa.capa.dto.*;
import com.dqh.nccapa.capa.entity.Capa;
import com.dqh.nccapa.capa.entity.CapaVerification;
import com.dqh.nccapa.capa.repository.CapaVerificationRepository;
import com.dqh.nccapa.capa.service.CapaService;
import com.dqh.nccapa.common.enums.ActionType;
import com.dqh.nccapa.common.enums.NcSource;
import com.dqh.nccapa.common.version.RecordVersionRepository;
import com.dqh.nccapa.nc.dto.TaoNcRequest;
import com.dqh.nccapa.nc.entity.Nc;
import com.dqh.nccapa.nc.service.NcService;
import com.dqh.nccapa.support.DefectSeed;
import com.dqh.nccapa.support.MySqlTestBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * D-04 / UAT-VER-01: endpoint REST POST /api/v1/capa/{id}/verification/sua-ket-qua phải TỒN TẠI và
 * thực sự sinh record_version + đổi ket_qua. Trước fix endpoint này trả 404.
 * Không so wall-clock — làm X đạt bằng cách lùi mốc trong DB.
 */
@AutoConfigureMockMvc
class SuaKetQuaKiemChungEndpointTest extends MySqlTestBase {

    @Autowired MockMvc mockMvc;
    @Autowired NcService ncService;
    @Autowired CapaService capaService;
    @Autowired CapaVerificationRepository verRepo;
    @Autowired RecordVersionRepository rvRepo;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        DefectSeed.seed(jdbc);
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

    private void lamXDat(Long capaId) {
        jdbc.update("UPDATE capa SET monitoring_bat_dau = DATE_SUB(NOW(), INTERVAL 60 DAY) WHERE id = ?", capaId);
        jdbc.update("UPDATE capa_verification SET han_kiem_chung_tiep_theo = DATE_SUB(NOW(), INTERVAL 1 DAY) WHERE capa_id = ?", capaId);
    }

    @Test
    void endpoint_suaKetQua_sinhRecordVersion_doiKetQua() throws Exception {
        Nc nc = taoNc("REC-06", "LOT-EP1");   // Nhẹ, N=0 → confirm bằng biên bản
        Long id = capaToMonitoring(nc.getSoHieu(), 0);
        lamXDat(id);

        ConfirmEffectiveRequest cf = new ConfirmEffectiveRequest();
        cf.bienBanKiemTra = "BB gốc"; cf.actor = "qa";
        capaService.confirmEffective(id, cf);

        CapaVerification ver = verRepo.findByCapaId(id).orElseThrow();
        String verId = String.valueOf(ver.getId());
        assertThat(rvRepo.countByDoiTuongLoaiAndDoiTuongId("CAPA_VERIFICATION", verId)).isZero();

        SuaKetQuaKiemChungRequest body = new SuaKetQuaKiemChungRequest();
        body.ketQua = "KHONG_HIEU_QUA"; body.bienBanKiemTra = "BB sửa"; body.lyDo = "tái diễn"; body.actor = "qatd";

        mockMvc.perform(post("/api/v1/capa/{id}/verification/sua-ket-qua", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.ketQua").value("KHONG_HIEU_QUA"));

        // record_version có bản ghi (snapshot giá trị cũ) + ket_qua hiện tại đã đổi.
        assertThat(rvRepo.countByDoiTuongLoaiAndDoiTuongId("CAPA_VERIFICATION", verId)).isEqualTo(1);
        CapaVerification now = verRepo.findByCapaId(id).orElseThrow();
        assertThat(now.getKetQua()).isEqualTo("KHONG_HIEU_QUA");
        assertThat(now.getBienBanKiemTra()).isEqualTo("BB sửa");
    }
}
