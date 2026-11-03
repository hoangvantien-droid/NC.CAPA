package com.dqh.nccapa.report;

import com.dqh.nccapa.common.error.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** API báo cáo (Đợt b). */
@RestController
@RequestMapping("/api/v1/report")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/dashboard")
    public ApiResponse<ReportService.DashboardData> dashboard(
            @RequestParam(value = "nhaMay", required = false) String nhaMay) {
        return ApiResponse.ok(reportService.dashboard(nhaMay));
    }
}
