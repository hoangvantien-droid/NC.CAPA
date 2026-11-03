package com.dqh.nccapa.attachment.service;

import com.dqh.nccapa.attachment.entity.Attachment;
import com.dqh.nccapa.attachment.repository.AttachmentRepository;
import com.dqh.nccapa.common.error.BusinessException;
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
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Lưu/đọc file đính kèm trên disk + metadata trong bảng attachment (Nhóm 2). */
@Service
public class AttachmentService {

    private static final Set<String> DOI_TUONG_HOP_LE = Set.of("NC", "ACTION");

    private final AttachmentRepository repo;
    private final AppProperties appProps;

    public AttachmentService(AttachmentRepository repo, AppProperties appProps) {
        this.repo = repo;
        this.appProps = appProps;
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
        if (doiTuong == null || !DOI_TUONG_HOP_LE.contains(doiTuong)) {
            throw BusinessException.businessRule("doiTuong phải là NC hoặc ACTION", "doiTuong");
        }
        if (doiTuongId == null || doiTuongId.isBlank()) {
            throw BusinessException.businessRule("doiTuongId bắt buộc", "doiTuongId");
        }
        if (file == null || file.isEmpty()) {
            throw BusinessException.businessRule("File rỗng", "file");
        }

        String tenGoc = file.getOriginalFilename() != null ? file.getOriginalFilename() : "file";
        String duoi = "";
        int dot = tenGoc.lastIndexOf('.');
        if (dot >= 0) duoi = tenGoc.substring(dot);
        String tenLuu = UUID.randomUUID().toString().replace("-", "") + duoi;

        Path dich = baseDir().resolve(tenLuu);
        try {
            Files.createDirectories(baseDir());
            try (var in = file.getInputStream()) {
                Files.copy(in, dich, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Không ghi được file đính kèm: " + tenLuu, e);
        }

        Attachment att = new Attachment(
                doiTuong, doiTuongId, tenGoc, tenLuu,
                file.getContentType(), file.getSize(), nguoiTai, LocalDateTime.now());
        return repo.save(att);
    }

    @Transactional(readOnly = true)
    public List<Attachment> danhSach(String doiTuong, String doiTuongId) {
        return repo.findByDoiTuongAndDoiTuongId(doiTuong, doiTuongId);
    }

    /** Lấy metadata + Resource để controller set header và trả nội dung. */
    @Transactional(readOnly = true)
    public TaiVeResult taiVe(Long id) {
        Attachment att = repo.findById(id)
                .orElseThrow(() -> BusinessException.notFound("Không tìm thấy đính kèm: " + id));
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
