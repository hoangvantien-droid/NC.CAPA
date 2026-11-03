package com.dqh.nccapa.nc;

import com.dqh.nccapa.common.enums.NcSource;
import com.dqh.nccapa.common.enums.NcStatus;
import com.dqh.nccapa.common.enums.Severity;
import com.dqh.nccapa.common.error.BusinessException;
import com.dqh.nccapa.nc.dto.CloseFastRequest;
import com.dqh.nccapa.nc.dto.EscalateRequest;
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

class EscalationTest extends MySqlTestBase {

    @Autowired NcService ncService;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setUp() { DefectSeed.seed(jdbc); }

    private Nc taoNcNhe(String lo) {
        TaoNcRequest r = new TaoNcRequest();
        r.loaiNc = "THANH_PHAM"; r.nguonPhatSinh = NcSource.THU_CONG;
        r.nhaMay = "MGP"; r.nguoiBaoCao = "user01"; r.loKiem = lo;
        TaoNcRequest.ChiTieuItem ct = new TaoNcRequest.ChiTieuItem();
        ct.maLoiId = "FG-01"; ct.maHang = "SP001";
        r.chiTieu = List.of(ct);
        return ncService.taoNc(r);
    }

    @Test // AC5: Nhẹ(New) -> Nặng: vẫn New, đổi mức, khóa close-fast
    void nheLenNang_giuNew_khoaCloseFast() {
        Nc nc = taoNcNhe("LOT-E1");
        EscalateRequest e = new EscalateRequest();
        e.mucMoi = Severity.NANG; e.lyDo = "Phát hiện mức độ nặng hơn"; e.actor = "qa01";
        Nc after = ncService.nangBac(nc.getSoHieu(), e);

        assertThat(after.getTrangThai()).isEqualTo(NcStatus.New);   // vẫn New
        assertThat(after.getMucNghiemTrong()).isEqualTo(Severity.NANG);

        CloseFastRequest cf = new CloseFastRequest();
        cf.ketQuaXuLy = "x"; cf.actor = "user01";
        assertThatThrownBy(() -> ncService.closeFast(nc.getSoHieu(), cf))
                .isInstanceOf(BusinessException.class);               // close-fast đã khóa
    }

    @Test // AC4/AC24: Nhẹ(New) -> Nghiêm trọng: chuyển Containment
    void newNheLenNghiemTrong_sangContainment() {
        Nc nc = taoNcNhe("LOT-E2");
        EscalateRequest e = new EscalateRequest();
        e.mucMoi = Severity.NGHIEM_TRONG; e.lyDo = "An toàn thực phẩm"; e.actor = "qatd01";
        Nc after = ncService.nangBac(nc.getSoHieu(), e);

        assertThat(after.getTrangThai()).isEqualTo(NcStatus.Containment);
        assertThat(after.getMucNghiemTrong()).isEqualTo(Severity.NGHIEM_TRONG);
        assertThat(after.isFoodSafety()).isTrue();
    }

    @Test
    void khongNangXuongBac() {
        Nc nc = taoNcNhe("LOT-E3");
        EscalateRequest e = new EscalateRequest();
        e.mucMoi = Severity.NHE; e.lyDo = "x"; e.actor = "qa01";
        assertThatThrownBy(() -> ncService.nangBac(nc.getSoHieu(), e))
                .isInstanceOf(BusinessException.class);
    }
}
