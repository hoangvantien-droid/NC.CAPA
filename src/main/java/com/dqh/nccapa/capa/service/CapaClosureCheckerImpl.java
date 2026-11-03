package com.dqh.nccapa.capa.service;

import com.dqh.nccapa.capa.entity.Capa;
import com.dqh.nccapa.capa.repository.CapaRepository;
import com.dqh.nccapa.capa.repository.NcCapaLinkDao;
import com.dqh.nccapa.common.enums.CapaStatus;
import com.dqh.nccapa.nc.service.CapaClosureChecker;
import org.springframework.stereotype.Service;

/**
 * Impl thật (lát 6): guard N9 (bản vá #1). CAPA ở trạng thái kết thúc =
 * Closed, HOẶC Ineffective đã handover (đã sinh NC kế thừa / mở PR).
 * Bean này thay bean no-op mặc định (NoCapaClosureChecker dùng @ConditionalOnMissingBean).
 */
@Service
public class CapaClosureCheckerImpl implements CapaClosureChecker {

    private final NcCapaLinkDao linkDao;
    private final CapaRepository capaRepo;

    public CapaClosureCheckerImpl(NcCapaLinkDao linkDao, CapaRepository capaRepo) {
        this.linkDao = linkDao;
        this.capaRepo = capaRepo;
    }

    @Override
    public String firstUnterminatedCapa(String ncSoHieu) {
        for (Long capaId : linkDao.capaIdsOfNc(ncSoHieu)) {
            Capa capa = capaRepo.findById(capaId).orElse(null);
            if (capa == null) continue;
            boolean ketThuc = capa.getTrangThai() == CapaStatus.Closed
                    || (capa.getTrangThai() == CapaStatus.Ineffective && capa.isHandoverDone());
            if (!ketThuc) {
                return "CAPA#" + capaId + " (" + capa.getTrangThai() + ")";
            }
        }
        return null;
    }
}
