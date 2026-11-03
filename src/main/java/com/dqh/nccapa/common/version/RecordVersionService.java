package com.dqh.nccapa.common.version;

import com.dqh.nccapa.common.error.BusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Cơ chế VERSIONING dùng chung (REQ mục 7, invariant I7): khi sửa một bản ghi ĐÃ XÁC NHẬN,
 * lưu SNAPSHOT bản cũ trước rồi caller mới áp giá trị mới. Giữ cả hai, liên kết qua phien_ban.
 *
 * TỔNG QUÁT theo (doiTuongLoai, doiTuongId) nên mở rộng cho loại bản ghi khác chỉ cần gọi
 * {@link #luuSnapshot} với một doiTuongLoai mới — không cần bảng/entity riêng.
 */
@Service
public class RecordVersionService {

    private final RecordVersionRepository repo;
    private final ObjectMapper objectMapper;

    public RecordVersionService(RecordVersionRepository repo, ObjectMapper objectMapper) {
        this.repo = repo;
        this.objectMapper = objectMapper;
    }

    /**
     * Lưu snapshot giá trị CŨ của một bản ghi đã xác nhận TRƯỚC khi áp giá trị mới.
     * Serialize {@code banGhiCu} thành JSON bằng Jackson, tính phien_ban kế tiếp (max+1, bắt đầu 1).
     *
     * @param doiTuongLoai   loại bản ghi, ví dụ CAPA_VERIFICATION
     * @param doiTuongId     id bản ghi gốc (dạng chuỗi)
     * @param banGhiCu       ảnh chụp giá trị CŨ (DTO/map/entity) — sẽ được serialize JSON
     * @param lyDo           lý do sửa (có thể null)
     * @param nguoiThucHien  người thực hiện
     */
    @Transactional
    public void luuSnapshot(String doiTuongLoai, String doiTuongId, Object banGhiCu,
                            String lyDo, String nguoiThucHien) {
        String snapshotJson;
        try {
            snapshotJson = objectMapper.writeValueAsString(banGhiCu);
        } catch (JsonProcessingException e) {
            throw BusinessException.businessRule(
                    "Không serialize được snapshot bản ghi: " + e.getOriginalMessage(), "snapshot");
        }
        Integer max = repo.maxPhienBan(doiTuongLoai, doiTuongId);
        int phienBan = (max == null ? 0 : max) + 1;
        repo.save(new RecordVersion(doiTuongLoai, doiTuongId, phienBan,
                snapshotJson, lyDo, nguoiThucHien));
    }

    /** Lịch sử phiên bản của một bản ghi, mới nhất trước. */
    @Transactional(readOnly = true)
    public List<RecordVersion> lichSu(String doiTuongLoai, String doiTuongId) {
        return repo.findByDoiTuongLoaiAndDoiTuongIdOrderByPhienBanDesc(doiTuongLoai, doiTuongId);
    }
}
