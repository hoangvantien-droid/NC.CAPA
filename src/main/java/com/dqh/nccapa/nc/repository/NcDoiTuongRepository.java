package com.dqh.nccapa.nc.repository;

import com.dqh.nccapa.nc.entity.NcDoiTuong;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface NcDoiTuongRepository extends JpaRepository<NcDoiTuong, Long> {
    List<NcDoiTuong> findByNcSoHieu(String ncSoHieu);
}
