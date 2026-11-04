package com.dqh.nccapa.demo;

/**
 * Cờ quyền MỨC UI suy trực tiếp từ {@link DemoRole} của tài khoản demo hiện tại.
 *
 * <p>CHỈ dùng để ẩn/hiện menu + nút trên template (tiện lợi UX). KHÔNG phải cơ chế bảo mật:
 * backend vẫn giữ guard thật qua {@code RoleChecker} (tra {@code app_user_role}). Vì vai trò
 * lấy từ {@code DemoUser.vaiTro()} (hằng số trong code) nên KHÔNG phát sinh query DB cho mỗi view.
 *
 * @param laNguoiBaoCao vai trò người báo cáo
 * @param laSx vai trò bộ phận sản xuất
 * @param laQaNhaMay vai trò QA nhà máy
 * @param laQaTapDoan vai trò QA tập đoàn
 * @param laAdmin vai trò quản trị (thuần quản trị, KHÔNG duyệt nghiệp vụ)
 */
public record QuyenUi(boolean laNguoiBaoCao, boolean laSx, boolean laQaNhaMay,
                      boolean laQaTapDoan, boolean laAdmin) {

    /** True nếu thuộc nhóm QA (QA nhà máy HOẶC QA tập đoàn) — nhóm được thao tác duyệt/CAPA. */
    public boolean laQa() {
        return laQaNhaMay || laQaTapDoan;
    }

    // ---- Ma trận menu/route: MỘT nguồn quyết định dùng chung cho sidebar và guard route view ----

    /** Hàng chờ duyệt: chỉ QA. */
    public boolean thayChoDuyet() {
        return laQa();
    }

    /** CAPA: SX (xem), QA, Admin (xem). */
    public boolean thayCapa() {
        return laSx || laQa() || laAdmin;
    }

    /** PR: QA, Admin (xem). */
    public boolean thayPr() {
        return laQa() || laAdmin;
    }

    /** Dashboard: QA, Admin. */
    public boolean thayDashboard() {
        return laQa() || laAdmin;
    }

    /** Ticket outbox: QA tập đoàn, Admin. */
    public boolean thayTicket() {
        return laQaTapDoan || laAdmin;
    }

    /**
     * Vai trò hiện tại có được upload file vào NC ở trạng thái {@code tt} không — cùng bảng ma trận
     * với backend ({@link com.dqh.nccapa.attachment.service.AttachmentQuyen}). Admin luôn false.
     */
    public boolean duocUploadNc(com.dqh.nccapa.common.enums.NcStatus tt) {
        var ds = com.dqh.nccapa.attachment.service.AttachmentQuyen.vaiTroUploadNc(tt);
        return (laNguoiBaoCao && ds.contains(com.dqh.nccapa.attachment.service.AttachmentQuyen.NGUOI_BAO_CAO))
                || (laSx && ds.contains(com.dqh.nccapa.attachment.service.AttachmentQuyen.BO_PHAN_SX))
                || (laQaNhaMay && ds.contains(com.dqh.nccapa.attachment.service.AttachmentQuyen.QA_NHA_MAY))
                || (laQaTapDoan && ds.contains(com.dqh.nccapa.attachment.service.AttachmentQuyen.QA_TAP_DOAN));
    }

    /** Map 1-1 từ {@link DemoRole} sang bộ cờ quyền UI. */
    public static QuyenUi tu(DemoRole vaiTro) {
        return new QuyenUi(
                vaiTro == DemoRole.NGUOI_BAO_CAO,
                vaiTro == DemoRole.BO_PHAN_SX,
                vaiTro == DemoRole.QA_NHA_MAY,
                vaiTro == DemoRole.QA_TAP_DOAN,
                vaiTro == DemoRole.ADMIN);
    }
}
