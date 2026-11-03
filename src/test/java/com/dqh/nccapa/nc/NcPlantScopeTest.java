package com.dqh.nccapa.nc;

import com.dqh.nccapa.common.enums.NcSource;
import com.dqh.nccapa.common.enums.NcStatus;
import com.dqh.nccapa.common.error.BusinessException;
import com.dqh.nccapa.nc.dto.*;
import com.dqh.nccapa.nc.entity.Nc;
import com.dqh.nccapa.nc.repository.NcRepository;
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

/**
 * SCOPE THEO NHÀ MÁY (phạm vi giới hạn: duyệt disposition NC + lọc danh sách ở repo).
 * actor gắn nhà máy cụ thể chỉ duyệt NC cùng nhà máy; actor toàn tập đoàn (nha_may NULL) duyệt mọi NC.
 */
class NcPlantScopeTest extends MySqlTestBase {

    @Autowired NcService ncService;
    @Autowired NcRepository ncRepo;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        DefectSeed.seed(jdbc); // nha_may MGP đã có; thêm DNA cho NC khác nhà máy
        jdbc.update("INSERT IGNORE INTO nha_may(ma, ten) VALUES ('DNA','Nha may DNA')");
        // QA nhà máy MGP (scope = MGP); QA nhà máy DNA (scope = DNA); QA tập đoàn (nha_may NULL)
        DefectSeed.seedUserRole(jdbc, "qaMGP", "QA_NHA_MAY", "MGP");
        DefectSeed.seedUserRole(jdbc, "qaMGP2", "QA_NHA_MAY", "MGP");
        DefectSeed.seedUserRole(jdbc, "qaTD", "QA_TAP_DOAN", null); // toàn tập đoàn
        DefectSeed.seedUserRole(jdbc, "qaTD2", "QA_TAP_DOAN", null);
    }

    private Nc taoNcNang(String nhaMay, String lo) {
        TaoNcRequest r = new TaoNcRequest();
        r.loaiNc = "TRONG_SX"; r.nguonPhatSinh = NcSource.THU_CONG;
        r.nhaMay = nhaMay; r.nguoiBaoCao = "user01"; r.loKiem = lo;
        TaoNcRequest.ChiTieuItem ct = new TaoNcRequest.ChiTieuItem();
        ct.maLoiId = "PRC-02"; ct.maHang = "SP001"; // Nặng
        r.chiTieu = List.of(ct);
        return ncService.taoNc(r);
    }

    private void submit(Nc nc) {
        SubmitApprovalRequest s = new SubmitApprovalRequest();
        s.nguyenNhanSoBo = "rc"; s.dispositionDeXuat = "REWORK";
        s.chiPhiUocTinh = new BigDecimal("1000000"); s.boPhanThucHien = "SX";
        s.actor = "proposer";
        ncService.submitApproval(nc.getSoHieu(), s);
    }

    private void approve(String so, String actor) {
        ApproveRequest a = new ApproveRequest(); a.actor = actor;
        ncService.approve(so, a);
    }

    @Test // actor QA nhà máy MGP duyệt NC nhà máy MGP -> qua (role + scope khớp)
    void qaMGP_duyetNcMGP_qua() {
        Nc nc = taoNcNang("MGP", "LOT-S1");
        submit(nc);
        approve(nc.getSoHieu(), "qaMGP");   // cấp 1
        assertThat(ncService.layNc(nc.getSoHieu()).getTrangThai()).isEqualTo(NcStatus.PendingApproval);
        approve(nc.getSoHieu(), "qaMGP2");  // cấp 2 khác người
        assertThat(ncService.layNc(nc.getSoHieu()).getTrangThai()).isEqualTo(NcStatus.Processing);
    }

    @Test // actor QA nhà máy MGP duyệt NC nhà máy DNA -> ROLE_DENIED (ngoài scope)
    void qaMGP_duyetNcDNA_biChanScope() {
        Nc nc = taoNcNang("DNA", "LOT-S2");
        submit(nc);
        assertThatThrownBy(() -> approve(nc.getSoHieu(), "qaMGP"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Ngoài phạm vi nhà máy");
        assertThat(ncService.layNc(nc.getSoHieu()).getTrangThai()).isEqualTo(NcStatus.PendingApproval);
    }

    @Test // actor QA tập đoàn (nha_may NULL) duyệt NC mọi nhà máy -> qua
    void qaTapDoan_duyetMoiNhaMay_qua() {
        Nc ncMgp = taoNcNang("MGP", "LOT-S3");
        submit(ncMgp);
        approve(ncMgp.getSoHieu(), "qaTD");
        approve(ncMgp.getSoHieu(), "qaTD2");
        assertThat(ncService.layNc(ncMgp.getSoHieu()).getTrangThai()).isEqualTo(NcStatus.Processing);

        Nc ncDna = taoNcNang("DNA", "LOT-S4");
        submit(ncDna);
        approve(ncDna.getSoHieu(), "qaTD");
        approve(ncDna.getSoHieu(), "qaTD2");
        assertThat(ncService.layNc(ncDna.getSoHieu()).getTrangThai()).isEqualTo(NcStatus.Processing);
    }

    @Test // lọc danh sách theo nhà máy ở tầng repo (dùng cho scope UI)
    void repoLocTheoNhaMay() {
        taoNcNang("MGP", "LOT-S5");
        taoNcNang("DNA", "LOT-S6");
        List<Nc> chiMgp = ncRepo.findTop200ByNhaMayOrderByCreatedAtDesc("MGP");
        assertThat(chiMgp).isNotEmpty();
        assertThat(chiMgp).allMatch(n -> "MGP".equals(n.getNhaMay()));
        List<Nc> tatCa = ncRepo.findTop200ByOrderByCreatedAtDesc();
        assertThat(tatCa.size()).isGreaterThan(chiMgp.size());
    }
}
