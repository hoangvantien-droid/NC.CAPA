package com.dqh.nccapa.common.version;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RecordVersionRepository extends JpaRepository<RecordVersion, Long> {

    /** Lịch sử phiên bản của một bản ghi, mới nhất (phien_ban lớn nhất) trước. */
    List<RecordVersion> findByDoiTuongLoaiAndDoiTuongIdOrderByPhienBanDesc(String doiTuongLoai, String doiTuongId);

    long countByDoiTuongLoaiAndDoiTuongId(String doiTuongLoai, String doiTuongId);

    /** phien_ban lớn nhất hiện có (null nếu chưa có snapshot nào) — để tính phien_ban kế tiếp. */
    @Query("SELECT MAX(rv.phienBan) FROM RecordVersion rv " +
           "WHERE rv.doiTuongLoai = :loai AND rv.doiTuongId = :id")
    Integer maxPhienBan(@Param("loai") String doiTuongLoai, @Param("id") String doiTuongId);
}
