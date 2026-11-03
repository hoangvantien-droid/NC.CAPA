package com.dqh.nccapa.nc.dto;

import jakarta.validation.constraints.NotBlank;

/** Body POST /nc/{so}/approve (N5). NC Nặng cần 2 lượt bởi 2 user khác nhau. */
public class ApproveRequest {
    @NotBlank public String actor;
}
