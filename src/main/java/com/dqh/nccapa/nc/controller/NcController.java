package com.dqh.nccapa.nc.controller;

import com.dqh.nccapa.common.error.ApiResponse;
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
import com.dqh.nccapa.nc.entity.NcDoiTuong;
import com.dqh.nccapa.nc.service.NcService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/nc")
public class NcController {

    private final NcService ncService;

    public NcController(NcService ncService) {
        this.ncService = ncService;
    }

    @PostMapping
    public ApiResponse<Nc> taoNc(@Valid @RequestBody TaoNcRequest req) {
        return ApiResponse.ok(ncService.taoNc(req));
    }

    @GetMapping("/{so}")
    public ApiResponse<Nc> layNc(@PathVariable("so") String so) {
        return ApiResponse.ok(ncService.layNc(so));
    }

    @PostMapping("/{so}/close-fast")
    public ApiResponse<Nc> closeFast(@PathVariable("so") String so, @Valid @RequestBody CloseFastRequest req) {
        return ApiResponse.ok(ncService.closeFast(so, req));
    }

    @PostMapping("/{so}/escalate")
    public ApiResponse<Nc> escalate(@PathVariable("so") String so, @Valid @RequestBody EscalateRequest req) {
        return ApiResponse.ok(ncService.nangBac(so, req));
    }

    @PostMapping("/{so}/submit-approval")
    public ApiResponse<Nc> submitApproval(@PathVariable("so") String so, @Valid @RequestBody SubmitApprovalRequest req) {
        return ApiResponse.ok(ncService.submitApproval(so, req));
    }

    @PostMapping("/{so}/approve")
    public ApiResponse<Nc> approve(@PathVariable("so") String so, @Valid @RequestBody ApproveRequest req) {
        return ApiResponse.ok(ncService.approve(so, req));
    }

    @PostMapping("/{so}/reject")
    public ApiResponse<Nc> reject(@PathVariable("so") String so, @Valid @RequestBody RejectRequest req) {
        return ApiResponse.ok(ncService.reject(so, req));
    }

    @PostMapping("/{so}/cancel")
    public ApiResponse<Nc> cancel(@PathVariable("so") String so, @Valid @RequestBody RejectRequest req) {
        return ApiResponse.ok(ncService.cancel(so, req));
    }

    @PostMapping("/{so}/confirm-done")
    public ApiResponse<Nc> confirmDone(@PathVariable("so") String so, @Valid @RequestBody ConfirmDoneRequest req) {
        return ApiResponse.ok(ncService.confirmDone(so, req));
    }

    @PostMapping("/{so}/verify-close")
    public ApiResponse<Nc> verifyClose(@PathVariable("so") String so, @Valid @RequestBody VerifyCloseRequest req) {
        return ApiResponse.ok(ncService.verifyClose(so, req));
    }

    @PostMapping("/{so}/containment")
    public ApiResponse<Nc> containment(@PathVariable("so") String so, @Valid @RequestBody ContainmentRequest req) {
        return ApiResponse.ok(ncService.containment(so, req));
    }

    @PostMapping("/{so}/doi-tuong/{id}/unhold")
    public ApiResponse<NcDoiTuong> unhold(@PathVariable("so") String so,
                                          @PathVariable("id") Long id,
                                          @Valid @RequestBody UnholdRequest req) {
        return ApiResponse.ok(ncService.unholdDongLo(so, id, req));
    }

    @GetMapping("/{so}/doi-tuong")
    public ApiResponse<java.util.List<NcDoiTuong>> doiTuong(@PathVariable("so") String so) {
        return ApiResponse.ok(ncService.doiTuongCua(so));
    }
}
