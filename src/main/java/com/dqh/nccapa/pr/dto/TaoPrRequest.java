package com.dqh.nccapa.pr.dto;

import com.dqh.nccapa.common.enums.LoaiThayDoi;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** POST /pr — tạo PR. */
public class TaoPrRequest {
    @NotNull public LoaiThayDoi loaiThayDoi;
    public String maHang;
    public String maLoiId;
    public List<String> ncSoHieu;   // NC gom vào PR
    @NotBlank public String actor;
}
