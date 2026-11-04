package com.dqh.nccapa.masterdata.controller;

import com.dqh.nccapa.common.error.ApiResponse;
import com.dqh.nccapa.masterdata.dto.MasterDataRequest;
import com.dqh.nccapa.masterdata.service.MasterDataLoai;
import com.dqh.nccapa.masterdata.service.MasterDataService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * REST CRUD master data (nha-may, ma-hang, nhom-loi, ma-loi, ncc). Không có DELETE: dùng "ngung-dung".
 * Mọi endpoint (kể cả đọc) từ chối với 403 nếu user hiện tại trong session không phải ADMIN.
 */
@RestController
@RequestMapping("/api/v1/masterdata")
public class MasterDataCrudController {

    private final MasterDataService service;
    private final MasterDataAdminGuard guard;

    public MasterDataCrudController(MasterDataService service, MasterDataAdminGuard guard) {
        this.service = service;
        this.guard = guard;
    }

    @GetMapping("/{loai}")
    public ApiResponse<List<Map<String, Object>>> danhSach(@PathVariable("loai") String loai,
                                                           @RequestParam(value = "q", required = false) String q,
                                                           HttpSession session) {
        guard.yeuCauAdmin(session);
        return ApiResponse.ok(service.danhSach(MasterDataLoai.tuSlug(loai), q));
    }

    @PostMapping("/{loai}")
    public ApiResponse<Void> tao(@PathVariable("loai") String loai, @RequestBody MasterDataRequest req,
                                 HttpSession session) {
        guard.yeuCauAdmin(session);
        service.tao(MasterDataLoai.tuSlug(loai), req);
        return ApiResponse.ok(null);
    }

    @PutMapping("/{loai}/{ma}")
    public ApiResponse<Void> capNhat(@PathVariable("loai") String loai, @PathVariable("ma") String ma,
                                     @RequestBody MasterDataRequest req, HttpSession session) {
        guard.yeuCauAdmin(session);
        service.capNhat(MasterDataLoai.tuSlug(loai), ma, req);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{loai}/{ma}/ngung-dung")
    public ApiResponse<Void> ngungDung(@PathVariable("loai") String loai, @PathVariable("ma") String ma,
                                       HttpSession session) {
        guard.yeuCauAdmin(session);
        service.ngungDung(MasterDataLoai.tuSlug(loai), ma);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{loai}/{ma}/kich-hoat")
    public ApiResponse<Void> kichHoat(@PathVariable("loai") String loai, @PathVariable("ma") String ma,
                                      HttpSession session) {
        guard.yeuCauAdmin(session);
        service.kichHoat(MasterDataLoai.tuSlug(loai), ma);
        return ApiResponse.ok(null);
    }
}
