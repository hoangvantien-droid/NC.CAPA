package com.dqh.nccapa.capa;

import com.dqh.nccapa.capa.dto.CompleteStepRequest;
import com.dqh.nccapa.capa.dto.TaoCapaRequest;
import com.dqh.nccapa.capa.entity.Capa;
import com.dqh.nccapa.capa.entity.Capa8dStep;
import com.dqh.nccapa.capa.repository.Capa8dStepRepository;
import com.dqh.nccapa.capa.service.CapaService;
import com.dqh.nccapa.common.enums.NcSource;
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

/** Đợt d — 8D (biến thể CAPA, checklist D0..D8). */
class Capa8dTest extends MySqlTestBase {

    @Autowired NcService ncService;
    @Autowired CapaService capaService;
    @Autowired Capa8dStepRepository stepRepo;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setUp() { DefectSeed.seed(jdbc); }

    private Capa taoCapa() {
        TaoNcRequest r = new TaoNcRequest();
        r.loaiNc = "KHIEU_NAI"; r.nguonPhatSinh = NcSource.THU_CONG;
        r.nhaMay = "MGP"; r.nguoiBaoCao = "u"; r.loKiem = "L-8D-1";
        TaoNcRequest.ChiTieuItem ct = new TaoNcRequest.ChiTieuItem();
        ct.maLoiId = "PRC-02"; ct.maHang = "SP001";
        r.chiTieu = List.of(ct);
        Nc nc = ncService.taoNc(r);
        TaoCapaRequest t = new TaoCapaRequest(); t.actor = "qatd";
        return capaService.taoTuNc(nc.getSoHieu(), t);
    }

    @Test // Bật 8D tạo đủ 9 bước D0..D8
    void bat8d_tao9Buoc() {
        Capa capa = taoCapa();
        List<Capa8dStep> steps = capaService.bat8d(capa.getId(), "qatd");
        assertThat(steps).hasSize(9);
        assertThat(steps).extracting(Capa8dStep::getBuoc)
                .containsExactly("D0","D1","D2","D3","D4","D5","D6","D7","D8");
        assertThat(capaService.layCapa(capa.getId())).isNotNull();
    }

    @Test // Hoàn thành một bước
    void hoanThanhBuoc() {
        Capa capa = taoCapa();
        capaService.bat8d(capa.getId(), "qatd");
        CompleteStepRequest c = new CompleteStepRequest(); c.noiDung = "Đã lập nhóm"; c.actor = "qatd";
        Capa8dStep s = capaService.hoanThanhBuoc8d(capa.getId(), "D1", c);
        assertThat(s.isHoanThanh()).isTrue();
        assertThat(stepRepo.countByCapaIdAndHoanThanhFalse(capa.getId())).isEqualTo(8); // còn 8 bước
    }

    @Test // Bật 8D hai lần không tạo trùng bước
    void bat8d_khongTaoTrung() {
        Capa capa = taoCapa();
        capaService.bat8d(capa.getId(), "qatd");
        capaService.bat8d(capa.getId(), "qatd");
        assertThat(stepRepo.findByCapaIdOrderByBuocAsc(capa.getId())).hasSize(9);
    }
}
