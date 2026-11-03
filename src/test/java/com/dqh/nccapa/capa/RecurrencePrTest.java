package com.dqh.nccapa.capa;

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

/**
 * TÁI DIỄN LẦN 3 trong rolling window 90 ngày (REQ mục 5, điểm mở #6):
 * NC mới cùng tổ hợp (mã hàng + mã lỗi) là lần xuất hiện thứ 3 trong cửa sổ → BẮT BUỘC mở PR thật
 * ({@code RecurrenceDetector.onNewNc} gọi {@code PrService.moTuDong}). Chống trùng: NC thứ 4 cùng
 * cặp không mở PR thứ 2 (đã có PR đang mở cho vấn đề). Cặp khác không kích hoạt.
 *
 * <p>Dùng mã lỗi FG-01 (Nhẹ) + mã hàng riêng để KHÔNG dính luồng TH2 fail sớm (không có CAPA
 * Monitoring trong test này) — cô lập đúng quy tắc tái diễn. Mọi NC tạo trong test đều trong
 * cửa sổ 90 ngày hiện tại nên không cần so wall-clock.
 */
class RecurrencePrTest extends MySqlTestBase {

    @Autowired NcService ncService;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setUp() { DefectSeed.seed(jdbc); }

    private Nc taoNc(String maHang, String maLoi, String lo) {
        TaoNcRequest r = new TaoNcRequest();
        r.loaiNc = "TRONG_SX"; r.nguonPhatSinh = NcSource.THU_CONG;
        r.nhaMay = "MGP"; r.nguoiBaoCao = "u"; r.loKiem = lo;
        TaoNcRequest.ChiTieuItem ct = new TaoNcRequest.ChiTieuItem();
        ct.maLoiId = maLoi; ct.maHang = maHang;
        r.chiTieu = List.of(ct);
        return ncService.taoNc(r);
    }

    private long demPrChoVanDe(String maHang, String maLoi) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM pr WHERE ma_hang = ? AND ma_loi_id = ?",
                Long.class, maHang, maLoi);
    }

    @Test // 2 NC cùng cặp -> chưa mở PR; NC thứ 3 -> mở PR; NC thứ 4 -> không mở PR thứ 2
    void taiDienLan3_moPr_chongTrung() {
        taoNc("SP900", "FG-01", "LOT-RC-1");
        assertThat(demPrChoVanDe("SP900", "FG-01")).isZero();

        taoNc("SP900", "FG-01", "LOT-RC-2");
        assertThat(demPrChoVanDe("SP900", "FG-01")).isZero();   // mới 2 lần, chưa đủ ngưỡng

        taoNc("SP900", "FG-01", "LOT-RC-3");                    // lần 3 -> bắt buộc mở PR
        assertThat(demPrChoVanDe("SP900", "FG-01")).isEqualTo(1);

        taoNc("SP900", "FG-01", "LOT-RC-4");                    // lần 4 -> idempotent, không mở PR thứ 2
        assertThat(demPrChoVanDe("SP900", "FG-01")).isEqualTo(1);
    }

    @Test // cặp (mã hàng + mã lỗi) khác không bị kích hoạt lây
    void capKhac_khongKichHoat() {
        // Đủ 3 NC cho cặp A -> mở PR cho A
        taoNc("SP900", "FG-01", "LOT-A-1");
        taoNc("SP900", "FG-01", "LOT-A-2");
        taoNc("SP900", "FG-01", "LOT-A-3");
        assertThat(demPrChoVanDe("SP900", "FG-01")).isEqualTo(1);

        // Cặp B khác mã hàng: chỉ 2 NC -> không mở PR cho B
        taoNc("SP901", "FG-01", "LOT-B-1");
        taoNc("SP901", "FG-01", "LOT-B-2");
        assertThat(demPrChoVanDe("SP901", "FG-01")).isZero();
    }
}
