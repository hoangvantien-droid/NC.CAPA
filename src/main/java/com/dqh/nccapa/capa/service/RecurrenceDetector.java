package com.dqh.nccapa.capa.service;

import com.dqh.nccapa.common.audit.AuditService;
import com.dqh.nccapa.common.enums.LoaiThayDoi;
import com.dqh.nccapa.config.AppProperties;
import com.dqh.nccapa.nc.entity.Nc;
import com.dqh.nccapa.nc.service.RecurrenceHook;
import com.dqh.nccapa.pr.entity.Pr;
import com.dqh.nccapa.pr.service.PrService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tái diễn lần 3 trong rolling window 90 ngày (REQ mục 5, bảng tái diễn dòng
 * "Tái diễn lần 3 cùng tổ hợp mã hàng + mã lỗi trong 90 ngày gần nhất → Bắt buộc mở PR";
 * cửa sổ cấu hình được qua {@link AppProperties.Nc#getRecurrenceWindowDays()}, điểm mở #6 = 90 ngày).
 *
 * <p>Gọi SAU khi một NC mới vừa được tạo, SAU hook fail sớm TH2 ({@code EarlyFailDetector}).
 * Đếm số NC cùng tổ hợp (mã hàng chính + mã lỗi chính) — kể cả NC mới — có {@code created_at}
 * trong {@code recurrenceWindowDays} ngày gần nhất. Khi NC mới là lần xuất hiện thứ 3 trở lên
 * (đếm &gt;= 3) → BẮT BUỘC mở PR thật cho đúng vấn đề (mã hàng + mã lỗi của NC mới).
 *
 * <p>Định nghĩa "tái diễn" đã chọn: đếm đơn giản theo cặp (mã hàng + mã lỗi) trong cửa sổ,
 * KHÔNG ràng buộc CAPA trước đó phải Closed. REQ (sơ đồ mục 4.2) mô tả tái diễn là NC xuất hiện
 * SAU khi CAPA đã Closed, nhưng để app mẫu đơn giản và tất định, mọi NC cùng cặp trong cửa sổ
 * đều được tính. Điều này an toàn phía "bắt buộc" (không bỏ sót vấn đề tái diễn thật) và dựa
 * idempotent của {@link PrService#moTuDong} để không mở PR trùng khi nhiều điều kiện cùng kích hoạt
 * (fail lần 2 / trần Nghiêm trọng / tái diễn) — REQ mục 5 "chống mở trùng PR".
 */
@Service
public class RecurrenceDetector implements RecurrenceHook {

    /** Ngưỡng tái diễn theo REQ: lần xuất hiện thứ 3 của cùng cặp trong cửa sổ → mở PR. */
    static final int NGUONG_TAI_DIEN = 3;

    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final PrService prService;
    private final AppProperties appProps;

    public RecurrenceDetector(JdbcTemplate jdbc, AuditService audit,
                              PrService prService, AppProperties appProps) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.prService = prService;
        this.appProps = appProps;
    }

    @Override
    @Transactional
    public boolean onNewNc(Nc ncMoi) {
        String maHang = ncMoi.getMaHangChinh();
        String maLoi = ncMoi.getMaLoiChinh();
        if (maHang == null || maLoi == null) return false;

        int windowDays = appProps.getNc().getRecurrenceWindowDays();
        // Đếm NC cùng cặp (mã hàng + mã lỗi) có created_at trong [now - windowDays, now].
        // Kể cả NC mới: taoNc() gọi ncRepo.saveAndFlush(nc) trước khi chạy hook nên dòng NC mới
        // đã có trong DB → count = số lần xuất hiện trong cửa sổ (REQ: lần thứ 3 → mở PR).
        Long soLan = jdbc.queryForObject(
            "SELECT COUNT(*) FROM nc " +
            "WHERE ma_hang_chinh = ? AND ma_loi_chinh = ? " +
            "AND created_at >= DATE_SUB(NOW(), INTERVAL ? DAY)",
            Long.class, maHang, maLoi, windowDays);

        if (soLan == null || soLan < NGUONG_TAI_DIEN) return false;

        // Đủ ngưỡng tái diễn → bắt buộc mở PR thật cho vấn đề (mã hàng + mã lỗi). Idempotent:
        // nếu đã có PR đang mở cho vấn đề (fail lần 2 / TH2 / tái diễn trước) thì link NC mới, không mở PR thứ 2.
        // maHang/maLoi đã được guard non-null ở trên → moTuDong không trả null tại đây.
        Pr pr = prService.moTuDong(LoaiThayDoi.QUY_TRINH_TB_SOP,
                maHang, maLoi, ncMoi.getSoHieu(), null, "SYSTEM");
        audit.log("NC", ncMoi.getSoHieu(), "MO_PR", null, String.valueOf(pr.getId()),
                "tái diễn lần " + soLan + " trong " + windowDays + " ngày · PR#" + pr.getId(), "SYSTEM");
        return true;
    }
}
