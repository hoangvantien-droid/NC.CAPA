package com.dqh.nccapa.common.enums;

import java.util.List;

/** Loại thay đổi PR + chuỗi duyệt (ma trận định tuyến REQ mục 4.3, cấu hình được). */
public enum LoaiThayDoi {
    QUY_CACH(List.of("QA_TAP_DOAN")),
    CONG_THUC(List.of("R_AND_D", "QA_TAP_DOAN")),
    BAO_BI_NHAN(List.of("R_AND_D", "QA_TAP_DOAN", "PHAP_CHE")),
    NHA_CUNG_CAP(List.of("QA_TAP_DOAN", "THU_MUA")),
    QUY_TRINH_TB_SOP(List.of("QA_TAP_DOAN", "GD_NHA_MAY_KY_THUAT"));

    private final List<String> chuoiDuyet;

    LoaiThayDoi(List<String> chuoiDuyet) {
        this.chuoiDuyet = chuoiDuyet;
    }

    /** Chuỗi vai trò duyệt theo thứ tự. */
    public List<String> chuoiDuyet() {
        return chuoiDuyet;
    }

    public int soCap() {
        return chuoiDuyet.size();
    }
}
