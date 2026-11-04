package com.dqh.nccapa.nc.service;

import com.dqh.nccapa.aggregation.SequenceGenerator;
import com.dqh.nccapa.common.audit.AuditService;
import com.dqh.nccapa.common.enums.NcSource;
import com.dqh.nccapa.common.enums.NcStatus;
import com.dqh.nccapa.common.enums.Severity;
import com.dqh.nccapa.common.error.BusinessException;
import com.dqh.nccapa.common.error.ErrorCode;
import com.dqh.nccapa.common.security.RoleChecker;
import com.dqh.nccapa.config.AppProperties;
import com.dqh.nccapa.masterdata.entity.MaLoi;
import com.dqh.nccapa.masterdata.service.DefectCatalogService;
import com.dqh.nccapa.nc.dto.ApproveRequest;
import com.dqh.nccapa.nc.dto.CloseFastRequest;
import com.dqh.nccapa.nc.dto.ConfirmDoneRequest;
import com.dqh.nccapa.nc.dto.ContainmentRequest;
import com.dqh.nccapa.nc.dto.EscalateRequest;
import com.dqh.nccapa.nc.dto.RejectRequest;
import com.dqh.nccapa.nc.dto.SubmitApprovalRequest;
import com.dqh.nccapa.nc.dto.TaoNcRequest;
import com.dqh.nccapa.nc.dto.UnholdRequest;
import com.dqh.nccapa.nc.dto.VerifyCloseRequest;
import com.dqh.nccapa.nc.entity.Nc;
import com.dqh.nccapa.nc.entity.NcChiTieu;
import com.dqh.nccapa.nc.entity.NcDoiTuong;
import com.dqh.nccapa.nc.repository.NcChiTieuRepository;
import com.dqh.nccapa.nc.repository.NcDoiTuongRepository;
import com.dqh.nccapa.nc.repository.NcRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.util.List;

/**
 * Nghiệp vụ NC — lát 1: tạo NC thủ công + sinh số hiệu + luồng Nhanh (close-fast) + nâng bậc.
 * Mọi chuyển trạng thái đi qua các method ở đây; guard kiểm ngay trong method (StateMachine).
 */
@Service
public class NcService {

    private final NcRepository ncRepo;
    private final NcChiTieuRepository chiTieuRepo;
    private final NcDoiTuongRepository doiTuongRepo;
    private final DefectCatalogService defectCatalog;
    private final SequenceGenerator sequence;
    private final AuditService audit;
    private final CapaClosureChecker capaClosureChecker;
    private final EarlyFailHook earlyFailHook;
    private final RecurrenceHook recurrenceHook;
    private final TicketCallbackHook ticketCallbackHook;
    private final AppProperties appProps;
    private final RoleChecker roleChecker;

    public NcService(NcRepository ncRepo, NcChiTieuRepository chiTieuRepo,
                     NcDoiTuongRepository doiTuongRepo, DefectCatalogService defectCatalog,
                     SequenceGenerator sequence, AuditService audit,
                     CapaClosureChecker capaClosureChecker, EarlyFailHook earlyFailHook,
                     RecurrenceHook recurrenceHook,
                     TicketCallbackHook ticketCallbackHook, AppProperties appProps,
                     RoleChecker roleChecker) {
        this.ncRepo = ncRepo;
        this.chiTieuRepo = chiTieuRepo;
        this.doiTuongRepo = doiTuongRepo;
        this.defectCatalog = defectCatalog;
        this.sequence = sequence;
        this.audit = audit;
        this.capaClosureChecker = capaClosureChecker;
        this.earlyFailHook = earlyFailHook;
        this.recurrenceHook = recurrenceHook;
        this.ticketCallbackHook = ticketCallbackHook;
        this.appProps = appProps;
        this.roleChecker = roleChecker;
    }

    /** NC có nguồn khiếu nại (có ticket gắn)? dùng để quyết định callback. */
    private boolean laNguonKhieuNai(Nc nc) {
        return nc.getNguonPhatSinh() == NcSource.TICKET;
    }

    @Transactional
    public Nc taoNc(TaoNcRequest req) {
        if (req.chiTieu == null || req.chiTieu.isEmpty()) {
            throw BusinessException.businessRule("NC phải có ít nhất một chỉ tiêu vi phạm", "chiTieu");
        }

        // Mức = mã lỗi nặng nhất; đồng thời xác định dòng nặng nhất để sync ma_hang_chinh/ma_loi_chinh
        Severity mucPhieu = Severity.NHE;
        boolean foodSafety = false;
        TaoNcRequest.ChiTieuItem dongNangNhat = null;
        if (req.doiTuong != null) {
            for (TaoNcRequest.DoiTuongItem dt : req.doiTuong) {
                defectCatalog.kiemTraDoiTuongDangDung(dt.loaiDoiTuong, dt.giaTri);
            }
        }
        for (TaoNcRequest.ChiTieuItem ct : req.chiTieu) {
            MaLoi ml = defectCatalog.get(ct.maLoiId);
            defectCatalog.kiemTraDangDungKhiTaoNc(req.nhaMay, ct.maHang, ml); // danh mục ngừng dùng không dùng cho NC mới
            if (dongNangNhat == null || ml.getMucNghiemTrongDefault().caoHon(mucPhieu)) {
                dongNangNhat = ct;
            }
            mucPhieu = Severity.nangNhat(mucPhieu, ml.getMucNghiemTrongDefault());
            if (ml.isBatBuocKhoanhVung()) foodSafety = true;
        }

        int nam = Year.now().getValue();
        String soHieu = sequence.nextSoHieu(nam, req.nhaMay);

        Nc nc = new Nc(soHieu, req.loaiNc, mucPhieu, req.nguonPhatSinh,
                req.nhaMay, req.nguoiBaoCao, req.nguoiBaoCao);
        nc.setLoKiem(req.loKiem);
        nc.setLenhSx(req.lenhSx);
        nc.setCongDoan(req.congDoan);
        nc.setNgaySx(req.ngaySx);
        nc.setKhuVuc(req.khuVuc);
        nc.setMoTaLoi(req.moTaLoi);
        nc.setSoLuong(req.soLuong);
        nc.setNguyenNhanSoBo(req.nguyenNhanSoBo);
        nc.setFoodSafety(foodSafety);
        nc.setMaLoiChinh(dongNangNhat.maLoiId);
        nc.setMaHangChinh(dongNangNhat.maHang);
        // saveAndFlush: ép INSERT xuống DB ngay (so_hieu là id gán sẵn → JPA hoãn INSERT tới flush;
        // recurrenceHook đếm NC qua JdbcTemplate KHÔNG kích hoạt autoflush của Hibernate nên nếu không
        // flush, dòng NC mới chưa có trong DB và bị đếm thiếu 1 lần). Xem RecurrenceDetector.
        ncRepo.saveAndFlush(nc);

        for (TaoNcRequest.ChiTieuItem ct : req.chiTieu) {
            chiTieuRepo.save(new NcChiTieu(soHieu, ct.maLoiId, ct.maHang, ct.inOutSpec,
                    req.nguonPhatSinh.name()));
        }
        if (req.doiTuong != null) {
            for (TaoNcRequest.DoiTuongItem dt : req.doiTuong) {
                doiTuongRepo.save(new NcDoiTuong(soHieu, dt.loaiDoiTuong, dt.giaTri));
            }
        }

        audit.log("NC", soHieu, "TAO_NC", null, mucPhieu.name(), null, req.nguoiBaoCao);
        earlyFailHook.onNewNc(nc);   // TH2 fail sớm nếu trùng (mã hàng+mã lỗi) với CAPA đang Monitoring
        recurrenceHook.onNewNc(nc);  // tái diễn lần 3 cùng (mã hàng+mã lỗi) trong rolling window 90 ngày → mở PR
        return nc;
    }

    /** N3: New -> PendingApproval (luồng Có xem xét, NC Nặng). Lưu người đề xuất disposition (SoD). */
    @Transactional
    public Nc submitApproval(String soHieu, SubmitApprovalRequest req) {
        Nc nc = layNc(soHieu);
        if (nc.getTrangThai() != NcStatus.New && nc.getTrangThai() != NcStatus.Rejected
                && nc.getTrangThai() != NcStatus.Containment) {
            throw BusinessException.guard("submit-approval không hợp lệ từ: " + nc.getTrangThai());
        }
        if (nc.getMucNghiemTrong() == Severity.NHE) {
            throw BusinessException.guard("NC Nhẹ dùng luồng Nhanh, không qua duyệt disposition");
        }
        // Luồng Có khoanh vùng: NC Nghiêm trọng / an toàn TP bắt buộc qua Containment trước PendingApproval
        boolean phaiKhoanhVung = nc.getMucNghiemTrong() == Severity.NGHIEM_TRONG || nc.isFoodSafety();
        if (phaiKhoanhVung && nc.getTrangThai() == NcStatus.New) {
            throw BusinessException.guard("NC Nghiêm trọng / an toàn TP phải qua bước Khoanh vùng trước khi trình duyệt");
        }
        nc.setNguyenNhanSoBo(req.nguyenNhanSoBo);
        nc.setDispositionDeXuat(req.dispositionDeXuat);
        nc.setChiPhiUocTinh(req.chiPhiUocTinh);
        nc.setBoPhanThucHien(req.boPhanThucHien);
        nc.setNguoiDeXuatDisposition(req.actor);
        nc.resetDuyetCap1();
        NcStatus tu = nc.getTrangThai();
        nc.setTrangThai(NcStatus.PendingApproval);
        ncRepo.save(nc);
        audit.logChuyenTrangThai("NC", soHieu, tu.name(), NcStatus.PendingApproval.name(), req.actor);
        return nc;
    }

    /**
     * N5: PendingApproval -> Processing.
     * NC Nghiêm trọng: QA TĐ duyệt 1 lần. NC Nặng: 2 lượt bởi 2 user khác nhau (API §6.2).
     * SoD (§2.2): người đề xuất disposition không được duyệt cấp 1.
     */
    @Transactional
    public Nc approve(String soHieu, ApproveRequest req) {
        Nc nc = layNc(soHieu);
        if (nc.getTrangThai() != NcStatus.PendingApproval) {
            throw BusinessException.guard("approve chỉ từ PendingApproval, hiện tại: " + nc.getTrangThai());
        }
        if (appProps.getSod().isEnabled() && req.actor.equals(nc.getNguoiDeXuatDisposition())) {
            throw new BusinessException(ErrorCode.ROLE_DENIED,
                    "SoD: người đề xuất disposition không được tự duyệt");
        }

        // SCOPE THEO NHÀ MÁY (phạm vi giới hạn: chỉ duyệt disposition NC):
        // actor toàn tập đoàn (app_user.nha_may NULL) duyệt NC mọi nhà máy;
        // actor gắn nhà máy cụ thể chỉ duyệt NC cùng nhà máy. Bổ sung cho guard role phía trên.
        String nhaMayActor = roleChecker.nhaMayCua(req.actor);
        if (nhaMayActor != null && !nhaMayActor.equals(nc.getNhaMay())) {
            throw new BusinessException(ErrorCode.ROLE_DENIED, "Ngoài phạm vi nhà máy");
        }

        // Siết SoD THEO ROLE THẬT (REQ mục 6.2, ma trận duyệt disposition): xác minh actor thực sự
        // có vai trò QA phù hợp với mức NC trong app_user_role, KHÔNG tin vai trò do client khai.
        //   - NC Nghiêm trọng: QA TẬP ĐOÀN (REQ: "NC Nghiêm trọng: QA tập đoàn").
        //   - NC Nặng: nhóm QA (REQ: "QA nhà máy + người thứ hai"). REQ không gắn cứng người thứ hai
        //     phải là QA tập đoàn → chấp nhận QA_NHA_MAY HOẶC QA_TAP_DOAN cho cả 2 lượt duyệt NC Nặng.
        if (nc.getMucNghiemTrong() == Severity.NGHIEM_TRONG) {
            if (!roleChecker.laQaTapDoan(req.actor)) {
                throw new BusinessException(ErrorCode.ROLE_DENIED,
                        "NC Nghiêm trọng: chỉ QA tập đoàn được duyệt disposition");
            }
        } else if (!roleChecker.laQa(req.actor)) {
            throw new BusinessException(ErrorCode.ROLE_DENIED,
                    "NC Nặng: người duyệt disposition phải thuộc nhóm QA");
        }

        if (nc.getMucNghiemTrong() == Severity.NGHIEM_TRONG) {
            // 1 lần duyệt (QA TĐ — vai trò đã xác minh ở trên)
            doChuyenProcessing(nc, req.actor);
        } else { // NANG: 2 cấp
            if (!nc.isDaDuyetCap1()) {
                nc.setDaDuyetCap1(true);
                nc.setNguoiDuyetCap1(req.actor);
                ncRepo.save(nc);
                audit.log("NC", soHieu, "DUYET_CAP_1", null, req.actor, null, req.actor);
                return nc; // vẫn PendingApproval, chờ người thứ hai
            }
            if (req.actor.equals(nc.getNguoiDuyetCap1())) {
                throw BusinessException.guard("Người duyệt cấp 2 phải khác người duyệt cấp 1");
            }
            doChuyenProcessing(nc, req.actor);
        }
        return nc;
    }

    private void doChuyenProcessing(Nc nc, String actor) {
        nc.setTrangThai(NcStatus.Processing);
        ncRepo.save(nc);
        audit.logChuyenTrangThai("NC", nc.getSoHieu(),
                NcStatus.PendingApproval.name(), NcStatus.Processing.name(), actor);
    }

    /** N6: PendingApproval -> Rejected. Reset duyệt cấp 1. */
    @Transactional
    public Nc reject(String soHieu, RejectRequest req) {
        Nc nc = layNc(soHieu);
        if (nc.getTrangThai() != NcStatus.PendingApproval) {
            throw BusinessException.guard("reject chỉ từ PendingApproval");
        }
        nc.resetDuyetCap1();
        nc.setTrangThai(NcStatus.Rejected);
        ncRepo.save(nc);
        audit.log("NC", soHieu, "CHUYEN_TRANG_THAI",
                NcStatus.PendingApproval.name(), NcStatus.Rejected.name(), req.lyDo, req.actor);
        return nc;
    }

    /** N8: Processing -> PendingVerification. Chi phí thực tế bắt buộc. */
    @Transactional
    public Nc confirmDone(String soHieu, ConfirmDoneRequest req) {
        Nc nc = layNc(soHieu);
        if (nc.getTrangThai() != NcStatus.Processing) {
            throw BusinessException.guard("confirm-done chỉ từ Processing");
        }
        if (req.chiPhiThucTe == null) {
            throw BusinessException.businessRule("Bắt buộc nhập chi phí xử lý thực tế", "chiPhiThucTe");
        }
        nc.setBangChung(req.bangChung);
        nc.setChiPhiThucTe(req.chiPhiThucTe);
        nc.setTrangThai(NcStatus.PendingVerification);
        ncRepo.save(nc);
        audit.logChuyenTrangThai("NC", soHieu,
                NcStatus.Processing.name(), NcStatus.PendingVerification.name(), req.actor);
        // Mốc phản hồi sớm cho NC nguồn khiếu nại (REQ mục 6)
        if (laNguonKhieuNai(nc)) ticketCallbackHook.mocSom(nc);
        return nc;
    }

    /**
     * N9: PendingVerification -> Closed.
     * Guard chống deadlock (vá #1): mọi CAPA ở trạng thái kết thúc.
     * Guard dòng lô (vá 2.1): chỉ kiểm dòng khoanh_vung=1.
     */
    @Transactional
    public Nc verifyClose(String soHieu, VerifyCloseRequest req) {
        Nc nc = layNc(soHieu);
        if (nc.getTrangThai() != NcStatus.PendingVerification) {
            throw BusinessException.guard("verify-close chỉ từ PendingVerification");
        }
        if (nc.getChiPhiThucTe() == null) {
            throw BusinessException.businessRule("Chưa nhập chi phí xử lý thực tế", "chiPhiThucTe");
        }
        String capaChuaXong = capaClosureChecker.firstUnterminatedCapa(soHieu);
        if (capaChuaXong != null) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE,
                    "Còn CAPA chưa ở trạng thái kết thúc: " + capaChuaXong);
        }
        for (NcDoiTuong dt : doiTuongRepo.findByNcSoHieu(soHieu)) {
            if (dt.isKhoanhVung() && !dt.coTrangThaiCuoi()) {
                throw new BusinessException(ErrorCode.BUSINESS_RULE,
                        "Dòng lô khoanh vùng chưa có trạng thái cuối: " + dt.getGiaTri());
            }
        }
        nc.setKetLuanThamTra(req.ketLuanThamTra);
        nc.setTrangThai(NcStatus.Closed);
        ncRepo.save(nc);
        audit.logChuyenTrangThai("NC", soHieu,
                NcStatus.PendingVerification.name(), NcStatus.Closed.name(), req.actor);
        // Mốc đóng hồ sơ (REQ mục 6)
        if (laNguonKhieuNai(nc)) ticketCallbackHook.mocDong(nc, "Confirmed");
        return nc;
    }

    /** N10: hủy phiếu (Cancelled). NC nguồn khiếu nại bị hủy vẫn callback case_closed = Cancelled. */
    @Transactional
    public Nc cancel(String soHieu, RejectRequest req) {
        Nc nc = layNc(soHieu);
        if (nc.getTrangThai() == NcStatus.Closed || nc.getTrangThai() == NcStatus.Cancelled) {
            throw BusinessException.guard("NC đã ở trạng thái cuối");
        }
        if (req.lyDo == null || req.lyDo.isBlank()) {
            throw BusinessException.businessRule("Hủy phiếu cần lý do", "lyDo");
        }
        NcStatus tu = nc.getTrangThai();
        nc.setLyDoHuy(req.lyDo);
        nc.setTrangThai(NcStatus.Cancelled);
        ncRepo.save(nc);
        audit.log("NC", soHieu, "CHUYEN_TRANG_THAI", tu.name(), NcStatus.Cancelled.name(), req.lyDo, req.actor);
        if (laNguonKhieuNai(nc)) ticketCallbackHook.mocDong(nc, "Cancelled");
        return nc;
    }

    /** N1: New -> Closed (luồng Nhanh, chỉ NC Nhẹ). */
    @Transactional
    public Nc closeFast(String soHieu, CloseFastRequest req) {
        Nc nc = layNc(soHieu);
        if (nc.getTrangThai() != NcStatus.New) {
            throw BusinessException.guard("close-fast chỉ từ trạng thái New, hiện tại: " + nc.getTrangThai());
        }
        if (nc.getMucNghiemTrong() != Severity.NHE) {
            // Bản vá #4: nâng bậc đã khóa đường tắt này
            throw BusinessException.guard("Chỉ NC Nhẹ được đóng nhanh; mức hiện tại: " + nc.getMucNghiemTrong());
        }
        nc.setKetQuaXuLy(req.ketQuaXuLy);
        nc.setTrangThai(NcStatus.Closed);
        ncRepo.save(nc);
        audit.logChuyenTrangThai("NC", soHieu, NcStatus.New.name(), NcStatus.Closed.name(), req.actor);
        return nc;
    }

    /**
     * Nâng bậc (StateMachine §2.2). Vá #4 (New: Nhẹ->Nặng giữ New) và vá vênh 3.1 (New: ->Nghiêm trọng sang Containment).
     */
    @Transactional
    public Nc nangBac(String soHieu, EscalateRequest req) {
        Nc nc = layNc(soHieu);
        Severity cu = nc.getMucNghiemTrong();
        Severity moi = req.mucMoi;
        if (!moi.caoHon(cu)) {
            throw BusinessException.guard("Mức mới phải cao hơn mức hiện tại");
        }
        if (nc.getTrangThai() == NcStatus.Closed || nc.getTrangThai() == NcStatus.Cancelled) {
            throw BusinessException.guard("Không nâng bậc NC đã kết thúc");
        }

        applyEscalation(nc, moi, req.lyDo, req.actor);
        return nc;
    }

    /**
     * Logic nâng bậc lõi dùng chung cho API escalate và auto-aggregation (StateMachine §2.2).
     * Vá #4: Nhẹ→Nặng ở New giữ New. Vá vênh 3.1: ->Nghiêm trọng (mọi ca) sang Containment.
     */
    public void applyEscalation(Nc nc, Severity moi, String lyDo, String actor) {
        Severity cu = nc.getMucNghiemTrong();
        nc.setMucNghiemTrong(moi);
        nc.setEscalated(true);
        nc.resetDuyetCap1(); // §2.2: duyệt cấp 1 cũ hết giá trị

        if (moi == Severity.NGHIEM_TRONG) {
            nc.setFoodSafety(true);
            // Chỉ đẩy về Containment nếu chưa kết thúc và chưa ở Containment
            if (nc.getTrangThai() != NcStatus.Containment) {
                nc.setTrangThai(NcStatus.Containment);
            }
        }
        // Nhẹ→Nặng ở New: giữ nguyên New (close-fast tự khóa do mức != NHE)

        ncRepo.save(nc);
        audit.log("NC", nc.getSoHieu(), "NANG_BAC", cu.name(), moi.name(), lyDo, actor);
    }

    /**
     * N2: New -> Containment (luồng Có khoanh vùng).
     * Bắt buộc ≥1 lô khoanh + chứng từ Hold (REQ 4.1, quy tắc #9). Không đẩy lệnh sang EBS.
     */
    @Transactional
    public Nc containment(String soHieu, ContainmentRequest req) {
        Nc nc = layNc(soHieu);
        if (nc.getTrangThai() != NcStatus.New && nc.getTrangThai() != NcStatus.Containment) {
            throw BusinessException.guard("containment chỉ từ New/Containment, hiện tại: " + nc.getTrangThai());
        }
        if (nc.getMucNghiemTrong() != Severity.NGHIEM_TRONG && !nc.isFoodSafety()) {
            throw BusinessException.guard("Containment chỉ áp cho NC Nghiêm trọng hoặc an toàn TP");
        }
        if (req.loKhoanh == null || req.loKhoanh.isEmpty()) {
            throw BusinessException.businessRule("Phải có ít nhất một lô khoanh vùng", "loKhoanh");
        }
        for (ContainmentRequest.LoKhoanh lk : req.loKhoanh) {
            if (lk.chungTuHold == null || lk.chungTuHold.isBlank()) {
                throw BusinessException.businessRule("Thiếu chứng từ Hold cho lô " + lk.soLo, "chungTuHold");
            }
            NcDoiTuong dt = new NcDoiTuong(soHieu, "LO", lk.soLo);
            dt.setKhoanhVung(true);
            dt.setTrangThaiDongLo("Held");
            dt.setChungTuHold(lk.chungTuHold);
            doiTuongRepo.save(dt);
        }
        nc.setTrangThai(NcStatus.Containment);
        ncRepo.save(nc);
        audit.logChuyenTrangThai("NC", soHieu, NcStatus.New.name(), NcStatus.Containment.name(), req.actor);
        return nc;
    }

    /**
     * Gỡ khoanh vùng theo dòng lô trong Processing (REQ 4.1 line-item):
     * lô đạt được Unhold trước, ghi chứng từ Unhold + trạng thái cuối (Released/Scrapped/Reworked).
     */
    @Transactional
    public NcDoiTuong unholdDongLo(String soHieu, Long doiTuongId, UnholdRequest req) {
        Nc nc = layNc(soHieu);
        if (nc.getTrangThai() != NcStatus.Processing) {
            throw BusinessException.guard("Gỡ khoanh dòng lô chỉ trong Processing, hiện tại: " + nc.getTrangThai());
        }
        NcDoiTuong dt = doiTuongRepo.findById(doiTuongId)
                .orElseThrow(() -> BusinessException.notFound("Không tìm thấy đối tượng: " + doiTuongId));
        if (!dt.getNcSoHieu().equals(soHieu)) {
            throw BusinessException.businessRule("Đối tượng không thuộc NC này", "doiTuongId");
        }
        if (!dt.isKhoanhVung()) {
            throw BusinessException.guard("Đối tượng không ở trạng thái khoanh vùng");
        }
        dt.setChungTuUnhold(req.chungTuUnhold);
        dt.setTrangThaiDongLo(req.trangThaiDongLo);
        dt.setKhoanhVung(false);
        doiTuongRepo.save(dt);
        audit.log("NC", soHieu, "GO_KHOANH_VUNG", "Held", req.trangThaiDongLo,
                "lô " + dt.getGiaTri(), req.actor);
        return dt;
    }

    /**
     * Tạo NC kế thừa khi CAPA fail (TH1 — StateMachine §4.2). Nâng 1 bậc (trần Nghiêm trọng),
     * link ngược CAPA fail. Dùng chung cho mark-ineffective.
     * @return NC mới. Caller chịu trách nhiệm set handover + (nếu Nghiêm trọng) mở PR.
     */
    @Transactional
    public Nc taoNcKeThua(Nc ncGoc, Long capaFailId) {
        Severity moi = ncGoc.getMucNghiemTrong().nangMotBac();
        int nam = Year.now().getValue();
        String soHieu = sequence.nextSoHieu(nam, ncGoc.getNhaMay());
        Nc nc = new Nc(soHieu, ncGoc.getLoaiNc(), moi, NcSource.NOI_BO,
                ncGoc.getNhaMay(), "SYSTEM", "SYSTEM");
        nc.setLoKiem(ncGoc.getLoKiem());
        nc.setMaHangChinh(ncGoc.getMaHangChinh());
        nc.setMaLoiChinh(ncGoc.getMaLoiChinh());
        nc.setNcKeThuaTuCapaId(capaFailId);
        nc.setEscalated(true);
        if (moi == Severity.NGHIEM_TRONG) {
            nc.setFoodSafety(true);
            nc.setTrangThai(NcStatus.Containment);
        }
        ncRepo.save(nc);
        // sao chép chỉ tiêu nặng nhất (để fail sớm/tái diễn truy theo cặp mã hàng+mã lỗi)
        if (ncGoc.getMaLoiChinh() != null) {
            chiTieuRepo.save(new NcChiTieu(soHieu, ncGoc.getMaLoiChinh(),
                    ncGoc.getMaHangChinh(), null, "KE_THUA"));
        }
        audit.log("NC", soHieu, "TAO_NC_KE_THUA", ncGoc.getSoHieu(), moi.name(),
                "kế thừa từ CAPA fail " + capaFailId, "SYSTEM");
        return nc;
    }

    @Transactional(readOnly = true)
    public Nc layNc(String soHieu) {
        return ncRepo.findById(soHieu)
                .orElseThrow(() -> BusinessException.notFound("Không tìm thấy NC: " + soHieu));
    }

    @Transactional(readOnly = true)
    public List<NcChiTieu> chiTieuCua(String soHieu) {
        return chiTieuRepo.findByNcSoHieu(soHieu);
    }

    @Transactional(readOnly = true)
    public List<NcDoiTuong> doiTuongCua(String soHieu) {
        return doiTuongRepo.findByNcSoHieu(soHieu);
    }
}
