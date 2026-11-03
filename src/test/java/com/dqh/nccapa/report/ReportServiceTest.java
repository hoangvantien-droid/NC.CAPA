package com.dqh.nccapa.report;

import com.dqh.nccapa.common.enums.NcSource;
import com.dqh.nccapa.nc.dto.CloseFastRequest;
import com.dqh.nccapa.nc.dto.TaoNcRequest;
import com.dqh.nccapa.nc.entity.Nc;
import com.dqh.nccapa.nc.service.NcService;
import com.dqh.nccapa.support.DefectSeed;
import com.dqh.nccapa.support.MySqlTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Đợt b: dashboard/báo cáo. */
class ReportServiceTest extends MySqlTestBase {

    @Autowired ReportService reportService;
    @Autowired NcService ncService;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setUp() { DefectSeed.seed(jdbc); }

    private Nc taoNc(String maLoi, String lo) {
        TaoNcRequest r = new TaoNcRequest();
        r.loaiNc = "THANH_PHAM"; r.nguonPhatSinh = NcSource.THU_CONG;
        r.nhaMay = "MGP"; r.nguoiBaoCao = "u"; r.loKiem = lo;
        TaoNcRequest.ChiTieuItem ct = new TaoNcRequest.ChiTieuItem();
        ct.maLoiId = maLoi; ct.maHang = "SP001";
        r.chiTieu = List.of(ct);
        return ncService.taoNc(r);
    }

    @Test
    void dashboard_demDungTheoMuc_vaTongMo() {
        taoNc("FG-01", "L1");   // Nhẹ
        taoNc("PRC-02", "L2");  // Nặng
        taoNc("FG-03", "L3");   // Nghiêm trọng (New, chưa khoanh vùng vì tạo thủ công để New)

        var d = reportService.dashboard(null);
        assertThat(d.tongMo).isEqualTo(3);
        assertThat(d.theoMuc.get("NHE")).isEqualTo(1L);
        assertThat(d.theoMuc.get("NANG")).isEqualTo(1L);
        assertThat(d.theoMuc.get("NGHIEM_TRONG")).isEqualTo(1L);
    }

    @Test
    void dashboard_dongPhieu_giamTongMo() {
        Nc nhe = taoNc("FG-01", "L10");
        CloseFastRequest cf = new CloseFastRequest(); cf.ketQuaXuLy = "ok"; cf.actor = "u";
        ncService.closeFast(nhe.getSoHieu(), cf);

        var d = reportService.dashboard(null);
        assertThat(d.tongMo).isEqualTo(0);                 // đã đóng
        assertThat(d.theoTrangThai.get("Closed")).isEqualTo(1L);
    }

    @Test
    void dashboard_locTheoNhaMay() {
        taoNc("FG-01", "L20");
        var mgp = reportService.dashboard("MGP");
        assertThat(mgp.tongMo).isEqualTo(1);
        var khac = reportService.dashboard("DNA");         // nhà máy khác, không có phiếu
        assertThat(khac.tongMo).isEqualTo(0);
    }

    @Test
    void dashboard_chuaCoCapa_tyLeNull() {
        taoNc("FG-01", "L30");
        var d = reportService.dashboard(null);
        assertThat(d.tyLeHieuQua).isNull();
    }
}
