package com.dqh.nccapa.pr.repository;

import com.dqh.nccapa.pr.entity.Pr;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrRepository extends JpaRepository<Pr, Long> {
}
