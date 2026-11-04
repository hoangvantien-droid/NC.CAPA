package com.dqh.nccapa.web;

import java.util.Map;

/**
 * Bảng nhãn tiếng Việt cho các mã enum hiển thị ra UI (chỉ đổi CHỮ HIỂN THỊ, giá trị gửi lên backend
 * vẫn là tên enum). Giá trị lạ / chưa có trong map thì trả về NGUYÊN MÃ (không lỗi, không trống);
 * {@code null} hiện "—". Được bơm vào view với tên {@code nhan} qua {@link NhanHienThiAdvice}
 * và dùng trong Thymeleaf: {@code ${nhan.nc(n.trangThai)}}.
 * Bản JS tương ứng (cho option của modal) là {@code window.LABELS} trong fragments/action-modal.html
 * — hai nơi phải đồng bộ.
 */
public final class NhanHienThi {

    /** Thể hiện dùng chung (không có trạng thái). */
    public static final NhanHienThi INSTANCE = new NhanHienThi();

    private static final Map<String, String> NC = Map.of(
            "New", "Mới",
            "Containment", "Đang khoanh vùng",
            "PendingApproval", "Chờ duyệt",
            "Processing", "Đang xử lý",
            "PendingVerification", "Chờ thẩm tra",
            "Rejected", "Bị từ chối",
            "Closed", "Đã đóng",
            "Cancelled", "Đã huỷ");

    private static final Map<String, String> CAPA = Map.of(
            "Open", "Mới mở",
            "Investigating", "Đang điều tra",
            "ActionPlan", "Lập kế hoạch hành động",
            "Processing", "Đang thực thi",
            "Monitoring", "Đang theo dõi",
            "Closed", "Đã đóng",
            "Ineffective", "Không hiệu quả");

    private static final Map<String, String> PR = Map.of(
            "Draft", "Nháp",
            "Analyzing", "Đang phân tích",
            "PendingApproval", "Chờ duyệt",
            "Approved", "Đã duyệt",
            "Implementing", "Đang thực hiện",
            "Closed", "Đã đóng",
            "Rejected", "Bị từ chối");

    private static final Map<String, String> ACTION_STATUS = Map.of(
            "Open", "Chưa làm",
            "InProgress", "Đang làm",
            "Completed", "Hoàn thành");

    private static final Map<String, String> LOAI_NC = Map.of(
            "THANH_PHAM", "Thành phẩm",
            "TRONG_SX", "Trong sản xuất",
            "NVL", "Nguyên vật liệu",
            "MOI_TRUONG", "Môi trường",
            "THIET_BI_DO", "Thiết bị đo",
            "KHIEU_NAI", "Khiếu nại");

    private static final Map<String, String> NGUON = Map.of(
            "OOS", "OOS (ngoài tiêu chuẩn)",
            "MES", "MES",
            "THU_CONG", "Nhập thủ công",
            "TICKET", "Ticket khiếu nại",
            "NOI_BO", "Nội bộ",
            "HIEU_CHUAN", "Hiệu chuẩn",
            "KIEM_TON", "Kiểm tồn");

    private static final Map<String, String> LOAI_THAY_DOI = Map.of(
            "QUY_CACH", "Quy cách",
            "CONG_THUC", "Công thức",
            "BAO_BI_NHAN", "Bao bì / nhãn",
            "NHA_CUNG_CAP", "Nhà cung cấp",
            "QUY_TRINH_TB_SOP", "Quy trình / thiết bị / SOP");

    private static final Map<String, String> VAI_TRO = Map.of(
            "QA_TAP_DOAN", "QA tập đoàn",
            "QA_NHA_MAY", "QA nhà máy",
            "R_AND_D", "R&D",
            "PHAP_CHE", "Pháp chế",
            "THU_MUA", "Thu mua",
            "GD_NHA_MAY_KY_THUAT", "GĐ nhà máy / Kỹ thuật");

    private static final Map<String, String> KET_QUA_DUYET = Map.of(
            "APPROVED", "Đã duyệt",
            "REJECTED", "Từ chối");

    private static final Map<String, String> RCA = Map.of(
            "FIVE_WHY", "5 Why",
            "FISHBONE", "Xương cá",
            "IS_IS_NOT", "Is / Is not");

    private static final Map<String, String> NHOM_6M = Map.of(
            "MAN", "Con người",
            "MACHINE", "Máy móc",
            "MATERIAL", "Nguyên vật liệu",
            "METHOD", "Phương pháp",
            "MEASURE", "Đo lường",
            "ENVIRONMENT", "Môi trường");

    private static final Map<String, String> LOAI_ACTION = Map.of(
            "TUC_THOI", "Khắc phục tức thời",
            "NGAN_TAI_DIEN", "Ngăn tái diễn");

    private static final Map<String, String> LOAI_HANH_DONG = Map.of(
            "CORRECTIVE", "Khắc phục",
            "PREVENTIVE", "Phòng ngừa");

    private static final Map<String, String> DISPOSITION = Map.of(
            "REWORK", "Làm lại",
            "RETURN_NCC", "Trả nhà cung cấp",
            "HA_CAP", "Hạ cấp",
            "TAI_CHE", "Tái chế",
            "TIEU_HUY", "Tiêu huỷ");

    private NhanHienThi() { }

    /** Tra nhãn; null -> "—"; không có trong map -> nguyên mã. */
    static String tra(Map<String, String> map, Object ma) {
        if (ma == null) {
            return "—";
        }
        String key = (ma instanceof Enum<?> e) ? e.name() : ma.toString();
        return map.getOrDefault(key, key);
    }

    public String nc(Object ma) { return tra(NC, ma); }

    public String capa(Object ma) { return tra(CAPA, ma); }

    public String pr(Object ma) { return tra(PR, ma); }

    public String actionStatus(Object ma) { return tra(ACTION_STATUS, ma); }

    public String loaiNc(Object ma) { return tra(LOAI_NC, ma); }

    public String nguon(Object ma) { return tra(NGUON, ma); }

    public String loaiThayDoi(Object ma) { return tra(LOAI_THAY_DOI, ma); }

    public String vaiTro(Object ma) { return tra(VAI_TRO, ma); }

    public String ketQuaDuyet(Object ma) { return tra(KET_QUA_DUYET, ma); }

    public String rca(Object ma) { return tra(RCA, ma); }

    public String nhom6m(Object ma) { return tra(NHOM_6M, ma); }

    public String loaiAction(Object ma) { return tra(LOAI_ACTION, ma); }

    public String loaiHanhDong(Object ma) { return tra(LOAI_HANH_DONG, ma); }

    public String disposition(Object ma) { return tra(DISPOSITION, ma); }
}
