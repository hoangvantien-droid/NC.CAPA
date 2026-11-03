package com.dqh.nccapa.pr.dto;

import jakarta.validation.constraints.NotBlank;

/** Body chung cho các bước PR (submit/approve/reject/implement/close). */
public class PrActionRequest {
    @NotBlank public String actor;
    public String lyDo;        // bắt buộc khi reject (service kiểm)
    public String vaiTro;      // vai trò người duyệt (khớp chuỗi duyệt) — với approve
}
