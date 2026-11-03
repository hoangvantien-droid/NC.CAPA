package com.dqh.nccapa.capa.repository;

import com.dqh.nccapa.capa.entity.CapaAction;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CapaActionRepository extends JpaRepository<CapaAction, Long> {
    List<CapaAction> findByCapaId(Long capaId);
    long countByCapaIdAndTrangThaiNot(Long capaId, com.dqh.nccapa.common.enums.ActionStatus trangThai);
}
