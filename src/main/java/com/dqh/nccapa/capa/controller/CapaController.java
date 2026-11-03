package com.dqh.nccapa.capa.controller;

import com.dqh.nccapa.capa.dto.*;
import com.dqh.nccapa.capa.entity.Capa;
import com.dqh.nccapa.capa.entity.CapaAction;
import com.dqh.nccapa.capa.entity.CapaActionExtension;
import com.dqh.nccapa.capa.service.CapaService;
import com.dqh.nccapa.common.error.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class CapaController {

    private final CapaService capaService;

    public CapaController(CapaService capaService) {
        this.capaService = capaService;
    }

    @PostMapping("/nc/{so}/capa")
    public ApiResponse<Capa> taoCapa(@PathVariable("so") String so, @Valid @RequestBody TaoCapaRequest req) {
        return ApiResponse.ok(capaService.taoTuNc(so, req));
    }

    @GetMapping("/capa/{id}")
    public ApiResponse<Capa> layCapa(@PathVariable("id") Long id) {
        return ApiResponse.ok(capaService.layCapa(id));
    }

    @PostMapping("/capa/{id}/to-investigating")
    public ApiResponse<Capa> toInvestigating(@PathVariable("id") Long id, @Valid @RequestBody ToInvestigatingRequest req) {
        return ApiResponse.ok(capaService.toInvestigating(id, req));
    }

    @PostMapping("/capa/{id}/to-action-plan")
    public ApiResponse<Capa> toActionPlan(@PathVariable("id") Long id, @Valid @RequestBody ToActionPlanRequest req) {
        return ApiResponse.ok(capaService.toActionPlan(id, req));
    }

    @PostMapping("/capa/{id}/actions")
    public ApiResponse<CapaAction> themAction(@PathVariable("id") Long id, @Valid @RequestBody AddActionRequest req) {
        return ApiResponse.ok(capaService.themAction(id, req));
    }

    @PostMapping("/capa/{id}/to-processing")
    public ApiResponse<Capa> toProcessing(@PathVariable("id") Long id, @Valid @RequestBody ToProcessingRequest req) {
        return ApiResponse.ok(capaService.toProcessing(id, req));
    }

    @PostMapping("/capa/actions/{aid}/complete")
    public ApiResponse<CapaAction> complete(@PathVariable("aid") Long aid, @Valid @RequestBody CompleteActionRequest req) {
        return ApiResponse.ok(capaService.completeAction(aid, req));
    }

    @PostMapping("/capa/actions/{aid}/extension")
    public ApiResponse<CapaActionExtension> extension(@PathVariable("aid") Long aid, @Valid @RequestBody ExtensionRequest req) {
        return ApiResponse.ok(capaService.yeuCauGiaHan(aid, req));
    }

    @PostMapping("/capa/actions/ext/{eid}/approve")
    public ApiResponse<CapaActionExtension> approveExt(@PathVariable("eid") Long eid, @Valid @RequestBody ApproveExtensionRequest req) {
        return ApiResponse.ok(capaService.duyetGiaHan(eid, req));
    }

    @PostMapping("/capa/{id}/to-monitoring")
    public ApiResponse<Capa> toMonitoring(@PathVariable("id") Long id, @Valid @RequestBody ToMonitoringRequest req) {
        return ApiResponse.ok(capaService.toMonitoring(id, req));
    }

    @GetMapping("/capa/{id}/verification-status")
    public ApiResponse<VerificationStatus> verificationStatus(@PathVariable("id") Long id) {
        return ApiResponse.ok(capaService.verificationStatus(id));
    }

    @PostMapping("/capa/{id}/postpone-reminder")
    public ApiResponse<Capa> postpone(@PathVariable("id") Long id, @Valid @RequestBody PostponeRequest req) {
        return ApiResponse.ok(capaService.postponeReminder(id, req));
    }

    @PostMapping("/capa/{id}/confirm-effective")
    public ApiResponse<Capa> confirmEffective(@PathVariable("id") Long id, @Valid @RequestBody ConfirmEffectiveRequest req) {
        return ApiResponse.ok(capaService.confirmEffective(id, req));
    }

    @PostMapping("/capa/{id}/mark-ineffective")
    public ApiResponse<IneffectiveResult> markIneffective(@PathVariable("id") Long id, @Valid @RequestBody MarkIneffectiveRequest req) {
        return ApiResponse.ok(capaService.markIneffective(id, req));
    }

    @PostMapping("/capa/{id}/8d/enable")
    public ApiResponse<java.util.List<com.dqh.nccapa.capa.entity.Capa8dStep>> bat8d(
            @PathVariable("id") Long id,
            @org.springframework.web.bind.annotation.RequestParam("actor") String actor) {
        return ApiResponse.ok(capaService.bat8d(id, actor));
    }

    @GetMapping("/capa/{id}/8d")
    public ApiResponse<java.util.List<com.dqh.nccapa.capa.entity.Capa8dStep>> steps8d(@PathVariable("id") Long id) {
        return ApiResponse.ok(capaService.steps8d(id));
    }

    @PostMapping("/capa/{id}/8d/{buoc}/complete")
    public ApiResponse<com.dqh.nccapa.capa.entity.Capa8dStep> hoanThanhBuoc(
            @PathVariable("id") Long id, @PathVariable("buoc") String buoc,
            @Valid @RequestBody com.dqh.nccapa.capa.dto.CompleteStepRequest req) {
        return ApiResponse.ok(capaService.hoanThanhBuoc8d(id, buoc, req));
    }
}

