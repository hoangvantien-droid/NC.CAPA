package com.dqh.nccapa.common.version;

import com.dqh.nccapa.capa.dto.*;
import com.dqh.nccapa.capa.entity.Capa;
import com.dqh.nccapa.capa.entity.CapaVerification;
import com.dqh.nccapa.capa.repository.CapaVerificationRepository;
import com.dqh.nccapa.capa.service.CapaService;
import com.dqh.nccapa.common.enums.ActionType;
import com.dqh.nccapa.common.enums.NcSource;
import com.dqh.nccapa.nc.dto.TaoNcRequest;
import com.dqh.nccapa.nc.entity.Nc;
import com.dqh.nccapa.nc.service.NcService;
import com.dqh.nccapa.support.DefectSeed;
import com.dqh.nccapa.support.MySqlTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * VERSIONING bản ghi đã xác nhận (REQ mục 7, invariant I7): sửa kết quả kiểm chứng CAPA đã xác nhận
 * → lưu SNAPSHOT bản cũ (phien_ban tăng dần), giá trị hiện tại là bản MỚI. KHÔNG so wall-clock.
 */
class RecordVersionTest extends MySqlTestBase {

    @Autowired NcService ncService;
    @Autowired CapaService capaService;
    @Autowired CapaVerificationRepository verRepo;
    @Autowired RecordVersionRepository rvRepo;
    @Autowired RecordVersionService rvService;
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

    /** Tạo CAPA tới Monitoring với n_lo cho trước (N=0 → confirm bằng biên bản). */
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
    void suaKetQuaDaXacNhan_luuSnapshotBanCu_apGiaTriMoi() throws Exception {
        // REC-06 = Nhẹ (N=0) → confirm bằng biên bản.
        Nc nc = taoNc("REC-06", "LOT-RV1");
        Long id = capaToMonitoring(nc.getSoHieu(), 0);
        lamXDat(id);

        // Xác nhận lần đầu: HIEU_QUA + biên bản gốc (chưa sinh version).
        ConfirmEffectiveRequest cf = new ConfirmEffectiveRequest();
        cf.bienBanKiemTra = "BB gốc"; cf.actor = "qa";
        capaService.confirmEffective(id, cf);

        CapaVerification ver = verRepo.findByCapaId(id).orElseThrow();
        String verId = String.valueOf(ver.getId());
        assertThat(rvRepo.countByDoiTuongLoaiAndDoiTuongId("CAPA_VERIFICATION", verId)).isZero();

        // Sửa lần 1: KHONG_HIEU_QUA → phải lưu snapshot phien_ban=1 chứa giá trị CŨ (HIEU_QUA/BB gốc).
        SuaKetQuaKiemChungRequest s1 = new SuaKetQuaKiemChungRequest();
        s1.ketQua = "KHONG_HIEU_QUA"; s1.bienBanKiemTra = "BB sửa 1"; s1.lyDo = "phát hiện tái diễn"; s1.actor = "qatd";
        capaService.suaKetQuaKiemChung(id, s1);

        List<RecordVersion> ls = rvService.lichSu("CAPA_VERIFICATION", verId);
        assertThat(ls).hasSize(1);
        RecordVersion v1 = ls.get(0);
        assertThat(v1.getPhienBan()).isEqualTo(1);
        assertThat(v1.getNguoiThucHien()).isEqualTo("qatd");
        JsonNode snap1 = objectMapper.readTree(v1.getSnapshotJson());
        assertThat(snap1.get("ketQua").asText()).isEqualTo("HIEU_QUA");           // giá trị CŨ
        assertThat(snap1.get("bienBanKiemTra").asText()).isEqualTo("BB gốc");      // giá trị CŨ
        assertThat(snap1.has("xacNhanLuc")).isTrue();

        // Giá trị HIỆN TẠI đã là bản MỚI.
        CapaVerification now = verRepo.findByCapaId(id).orElseThrow();
        assertThat(now.getKetQua()).isEqualTo("KHONG_HIEU_QUA");
        assertThat(now.getBienBanKiemTra()).isEqualTo("BB sửa 1");

        // Sửa lần 2 → phien_ban=2, snapshot chứa giá trị của lần 1.
        SuaKetQuaKiemChungRequest s2 = new SuaKetQuaKiemChungRequest();
        s2.ketQua = "HIEU_QUA"; s2.bienBanKiemTra = "BB sửa 2"; s2.lyDo = "đánh giá lại"; s2.actor = "qatd";
        capaService.suaKetQuaKiemChung(id, s2);

        List<RecordVersion> ls2 = rvService.lichSu("CAPA_VERIFICATION", verId);
        assertThat(ls2).hasSize(2);
        assertThat(ls2.get(0).getPhienBan()).isEqualTo(2);   // mới nhất trước
        JsonNode snap2 = objectMapper.readTree(ls2.get(0).getSnapshotJson());
        assertThat(snap2.get("ketQua").asText()).isEqualTo("KHONG_HIEU_QUA");      // giá trị của lần 1
        assertThat(snap2.get("bienBanKiemTra").asText()).isEqualTo("BB sửa 1");

        CapaVerification now2 = verRepo.findByCapaId(id).orElseThrow();
        assertThat(now2.getKetQua()).isEqualTo("HIEU_QUA");
        assertThat(now2.getBienBanKiemTra()).isEqualTo("BB sửa 2");
    }
}
