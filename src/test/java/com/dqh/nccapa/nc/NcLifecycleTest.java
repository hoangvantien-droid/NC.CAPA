package com.dqh.nccapa.nc;

import com.dqh.nccapa.common.enums.NcSource;
import com.dqh.nccapa.common.enums.NcStatus;
import com.dqh.nccapa.common.enums.Severity;
import com.dqh.nccapa.common.error.BusinessException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NcLifecycleTest extends MySqlTestBase {

    @Autowired NcService ncService;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        DefectSeed.seed(jdbc);
    }

    private TaoNcRequest reqNhe() {
        TaoNcRequest r = new TaoNcRequest();
        r.loaiNc = "THANH_PHAM";
        r.nguonPhatSinh = NcSource.THU_CONG;
        r.nhaMay = "MGP";
        r.nguoiBaoCao = "user01";
        r.loKiem = "LOT-NHE-1";
        TaoNcRequest.ChiTieuItem ct = new TaoNcRequest.ChiTieuItem();
        ct.maLoiId = "FG-01"; ct.maHang = "SP001";
        r.chiTieu = List.of(ct);
        return r;
    }

    @Test // AC1: số hiệu đúng format
    void taoNc_sinhSoHieuDungFormat() {
        Nc nc = ncService.taoNc(reqNhe());
        assertThat(nc.getSoHieu()).matches("KPH-\\d{4}-MGP-\\d{5}");
        assertThat(nc.getMucNghiemTrong()).isEqualTo(Severity.NHE);
        assertThat(nc.getTrangThai()).isEqualTo(NcStatus.New);
        assertThat(nc.getMaLoiChinh()).isEqualTo("FG-01");
    }

    @Test // AC2: luồng Nhanh New -> Closed
    void luongNhanh() {
        Nc nc = ncService.taoNc(reqNhe());
        CloseFastRequest cf = new CloseFastRequest();
        cf.ketQuaXuLy = "Đã loại lô, xử lý trong ca";
        cf.actor = "user01";
        Nc closed = ncService.closeFast(nc.getSoHieu(), cf);
        assertThat(closed.getTrangThai()).isEqualTo(NcStatus.Closed);
    }

    @Test // AC1: mức = mã lỗi nặng nhất khi nhiều chỉ tiêu
    void mucPhieuBangMaLoiNangNhat() {
        TaoNcRequest r = reqNhe();
        TaoNcRequest.ChiTieuItem nang = new TaoNcRequest.ChiTieuItem();
        nang.maLoiId = "PRC-02"; nang.maHang = "SP001";
        r.chiTieu = List.of(r.chiTieu.get(0), nang);
        Nc nc = ncService.taoNc(r);
        assertThat(nc.getMucNghiemTrong()).isEqualTo(Severity.NANG);
        assertThat(nc.getMaLoiChinh()).isEqualTo("PRC-02");
    }

    @Test // AC8 (phần chi phí thực tế sẽ test ở lát sau; ở đây: close-fast chỉ cho NC Nhẹ)
    void closeFast_chanNcKhongNhe() {
        TaoNcRequest r = reqNhe();
        TaoNcRequest.ChiTieuItem nang = new TaoNcRequest.ChiTieuItem();
        nang.maLoiId = "PRC-02"; nang.maHang = "SP001";
        r.chiTieu = List.of(nang);
        Nc nc = ncService.taoNc(r);
        CloseFastRequest cf = new CloseFastRequest();
        cf.ketQuaXuLy = "x"; cf.actor = "user01";
        assertThatThrownBy(() -> ncService.closeFast(nc.getSoHieu(), cf))
                .isInstanceOf(BusinessException.class);
    }
}
