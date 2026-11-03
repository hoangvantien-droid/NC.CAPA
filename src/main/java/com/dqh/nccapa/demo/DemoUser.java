package com.dqh.nccapa.demo;

/**
 * Một tài khoản demo cố định (hằng số trong code — KHÔNG lưu DB).
 *
 * @param username chuỗi dùng làm {@code actor} khi gọi API nghiệp vụ
 * @param tenHienThi tên hiển thị trên UI
 * @param vaiTro vai trò giả lập
 */
public record DemoUser(String username, String tenHienThi, DemoRole vaiTro) {
}
