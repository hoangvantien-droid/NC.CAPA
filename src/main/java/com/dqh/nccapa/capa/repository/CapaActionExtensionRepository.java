package com.dqh.nccapa.capa.repository;

import com.dqh.nccapa.capa.entity.CapaActionExtension;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CapaActionExtensionRepository extends JpaRepository<CapaActionExtension, Long> {
    java.util.List<CapaActionExtension> findByActionIdInOrderByIdAsc(java.util.Collection<Long> actionIds);
}
