package com.dqh.nccapa.common.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findByDoiTuongAndDoiTuongIdOrderByThucHienLucAsc(String doiTuong, String doiTuongId);
}
