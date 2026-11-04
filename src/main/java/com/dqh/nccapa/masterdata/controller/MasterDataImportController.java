package com.dqh.nccapa.masterdata.controller;

import com.dqh.nccapa.common.error.ApiResponse;
import com.dqh.nccapa.common.error.BusinessException;
import com.dqh.nccapa.masterdata.service.ImportResult;
import com.dqh.nccapa.masterdata.service.MaLoiImportService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Locale;
import java.util.Set;

/** REST import master data bộ mã lỗi từ file xlsx (kênh bổ sung cho seed tay DemoSeeder). */
@RestController
@RequestMapping("/api/v1/masterdata")
public class MasterDataImportController {

    /** Content-type hợp lệ cho xlsx (có thể null tuỳ client → fallback theo đuôi .xlsx). */
    private static final Set<String> CT_XLSX = Set.of(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-excel",
            "application/octet-stream");

    private final MaLoiImportService service;

    private final MasterDataAdminGuard guard;

    public MasterDataImportController(MaLoiImportService service, MasterDataAdminGuard guard) {
        this.service = service;
        this.guard = guard;
    }

    @PostMapping(value = "/ma-loi/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ImportResult> importMaLoi(@RequestParam("file") MultipartFile file, HttpSession session) {
        guard.yeuCauAdmin(session); // ghi master data: chi ADMIN, chan o server truoc khi xu ly file
        if (file == null || file.isEmpty()) {
            throw BusinessException.businessRule("File rỗng", "file");
        }
        validateXlsx(file);
        try (InputStream in = file.getInputStream()) {
            return ApiResponse.ok(service.importXlsx(in));
        } catch (IOException e) {
            throw new UncheckedIOException("Không đọc được file upload", e);
        }
    }

    /** Chỉ nhận .xlsx: kiểm đuôi file + content-type (khoan dung content-type null/octet-stream). */
    private static void validateXlsx(MultipartFile file) {
        String ten = file.getOriginalFilename();
        if (ten == null || !ten.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw BusinessException.businessRule("Chỉ chấp nhận file .xlsx", "file");
        }
        String ct = file.getContentType();
        if (ct != null && !ct.isBlank() && !CT_XLSX.contains(ct)) {
            throw BusinessException.businessRule("Content-type không phải xlsx: " + ct, "file");
        }
    }
}
