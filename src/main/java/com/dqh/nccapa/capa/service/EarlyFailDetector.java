package com.dqh.nccapa.capa.service;

import com.dqh.nccapa.capa.entity.Capa;
import com.dqh.nccapa.capa.entity.CapaVerification;
import com.dqh.nccapa.capa.repository.CapaRepository;
import com.dqh.nccapa.capa.repository.CapaVerificationRepository;
import com.dqh.nccapa.common.audit.AuditService;
import com.dqh.nccapa.common.enums.CapaStatus;
import com.dqh.nccapa.common.enums.Severity;
import com.dqh.nccapa.nc.entity.Nc;
import com.dqh.nccapa.nc.repository.NcRepository;
import com.dqh.nccapa.nc.service.EarlyFailHook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * TH2 — Fail sớm (StateMachine §4.3): khi NC mới phát sinh cùng (mã hàng + mã lỗi) với NC gốc
 * của một CAPA đang Monitoring → CAPA đó Ineffective NGAY, dùng chính NC mới làm NC kế thừa
 * (KHÔNG tạo NC thứ 3), nâng NC mới lên 1 bậc so với NC gốc. Trần Nghiêm trọng → cờ mở PR.
 *
 * Gọi sau khi một NC mới vừa được tạo (thủ công hoặc OOS).
 */
@Service
public class EarlyFailDetector implements EarlyFailHook {

    private final JdbcTemplate jdbc;
    private final CapaRepository capaRepo;
    private final CapaVerificationRepository verRepo;
    private final NcRepository ncRepo;
    private final AuditService audit;

    public EarlyFailDetector(JdbcTemplate jdbc, CapaRepository capaRepo,
                             CapaVerificationRepository verRepo, NcRepository ncRepo,
                             AuditService audit) {
        this.jdbc = jdbc;
        this.capaRepo = capaRepo;
        this.verRepo = verRepo;
        this.ncRepo = ncRepo;
        this.audit = audit;
    }

    /**
     * @return true nếu đã xử lý fail sớm (NC mới trở thành NC kế thừa).
     */
    @Override
    @Transactional
    public boolean onNewNc(Nc ncMoi) {
        if (ncMoi.getMaHangChinh() == null || ncMoi.getMaLoiChinh() == null) return false;
        if (ncMoi.getNcKeThuaTuCapaId() != null) return false; // đã là NC kế thừa, bỏ qua

        // Tìm CAPA đang Monitoring mà NC gốc có cùng (mã hàng chính + mã lỗi chính), khác chính NC mới
        List<Long> capaIds = jdbc.queryForList(
            "SELECT DISTINCT nc.capa_id FROM nc_capa nc " +
            "JOIN nc n ON n.so_hieu = nc.nc_so_hieu " +
            "JOIN capa c ON c.id = nc.capa_id " +
            "WHERE c.trang_thai = 'Monitoring' " +
            "AND n.ma_hang_chinh = ? AND n.ma_loi_chinh = ? AND n.so_hieu <> ?",
            Long.class, ncMoi.getMaHangChinh(), ncMoi.getMaLoiChinh(), ncMoi.getSoHieu());

        if (capaIds.isEmpty()) return false;

        Long capaId = capaIds.get(0); // app mẫu: xử lý CAPA Monitoring đầu tiên khớp
        Capa capa = capaRepo.findById(capaId).orElse(null);
        if (capa == null || capa.getTrangThai() != CapaStatus.Monitoring) return false;

        // Mức NC gốc = mức cao nhất trong các NC gốc của CAPA (trừ NC mới)
        Severity mucGoc = mucNcGoc(capaId, ncMoi.getSoHieu());
        Severity mucMoi = mucGoc.nangMotBac();

        // NC mới trở thành NC kế thừa: nâng mức + link ngược CAPA (KHÔNG tạo NC thứ 3)
        ncMoi.setMucNghiemTrong(mucMoi);
        ncMoi.setNcKeThuaTuCapaId(capaId);
        ncMoi.setEscalated(true);
        if (mucMoi == Severity.NGHIEM_TRONG) {
            ncMoi.setFoodSafety(true);
        }
        ncRepo.save(ncMoi);

        // CAPA -> Ineffective + handover
        CapaVerification ver = verRepo.findByCapaId(capaId).orElse(null);
        if (ver != null) { ver.setKetQua("KHONG_HIEU_QUA"); verRepo.save(ver); }
        capa.setHandoverDone(true);
        capa.setTrangThai(CapaStatus.Ineffective);
        capaRepo.save(capa);

        audit.log("CAPA", String.valueOf(capaId), "FAIL_SOM_TH2",
                CapaStatus.Monitoring.name(), CapaStatus.Ineffective.name(),
                "NC kế thừa " + ncMoi.getSoHieu(), "SYSTEM");
        audit.log("NC", ncMoi.getSoHieu(), "NANG_BAC_KE_THUA", mucGoc.name(), mucMoi.name(),
                "fail sớm TH2 từ CAPA " + capaId, "SYSTEM");
        if (mucMoi == Severity.NGHIEM_TRONG) {
            audit.log("CAPA", String.valueOf(capaId), "MO_PR", null, null, "trần Nghiêm trọng (TH2)", "SYSTEM");
        }
        return true;
    }

    private Severity mucNcGoc(Long capaId, String loaiTruSoHieu) {
        List<String> ncs = jdbc.queryForList(
                "SELECT nc_so_hieu FROM nc_capa WHERE capa_id = ?", String.class, capaId);
        Severity max = Severity.NHE;
        for (String so : ncs) {
            if (so.equals(loaiTruSoHieu)) continue;
            Nc nc = ncRepo.findById(so).orElse(null);
            if (nc != null) max = Severity.nangNhat(max, nc.getMucNghiemTrong());
        }
        return max;
    }
}
