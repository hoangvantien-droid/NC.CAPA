package com.dqh.nccapa.nc;

import com.dqh.nccapa.common.enums.NcSource;
import com.dqh.nccapa.common.enums.NcStatus;
import com.dqh.nccapa.common.enums.Severity;
import com.dqh.nccapa.common.error.BusinessException;
import com.dqh.nccapa.nc.dto.*;
import com.dqh.nccapa.nc.entity.Nc;
import com.dqh.nccapa.nc.entity.NcDoiTuong;
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

/** Lát 3: luồng Có khoanh vùng (NC Nghiêm trọng). */
class NcContainmentFlowTest extends MySqlTestBase {

    @Autowired NcService ncService;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setUp() { DefectSeed.seed(jdbc); }

    private Nc taoNcNghiemTrong(String lo) {
        TaoNcRequest r = new TaoNcRequest();
        r.loaiNc = "THANH_PHAM"; r.nguonPhatSinh = NcSource.THU_CONG;
        r.nhaMay = "MGP"; r.nguoiBaoCao = "user01"; r.loKiem = lo;
        TaoNcRequest.ChiTieuItem ct = new TaoNcRequest.ChiTieuItem();
        ct.maLoiId = "FG-03"; ct.maHang = "SP001"; // Nghiêm trọng + khoanh vùng
        r.chiTieu = List.of(ct);
        return ncService.taoNc(r);
    }

    private void containment(String so, String lo, String hold) {
        ContainmentRequest c = new ContainmentRequest();
        ContainmentRequest.LoKhoanh lk = new ContainmentRequest.LoKhoanh();
        lk.soLo = lo; lk.chungTuHold = hold;
        c.loKhoanh = List.of(lk); c.actor = "qatd";
        ncService.containment(so, c);
    }

    private void submitApprove(String so) {
        SubmitApprovalRequest s = new SubmitApprovalRequest();
        s.nguyenNhanSoBo = "rc"; s.dispositionDeXuat = "TIEU_HUY";
        s.chiPhiUocTinh = new BigDecimal("5000000"); s.boPhanThucHien = "QA";
        s.actor = "proposer";
        ncService.submitApproval(so, s);
        ApproveRequest a = new ApproveRequest(); a.actor = "qatd";  // Nghiêm trọng: 1 lần
        ncService.approve(so, a);
    }

    @Test // AC2: NC Nghiêm trọng bắt buộc qua Containment có Hold
    void nghiemTrong_batBuocContainment() {
        Nc nc = taoNcNghiemTrong("LOT-C1");
        assertThat(nc.isFoodSafety()).isTrue();
        containment(nc.getSoHieu(), "LOT-C1", "HOLD-001");
        assertThat(ncService.layNc(nc.getSoHieu()).getTrangThai()).isEqualTo(NcStatus.Containment);
    }

    @Test // quy tắc #9: thiếu chứng từ Hold bị chặn
    void containment_thieuHold_biChan() {
        Nc nc = taoNcNghiemTrong("LOT-C2");
        ContainmentRequest c = new ContainmentRequest();
        ContainmentRequest.LoKhoanh lk = new ContainmentRequest.LoKhoanh();
        lk.soLo = "LOT-C2"; lk.chungTuHold = "   ";  // trống
        c.loKhoanh = List.of(lk); c.actor = "qatd";
        assertThatThrownBy(() -> ncService.containment(nc.getSoHieu(), c))
                .isInstanceOf(BusinessException.class);
    }

    @Test // AC19: lô khoanh chưa gỡ -> không đóng được; gỡ xong -> đóng được
    void chiDongKhiMoiDongLoKhoanhCoTrangThaiCuoi() {
        Nc nc = taoNcNghiemTrong("LOT-C3");
        String so = nc.getSoHieu();
        containment(so, "LOT-C3", "HOLD-003");
        submitApprove(so);
        assertThat(ncService.layNc(so).getTrangThai()).isEqualTo(NcStatus.Processing);

        ConfirmDoneRequest cd = new ConfirmDoneRequest();
        cd.bangChung = "bb"; cd.chiPhiThucTe = new BigDecimal("4000000"); cd.actor = "QA";
        ncService.confirmDone(so, cd);

        // Chưa gỡ khoanh dòng lô -> verify-close bị chặn
        VerifyCloseRequest v = new VerifyCloseRequest();
        v.ketLuanThamTra = "x"; v.actor = "qatd";
        assertThatThrownBy(() -> ncService.verifyClose(so, v))
                .isInstanceOf(BusinessException.class);
    }

    @Test // unhold dòng lô trong Processing rồi đóng được
    void unholdDongLo_roiDongDuoc() {
        Nc nc = taoNcNghiemTrong("LOT-C4");
        String so = nc.getSoHieu();
        containment(so, "LOT-C4", "HOLD-004");
        submitApprove(so);

        NcDoiTuong dt = ncService.doiTuongCua(so).get(0);
        // unhold phải trong Processing
        UnholdRequest u = new UnholdRequest();
        u.chungTuUnhold = "UNHOLD-004"; u.trangThaiDongLo = "Scrapped"; u.actor = "qatd";
        ncService.unholdDongLo(so, dt.getId(), u);

        ConfirmDoneRequest cd = new ConfirmDoneRequest();
        cd.bangChung = "bb tiêu hủy"; cd.chiPhiThucTe = new BigDecimal("4000000"); cd.actor = "QA";
        ncService.confirmDone(so, cd);

        VerifyCloseRequest v = new VerifyCloseRequest();
        v.ketLuanThamTra = "đạt"; v.actor = "qatd";
        Nc closed = ncService.verifyClose(so, v);
        assertThat(closed.getTrangThai()).isEqualTo(NcStatus.Closed);
    }
}
