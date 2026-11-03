package com.dqh.nccapa.nc.service;

/**
 * Kiểm mọi CAPA của một NC đã ở trạng thái kết thúc chưa (guard N9, bản vá #1).
 * Trạng thái kết thúc = Closed, HOẶC Ineffective đã handover (sinh NC kế thừa/PR).
 * Lát CAPA sẽ cung cấp impl thật; lát 2 dùng impl mặc định (chưa có CAPA → không chặn).
 */
public interface CapaClosureChecker {
    /** @return mô tả CAPA đầu tiên CHƯA kết thúc (để báo lỗi), hoặc null nếu tất cả đã kết thúc. */
    String firstUnterminatedCapa(String ncSoHieu);
}
