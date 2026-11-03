package com.dqh.nccapa.nc.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * Body POST /nc/{so}/containment (N2).
 * Bắt buộc ≥1 lô khoanh + chứng từ Hold (REQ mục 4.1, quy tắc cứng #9).
 */
public class ContainmentRequest {
    @NotEmpty public List<LoKhoanh> loKhoanh;
    @NotBlank public String actor;

    public static class LoKhoanh {
        @NotBlank public String soLo;            // giá trị đối tượng loại LO
        @NotBlank public String chungTuHold;     // mã chứng từ Hold EBS / đường dẫn ảnh
    }
}
