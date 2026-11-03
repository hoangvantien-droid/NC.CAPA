package com.dqh.nccapa.common.enums;

/** 8 bước 8D (REQ mục 4.2). D0 phản ứng khẩn → D8 ghi nhận & đóng. */
public enum Disciplines8D {
    D0("Chuẩn bị & phản ứng khẩn"),
    D1("Lập nhóm"),
    D2("Mô tả vấn đề"),
    D3("Hành động ngăn chặn tạm thời"),
    D4("Nguyên nhân gốc"),
    D5("Hành động khắc phục vĩnh viễn"),
    D6("Triển khai & kiểm chứng"),
    D7("Ngăn tái diễn (hệ thống hóa)"),
    D8("Ghi nhận nhóm & đóng");

    private final String ten;
    Disciplines8D(String ten) { this.ten = ten; }
    public String getTen() { return ten; }
}
