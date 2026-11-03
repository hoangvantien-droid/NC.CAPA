package com.dqh.nccapa.common.audit;

import org.springframework.stereotype.Service;
import java.util.List;

/**
 * Ghi audit cho mọi hành động trọng yếu (REQ mục 7A, invariant I7).
 * Mọi chuyển trạng thái / đổi mức / nâng bậc / dời hạn / hủy đều đi qua đây.
 */
@Service
public class AuditService {

    private final AuditLogRepository repo;

    public AuditService(AuditLogRepository repo) {
        this.repo = repo;
    }

    public void log(String doiTuong, String doiTuongId, String hanhDong,
                    String giaTriCu, String giaTriMoi, String lyDo, String nguoi) {
        repo.save(new AuditLog(doiTuong, doiTuongId, hanhDong, giaTriCu, giaTriMoi, lyDo, nguoi));
    }

    public void logChuyenTrangThai(String doiTuong, String id, String tu, String den, String nguoi) {
        log(doiTuong, id, "CHUYEN_TRANG_THAI", tu, den, null, nguoi);
    }

    public List<AuditLog> timTheo(String doiTuong, String id) {
        return repo.findByDoiTuongAndDoiTuongIdOrderByThucHienLucAsc(doiTuong, id);
    }
}
