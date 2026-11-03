package com.dqh.nccapa.pr.service;

import com.dqh.nccapa.common.audit.AuditService;
import com.dqh.nccapa.common.enums.LoaiThayDoi;
import com.dqh.nccapa.common.enums.PrStatus;
import com.dqh.nccapa.common.error.BusinessException;
import com.dqh.nccapa.common.error.ErrorCode;
import com.dqh.nccapa.common.security.RoleChecker;
import com.dqh.nccapa.pr.dto.PrActionRequest;
import com.dqh.nccapa.pr.dto.TaoPrRequest;
import com.dqh.nccapa.pr.entity.Pr;
import com.dqh.nccapa.pr.entity.PrApproval;
import com.dqh.nccapa.pr.repository.PrApprovalRepository;
import com.dqh.nccapa.pr.repository.PrLinkDao;
import com.dqh.nccapa.pr.repository.PrRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * PR (REQ mục 4.3, Đợt d). Máy trạng thái nhiều cấp + ma trận định tuyến theo loại thay đổi.
 * Ràng buộc: mỗi (mã hàng + mã lỗi) tối đa 1 PR đang mở (quy tắc #13).
 */
@Service
public class PrService {

    private final PrRepository prRepo;
    private final PrApprovalRepository approvalRepo;
    private final PrLinkDao linkDao;
    private final AuditService audit;
    private final RoleChecker roleChecker;

    public PrService(PrRepository prRepo, PrApprovalRepository approvalRepo,
                     PrLinkDao linkDao, AuditService audit, RoleChecker roleChecker) {
        this.prRepo = prRepo;
        this.approvalRepo = approvalRepo;
        this.linkDao = linkDao;
        this.audit = audit;
        this.roleChecker = roleChecker;
    }

    @Transactional
    public Pr tao(TaoPrRequest req) {
        // Chống mở trùng PR cho cùng vấn đề
        Long dangMo = linkDao.prDangMoChoVanDe(req.maHang, req.maLoiId, null);
        if (dangMo != null) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE,
                    "Đã có PR đang mở cho vấn đề này (PR#" + dangMo + ")", "maLoiId");
        }
        Pr pr = new Pr(req.loaiThayDoi, req.maHang, req.maLoiId, req.actor);
        prRepo.save(pr);
        if (req.ncSoHieu != null) {
            for (String nc : req.ncSoHieu) linkDao.linkNc(pr.getId(), nc);
        }
        audit.log("PR", String.valueOf(pr.getId()), "TAO_PR", null, req.loaiThayDoi.name(), null, req.actor);
        return pr;
    }

    /**
     * Tự động mở PR cho một vấn đề (mã hàng + mã lỗi) khi CAPA fail lần 2 / trần Nghiêm trọng
     * (REQ mục 4.2, 5, quy tắc #13). IDEMPOTENT: nếu đã có PR đang mở cho vấn đề → KHÔNG tạo PR
     * thứ 2, trả về PR đang mở đó. Dùng chung ràng buộc chống trùng {@link PrLinkDao#prDangMoChoVanDe}
     * với luồng tạo PR thủ công ({@link #tao}).
     *
     * @return PR vừa tạo, hoặc PR đang mở sẵn (không null nếu maHang/maLoiId hợp lệ), hoặc null
     *         nếu không đủ thông tin vấn đề để mở PR.
     */
    @Transactional
    public Pr moTuDong(LoaiThayDoi loaiThayDoi, String maHang, String maLoiId,
                       String ncSoHieu, Long capaId, String actor) {
        if (maHang == null || maLoiId == null) return null;   // không đủ thông tin vấn đề
        Long dangMo = linkDao.prDangMoChoVanDe(maHang, maLoiId, null);
        if (dangMo != null) {
            // Đã có PR đang mở cho vấn đề này → chống trùng: không tạo PR thứ 2.
            if (ncSoHieu != null) linkDao.linkNc(dangMo, ncSoHieu);
            if (capaId != null) linkDao.linkCapa(dangMo, capaId);
            return lay(dangMo);
        }
        Pr pr = new Pr(loaiThayDoi, maHang, maLoiId, actor);
        prRepo.save(pr);
        if (ncSoHieu != null) linkDao.linkNc(pr.getId(), ncSoHieu);
        if (capaId != null) linkDao.linkCapa(pr.getId(), capaId);
        audit.log("PR", String.valueOf(pr.getId()), "TAO_PR_TU_DONG", null, loaiThayDoi.name(),
                "tự động mở từ CAPA " + capaId, actor);
        return pr;
    }

    @Transactional
    public Pr toAnalyzing(Long id, PrActionRequest req) {
        Pr pr = lay(id);
        if (pr.getTrangThai() != PrStatus.Draft && pr.getTrangThai() != PrStatus.Rejected) {
            throw BusinessException.guard("toAnalyzing chỉ từ Draft/Rejected");
        }
        chuyen(pr, PrStatus.Analyzing, req.actor);
        return pr;
    }

    @Transactional
    public Pr submit(Long id, PrActionRequest req) {
        Pr pr = lay(id);
        if (pr.getTrangThai() != PrStatus.Analyzing) {
            throw BusinessException.guard("submit chỉ từ Analyzing");
        }
        pr.setCapHienTai(0);
        chuyen(pr, PrStatus.PendingApproval, req.actor);
        return pr;
    }

    /** Duyệt cấp hiện tại; đúng vai trò theo chuỗi; hết cấp → Approved. */
    @Transactional
    public Pr approve(Long id, PrActionRequest req) {
        Pr pr = lay(id);
        if (pr.getTrangThai() != PrStatus.PendingApproval) {
            throw BusinessException.guard("approve chỉ từ PendingApproval");
        }
        List<String> chuoi = pr.getLoaiThayDoi().chuoiDuyet();
        int cap = pr.getCapHienTai();
        String vaiTroCanDuyet = chuoi.get(cap);
        if (req.vaiTro == null || !req.vaiTro.equals(vaiTroCanDuyet)) {
            throw new BusinessException(ErrorCode.ROLE_DENIED,
                    "Cấp " + (cap + 1) + " cần vai trò: " + vaiTroCanDuyet);
        }
        // Siết SoD THEO ROLE THẬT: ngoài việc vaiTro khai phải khớp chuỗi duyệt, actor PHẢI thực sự
        // được gán role đó trong app_user_role (không tin vai trò do client tự khai).
        if (!roleChecker.coRole(req.actor, vaiTroCanDuyet)) {
            throw new BusinessException(ErrorCode.ROLE_DENIED,
                    "Người duyệt không có vai trò thực: " + vaiTroCanDuyet);
        }
        approvalRepo.save(new PrApproval(id, cap, vaiTroCanDuyet, "APPROVED", req.actor, null));
        audit.log("PR", String.valueOf(id), "DUYET_CAP", String.valueOf(cap), vaiTroCanDuyet, null, req.actor);

        if (cap + 1 < chuoi.size()) {
            pr.setCapHienTai(cap + 1);
            prRepo.save(pr);   // vẫn PendingApproval, sang cấp kế
        } else {
            chuyen(pr, PrStatus.Approved, req.actor);  // cấp cuối duyệt
        }
        return pr;
    }

    @Transactional
    public Pr reject(Long id, PrActionRequest req) {
        Pr pr = lay(id);
        if (pr.getTrangThai() != PrStatus.PendingApproval) {
            throw BusinessException.guard("reject chỉ từ PendingApproval");
        }
        if (req.lyDo == null || req.lyDo.isBlank()) {
            throw BusinessException.businessRule("Từ chối cần lý do", "lyDo");
        }
        String vaiTro = pr.getLoaiThayDoi().chuoiDuyet().get(pr.getCapHienTai());
        approvalRepo.save(new PrApproval(id, pr.getCapHienTai(), vaiTro, "REJECTED", req.actor, req.lyDo));
        pr.setLyDoTuChoi(req.lyDo);
        pr.setCapHienTai(0);   // reset chuỗi duyệt
        chuyen(pr, PrStatus.Rejected, req.actor);
        return pr;
    }

    @Transactional
    public Pr implement(Long id, PrActionRequest req) {
        Pr pr = lay(id);
        if (pr.getTrangThai() != PrStatus.Approved) {
            throw BusinessException.guard("implement chỉ từ Approved");
        }
        chuyen(pr, PrStatus.Implementing, req.actor);
        return pr;
    }

    @Transactional
    public Pr close(Long id, PrActionRequest req) {
        Pr pr = lay(id);
        if (pr.getTrangThai() != PrStatus.Implementing) {
            throw BusinessException.guard("close chỉ từ Implementing");
        }
        chuyen(pr, PrStatus.Closed, req.actor);
        return pr;
    }

    @Transactional(readOnly = true)
    public Pr lay(Long id) {
        return prRepo.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Không tìm thấy PR: " + id));
    }

    @Transactional(readOnly = true)
    public List<PrApproval> lichSuDuyet(Long id) {
        return approvalRepo.findByPrIdOrderByDuyetLucAsc(id);
    }

    private void chuyen(Pr pr, PrStatus den, String actor) {
        PrStatus tu = pr.getTrangThai();
        pr.setTrangThai(den);
        prRepo.save(pr);
        audit.logChuyenTrangThai("PR", String.valueOf(pr.getId()), tu.name(), den.name(), actor);
    }
}
