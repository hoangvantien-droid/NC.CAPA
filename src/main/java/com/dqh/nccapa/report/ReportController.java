package com.dqh.nccapa.report;

import com.dqh.nccapa.common.error.ApiResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** API báo cáo (Đợt b). */
@RestController
@RequestMapping("/api/v1/report")
public class ReportController {

    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final ReportService reportService;
    private final ReportExportService reportExportService;

    public ReportController(ReportService reportService, ReportExportService reportExportService) {
        this.reportService = reportService;
        this.reportExportService = reportExportService;
    }

    @GetMapping("/dashboard")
    public ApiResponse<ReportService.DashboardData> dashboard(
            @RequestParam(value = "nhaMay", required = false) String nhaMay) {
        return ApiResponse.ok(reportService.dashboard(nhaMay));
    }

    /** Xuất báo cáo dashboard ra file Excel (xlsx). */
    @GetMapping("/export/excel")
    public ResponseEntity<byte[]> exportExcel(
            @RequestParam(value = "nhaMay", required = false) String nhaMay) {
        byte[] body = reportExportService.excel(nhaMay);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(XLSX))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"bao-cao-nc-capa.xlsx\"")
                .body(body);
    }

    /** Xuất báo cáo dashboard ra file PDF. */
    @GetMapping("/export/pdf")
    public ResponseEntity<byte[]> exportPdf(
            @RequestParam(value = "nhaMay", required = false) String nhaMay) {
        byte[] body = reportExportService.pdf(nhaMay);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"bao-cao-nc-capa.pdf\"")
                .body(body);
    }
}
