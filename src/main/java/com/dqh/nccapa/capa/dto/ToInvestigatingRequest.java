package com.dqh.nccapa.capa.dto;

import jakarta.validation.constraints.NotBlank;

/** C1: Open -> Investigating. Kế hoạch điều tra (mục tiêu/phương pháp/nguồn lực) bắt buộc nếu NC >= Nặng. */
public class ToInvestigatingRequest {
    public String keHoachDieuTra;   // (cũ, tuỳ chọn — giữ tương thích)
    public String khMucTieu;        // mục tiêu
    public String khPhuongPhap;     // phương pháp
    public String khNguonLuc;       // nguồn lực
    public String nguoiDieuTra;     // người điều tra (tuỳ chọn; mặc định = người tạo CAPA)
    @NotBlank public String actor;
}
