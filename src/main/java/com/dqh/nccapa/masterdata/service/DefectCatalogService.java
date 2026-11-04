package com.dqh.nccapa.masterdata.service;

import com.dqh.nccapa.common.enums.NcFlow;
import com.dqh.nccapa.common.enums.Severity;
import com.dqh.nccapa.common.error.BusinessException;
import com.dqh.nccapa.masterdata.entity.MaLoi;
import com.dqh.nccapa.masterdata.repository.MaLoiRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/** Tra cứu bộ mã lỗi + quyết định luồng/mức (StateMachine §6, APPX mục 4). */
@Service
public class DefectCatalogService {

    private final MaLoiRepository repo;

    private final JdbcTemplate jdbc;

    public DefectCatalogService(MaLoiRepository repo, JdbcTemplate jdbc) {
        this.repo = repo;
        this.jdbc = jdbc;
    }

    /**
     * Chặn dùng danh mục đã "ngừng dùng" khi TẠO NC mới: mã lỗi hết hiệu lực (hieu_luc_den đã qua) hoặc
     * thuộc nhóm lỗi ngừng dùng; nhà máy / mã hàng ngừng dùng. Bản ghi không có trong bảng danh mục
     * (NC nhập tự do) KHÔNG bị chặn ở đây, chỉ chặn khi biết chắc là đã ngừng. NC cũ không bị ảnh hưởng.
     */
    public void kiemTraDangDungKhiTaoNc(String nhaMay, String maHang, MaLoi ml) {
        LocalDate den = ml.getHieuLucDen();
        if (den != null && LocalDate.now().isAfter(den)) {
            throw BusinessException.businessRule("Mã lỗi " + ml.getMaLoiId() + " đã ngừng dùng", "maLoiId");
        }
        if (ngung("SELECT COUNT(*) FROM nhom_loi WHERE nhom_loi_id = ? AND active = 0", ml.getNhomLoiId())) {
            throw BusinessException.businessRule("Nhóm lỗi " + ml.getNhomLoiId() + " đã ngừng dùng", "maLoiId");
        }
        if (nhaMay != null && ngung("SELECT COUNT(*) FROM nha_may WHERE ma = ? AND active = 0", nhaMay)) {
            throw BusinessException.businessRule("Nhà máy " + nhaMay + " đã ngừng dùng", "nhaMay");
        }
        if (maHang != null && ngung("SELECT COUNT(*) FROM ma_hang WHERE ma = ? AND active = 0", maHang)) {
            throw BusinessException.businessRule("Mã hàng " + maHang + " đã ngừng dùng", "maHang");
        }
    }

    /**
     * Đối tượng NC mới kiểu NCC / MA_HANG mà đã ngừng dùng thì bị chặn (chỉ chặn khi biết chắc active=0).
     * NC do hệ thống tự sinh (AutoAggregationService) KHÔNG qua kiểm tra này: sự kiện đã xảy ra, không từ chối được.
     */
    public void kiemTraDoiTuongDangDung(String loaiDoiTuong, String giaTri) {
        if (loaiDoiTuong == null || giaTri == null) return;
        String loai = loaiDoiTuong.trim().toUpperCase();
        if (loai.equals("NCC") && ngung("SELECT COUNT(*) FROM ncc WHERE ma = ? AND active = 0", giaTri.trim())) {
            throw BusinessException.businessRule("Nhà cung cấp " + giaTri + " đã ngừng dùng", "doiTuong");
        }
        if (loai.equals("MA_HANG") && ngung("SELECT COUNT(*) FROM ma_hang WHERE ma = ? AND active = 0", giaTri.trim())) {
            throw BusinessException.businessRule("Mã hàng " + giaTri + " đã ngừng dùng", "doiTuong");
        }
    }

    private boolean ngung(String sql, String ma) {
        Integer n = jdbc.queryForObject(sql, Integer.class, ma);
        return n != null && n > 0;
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
