package com.dqh.nccapa.demo;

/**
 * Vai trò giả lập cho tính năng chuyển tài khoản demo (KHÔNG login thật, KHÔNG Spring Security).
 * Các mã vai trò bám theo role seed trong {@code DemoSeeder} để nhất quán với nghiệp vụ.
 */
public enum DemoRole {
    NGUOI_BAO_CAO("Người báo cáo"),
    BO_PHAN_SX("Bộ phận sản xuất"),
    QA_NHA_MAY("QA nhà máy"),
    QA_TAP_DOAN("QA tập đoàn"),
    ADMIN("Quản trị");

    private final String tenHienThi;

    DemoRole(String tenHienThi) {
        this.tenHienThi = tenHienThi;
    }

    public String getTenHienThi() {
        return tenHienThi;
    }
}
