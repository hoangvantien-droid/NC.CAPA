package com.dqh.nccapa.aggregation;

import com.dqh.nccapa.aggregation.dto.AggregationResult;
import com.dqh.nccapa.aggregation.dto.OosEventRequest;
import com.dqh.nccapa.common.audit.AuditService;
import com.dqh.nccapa.common.enums.NcFlow;
import com.dqh.nccapa.common.enums.NcSource;
import com.dqh.nccapa.common.enums.NcStatus;
import com.dqh.nccapa.common.enums.Severity;
import com.dqh.nccapa.common.error.BusinessException;
import com.dqh.nccapa.masterdata.entity.MaLoi;
import com.dqh.nccapa.masterdata.service.DefectCatalogService;
import com.dqh.nccapa.nc.entity.Nc;
import com.dqh.nccapa.nc.entity.NcChiTieu;
import com.dqh.nccapa.nc.repository.NcChiTieuRepository;
import com.dqh.nccapa.nc.repository.NcRepository;
import com.dqh.nccapa.nc.service.NcService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.util.List;
import java.util.Optional;

/**
 * Auto-aggregation: OOS/sự kiện → NC (StateMachine §5.1).
 * - Mutex nc_agg_lock ở ĐẦU (vá vòng 3) tuần tự hóa theo lo_kiem.
 * - Có NC mở cùng lô → append nc_chi_tieu + nâng mức theo chỉ tiêu nặng nhất (KHÔNG tạo NC mới).
 * - Chưa có → tạo NC mới (số hiệu an toàn concurrency).
 */
@Service
public class AutoAggregationService {

    private static final List<NcStatus> OPEN_STATES = List.of(
            NcStatus.New, NcStatus.Containment, NcStatus.PendingApproval);

    private final AggLockDao aggLock;
    private final NcRepository ncRepo;
    private final NcChiTieuRepository chiTieuRepo;
    private final DefectCatalogService defectCatalog;
    private final SequenceGenerator sequence;
    private final NcService ncService;
    private final com.dqh.nccapa.nc.service.EarlyFailHook earlyFailHook;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final AuditService audit;

    public AutoAggregationService(AggLockDao aggLock, NcRepository ncRepo,
                                  NcChiTieuRepository chiTieuRepo, DefectCatalogService defectCatalog,
                                  SequenceGenerator sequence, NcService ncService,
                                  com.dqh.nccapa.nc.service.EarlyFailHook earlyFailHook,
                                  org.springframework.jdbc.core.JdbcTemplate jdbc,
                                  AuditService audit) {
        this.aggLock = aggLock;
        this.ncRepo = ncRepo;
        this.chiTieuRepo = chiTieuRepo;
        this.defectCatalog = defectCatalog;
        this.sequence = sequence;
        this.ncService = ncService;
        this.earlyFailHook = earlyFailHook;
        this.jdbc = jdbc;
        this.audit = audit;
    }

    /** READ COMMITTED (cấu hình Hikari) để luồng sau thấy NC luồng trước vừa commit. */
    @Transactional
    public AggregationResult onOos(OosEventRequest ev) {
        if (ev.loKiem == null || ev.loKiem.isBlank()) {
            throw BusinessException.businessRule("OOS phải có lô kiểm để aggregation", "loKiem");
        }
        MaLoi ml = defectCatalog.get(ev.maLoiId);

        // 1) MUTEX đầu tiên theo lô (upsert, không retry đệ quy)
        aggLock.acquire(ev.loKiem);

        // 2) Tìm NC đang mở cùng lô (khóa dòng nếu có)
        Optional<Nc> opt = ncRepo.findOpenByLoKiemForUpdate(ev.loKiem, OPEN_STATES);
        if (opt.isPresent()) {
            Nc nc = opt.get();
            chiTieuRepo.save(new NcChiTieu(nc.getSoHieu(), ev.maLoiId, ev.maHang, ev.inOutSpec, "OOS"));
            Severity moi = Severity.nangNhat(nc.getMucNghiemTrong(), ml.getMucNghiemTrongDefault());
            syncMaChinh(nc);
            if (moi.caoHon(nc.getMucNghiemTrong())) {
                ncService.applyEscalation(nc, moi, "Auto-aggregation nâng theo chỉ tiêu nặng nhất", "SYSTEM");
            } else {
                ncRepo.save(nc);
            }
            audit.log("NC", nc.getSoHieu(), "APPEND_CHI_TIEU", null, ev.maLoiId, "OOS " + ev.loKiem, "SYSTEM");
            return new AggregationResult(AggregationResult.Action.APPENDED,
                    nc.getSoHieu(), nc.getMucNghiemTrong().name());
        }

        // 3) Chưa có NC → tạo mới
        Severity severity = ml.getMucNghiemTrongDefault();
        NcFlow flow = defectCatalog.quyetDinhLuong(ml);
        int nam = Year.now().getValue();
        String soHieu = sequence.nextSoHieu(nam, ev.nhaMay);

        Nc nc = new Nc(soHieu, suyLoaiNc(flow), severity, NcSource.OOS, ev.nhaMay, "SYSTEM", "SYSTEM");
        nc.setLoKiem(ev.loKiem);
        nc.setMaLoiChinh(ev.maLoiId);
        nc.setMaHangChinh(ev.maHang);
        nc.setFoodSafety(ml.isBatBuocKhoanhVung());
        ncRepo.save(nc);
        chiTieuRepo.save(new NcChiTieu(soHieu, ev.maLoiId, ev.maHang, ev.inOutSpec, "OOS"));
        audit.log("NC", soHieu, "TAO_NC_AUTO", null, severity.name(), "OOS " + ev.loKiem, "SYSTEM");

        earlyFailHook.onNewNc(nc);   // TH2 fail sớm
        return new AggregationResult(AggregationResult.Action.CREATED, soHieu,
                ncRepo.findById(soHieu).map(x -> x.getMucNghiemTrong().name()).orElse(severity.name()));
    }

    private void syncMaChinh(Nc nc) {
        // Tìm chỉ tiêu nặng nhất trong danh sách để đồng bộ ma_hang_chinh/ma_loi_chinh
        List<NcChiTieu> cts = chiTieuRepo.findByNcSoHieu(nc.getSoHieu());
        NcChiTieu nangNhat = null;
        Severity mucNangNhat = Severity.NHE;
        for (NcChiTieu ct : cts) {
            Severity m = defectCatalog.mucDefault(ct.getMaLoiId());
            if (nangNhat == null || m.caoHon(mucNangNhat)) {
                nangNhat = ct; mucNangNhat = m;
            }
        }
        if (nangNhat != null) {
            nc.setMaLoiChinh(nangNhat.getMaLoiId());
            nc.setMaHangChinh(nangNhat.getMaHang());
        }
    }

    private String suyLoaiNc(NcFlow flow) {
        // OOS trên lô kiểm → mặc định loại THANH_PHAM cho app mẫu (có thể tinh chỉnh theo điểm lấy mẫu sau)
        return "THANH_PHAM";
    }

    /**
     * Ticket khiếu nại → NC (REQ mục 5, 7 #5).
     * Food_Safety: ÉP Nghiêm trọng + khoanh vùng, KHÔNG cho tự phân loại (bỏ qua maLoiId gợi ý về mức).
     * Quality: mức theo bộ mã lỗi (nếu có maLoiId), mặc định Nặng.
     * Liên kết nc_ticket (nhiều ticket — một NC); nếu đã có NC cùng lô mở → gắn thêm ticket + append.
     */
    @Transactional
    public AggregationResult onTicket(com.dqh.nccapa.aggregation.dto.TicketEventRequest ev) {
        boolean foodSafety = "Food_Safety".equalsIgnoreCase(ev.ticketCategory);
        String key = ev.lotNumber != null && !ev.lotNumber.isBlank() ? ev.lotNumber : null;

        // Nếu có lô + NC mở cùng lô → gắn ticket vào NC đó + append chỉ tiêu
        if (key != null) {
            aggLock.acquire(key);
            Optional<Nc> opt = ncRepo.findOpenByLoKiemForUpdate(key, OPEN_STATES);
            if (opt.isPresent()) {
                Nc nc = opt.get();
                linkTicket(nc.getSoHieu(), ev.ticketId);
                if (ev.maLoiId != null && !ev.maLoiId.isBlank()) {
                    chiTieuRepo.save(new NcChiTieu(nc.getSoHieu(), ev.maLoiId, ev.productCode, null, "TICKET"));
                }
                if (foodSafety && nc.getMucNghiemTrong() != Severity.NGHIEM_TRONG) {
                    ncService.applyEscalation(nc, Severity.NGHIEM_TRONG,
                            "Khiếu nại an toàn TP (ép Nghiêm trọng)", "SYSTEM");
                }
                audit.log("NC", nc.getSoHieu(), "GAN_TICKET", null, ev.ticketId, ev.ticketCategory, "SYSTEM");
                return new AggregationResult(AggregationResult.Action.APPENDED,
                        nc.getSoHieu(), nc.getMucNghiemTrong().name());
            }
        }

        // Tạo NC mới từ ticket
        Severity severity;
        boolean kv;
        if (foodSafety) {
            severity = Severity.NGHIEM_TRONG; kv = true;     // không cho tự phân loại
        } else if (ev.maLoiId != null && !ev.maLoiId.isBlank()) {
            var ml = defectCatalog.get(ev.maLoiId);
            severity = ml.getMucNghiemTrongDefault(); kv = ml.isBatBuocKhoanhVung();
        } else {
            severity = Severity.NANG; kv = false;
        }
        int nam = Year.now().getValue();
        String soHieu = sequence.nextSoHieu(nam, ev.nhaMay);
        Nc nc = new Nc(soHieu, "KHIEU_NAI", severity, NcSource.TICKET, ev.nhaMay, "SYSTEM", "SYSTEM");
        nc.setLoKiem(key);
        nc.setFoodSafety(kv);
        if (ev.maLoiId != null) { nc.setMaLoiChinh(ev.maLoiId); nc.setMaHangChinh(ev.productCode); }
        if (severity == Severity.NGHIEM_TRONG) nc.setTrangThai(NcStatus.Containment);
        ncRepo.save(nc);
        if (ev.maLoiId != null && !ev.maLoiId.isBlank()) {
            chiTieuRepo.save(new NcChiTieu(soHieu, ev.maLoiId, ev.productCode, null, "TICKET"));
        }
        linkTicket(soHieu, ev.ticketId);
        audit.log("NC", soHieu, "TAO_NC_TICKET", null, severity.name(), ev.ticketId, "SYSTEM");
        earlyFailHook.onNewNc(nc);
        return new AggregationResult(AggregationResult.Action.CREATED, soHieu, severity.name());
    }

    private void linkTicket(String ncSoHieu, String ticketId) {
        // idempotent qua UNIQUE(nc_so_hieu, ticket_id)
        jdbc.update("INSERT IGNORE INTO nc_ticket(nc_so_hieu, ticket_id, created_at) VALUES (?, ?, NOW())",
                ncSoHieu, ticketId);
    }
}
