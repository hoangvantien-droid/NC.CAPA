package com.dqh.nccapa.nc.repository;

import com.dqh.nccapa.nc.entity.NcChiTieu;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface NcChiTieuRepository extends JpaRepository<NcChiTieu, Long> {
    List<NcChiTieu> findByNcSoHieu(String ncSoHieu);
}
