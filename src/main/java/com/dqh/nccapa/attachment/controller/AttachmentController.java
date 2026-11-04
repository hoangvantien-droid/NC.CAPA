package com.dqh.nccapa.attachment.controller;

import com.dqh.nccapa.attachment.entity.Attachment;
import com.dqh.nccapa.attachment.service.AttachmentService;
import com.dqh.nccapa.common.error.ApiResponse;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** REST đính kèm file/ảnh cho NC & CAPA action (Nhóm 2). */
@RestController
@RequestMapping("/api/v1/attachment")
public class AttachmentController {

    private final AttachmentService service;

    public AttachmentController(AttachmentService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Attachment> upload(
            @RequestParam("doiTuong") String doiTuong,
            @RequestParam("doiTuongId") String doiTuongId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "actor", required = false) String actor) {
        return ApiResponse.ok(service.luu(doiTuong, doiTuongId, file, actor));
    }

    @GetMapping
    public ApiResponse<List<Attachment>> danhSach(
            @RequestParam("doiTuong") String doiTuong,
            @RequestParam("doiTuongId") String doiTuongId,
            @RequestParam(value = "actor", required = false) String actor) {
        return ApiResponse.ok(service.danhSach(doiTuong, doiTuongId, actor));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable("id") Long id,
            @RequestParam(value = "actor", required = false) String actor) {
        AttachmentService.TaiVeResult kq = service.taiVe(id, actor);
        Attachment att = kq.getAttachment();
        MediaType mediaType = parseContentType(att.getContentType());
        String tenFile = URLEncoder.encode(att.getTenFile(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + tenFile)
                .contentType(mediaType)
                .body(kq.getResource());
    }

    /** Parse content-type an toàn: null/blank hoặc chuỗi lỗi định dạng → octet-stream. */
    private static MediaType parseContentType(String ct) {
        if (ct == null || ct.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(ct);
        } catch (InvalidMediaTypeException e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
