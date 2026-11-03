package com.dqh.nccapa.pr.repository;

import com.dqh.nccapa.pr.entity.Pr;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PrRepository extends JpaRepository<Pr, Long> {
    List<Pr> findTop200ByOrderByIdDesc();
}
