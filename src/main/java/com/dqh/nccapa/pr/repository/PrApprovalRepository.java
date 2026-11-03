package com.dqh.nccapa.pr.repository;

import com.dqh.nccapa.pr.entity.PrApproval;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PrApprovalRepository extends JpaRepository<PrApproval, Long> {
    List<PrApproval> findByPrIdOrderByDuyetLucAsc(Long prId);
}
