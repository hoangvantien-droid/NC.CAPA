package com.dqh.nccapa.web;

import com.dqh.nccapa.common.audit.AuditService;
import com.dqh.nccapa.common.enums.NcStatus;
import com.dqh.nccapa.common.enums.Severity;
import com.dqh.nccapa.nc.entity.Nc;
import com.dqh.nccapa.nc.repository.NcRepository;
import com.dqh.nccapa.nc.service.NcService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

/**
 * Giao diện Thymeleaf — vài màn chính (danh sách, chi tiết, tạo NC).
 * Chỉ đọc/điều hướng; mọi thao tác chuyển trạng thái gọi REST API hiện có.
 */
@Controller
public class ViewController {

    private final NcRepository ncRepo;
    private final NcService ncService;
    private final AuditService audit;
    private final com.dqh.nccapa.report.ReportService reportService;
    private final com.dqh.nccapa.capa.service.CapaService capaService;
    private final com.dqh.nccapa.capa.repository.NcCapaLinkDao ncCapaLink;
    private final com.dqh.nccapa.capa.repository.CapaVerificationRepository verRepo;
    private final com.dqh.nccapa.pr.service.PrService prService;
    private final com.dqh.nccapa.pr.repository.PrRepository prRepo;
    private final com.dqh.nccapa.pr.repository.PrLinkDao prLink;
    private final com.dqh.nccapa.ticket.repository.TicketCallbackOutboxRepository outboxRepo;

    public ViewController(NcRepository ncRepo, NcService ncService, AuditService audit,
                          com.dqh.nccapa.report.ReportService reportService,
                          com.dqh.nccapa.capa.service.CapaService capaService,
                          com.dqh.nccapa.capa.repository.NcCapaLinkDao ncCapaLink,
                          com.dqh.nccapa.capa.repository.CapaVerificationRepository verRepo,
                          com.dqh.nccapa.pr.service.PrService prService,
                          com.dqh.nccapa.pr.repository.PrRepository prRepo,
                          com.dqh.nccapa.pr.repository.PrLinkDao prLink,
                          com.dqh.nccapa.ticket.repository.TicketCallbackOutboxRepository outboxRepo) {
        this.ncRepo = ncRepo;
        this.ncService = ncService;
        this.audit = audit;
        this.reportService = reportService;
        this.capaService = capaService;
        this.ncCapaLink = ncCapaLink;
        this.verRepo = verRepo;
        this.prService = prService;
        this.prRepo = prRepo;
        this.prLink = prLink;
        this.outboxRepo = outboxRepo;
    }

    @GetMapping("/dashboard")
    public String dashboard(@org.springframework.web.bind.annotation.RequestParam(value = "nhaMay", required = false) String nhaMay,
                            Model model) {
        model.addAttribute("d", reportService.dashboard(nhaMay));
        model.addAttribute("nhaMay", nhaMay);
        return "dashboard";
    }

    @GetMapping("/")
    public String danhSach(Model model) {
        List<Nc> dsNc = ncRepo.findTop200ByOrderByCreatedAtDesc();
        model.addAttribute("dsNc", dsNc);
        model.addAttribute("dangMo", dsNc.stream()
                .filter(n -> n.getTrangThai() != NcStatus.Closed && n.getTrangThai() != NcStatus.Cancelled)
                .count());
        model.addAttribute("nghiemTrong", dsNc.stream()
                .filter(n -> n.getMucNghiemTrong() == Severity.NGHIEM_TRONG
                        && n.getTrangThai() != NcStatus.Closed && n.getTrangThai() != NcStatus.Cancelled)
                .count());
        model.addAttribute("khoanhVung", dsNc.stream()
                .filter(n -> n.getTrangThai() == NcStatus.Containment)
                .count());
        return "nc-list";
    }

    @GetMapping("/nc/{so}")
    public String chiTiet(@PathVariable("so") String so, Model model) {
        Nc nc = ncService.layNc(so);
        model.addAttribute("nc", nc);
        model.addAttribute("chiTieu", ncService.chiTieuCua(so));
        model.addAttribute("doiTuong", ncService.doiTuongCua(so));
        model.addAttribute("audit", audit.timTheo("NC", so));
        model.addAttribute("hanhDong", hanhDongChoPhep(nc));
        // CAPA liên kết
        List<com.dqh.nccapa.capa.entity.Capa> capas = ncCapaLink.capaIdsOfNc(so).stream()
                .map(capaService::layCapa).toList();
        model.addAttribute("capas", capas);
        return "nc-detail";
    }

    @GetMapping("/nc/tao")
    public String formTao() {
        return "nc-create";
    }

    @GetMapping("/capa/{id}")
    public String capaDetail(@PathVariable("id") Long id, Model model) {
        var capa = capaService.layCapa(id);
        model.addAttribute("capa", capa);
        model.addAttribute("actions", capaService.actionsCua(id));
        model.addAttribute("audit", audit.timTheo("CAPA", String.valueOf(id)));
        model.addAttribute("ncs", ncCapaLink.ncSoHieuOfCapa(id));
        model.addAttribute("hanhDong", hanhDongCapa(capa));
        model.addAttribute("steps8d", capa.getTrangThai() != null ? capaService.steps8d(id) : java.util.List.of());
        return "capa-detail";
    }

    private List<HanhDong> hanhDongCapa(com.dqh.nccapa.capa.entity.Capa capa) {
        List<String> ma = switch (capa.getTrangThai()) {
            case Open -> List.of("to-investigating");
            case Investigating -> List.of("to-action-plan");
            case ActionPlan -> List.of("add-action", "to-processing");
            case Processing -> List.of("to-monitoring");
            case Monitoring -> List.of("confirm-effective", "postpone-reminder", "mark-ineffective");
            default -> List.of();
        };
        return ma.stream().map(m -> new HanhDong(m, NHAN_CAPA.getOrDefault(m, m))).toList();
    }

    // ---- PR ----

    @GetMapping("/pr")
    public String prList(Model model) {
        model.addAttribute("dsPr", prRepo.findTop200ByOrderByIdDesc());
        return "pr-list";
    }

    @GetMapping("/pr/{id}")
    public String prDetail(@PathVariable("id") Long id, Model model) {
        var pr = prService.lay(id);
        model.addAttribute("pr", pr);
        model.addAttribute("chuoi", pr.getLoaiThayDoi().chuoiDuyet());
        model.addAttribute("lichSu", prService.lichSuDuyet(id));
        model.addAttribute("ncs", prLink.ncsOfPr(id));
        model.addAttribute("audit", audit.timTheo("PR", String.valueOf(id)));
        model.addAttribute("hanhDong", hanhDongPr(pr));
        // vai trò cần duyệt ở cấp hiện tại (nếu đang PendingApproval)
        String vaiTroCanDuyet = null;
        if (pr.getTrangThai().name().equals("PendingApproval")
                && pr.getCapHienTai() < pr.getLoaiThayDoi().chuoiDuyet().size()) {
            vaiTroCanDuyet = pr.getLoaiThayDoi().chuoiDuyet().get(pr.getCapHienTai());
        }
        model.addAttribute("vaiTroCanDuyet", vaiTroCanDuyet);
        return "pr-detail";
    }

    private List<HanhDong> hanhDongPr(com.dqh.nccapa.pr.entity.Pr pr) {
        List<String> ma = switch (pr.getTrangThai()) {
            case Draft -> List.of("to-analyzing");
            case Analyzing -> List.of("submit");
            case PendingApproval -> List.of("approve", "reject");
            case Rejected -> List.of("to-analyzing");
            case Approved -> List.of("implement");
            case Implementing -> List.of("close");
            default -> List.of();
        };
        return ma.stream().map(m -> new HanhDong(m, NHAN_PR.getOrDefault(m, m))).toList();
    }

    private static final java.util.Map<String, String> NHAN_PR = java.util.Map.ofEntries(
            java.util.Map.entry("to-analyzing", "Phân tích"),
            java.util.Map.entry("submit", "Trình duyệt"),
            java.util.Map.entry("approve", "Duyệt cấp này"),
            java.util.Map.entry("reject", "Từ chối"),
            java.util.Map.entry("implement", "Bắt đầu thực hiện"),
            java.util.Map.entry("close", "Đóng PR"));

    // ---- Ticket outbox ----

    @GetMapping("/ticket")
    public String ticketOutbox(Model model) {
        model.addAttribute("rows", outboxRepo.findAll());
        return "ticket-outbox";
    }

    private static final java.util.Map<String, String> NHAN_CAPA = java.util.Map.ofEntries(
            java.util.Map.entry("to-investigating", "Bắt đầu điều tra"),
            java.util.Map.entry("to-action-plan", "Chuyển kế hoạch hành động"),
            java.util.Map.entry("add-action", "Thêm action"),
            java.util.Map.entry("to-processing", "Bắt đầu thực hiện"),
            java.util.Map.entry("to-monitoring", "Chuyển theo dõi"),
            java.util.Map.entry("confirm-effective", "Xác nhận hiệu quả"),
            java.util.Map.entry("postpone-reminder", "Dời hạn kiểm chứng"),
            java.util.Map.entry("mark-ineffective", "Đánh không hiệu quả"));

    /** Nút hành động (mã + nhãn tiếng Việt) theo trạng thái; guard thật ở service. */
    public record HanhDong(String ma, String nhan) { }

    private static final java.util.Map<String, String> NHAN = java.util.Map.ofEntries(
            java.util.Map.entry("close-fast", "Đóng nhanh"),
            java.util.Map.entry("escalate", "Nâng mức"),
            java.util.Map.entry("containment", "Khoanh vùng"),
            java.util.Map.entry("submit-approval", "Trình duyệt"),
            java.util.Map.entry("approve", "Duyệt"),
            java.util.Map.entry("reject", "Từ chối"),
            java.util.Map.entry("resubmit", "Nộp lại"),
            java.util.Map.entry("confirm-done", "Xác nhận đã xử lý"),
            java.util.Map.entry("verify-close", "Thẩm tra & đóng"),
            java.util.Map.entry("cancel", "Hủy phiếu"),
            java.util.Map.entry("capa", "Mở CAPA"));

    private List<HanhDong> hanhDongChoPhep(Nc nc) {
        List<String> ma = switch (nc.getTrangThai()) {
            case New -> nc.getMucNghiemTrong() == Severity.NHE
                    ? List.of("close-fast", "escalate", "cancel")
                    : (nc.getMucNghiemTrong() == Severity.NGHIEM_TRONG || nc.isFoodSafety()
                        ? List.of("containment", "escalate", "cancel")
                        : List.of("submit-approval", "escalate", "cancel"));
            case Containment -> List.of("submit-approval", "cancel");
            case PendingApproval -> List.of("approve", "reject", "cancel");
            case Rejected -> List.of("resubmit");
            case Processing -> List.of("confirm-done", "capa");
            case PendingVerification -> List.of("verify-close", "capa");
            default -> List.of();
        };
        return ma.stream().map(m -> new HanhDong(m, NHAN.getOrDefault(m, m))).toList();
    }
}
