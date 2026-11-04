package com.dqh.nccapa.masterdata.controller;

import com.dqh.nccapa.masterdata.service.MasterDataLoai;
import com.dqh.nccapa.masterdata.service.MasterDataService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * Màn hình quản lý master data (chỉ ADMIN). Non-ADMIN bị redirect về landing ở SERVER (không chỉ ẩn menu).
 * Mọi thao tác ghi gọi {@link MasterDataCrudController} (cũng chặn non-ADMIN).
 */
@Controller
public class MasterDataViewController {

    private final MasterDataService service;
    private final MasterDataAdminGuard guard;

    public MasterDataViewController(MasterDataService service, MasterDataAdminGuard guard) {
        this.service = service;
        this.guard = guard;
    }

    @GetMapping("/quan-tri/master-data")
    public String macDinh(HttpSession session) {
        if (!guard.laAdmin(session)) return "redirect:/home";
        return "redirect:/quan-tri/master-data/" + MasterDataLoai.NHA_MAY.getSlug();
    }

    @GetMapping("/quan-tri/master-data/{loai}")
    public String danhMuc(@PathVariable("loai") String slug,
                          @RequestParam(value = "q", required = false) String q,
                          HttpSession session, Model model) {
        if (!guard.laAdmin(session)) return "redirect:/home";
        MasterDataLoai loai = MasterDataLoai.tuSlug(slug);
        model.addAttribute("loai", loai.getSlug());
        model.addAttribute("loaiNhan", loai.getNhan());
        model.addAttribute("loaiList", MasterDataLoai.values());
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("cols", cot(loai));
        model.addAttribute("rows", service.danhSach(loai, q));
        model.addAttribute("nhaMayOpts", loai == MasterDataLoai.MA_HANG
                ? service.luaChonTatCa(MasterDataLoai.NHA_MAY) : List.of());
        model.addAttribute("nhomLoiOpts", loai == MasterDataLoai.MA_LOI
                ? service.luaChonTatCa(MasterDataLoai.NHOM_LOI) : List.of());
        return "master-data";
    }

    private static Map<String, String> c(String key, String label, String fmt) {
        return Map.of("key", key, "label", label, "fmt", fmt);
    }

    /** Cột hiển thị theo danh mục; fmt: text|bool|muc|nguy|nhomN|trangThai. */
    private static List<Map<String, String>> cot(MasterDataLoai loai) {
        return switch (loai) {
            case NHA_MAY -> List.of(c("ma", "Mã", "text"), c("ten", "Tên", "text"), c("dangDung", "Trạng thái", "trangThai"));
            case MA_HANG -> List.of(c("ma", "Mã", "text"), c("ten", "Tên", "text"), c("nhaMay", "Nhà máy", "text"),
                    c("dangDung", "Trạng thái", "trangThai"));
            case NHOM_LOI -> List.of(c("ma", "Mã", "text"), c("ten", "Tên nhóm", "text"),
                    c("theoNguon", "Theo nguồn", "text"), c("dangDung", "Trạng thái", "trangThai"));
            case NCC -> List.of(c("ma", "Mã", "text"), c("ten", "Tên", "text"), c("coGiayCn", "Có giấy CN", "bool"),
                    c("cnHetHanNgay", "CN hết hạn", "text"), c("dangDung", "Trạng thái", "trangThai"));
            case MA_LOI -> List.of(c("ma", "Mã lỗi", "text"), c("nhomLoiId", "Nhóm", "text"), c("ten", "Tên mã lỗi", "text"),
                    c("mucNghiemTrongDefault", "Mức mặc định", "muc"), c("moiNguyDefault", "Mối nguy", "nguy"),
                    c("batBuocKhoanhVung", "Bắt buộc khoanh vùng", "bool"), c("nhomNKiemChung", "Nhóm N kiểm chứng", "nhomN"),
                    c("nguoiSoHuu", "Người sở hữu", "text"), c("hieuLucTu", "Hiệu lực từ", "text"),
                    c("hieuLucDen", "Hiệu lực đến", "text"), c("dangDung", "Trạng thái", "trangThai"));
        };
    }
}
