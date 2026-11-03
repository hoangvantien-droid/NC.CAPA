package com.dqh.nccapa.nc.repository;

import com.dqh.nccapa.common.enums.NcStatus;
import com.dqh.nccapa.nc.entity.Nc;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface NcRepository extends JpaRepository<Nc, String> {

    /** Tìm NC đang mở cùng lô kiểm (auto-aggregation §5.1), khóa dòng FOR UPDATE. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from Nc n where n.loKiem = :loKiem and n.trangThai in :openStates")
    Optional<Nc> findOpenByLoKiemForUpdate(@Param("loKiem") String loKiem,
                                           @Param("openStates") List<NcStatus> openStates);

    List<Nc> findByNhaMay(String nhaMay);

    List<Nc> findTop200ByOrderByCreatedAtDesc();

    List<Nc> findTop200ByNhaMayOrderByCreatedAtDesc(String nhaMay);

    long countByTrangThaiNot(NcStatus trangThai);

    long countByMucNghiemTrongAndTrangThaiNot(com.dqh.nccapa.common.enums.Severity muc, NcStatus trangThai);

    long countByTrangThai(NcStatus trangThai);
}
