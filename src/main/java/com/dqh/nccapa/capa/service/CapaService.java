package com.dqh.nccapa.capa.service;

import com.dqh.nccapa.capa.dto.*;
import com.dqh.nccapa.capa.entity.Capa;
import com.dqh.nccapa.capa.entity.CapaAction;
import com.dqh.nccapa.capa.entity.CapaActionExtension;
import com.dqh.nccapa.capa.entity.CapaVerification;
import com.dqh.nccapa.capa.repository.CapaActionExtensionRepository;
import com.dqh.nccapa.capa.repository.CapaActionRepository;
import com.dqh.nccapa.capa.repository.CapaRepository;
import com.dqh.nccapa.capa.repository.CapaVerificationRepository;
import com.dqh.nccapa.capa.repository.NcCapaLinkDao;
import com.dqh.nccapa.common.audit.AuditService;
import com.dqh.nccapa.common.enums.ActionStatus;
import com.dqh.nccapa.common.enums.CapaStatus;
import com.dqh.nccapa.common.enums.ExtensionStatus;
import com.dqh.nccapa.common.enums.Severity;
import com.dqh.nccapa.common.error.BusinessException;
import com.dqh.nccapa.config.AppProperties;
import com.dqh.nccapa.nc.entity.Nc;
import com.dqh.nccapa.nc.repository.NcRepository;
import com.dqh.nccapa.nc.service.NcService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Nghiệp vụ CAPA — lát 5: tạo CAPA + vòng Open→Investigating→ActionPlan→Processing→Monitoring
 * + action con + gia hạn có duyệt. (2 mốc / TH1/TH2 / Ineffective ở lát 6.)
 */
@Service
public class CapaService {

    private final CapaRepository capaRepo;
    private final CapaActionRepository actionRepo;
    private final CapaActionExtensionRepository extRepo;
    private final CapaVerificationRepository verRepo;
    private final com.dqh.nccapa.capa.repository.Capa8dStepRepository stepRepo;
    private final NcCapaLinkDao linkDao;
    private final NcRepository ncRepo;
    private final NcService ncService;
    private final MesLotCounter mesLotCounter;
    private final AppProperties appProps;
    private final JdbcTemplate jdbc;
    private final AuditService audit;

    public CapaService(CapaRepository capaRepo, CapaActionRepository actionRepo,
                       CapaActionExtensionRepository extRepo, CapaVerificationRepository verRepo,
                       com.dqh.nccapa.capa.repository.Capa8dStepRepository stepRepo,
                       NcCapaLinkDao linkDao, NcRepository ncRepo, NcService ncService,
                       MesLotCounter mesLotCounter, AppProperties appProps, JdbcTemplate jdbc,
                       AuditService audit) {
        this.capaRepo = capaRepo;
        this.actionRepo = actionRepo;
        this.extRepo = extRepo;
        this.verRepo = verRepo;
        this.stepRepo = stepRepo;
        this.linkDao = linkDao;
        this.ncRepo = ncRepo;
        this.ncService = ncService;
        this.mesLotCounter = mesLotCounter;
        this.appProps = appProps;
        this.jdbc = jdbc;
        this.audit = audit;
    }

    // ---- 8D (Đợt d) ----

    /** Bật chế độ 8D cho CAPA: tạo đủ 9 bước D0..D8 (nếu chưa có). */
    @Transactional
    public List<com.dqh.nccapa.capa.entity.Capa8dStep> bat8d(Long capaId, String actor) {
        Capa capa = layCapa(capaId);
        capa.setIs8d(true);
        capaRepo.save(capa);
        if (stepRepo.findByCapaIdOrderByBuocAsc(capaId).isEmpty()) {
            for (com.dqh.nccapa.common.enums.Disciplines8D d : com.dqh.nccapa.common.enums.Disciplines8D.values()) {
                stepRepo.save(new com.dqh.nccapa.capa.entity.Capa8dStep(capaId, d.name()));
            }
        }
        audit.log("CAPA", String.valueOf(capaId), "BAT_8D", null, "D0..D8", null, actor);
        return stepRepo.findByCapaIdOrderByBuocAsc(capaId);
    }

    /** Hoàn thành một bước 8D. */
    @Transactional
    public com.dqh.nccapa.capa.entity.Capa8dStep hoanThanhBuoc8d(Long capaId, String buoc, CompleteStepRequest req) {
        var step = stepRepo.findByCapaIdAndBuoc(capaId, buoc)
                .orElseThrow(() -> BusinessException.notFound("CAPA chưa bật 8D hoặc không có bước " + buoc));
        step.hoanThanh(req.noiDung, req.actor);
        stepRepo.save(step);
        audit.log("CAPA", String.valueOf(capaId), "HOAN_THANH_8D", null, buoc, null, req.actor);
        return step;
    }

    @Transactional(readOnly = true)
    public List<com.dqh.nccapa.capa.entity.Capa8dStep> steps8d(Long capaId) {
        return stepRepo.findByCapaIdOrderByBuocAsc(capaId);
    }

    /** Tạo CAPA từ NC → set is_escalated, link nc_capa. NC KHÔNG đổi trạng thái chính (REQ 3.2). */
    @Transactional
    public Capa taoTuNc(String ncSoHieu, TaoCapaRequest req) {
        Nc nc = ncRepo.findById(ncSoHieu)
                .orElseThrow(() -> BusinessException.notFound("Không tìm thấy NC: " + ncSoHieu));
        Capa capa = new Capa(req.actor);
        capa.setNguoiDieuTra(req.actor);
        capaRepo.save(capa);
        linkDao.link(ncSoHieu, capa.getId());
        nc.setEscalated(true);
        ncRepo.save(nc);
        audit.log("CAPA", String.valueOf(capa.getId()), "TAO_CAPA", null, "Open",
                "từ NC " + ncSoHieu, req.actor);
        return capa;
    }

    /** C1: Open -> Investigating. Kế hoạch điều tra bắt buộc nếu NC gốc >= Nặng (vá #3). */
    @Transactional
    public Capa toInvestigating(Long capaId, ToInvestigatingRequest req) {
        Capa capa = layCapa(capaId);
        yeuCau(capa, CapaStatus.Open);
        boolean coKeHoach = !isBlank(req.khMucTieu) || !isBlank(req.keHoachDieuTra);
        if (mucNcGocCaoNhat(capaId).ordinalGte(Severity.NANG) && !coKeHoach) {
            throw BusinessException.businessRule(
                    "NC Nặng trở lên: kế hoạch điều tra bắt buộc (ít nhất mục tiêu)", "khMucTieu");
        }
        capa.setKeHoachDieuTra(req.keHoachDieuTra);
        capa.setKhMucTieu(req.khMucTieu);
        capa.setKhPhuongPhap(req.khPhuongPhap);
        capa.setKhNguonLuc(req.khNguonLuc);
        chuyen(capa, CapaStatus.Investigating, req.actor);
        return capa;
    }

    /** C2: Investigating -> ActionPlan. RCA + nhóm nguyên nhân + chi phí bắt buộc nếu NC >= Nặng. */
    @Transactional
    public Capa toActionPlan(Long capaId, ToActionPlanRequest req) {
        Capa capa = layCapa(capaId);
        yeuCau(capa, CapaStatus.Investigating);
        boolean nang = mucNcGocCaoNhat(capaId).ordinalGte(Severity.NANG);
        if (nang) {
            boolean coNhomNguyenNhan = !isBlank(req.nhomNguyenNhan) || req.nhomNguyenNhan6m != null;
            if (isBlank(req.rcaNoiDung) || !coNhomNguyenNhan || req.chiPhiDauTu == null) {
                throw BusinessException.businessRule(
                        "NC Nặng trở lên: cần RCA (kết luận) + nhóm nguyên nhân (6M hoặc mô tả) + chi phí đầu tư", "rcaNoiDung");
            }
        }
        capa.setRcaNoiDung(req.rcaNoiDung);
        capa.setRcaMethod(req.rcaMethod);
        capa.setRcaData(req.rcaData);
        capa.setNhomNguyenNhan(req.nhomNguyenNhan);
        capa.setNhomNguyenNhan6m(req.nhomNguyenNhan6m);
        if (req.taiDien != null) capa.setTaiDien(req.taiDien);
        capa.setChiPhiDauTu(req.chiPhiDauTu);
        if (req.rpnS != null && req.rpnO != null && req.rpnD != null) {
            validateRpn(req.rpnS); validateRpn(req.rpnO); validateRpn(req.rpnD);
            capa.setRpn(req.rpnS, req.rpnO, req.rpnD);
        }
        chuyen(capa, CapaStatus.ActionPlan, req.actor);
        return capa;
    }

    /** Thêm action con (chỉ khi ActionPlan). */
    @Transactional
    public CapaAction themAction(Long capaId, AddActionRequest req) {
        Capa capa = layCapa(capaId);
        if (capa.getTrangThai() != CapaStatus.ActionPlan && capa.getTrangThai() != CapaStatus.Processing) {
            throw BusinessException.guard("Chỉ thêm action ở ActionPlan/Processing");
        }
        CapaAction action = new CapaAction(capaId, req.loai, req.moTa, req.nguoiPhuTrach, req.deadline);
        action.setLoaiHanhDong(req.loaiHanhDong != null
                ? req.loaiHanhDong
                : com.dqh.nccapa.common.enums.ActionCategory.CORRECTIVE);
        CapaAction a = actionRepo.save(action);
        audit.log("ACTION", String.valueOf(a.getId()), "THEM_ACTION", null, req.loai.name(),
                "capa " + capaId + " · " + a.getLoaiHanhDong().name(), req.actor);
        return a;
    }

    /**
     * C3: ActionPlan -> Processing. ≥1 action; NC >= Nặng cần đủ 2 ô (tức thời + ngăn tái diễn).
     * Callback mốc sớm (mô phỏng log).
     */
    @Transactional
    public Capa toProcessing(Long capaId, ToProcessingRequest req) {
        Capa capa = layCapa(capaId);
        yeuCau(capa, CapaStatus.ActionPlan);
        List<CapaAction> actions = actionRepo.findByCapaId(capaId);
        if (actions.isEmpty()) {
            throw BusinessException.businessRule("Cần ít nhất một action con", "actions");
        }
        capa.setKhacPhucTucThoi(req.khacPhucTucThoi);
        capa.setNganTaiDien(req.nganTaiDien);
        if (mucNcGocCaoNhat(capaId).ordinalGte(Severity.NANG)
                && (isBlank(req.khacPhucTucThoi) || isBlank(req.nganTaiDien))) {
            throw BusinessException.businessRule(
                    "NC Nặng trở lên: cần cả 2 ô khắc phục tức thời + ngăn tái diễn", "nganTaiDien");
        }
        chuyen(capa, CapaStatus.Processing, req.actor);
        audit.log("CAPA", String.valueOf(capaId), "CALLBACK_MOC_SOM", null, null,
                "mô phỏng: đẩy mốc sớm về ticket", req.actor);
        return capa;
    }

    /** Đánh dấu action Completed. */
    @Transactional
    public CapaAction completeAction(Long actionId, CompleteActionRequest req) {
        CapaAction a = actionRepo.findById(actionId)
                .orElseThrow(() -> BusinessException.notFound("Không tìm thấy action: " + actionId));
        a.setBangChung(req.bangChung);
        a.setTrangThai(ActionStatus.Completed);
        actionRepo.save(a);
        audit.log("ACTION", String.valueOf(actionId), "COMPLETE_ACTION", null, "Completed", null, req.actor);
        return a;
    }

    /** Yêu cầu gia hạn action (PENDING, chờ duyệt). */
    @Transactional
    public CapaActionExtension yeuCauGiaHan(Long actionId, ExtensionRequest req) {
        actionRepo.findById(actionId)
                .orElseThrow(() -> BusinessException.notFound("Không tìm thấy action: " + actionId));
        CapaActionExtension ext = extRepo.save(
                new CapaActionExtension(actionId, req.deadlineMoi, req.lyDo, req.actor));
        audit.log("ACTION", String.valueOf(actionId), "YEU_CAU_GIA_HAN", null,
                req.deadlineMoi.toString(), req.lyDo, req.actor);
        return ext;
    }

    /** Duyệt gia hạn → đổi deadline action (chỉ khi được duyệt mới đổi — AC10). */
    @Transactional
    public CapaActionExtension duyetGiaHan(Long extId, ApproveExtensionRequest req) {
        CapaActionExtension ext = extRepo.findById(extId)
                .orElseThrow(() -> BusinessException.notFound("Không tìm thấy yêu cầu gia hạn: " + extId));
        if (ext.getTrangThai() != ExtensionStatus.PENDING) {
            throw BusinessException.guard("Yêu cầu gia hạn không ở trạng thái PENDING");
        }
        if (req.actor.equals(ext.getNguoiYeuCau())) {
            throw BusinessException.guard("Người duyệt gia hạn phải khác người yêu cầu");
        }
        CapaAction a = actionRepo.findById(ext.getActionId())
                .orElseThrow(() -> BusinessException.notFound("Không tìm thấy action"));
        ext.setTrangThai(ExtensionStatus.APPROVED);
        ext.setNguoiDuyet(req.actor);
        extRepo.save(ext);
        a.setDeadline(ext.getDeadlineMoi());   // chỉ đổi deadline SAU khi duyệt
        actionRepo.save(a);
        audit.log("ACTION", String.valueOf(a.getId()), "DOI_HAN", null,
                ext.getDeadlineMoi().toString(), "duyệt gia hạn", req.actor);
        return ext;
    }

    /** C4: Processing -> Monitoring. Mọi action Completed; đặt 2 mốc; set monitoring_bat_dau. */
    @Transactional
    public Capa toMonitoring(Long capaId, ToMonitoringRequest req) {
        Capa capa = layCapa(capaId);
        yeuCau(capa, CapaStatus.Processing);
        long chuaXong = actionRepo.countByCapaIdAndTrangThaiNot(capaId, ActionStatus.Completed);
        if (chuaXong > 0) {
            throw BusinessException.businessRule("Còn action chưa Completed: " + chuaXong, "actions");
        }
        capa.setXNgay(req.xNgay);
        capa.setNLo(req.nLo);
        LocalDateTime batDau = LocalDateTime.now();
        capa.setMonitoringBatDau(batDau);
        // tạo bản ghi verification với mốc hẹn X ngày đầu tiên
        CapaVerification ver = new CapaVerification(capaId);
        ver.setHanKiemChungTiepTheo(batDau.plusDays(req.xNgay));
        verRepo.save(ver);
        chuyen(capa, CapaStatus.Monitoring, req.actor);
        return capa;
    }

    // ---- Kiểm chứng 2 mốc (lát 6) ----

    @Transactional(readOnly = true)
    public VerificationStatus verificationStatus(Long capaId) {
        Capa capa = layCapa(capaId);
        yeuCau(capa, CapaStatus.Monitoring);
        CapaVerification ver = layVer(capaId);
        boolean xDat = xDat(capa, ver);
        boolean nDat = nDat(capa, ver);
        return new VerificationStatus(xDat, nDat, ver.getSoLanDoiHan());
    }

    /** X đạt: now >= mốc hẹn (COALESCE han_kiem_chung_tiep_theo, monitoring_bat_dau + x_ngay) — vá 2.4. */
    private boolean xDat(Capa capa, CapaVerification ver) {
        LocalDateTime mocX = ver.getHanKiemChungTiepTheo() != null
                ? ver.getHanKiemChungTiepTheo()
                : capa.getMonitoringBatDau().plusDays(capa.getXNgay());
        return !LocalDateTime.now().isBefore(mocX);
    }

    /** N đạt: n_lo<=0 → cần biên bản; ngược lại mọi mã hàng đạt n_lo từ MES. */
    private boolean nDat(Capa capa, CapaVerification ver) {
        int nLo = capa.getNLo() == null ? 0 : capa.getNLo();
        if (nLo <= 0) {
            return ver.getBienBanKiemTra() != null && !ver.getBienBanKiemTra().isBlank();
        }
        List<String> maHangs = linkDao.maHangsOfCapa(capaId(capa));
        return mesLotCounter.allReached(maHangs, nLo, capa.getMonitoringBatDau());
    }

    private Long capaId(Capa capa) { return capa.getId(); }

    /** Dời hạn mốc chưa đạt (chỉ đẩy mốc X hẹn kế tiếp); tối đa M lần → tự Ineffective. */
    @Transactional
    public Capa postponeReminder(Long capaId, PostponeRequest req) {
        Capa capa = layCapa(capaId);
        yeuCau(capa, CapaStatus.Monitoring);
        CapaVerification ver = layVer(capaId);
        if (ver.getSoLanDoiHan() >= appProps.getCapa().getReminder().getMaxPostpone()) {
            // quá M lần → tự đánh Ineffective (commit side-effect, KHÔNG ném để tránh rollback)
            markIneffectiveInternal(capa, "Quá số lần dời hạn cho phép (M="
                    + appProps.getCapa().getReminder().getMaxPostpone() + ")", "SYSTEM");
            return capa; // trạng thái đã là Ineffective
        }
        LocalDateTime goc = ver.getHanKiemChungTiepTheo() != null
                ? ver.getHanKiemChungTiepTheo()
                : capa.getMonitoringBatDau().plusDays(capa.getXNgay());
        ver.setHanKiemChungTiepTheo(goc.plusDays(req.soNgayDoiThem));
        ver.setSoLanDoiHan(ver.getSoLanDoiHan() + 1);
        verRepo.save(ver);
        audit.log("CAPA", String.valueOf(capaId), "DOI_HAN_MOC", null,
                String.valueOf(ver.getSoLanDoiHan()), req.lyDo, req.actor);
        return capa;
    }

    /** C5: Monitoring -> Closed. Chỉ khi đủ cả 2 mốc (hoặc N=0 + biên bản). */
    @Transactional
    public Capa confirmEffective(Long capaId, ConfirmEffectiveRequest req) {
        Capa capa = layCapa(capaId);
        yeuCau(capa, CapaStatus.Monitoring);
        CapaVerification ver = layVer(capaId);
        int nLo = capa.getNLo() == null ? 0 : capa.getNLo();
        if (nLo <= 0 && req.bienBanKiemTra != null && !req.bienBanKiemTra.isBlank()) {
            ver.setBienBanKiemTra(req.bienBanKiemTra);
            verRepo.save(ver);
        }
        if (!xDat(capa, ver)) {
            throw BusinessException.guard("Chưa đủ mốc thời gian (X ngày)");
        }
        if (!nDat(capa, ver)) {
            throw BusinessException.guard(nLo <= 0
                    ? "Nhóm N=0: cần biên bản kiểm tra thực tế"
                    : "Chưa đủ số lô MES cho mọi mã hàng");
        }
        ver.setKetQua("HIEU_QUA");
        ver.setXacNhanBoi(req.actor);
        ver.setXacNhanLuc(LocalDateTime.now());
        verRepo.save(ver);
        chuyen(capa, CapaStatus.Closed, req.actor);
        return capa;
    }

    /** C6 (TH1): Monitoring -> Ineffective (QA đánh giá fail tại mốc). Sinh NC kế thừa + handover. */
    @Transactional
    public IneffectiveResult markIneffective(Long capaId, MarkIneffectiveRequest req) {
        Capa capa = layCapa(capaId);
        yeuCau(capa, CapaStatus.Monitoring);
        return markIneffectiveInternal(capa, req.lyDo, req.actor);
    }

    private IneffectiveResult markIneffectiveInternal(Capa capa, String lyDo, String actor) {
        CapaVerification ver = layVer(capa.getId());
        ver.setKetQua("KHONG_HIEU_QUA");
        verRepo.save(ver);

        // NC gốc = NC đầu tiên trong link (app mẫu: 1 NC/CAPA điển hình)
        List<String> ncs = linkDao.ncSoHieuOfCapa(capa.getId());
        Nc ncGoc = ncs.isEmpty() ? null : ncRepo.findById(ncs.get(0)).orElse(null);

        boolean failLan2 = laFailLan2(capa.getId());

        String ncKeThuaSo = null;
        String ncKeThuaMuc = null;
        boolean moPr = failLan2;
        if (ncGoc != null) {
            Nc ncMoi = ncService.taoNcKeThua(ncGoc, capa.getId());
            ncKeThuaSo = ncMoi.getSoHieu();
            ncKeThuaMuc = ncMoi.getMucNghiemTrong().name();
            // trần Nghiêm trọng → mở PR ngay
            if (ncMoi.getMucNghiemTrong() == Severity.NGHIEM_TRONG) moPr = true;
        }

        capa.setHandoverDone(true);   // vá #1: cho phép đóng NC gốc
        capa.setTrangThai(CapaStatus.Ineffective);
        capaRepo.save(capa);
        audit.log("CAPA", String.valueOf(capa.getId()), "CHUYEN_TRANG_THAI",
                CapaStatus.Monitoring.name(), CapaStatus.Ineffective.name(), lyDo, actor);
        if (moPr) {
            audit.log("CAPA", String.valueOf(capa.getId()), "MO_PR", null, null,
                    failLan2 ? "fail lần 2" : "trần Nghiêm trọng", actor);
        }
        return new IneffectiveResult(capa.getId(), ncKeThuaSo, ncKeThuaMuc, moPr, failLan2);
    }

    /** Fail lần 2 (vá 4.3): NC gốc của CAPA này có nc_ke_thua_tu_capa_id != null → đây là lần 2. */
    private boolean laFailLan2(Long capaId) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM nc_capa nc JOIN nc n ON n.so_hieu = nc.nc_so_hieu " +
                "WHERE nc.capa_id = ? AND n.nc_ke_thua_tu_capa_id IS NOT NULL",
                Integer.class, capaId);
        return n != null && n > 0;
    }

    private CapaVerification layVer(Long capaId) {
        return verRepo.findByCapaId(capaId)
                .orElseThrow(() -> BusinessException.businessRule(
                        "CAPA chưa vào Monitoring (chưa có bản ghi kiểm chứng)", null));
    }

    // ---- helpers ----

    @Transactional(readOnly = true)
    public Capa layCapa(Long id) {
        return capaRepo.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Không tìm thấy CAPA: " + id));
    }

    @Transactional(readOnly = true)
    public List<CapaAction> actionsCua(Long capaId) {
        return actionRepo.findByCapaId(capaId);
    }

    /** Mức cao nhất trong các NC gốc của CAPA (để áp guard theo mức). */
    Severity mucNcGocCaoNhat(Long capaId) {
        Severity max = Severity.NHE;
        for (String so : linkDao.ncSoHieuOfCapa(capaId)) {
            Nc nc = ncRepo.findById(so).orElse(null);
            if (nc != null) max = Severity.nangNhat(max, nc.getMucNghiemTrong());
        }
        return max;
    }

    private void yeuCau(Capa capa, CapaStatus expected) {
        if (capa.getTrangThai() != expected) {
            throw BusinessException.guard("Transition không hợp lệ: cần " + expected
                    + ", hiện tại " + capa.getTrangThai());
        }
    }

    private void chuyen(Capa capa, CapaStatus den, String actor) {
        CapaStatus tu = capa.getTrangThai();
        capa.setTrangThai(den);
        capaRepo.save(capa);
        audit.logChuyenTrangThai("CAPA", String.valueOf(capa.getId()), tu.name(), den.name(), actor);
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }

    private static void validateRpn(int v) {
        if (v < 1 || v > 5) throw BusinessException.businessRule("RPN mỗi yếu tố thang 1–5", "rpn");
    }
}
