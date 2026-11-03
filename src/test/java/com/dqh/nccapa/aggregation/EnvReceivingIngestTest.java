package com.dqh.nccapa.aggregation;

import com.dqh.nccapa.aggregation.dto.AggregationResult;
import com.dqh.nccapa.aggregation.dto.EnvEventRequest;
import com.dqh.nccapa.aggregation.dto.ReceivingEventRequest;
import com.dqh.nccapa.common.enums.NcStatus;
import com.dqh.nccapa.common.enums.Severity;
import com.dqh.nccapa.nc.service.NcService;
import com.dqh.nccapa.support.DefectSeed;
import com.dqh.nccapa.support.MySqlTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/** Đợt b — nguồn môi trường + nhận hàng. */
class EnvReceivingIngestTest extends MySqlTestBase {

    @Autowired AutoAggregationService aggregation;
    @Autowired NcService ncService;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setUp() { DefectSeed.seed(jdbc); }

    @Test // Bootswab vùng 1 -> ENV-01 Nghiêm trọng + khoanh vùng
    void moiTruong_bootswab_nghiemTrong_khoanhVung() {
        EnvEventRequest e = new EnvEventRequest();
        e.diemLayMau = "ZONE1-DRAIN-07"; e.maLoiId = "ENV-01"; e.nhaMay = "MGP"; e.ketQua = "Listeria +";
        AggregationResult r = aggregation.onEnv(e);

        assertThat(r.action).isEqualTo(AggregationResult.Action.CREATED);
        assertThat(r.mucNghiemTrong).isEqualTo("NGHIEM_TRONG");
        var nc = ncService.layNc(r.soHieu);
        assertThat(nc.getTrangThai()).isEqualTo(NcStatus.Containment);
        assertThat(nc.isFoodSafety()).isTrue();
        // điểm lấy mẫu được lưu như đối tượng
        assertThat(ncService.doiTuongCua(r.soHieu)).anyMatch(d -> "ZONE1-DRAIN-07".equals(d.getGiaTri()));
    }

    @Test // Sự kiện môi trường thứ 2 cùng điểm lấy mẫu -> append
    void moiTruong_cungDiem_append() {
        EnvEventRequest e1 = new EnvEventRequest();
        e1.diemLayMau = "ZONE2-WALL-03"; e1.maLoiId = "ENV-01"; e1.nhaMay = "MGP";
        AggregationResult r1 = aggregation.onEnv(e1);
        EnvEventRequest e2 = new EnvEventRequest();
        e2.diemLayMau = "ZONE2-WALL-03"; e2.maLoiId = "ENV-01"; e2.nhaMay = "MGP";
        AggregationResult r2 = aggregation.onEnv(e2);
        assertThat(r2.action).isEqualTo(AggregationResult.Action.APPENDED);
        assertThat(r2.soHieu).isEqualTo(r1.soHieu);
    }

    @Test // Nhận hàng NVL nhiễm tạp chất -> REC-02 Nặng
    void nhanHang_nvlNang() {
        ReceivingEventRequest r = new ReceivingEventRequest();
        r.loNvl = "NVL-LOT-001"; r.maLoiId = "REC-02"; r.ncc = "NCC01"; r.nhaMay = "MGP";
        AggregationResult res = aggregation.onReceiving(r);
        assertThat(res.action).isEqualTo(AggregationResult.Action.CREATED);
        assertThat(res.mucNghiemTrong).isEqualTo("NANG");
        var nc = ncService.layNc(res.soHieu);
        assertThat(nc.getLoaiNc()).isEqualTo("NVL");
        assertThat(ncService.doiTuongCua(res.soHieu)).anyMatch(d -> "NCC01".equals(d.getGiaTri()));
    }

    @Test // NVL vượt ngưỡng vi sinh -> REC-03 Nghiêm trọng + khoanh vùng
    void nhanHang_viSinh_nghiemTrong() {
        ReceivingEventRequest r = new ReceivingEventRequest();
        r.loNvl = "NVL-LOT-002"; r.maLoiId = "REC-03"; r.ncc = "NCC01"; r.nhaMay = "MGP";
        AggregationResult res = aggregation.onReceiving(r);
        assertThat(res.mucNghiemTrong).isEqualTo("NGHIEM_TRONG");
        assertThat(ncService.layNc(res.soHieu).getTrangThai()).isEqualTo(NcStatus.Containment);
    }
}
