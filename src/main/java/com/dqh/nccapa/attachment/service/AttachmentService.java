package com.dqh.nccapa.attachment.service;

import com.dqh.nccapa.attachment.entity.Attachment;
import com.dqh.nccapa.attachment.repository.AttachmentRepository;
import com.dqh.nccapa.capa.entity.CapaAction;
import com.dqh.nccapa.capa.repository.CapaActionRepository;
import com.dqh.nccapa.capa.repository.CapaRepository;
import com.dqh.nccapa.common.audit.AuditService;
import com.dqh.nccapa.common.enums.CapaStatus;
import com.dqh.nccapa.capa.repository.NcCapaLinkDao;
import com.dqh.nccapa.common.error.BusinessException;
import com.dqh.nccapa.common.error.ErrorCode;
import com.dqh.nccapa.common.security.RoleChecker;
import com.dqh.nccapa.nc.entity.Nc;
import com.dqh.nccapa.nc.repository.NcRepository;
import com.dqh.nccapa.config.AppProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Lưu/đọc file đính kèm trên disk + metadata trong bảng attachment (Nhóm 2). */
@Service
public class AttachmentService {

    private static final Set<String> DOI_TUONG_HOP_LE = Set.of("NC", "ACTION");

    /** Mặc định khi content-type null → coi như generic octet-stream (không vỡ client cũ). */
    private static final String CT_MAC_DINH = "application/octet-stream";

    /** Whitelist content-type cho phép upload (loại chứng cứ phổ biến). */
    private static final Set<String> CONTENT_TYPE_HOP_LE = Set.of(
            "image/png",
            "image/jpeg",
            "image/gif",
            "image/webp",
            "application/pdf",
            "text/plain",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/octet-stream"
    );

    /** Content-type dựa trên ZIP container (OOXML docx/xlsx). Magic đều là 50 4B 03 04 nên
     *  KHÔNG phân biệt được bằng byte — dựa vào header client khai cho nhóm này. */
    private static final Set<String> ZIP_BASED_HOP_LE = Set.of(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/octet-stream"
    );

    /** Số byte header đọc để sniff magic-number. */
    private static final int HEADER_LEN = 12;

    private final AttachmentRepository repo;
    private final AppProperties appProps;

    private final NcRepository ncRepo;
    private final CapaActionRepository actionRepo;
    private final CapaRepository capaRepo;
    private final AuditService audit;
    private final RoleChecker roleChecker;
    private final NcCapaLinkDao ncCapaLink;

    public AttachmentService(AttachmentRepository repo, AppProperties appProps, NcRepository ncRepo,
                             CapaActionRepository actionRepo, CapaRepository capaRepo, AuditService audit,
                             RoleChecker roleChecker, NcCapaLinkDao ncCapaLink) {
        this.roleChecker = roleChecker;
        this.ncCapaLink = ncCapaLink;
        this.repo = repo;
        this.appProps = appProps;
        this.ncRepo = ncRepo;
        this.actionRepo = actionRepo;
        this.capaRepo = capaRepo;
        this.audit = audit;
    }

    /**
     * Kiểm đối tượng đính kèm tồn tại và chưa ở trạng thái cuối. Chạy TRƯỚC khi ghi file ra đĩa.
     * @return cặp (loại audit, id audit) để ghi vết sau khi lưu thành công.
     */
    private String[] kiemDoiTuong(String doiTuong, String doiTuongId, String actor) {
        if ("NC".equals(doiTuong)) {
            Nc nc = ncRepo.findById(doiTuongId)
                    .orElseThrow(() -> BusinessException.notFound("Không tìm thấy NC: " + doiTuongId));
            if (nc.getTrangThai().laKetThuc()) {
                throw BusinessException.businessRule("Phiếu đã đóng/huỷ, không đính kèm thêm", "doiTuongId");
            }
            // Vai trò THẬT (app_user_role) theo ma trận trạng thái dùng chung với UI (AttachmentQuyen).
            boolean duoc = AttachmentQuyen.vaiTroUploadNc(nc.getTrangThai()).stream()
                    .anyMatch(r -> roleChecker.coRole(actor, r));
            if (!duoc) {
                throw new BusinessException(ErrorCode.ROLE_DENIED,
                        "Vai trò hiện tại không được đính kèm file khi phiếu ở trạng thái " + nc.getTrangThai());
            }
            kiemScopeNhaMay(actor, nc.getNhaMay());
            return new String[] {"NC", doiTuongId};
        }
        long actionId;
        try {
            actionId = Long.parseLong(doiTuongId.trim());
        } catch (NumberFormatException e) {
            throw BusinessException.notFound("Không tìm thấy action: " + doiTuongId);
        }
        CapaAction action = actionRepo.findById(actionId)
                .orElseThrow(() -> BusinessException.notFound("Không tìm thấy action: " + doiTuongId));
        capaRepo.findById(action.getCapaId()).ifPresent(capa -> {
            if (capa.getTrangThai() == CapaStatus.Closed || capa.getTrangThai() == CapaStatus.Ineffective) {
                throw BusinessException.businessRule("CAPA đã đóng/không hiệu quả, không đính kèm thêm", "doiTuongId");
            }
        });
        // Action: QA (nhà máy/tập đoàn) HOẶC chính người phụ trách action đó.
        boolean laPhuTrach = action.getNguoiPhuTrach() != null && action.getNguoiPhuTrach().equals(actor);
        if (!roleChecker.laQa(actor) && !laPhuTrach) {
            throw new BusinessException(ErrorCode.ROLE_DENIED,
                    "Chỉ QA hoặc người phụ trách action được đính kèm file");
        }
        kiemScopeAction(actor, action.getCapaId());
        return new String[] {"CAPA", String.valueOf(action.getCapaId())};
    }

    /** Actor bắt buộc có (app chưa có xác thực thật nên actor do client gửi, như các guard khác). */
    private static void yeuCauActor(String actor) {
        if (actor == null || actor.isBlank()) {
            throw new BusinessException(ErrorCode.ROLE_DENIED, "Thiếu actor");
        }
    }

    /**
     * Scope nhà máy: actor gắn nhà máy cụ thể chỉ thao tác với đối tượng cùng nhà máy; actor toàn tập đoàn
     * (nha_may NULL) hoặc ADMIN không bị giới hạn. Vi phạm → ROLE_DENIED (cùng cách NcService.approve).
     */
    private void kiemScopeNhaMay(String actor, String nhaMayNc) {
        String nhaMayActor = roleChecker.nhaMayCua(actor);
        if (nhaMayActor != null && !nhaMayActor.equals(nhaMayNc) && !roleChecker.coRole(actor, "ADMIN")) {
            throw new BusinessException(ErrorCode.ROLE_DENIED, "Ngoài phạm vi nhà máy");
        }
    }

    /**
     * Scope cho action: nhà máy của NC gốc liên kết với CAPA cha (nc_capa). Actor có nhà máy cụ thể cần
     * ít nhất một NC gốc cùng nhà máy; CAPA không có NC gốc thì không xác định được nhà máy → chặn (fail-closed).
     */
    private void kiemScopeAction(String actor, Long capaId) {
        String nhaMayActor = roleChecker.nhaMayCua(actor);
        if (nhaMayActor == null || roleChecker.coRole(actor, "ADMIN")) return;
        boolean trung = ncCapaLink.ncSoHieuOfCapa(capaId).stream()
                .map(ncRepo::findById)
                .anyMatch(o -> o.isPresent() && nhaMayActor.equals(o.get().getNhaMay()));
        if (!trung) {
            throw new BusinessException(ErrorCode.ROLE_DENIED, "Ngoài phạm vi nhà máy");
        }
    }

    /** Scope đọc cho (doiTuong, id); đối tượng không tồn tại thì không có gì để chặn. */
    private void kiemScopeDoc(String actor, String doiTuong, String doiTuongId) {
        if ("NC".equals(doiTuong)) {
            ncRepo.findById(doiTuongId).ifPresent(nc -> kiemScopeNhaMay(actor, nc.getNhaMay()));
        } else if ("ACTION".equals(doiTuong) && doiTuongId != null) {
            try {
                actionRepo.findById(Long.parseLong(doiTuongId.trim()))
                        .ifPresent(a -> kiemScopeAction(actor, a.getCapaId()));
            } catch (NumberFormatException ignore) {
                // id không hợp lệ ⇒ không có đối tượng
            }
        }
    }

    private Path baseDir() {
        return Paths.get(appProps.getUpload().getDir());
    }

    @PostConstruct
    void ensureDir() {
        try {
            Files.createDirectories(baseDir());
        } catch (IOException e) {
            throw new UncheckedIOException("Không tạo được thư mục upload: " + baseDir(), e);
        }
    }

    /** Lưu file: validate, sinh tên duy nhất, ghi ra disk, lưu metadata. */
    @Transactional
    public Attachment luu(String doiTuong, String doiTuongId, MultipartFile file, String nguoiTai) {
        yeuCauActor(nguoiTai);
        if (doiTuong == null || !DOI_TUONG_HOP_LE.contains(doiTuong)) {
            throw BusinessException.businessRule("doiTuong phải là NC hoặc ACTION", "doiTuong");
        }
        if (doiTuongId == null || doiTuongId.isBlank()) {
            throw BusinessException.businessRule("doiTuongId bắt buộc", "doiTuongId");
        }
        if (file == null || file.isEmpty()) {
            throw BusinessException.businessRule("File rỗng", "file");
        }

        // Tồn tại + trạng thái: kiểm trước khi đọc/ghi file để không sinh file mồ côi.
        String[] dauVet = kiemDoiTuong(doiTuong, doiTuongId, nguoiTai);

        // Đọc toàn bộ byte 1 lần: cần header để sniff magic + nội dung để ghi disk.
        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Không đọc được file đính kèm", e);
        }

        // Giới hạn TỔNG dung lượng cho (doiTuong, doiTuongId): tổng cũ + file mới.
        long maxTotal = appProps.getUpload().getMaxTotalBytesPerObject();
        long tongHienCo = repo.findByDoiTuongAndDoiTuongId(doiTuong, doiTuongId).stream()
                .mapToLong(Attachment::getKichThuoc).sum();
        if (tongHienCo + data.length > maxTotal) {
            throw BusinessException.businessRule("Vượt tổng dung lượng cho phép", "file");
        }

        // Sniff loại thật bằng magic-number; quyết định content-type lưu DB (xem bảng trong xacDinhContentType).
        String headerCt = file.getContentType();
        String contentType = xacDinhContentType(headerCt, data);

        String tenGoc = file.getOriginalFilename() != null ? file.getOriginalFilename() : "file";
        String duoi = "";
        int dot = tenGoc.lastIndexOf('.');
        if (dot >= 0) duoi = tenGoc.substring(dot);
        String tenLuu = UUID.randomUUID().toString().replace("-", "") + duoi;

        Path dich = baseDir().resolve(tenLuu);
        try {
            Files.createDirectories(baseDir());
            Files.write(dich, data);
        } catch (IOException e) {
            throw new UncheckedIOException("Không ghi được file đính kèm: " + tenLuu, e);
        }

        Attachment att = new Attachment(
                doiTuong, doiTuongId, tenGoc, tenLuu,
                contentType, (long) data.length, nguoiTai, LocalDateTime.now());
        Attachment daLuu = repo.save(att);
        // audit_log.nguoi_thuc_hien NOT NULL, gia_tri_moi tối đa 255 → chuẩn hoá trước khi ghi.
        String nguoi = nguoiTai == null || nguoiTai.isBlank() ? "unknown" : nguoiTai;
        String tenAudit = tenGoc.length() > 255 ? tenGoc.substring(0, 255) : tenGoc;
        audit.log(dauVet[0], dauVet[1], "UPLOAD_ATTACHMENT", null, tenAudit,
                "ACTION".equals(doiTuong) ? "action #" + doiTuongId : null, nguoi);
        return daLuu;
    }

    /**
     * Xác định content-type lưu DB dựa trên magic-number thật (sniff byte), KHÔNG tin header client.
     *
     * Ma trận quyết định (header client khai X + magic thật Y → kết quả):
     * <pre>
     * magic PNG  (89 50 4E 47)         → image/png          (bỏ qua header, dùng loại thật)
     * magic JPEG (FF D8 FF)            → image/jpeg
     * magic GIF  (47 49 46 38, "GIF8") → image/gif
     * magic PDF  (25 50 44 46, "%PDF") → application/pdf
     * magic WEBP (RIFF....WEBP)        → image/webp
     * magic ZIP  (50 4B 03 04)         → nếu header khai 1 loại ZIP-based whitelist (docx/xlsx/octet)
     *                                     → TIN header (không phân biệt docx vs xlsx vs zip bằng magic);
     *                                     ngược lại → từ chối.
     * KHÔNG match magic nào ở trên:
     *   - header khai text/plain & nội dung là text in được  → text/plain
     *   - header khai application/octet-stream (hoặc null)     → application/octet-stream
     *   - còn lại (vd khai image/png nhưng byte là MZ/rác)     → TỪ CHỐI (nội dung không khớp loại khai)
     * </pre>
     * Nếu loại thật nhận diện được mà KHÔNG nằm trong whitelist → từ chối.
     *
     * Giới hạn đã biết: docx/xlsx/zip đều có magic 50 4B 03 04 nên với file ZIP-based ta buộc
     * phải tin header client khai (không sniff phân biệt được bằng byte đầu).
     */
    private String xacDinhContentType(String headerCt, byte[] data) {
        String header = headerCt == null ? "" : headerCt.trim();
        String magic = detectMagic(data);

        if (magic != null) {
            if ("application/zip".equals(magic)) {
                // ZIP container: tin header nếu là loại ZIP-based whitelist; octet-stream mặc định.
                if (header.isBlank()) return CT_MAC_DINH;
                if (ZIP_BASED_HOP_LE.contains(header)) return header;
                throw BusinessException.businessRule("Nội dung file không khớp loại cho phép", "file");
            }
            // Magic nhận diện cụ thể (ảnh/pdf): dùng loại thật, chặn nếu ngoài whitelist.
            if (!CONTENT_TYPE_HOP_LE.contains(magic)) {
                throw BusinessException.businessRule("Loại file không được phép: " + magic, "file");
            }
            return magic;
        }

        // Không match magic cố định: text/plain hoặc octet-stream mới cho qua.
        if ("text/plain".equals(header) && laTextInDuoc(data)) {
            return "text/plain";
        }
        if (header.isBlank() || CT_MAC_DINH.equals(header)) {
            return CT_MAC_DINH;
        }
        // Header khai loại có magic cố định (png/jpeg/pdf...) nhưng byte không khớp → gian lận.
        throw BusinessException.businessRule("Nội dung file không khớp loại cho phép", "file");
    }

    /** So khớp signature cho các loại trong whitelist; trả null nếu không nhận diện được. */
    private static String detectMagic(byte[] b) {
        int n = b.length;
        if (n >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && (b[4] & 0xFF) == 0x0D && (b[5] & 0xFF) == 0x0A
                && (b[6] & 0xFF) == 0x1A && (b[7] & 0xFF) == 0x0A) {
            return "image/png";
        }
        if (n >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (n >= 4 && b[0] == 'G' && b[1] == 'I' && b[2] == 'F' && b[3] == '8') {
            return "image/gif";
        }
        if (n >= 4 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F') {
            return "application/pdf";
        }
        if (n >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return "image/webp";
        }
        if (n >= 4 && b[0] == 'P' && b[1] == 'K' && (b[2] & 0xFF) == 0x03 && (b[3] & 0xFF) == 0x04) {
            return "application/zip";
        }
        return null;
    }

    /**
     * Kiểm tra header file có là text in được không (text/plain không có magic cố định).
     * Chấp nhận ký tự in được + tab/newline/CR; từ chối byte NUL hoặc control khác.
     */
    private static boolean laTextInDuoc(byte[] b) {
        int limit = Math.min(b.length, HEADER_LEN * 8);
        for (int i = 0; i < limit; i++) {
            int c = b[i] & 0xFF;
            if (c == 0x09 || c == 0x0A || c == 0x0D) continue; // tab, LF, CR
            if (c < 0x20 || c == 0x7F) return false;            // control/DEL → không phải text
        }
        return true;
    }

    @Transactional(readOnly = true)
    public List<Attachment> danhSach(String doiTuong, String doiTuongId, String actor) {
        yeuCauActor(actor);
        kiemScopeDoc(actor, doiTuong, doiTuongId);
        return repo.findByDoiTuongAndDoiTuongId(doiTuong, doiTuongId);
    }

    /** Lấy metadata + Resource để controller set header và trả nội dung. */
    @Transactional(readOnly = true)
    public TaiVeResult taiVe(Long id, String actor) {
        yeuCauActor(actor);
        Attachment att = repo.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Không tìm thấy đính kèm: " + id));
        kiemScopeDoc(actor, att.getDoiTuong(), att.getDoiTuongId());
        Path p = baseDir().resolve(att.getTenLuu());
        if (!Files.exists(p)) {
            throw BusinessException.notFound("File không tồn tại trên disk: " + att.getTenLuu());
        }
        return new TaiVeResult(att, new PathResource(p));
    }

    /** Metadata + Resource trả về cho tải file. */
    public static final class TaiVeResult {
        private final Attachment attachment;
        private final Resource resource;
        public TaiVeResult(Attachment attachment, Resource resource) {
            this.attachment = attachment;
            this.resource = resource;
        }
        public Attachment getAttachment() { return attachment; }
        public Resource getResource() { return resource; }
    }
}
