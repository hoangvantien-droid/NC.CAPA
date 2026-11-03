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

    public ViewController(NcRepository ncRepo, NcService ncService, AuditService audit) {
        this.ncRepo = ncRepo;
        this.ncService = ncService;
        this.audit = audit;
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
        return "nc-detail";
    }

    @GetMapping("/nc/tao")
    public String formTao() {
        return "nc-create";
    }

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
            java.util.Map.entry("verify-close", "Thẩm tra & đóng"));

    private List<HanhDong> hanhDongChoPhep(Nc nc) {
        List<String> ma = switch (nc.getTrangThai()) {
            case New -> nc.getMucNghiemTrong() == Severity.NHE
                    ? List.of("close-fast", "escalate")
                    : (nc.getMucNghiemTrong() == Severity.NGHIEM_TRONG || nc.isFoodSafety()
                        ? List.of("containment", "escalate")
                        : List.of("submit-approval", "escalate"));
            case Containment -> List.of("submit-approval");
            case PendingApproval -> List.of("approve", "reject");
            case Rejected -> List.of("resubmit");
            case Processing -> List.of("confirm-done");
            case PendingVerification -> List.of("verify-close");
            default -> List.of();
        };
        return ma.stream().map(m -> new HanhDong(m, NHAN.getOrDefault(m, m))).toList();
    }
}
