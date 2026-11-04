package com.dqh.nccapa.attachment.service;

import com.dqh.nccapa.common.enums.NcStatus;

import java.util.Set;

/**
 * Ma trận quyền UPLOAD file đính kèm vào NC theo trạng thái NC — MỘT nguồn quyết định, dùng chung cho
 * backend ({@link AttachmentService}) và UI ({@code QuyenUi}) để không lệch nhau.
 *
 * <p>Mã vai trò trùng {@code app_user_role.role_ma}. ADMIN không có mặt ở bất kỳ trạng thái nào
 * (admin thuần quản trị, không upload). Trạng thái không có trong bảng ⇒ rỗng ⇒ CHẶN (fail-closed).
 */
public final class AttachmentQuyen {

    public static final String NGUOI_BAO_CAO = "NGUOI_BAO_CAO";
    public static final String BO_PHAN_SX = "BO_PHAN_SX";
    public static final String BO_PHAN_KHO = "BO_PHAN_KHO";
    public static final String QA_NHA_MAY = "QA_NHA_MAY";
    public static final String QA_TAP_DOAN = "QA_TAP_DOAN";

    private static final Set<String> MOI_VAI_TRO_NGHIEP_VU =
            Set.of(NGUOI_BAO_CAO, BO_PHAN_SX, BO_PHAN_KHO, QA_NHA_MAY, QA_TAP_DOAN);
    private static final Set<String> CHI_QA = Set.of(QA_NHA_MAY, QA_TAP_DOAN);
    private static final Set<String> QA_VA_BO_PHAN_XU_LY =
            Set.of(QA_NHA_MAY, QA_TAP_DOAN, BO_PHAN_SX, BO_PHAN_KHO);

    private AttachmentQuyen() { }

    /** Các vai trò được upload vào NC ở trạng thái {@code tt}; rỗng nếu không ai được (kể cả trạng thái lạ). */
    public static Set<String> vaiTroUploadNc(NcStatus tt) {
        if (tt == null) return Set.of();
        return switch (tt) {
            case New -> MOI_VAI_TRO_NGHIEP_VU;
            case Containment, PendingApproval, Rejected -> CHI_QA;
            case Processing, PendingVerification -> QA_VA_BO_PHAN_XU_LY;
            case Closed, Cancelled -> Set.of();
            // Trạng thái mới thêm vào enum mà chưa khai báo ở đây ⇒ mặc định chặn.
            default -> Set.of();
        };
    }

    /** True nếu vai trò {@code roleMa} được upload vào NC ở trạng thái {@code tt}. */
    public static boolean duocUploadNc(NcStatus tt, String roleMa) {
        return roleMa != null && vaiTroUploadNc(tt).contains(roleMa);
    }
}
