package com.dqh.nccapa.capa.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * POST /capa/{id}/verification/sua-ket-qua — sửa kết quả kiểm chứng CAPA ĐÃ XÁC NHẬN.
 * Minh họa invariant I7: trước khi đổi ket_qua/bien_ban phải lưu SNAPSHOT bản cũ (versioning).
 */
public class SuaKetQuaKiemChungRequest {
    /** Kết quả mới: HIEU_QUA | KHONG_HIEU_QUA. */
    @NotBlank public String ketQua;
    /** Biên bản kiểm tra mới (tuỳ chọn). */
    public String bienBanKiemTra;
    /** Lý do sửa — lưu kèm snapshot. */
    public String lyDo;
    @NotBlank public String actor;
}
