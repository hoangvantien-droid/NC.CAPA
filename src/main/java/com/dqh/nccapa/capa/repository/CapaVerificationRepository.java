package com.dqh.nccapa.capa.repository;

import com.dqh.nccapa.capa.entity.CapaVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CapaVerificationRepository extends JpaRepository<CapaVerification, Long> {
    Optional<CapaVerification> findByCapaId(Long capaId);
}
