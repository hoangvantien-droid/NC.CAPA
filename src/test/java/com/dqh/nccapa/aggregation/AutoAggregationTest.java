package com.dqh.nccapa.aggregation;

import com.dqh.nccapa.aggregation.dto.AggregationResult;
import com.dqh.nccapa.aggregation.dto.OosEventRequest;
import com.dqh.nccapa.common.enums.Severity;
import com.dqh.nccapa.nc.repository.NcRepository;
import com.dqh.nccapa.nc.service.NcService;
import com.dqh.nccapa.support.DefectSeed;
import com.dqh.nccapa.support.MySqlTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** Lát 4: auto-aggregation (OOS → NC). */
class AutoAggregationTest extends MySqlTestBase {

    @Autowired AutoAggregationService aggregation;
    @Autowired NcRepository ncRepo;
    @Autowired NcService ncService;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setUp() { DefectSeed.seed(jdbc); }

    private OosEventRequest oos(String lo, String maLoi) {
        OosEventRequest e = new OosEventRequest();
        e.loKiem = lo; e.maLoiId = maLoi; e.maHang = "SP001"; e.inOutSpec = 2; e.nhaMay = "MGP";
        return e;
    }

    @Test // AC3: OOS thứ 2 cùng lô -> APPENDED, không tạo NC mới; mức tự nâng theo chỉ tiêu nặng nhất
    void oosThu2CungLo_append_khongTaoMoi_napMuc() {
        AggregationResult r1 = aggregation.onOos(oos("LOT-AGG-1", "FG-01")); // Nhẹ
        assertThat(r1.action).isEqualTo(AggregationResult.Action.CREATED);
        assertThat(r1.mucNghiemTrong).isEqualTo("NHE");

        AggregationResult r2 = aggregation.onOos(oos("LOT-AGG-1", "PRC-02")); // Nặng
        assertThat(r2.action).isEqualTo(AggregationResult.Action.APPENDED);
        assertThat(r2.soHieu).isEqualTo(r1.soHieu);                 // cùng NC
        assertThat(r2.mucNghiemTrong).isEqualTo("NANG");            // nâng theo chỉ tiêu nặng nhất

        assertThat(ncService.layNc(r1.soHieu).getMucNghiemTrong()).isEqualTo(Severity.NANG);
        assertThat(ncService.chiTieuCua(r1.soHieu)).hasSize(2);
    }

    @Test // OOS Nghiêm trọng -> tạo NC Nghiêm trọng + food safety
    void oosNghiemTrong_taoNcFoodSafety() {
        AggregationResult r = aggregation.onOos(oos("LOT-AGG-2", "FG-03")); // Nghiêm trọng + KV
        assertThat(r.action).isEqualTo(AggregationResult.Action.CREATED);
        assertThat(r.mucNghiemTrong).isEqualTo("NGHIEM_TRONG");
        assertThat(ncService.layNc(r.soHieu).isFoodSafety()).isTrue();
    }

    @Test // AC22: 2 OOS cùng lô MỚI đồng thời -> đúng 1 NC (mutex nc_agg_lock)
    void haiOosCungLoDongThoi_chiMotNc() throws Exception {
        int n = 2;
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(n);
        ConcurrentLinkedQueue<AggregationResult> results = new ConcurrentLinkedQueue<>();
        AtomicInteger errors = new AtomicInteger();

        for (int i = 0; i < n; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    results.add(aggregation.onOos(oos("LOT-RACE", "FG-01")));
                } catch (Exception ex) {
                    errors.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        done.await(30, TimeUnit.SECONDS);
        pool.shutdownNow();

        assertThat(errors.get()).isZero();
        long created = results.stream()
                .filter(r -> r.action == AggregationResult.Action.CREATED).count();
        long appended = results.stream()
                .filter(r -> r.action == AggregationResult.Action.APPENDED).count();
        assertThat(created).isEqualTo(1);     // chỉ 1 NC được tạo
        assertThat(appended).isEqualTo(1);    // cái còn lại append
        // tất cả cùng một số hiệu
        assertThat(results.stream().map(r -> r.soHieu).distinct().count()).isEqualTo(1);
    }
}
