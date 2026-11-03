package com.dqh.nccapa.capa.repository;

import com.dqh.nccapa.capa.entity.Capa8dStep;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface Capa8dStepRepository extends JpaRepository<Capa8dStep, Long> {
    List<Capa8dStep> findByCapaIdOrderByBuocAsc(Long capaId);
    Optional<Capa8dStep> findByCapaIdAndBuoc(Long capaId, String buoc);
    long countByCapaIdAndHoanThanhFalse(Long capaId);
}
