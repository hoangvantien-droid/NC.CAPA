package com.dqh.nccapa.pr.controller;

import com.dqh.nccapa.common.error.ApiResponse;
import com.dqh.nccapa.pr.dto.PrActionRequest;
import com.dqh.nccapa.pr.dto.TaoPrRequest;
import com.dqh.nccapa.pr.entity.Pr;
import com.dqh.nccapa.pr.service.PrService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/pr")
public class PrController {

    private final PrService prService;

    public PrController(PrService prService) {
        this.prService = prService;
    }

    @PostMapping
    public ApiResponse<Pr> tao(@Valid @RequestBody TaoPrRequest req) {
        return ApiResponse.ok(prService.tao(req));
    }

    @GetMapping("/{id}")
    public ApiResponse<Pr> lay(@PathVariable("id") Long id) {
        return ApiResponse.ok(prService.lay(id));
    }

    @PostMapping("/{id}/to-analyzing")
    public ApiResponse<Pr> toAnalyzing(@PathVariable("id") Long id, @Valid @RequestBody PrActionRequest req) {
        return ApiResponse.ok(prService.toAnalyzing(id, req));
    }

    @PostMapping("/{id}/submit")
    public ApiResponse<Pr> submit(@PathVariable("id") Long id, @Valid @RequestBody PrActionRequest req) {
        return ApiResponse.ok(prService.submit(id, req));
    }

    @PostMapping("/{id}/approve")
    public ApiResponse<Pr> approve(@PathVariable("id") Long id, @Valid @RequestBody PrActionRequest req) {
        return ApiResponse.ok(prService.approve(id, req));
    }

    @PostMapping("/{id}/reject")
    public ApiResponse<Pr> reject(@PathVariable("id") Long id, @Valid @RequestBody PrActionRequest req) {
        return ApiResponse.ok(prService.reject(id, req));
    }

    @PostMapping("/{id}/implement")
    public ApiResponse<Pr> implement(@PathVariable("id") Long id, @Valid @RequestBody PrActionRequest req) {
        return ApiResponse.ok(prService.implement(id, req));
    }

    @PostMapping("/{id}/close")
    public ApiResponse<Pr> close(@PathVariable("id") Long id, @Valid @RequestBody PrActionRequest req) {
        return ApiResponse.ok(prService.close(id, req));
    }
}
