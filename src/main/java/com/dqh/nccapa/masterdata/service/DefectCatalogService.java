package com.dqh.nccapa.masterdata.service;

import com.dqh.nccapa.common.enums.NcFlow;
import com.dqh.nccapa.common.enums.Severity;
import com.dqh.nccapa.common.error.BusinessException;
import com.dqh.nccapa.masterdata.entity.MaLoi;
import com.dqh.nccapa.masterdata.repository.MaLoiRepository;
import org.springframework.stereotype.Service;

/** Tra cứu bộ mã lỗi + quyết định luồng/mức (StateMachine §6, APPX mục 4). */
@Service
public class DefectCatalogService {

    private final MaLoiRepository repo;

    public DefectCatalogService(MaLoiRepository repo) {
        this.repo = repo;
    }

    public MaLoi get(String maLoiId) {
        return repo.findById(maLoiId)
                .orElseThrow(() -> BusinessException.notFound("Không tìm thấy mã lỗi: " + maLoiId));
    }

    public Severity mucDefault(String maLoiId) {
        return get(maLoiId).getMucNghiemTrongDefault();
    }

    /** Quyết định luồng từ mã lỗi (StateMachine §6): cờ khoanh vùng ưu tiên, rồi theo mức. */
    public NcFlow quyetDinhLuong(String maLoiId) {
        return quyetDinhLuong(get(maLoiId));
    }

    public NcFlow quyetDinhLuong(MaLoi ml) {
        if (ml.isBatBuocKhoanhVung()) return NcFlow.CO_KHOANH_VUNG;
        return switch (ml.getMucNghiemTrongDefault()) {
            case NGHIEM_TRONG -> NcFlow.CO_KHOANH_VUNG;
            case NANG -> NcFlow.CO_XEM_XET;
            case NHE -> NcFlow.NHANH;
        };
    }
}
