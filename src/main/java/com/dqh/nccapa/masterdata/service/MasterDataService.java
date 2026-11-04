package com.dqh.nccapa.masterdata.service;

import com.dqh.nccapa.common.enums.HazardType;
import com.dqh.nccapa.common.enums.NKiemChungGroup;
import com.dqh.nccapa.common.enums.Severity;
import com.dqh.nccapa.common.error.BusinessException;
import com.dqh.nccapa.masterdata.dto.MasterDataRequest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Quản lý master data (xem/tìm/thêm/sửa/ngừng dùng) cho 5 danh mục. KHÔNG xoá cứng: "ngừng dùng" =
 * cờ active=0 (nha_may, ma_hang, nhom_loi, ncc) hoặc đặt hieu_luc_den (ma_loi). Mã (khoá chính) không đổi
 * được sau khi tạo. Việc kiểm quyền ADMIN làm ở controller (cần session), service không biết user.
 */
@Service
public class MasterDataService {

    private static final Pattern MA_HOP_LE = Pattern.compile("[A-Za-z0-9_.-]+");
    private static final Set<String> COT_BOOL = Set.of("active", "coGiayCn", "batBuocKhoanhVung");

    private final JdbcTemplate jdbc;

    public MasterDataService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ---------------- Đọc ----------------

    /** Danh sách (lọc theo mã/tên nếu {@code q} không rỗng). Giá trị đã chuẩn hoá: ngày -> chuỗi ISO, cờ -> boolean. */
    public List<Map<String, Object>> danhSach(MasterDataLoai loai, String q) {
        String like = (q == null || q.isBlank()) ? null : "%" + q.trim() + "%";
        String sql = switch (loai) {
            case NHA_MAY -> "SELECT ma AS ma, ten AS ten, active AS active FROM nha_may"
                    + (like != null ? " WHERE ma LIKE ? OR ten LIKE ?" : "") + " ORDER BY ma";
            case MA_HANG -> "SELECT ma AS ma, ten AS ten, nha_may AS nhaMay, active AS active FROM ma_hang"
                    + (like != null ? " WHERE ma LIKE ? OR ten LIKE ?" : "") + " ORDER BY ma";
            case NHOM_LOI -> "SELECT nhom_loi_id AS ma, ten_nhom AS ten, theo_nguon AS theoNguon, active AS active "
                    + "FROM nhom_loi" + (like != null ? " WHERE nhom_loi_id LIKE ? OR ten_nhom LIKE ?" : "")
                    + " ORDER BY nhom_loi_id";
            case NCC -> "SELECT ma AS ma, ten AS ten, co_giay_cn AS coGiayCn, cn_het_han_ngay AS cnHetHanNgay, "
                    + "active AS active FROM ncc" + (like != null ? " WHERE ma LIKE ? OR ten LIKE ?" : "")
                    + " ORDER BY ma";
            case MA_LOI -> "SELECT ma_loi_id AS ma, nhom_loi_id AS nhomLoiId, ten_ma_loi AS ten, "
                    + "muc_nghiem_trong_default AS mucNghiemTrongDefault, moi_nguy_default AS moiNguyDefault, "
                    + "bat_buoc_khoanh_vung AS batBuocKhoanhVung, nhom_n_kiem_chung AS nhomNKiemChung, "
                    + "nguoi_so_huu AS nguoiSoHuu, hieu_luc_tu AS hieuLucTu, hieu_luc_den AS hieuLucDen "
                    + "FROM ma_loi" + (like != null ? " WHERE ma_loi_id LIKE ? OR ten_ma_loi LIKE ?" : "")
                    + " ORDER BY ma_loi_id";
        };
        List<Map<String, Object>> raw = like != null ? jdbc.queryForList(sql, like, like) : jdbc.queryForList(sql);
        LocalDate homNay = LocalDate.now();
        return raw.stream().map(r -> chuanHoa(r, loai, homNay)).toList();
    }

    private Map<String, Object> chuanHoa(Map<String, Object> raw, MasterDataLoai loai, LocalDate homNay) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : raw.entrySet()) {
            Object v = e.getValue();
            if (COT_BOOL.contains(e.getKey())) {
                v = (v instanceof Boolean b) ? b : (v instanceof Number n && n.intValue() != 0);
            } else if (v != null && !(v instanceof String) && !(v instanceof Number)) {
                v = v.toString(); // java.sql.Date / LocalDate -> yyyy-MM-dd
            }
            out.put(e.getKey(), v);
        }
        if (loai == MasterDataLoai.MA_LOI) {
            String den = (String) out.get("hieuLucDen");
            out.put("dangDung", den == null || !homNay.isAfter(LocalDate.parse(den)));
        } else {
            out.put("dangDung", out.get("active"));
        }
        return out;
    }

    /** Lựa chọn {value,label} cho dropdown (chỉ bản ghi đang dùng). */
    public List<Map<String, String>> luaChonDangDung(MasterDataLoai loai) {
        String sql = switch (loai) {
            case NHA_MAY -> "SELECT ma, ten FROM nha_may WHERE active = 1 ORDER BY ma";
            case NHOM_LOI -> "SELECT nhom_loi_id AS ma, ten_nhom AS ten FROM nhom_loi WHERE active = 1 ORDER BY nhom_loi_id";
            default -> throw new IllegalArgumentException("Không hỗ trợ dropdown cho " + loai);
        };
        return jdbc.query(sql, (rs, i) -> Map.of("value", rs.getString("ma"),
                "label", rs.getString("ma") + " — " + rs.getString("ten")));
    }

    /**
     * Lựa chọn cho form SỬA: gồm cả bản ghi ngừng dùng (nhãn "(ngừng dùng)", ngung=1) để cha hiện tại
     * của bản ghi luôn có trong dropdown, tránh form âm thầm chuyển sang cha khác.
     */
    public List<Map<String, String>> luaChonTatCa(MasterDataLoai loai) {
        String sql = switch (loai) {
            case NHA_MAY -> "SELECT ma, ten, active FROM nha_may ORDER BY ma";
            case NHOM_LOI -> "SELECT nhom_loi_id AS ma, ten_nhom AS ten, active FROM nhom_loi ORDER BY nhom_loi_id";
            default -> throw new IllegalArgumentException("Không hỗ trợ dropdown cho " + loai);
        };
        return jdbc.query(sql, (rs, i) -> {
            boolean ngung = rs.getInt("active") == 0;
            return Map.of("value", rs.getString("ma"),
                    "label", rs.getString("ma") + " — " + rs.getString("ten") + (ngung ? " (ngừng dùng)" : ""),
                    "ngung", ngung ? "1" : "0");
        });
    }

    // ---------------- Ghi ----------------

    @Transactional
    public void tao(MasterDataLoai loai, MasterDataRequest req) {
        String ma = chuanMa(loai, req.ma);
        String ten = batBuoc(req.ten, "ten", "Tên là bắt buộc", 255);
        if (tonTai(loai, ma)) {
            throw BusinessException.businessRule("Mã \"" + ma + "\" đã tồn tại", "ma");
        }
        try {
            switch (loai) {
                case NHA_MAY -> jdbc.update("INSERT INTO nha_may(ma, ten, active) VALUES (?,?,1)", ma, ten);
                case MA_HANG -> {
                    String nm = batBuoc(req.nhaMay, "nhaMay", "Nhà máy là bắt buộc", 16);
                    kiemNhaMayDangDung(nm);
                    jdbc.update("INSERT INTO ma_hang(ma, ten, nha_may, active) VALUES (?,?,?,1)", ma, ten, nm);
                }
                case NHOM_LOI -> jdbc.update("INSERT INTO nhom_loi(nhom_loi_id, ten_nhom, theo_nguon, active) "
                        + "VALUES (?,?,?,1)", ma, ten, batBuoc(req.theoNguon, "theoNguon", "Theo nguồn là bắt buộc", 64));
                case NCC -> jdbc.update("INSERT INTO ncc(ma, ten, co_giay_cn, cn_het_han_ngay, active) VALUES (?,?,?,?,1)",
                        ma, ten, Boolean.TRUE.equals(req.coGiayCn) ? 1 : 0, ngay(req.cnHetHanNgay, "cnHetHanNgay"));
                case MA_LOI -> taoMaLoi(ma, ten, req);
            }
        } catch (DuplicateKeyException e) {
            throw BusinessException.businessRule("Mã \"" + ma + "\" đã tồn tại", "ma");
        }
    }

    private void taoMaLoi(String ma, String ten, MasterDataRequest req) {
        String nhom = batBuoc(req.nhomLoiId, "nhomLoiId", "Nhóm lỗi là bắt buộc", 8);
        kiemNhomLoiDangDung(nhom);
        Severity muc = enumHopLe(Severity.class, req.mucNghiemTrongDefault, "mucNghiemTrongDefault", "Mức nghiêm trọng");
        HazardType nguy = enumHopLe(HazardType.class, req.moiNguyDefault, "moiNguyDefault", "Mối nguy");
        NKiemChungGroup nhomN = enumHopLe(NKiemChungGroup.class, req.nhomNKiemChung, "nhomNKiemChung", "Nhóm N kiểm chứng");
        LocalDate tu = ngay(req.hieuLucTu, "hieuLucTu");
        if (tu == null) throw BusinessException.businessRule("Hiệu lực từ là bắt buộc", "hieuLucTu");
        LocalDate den = ngay(req.hieuLucDen, "hieuLucDen");
        if (den != null && den.isBefore(tu)) {
            throw BusinessException.businessRule("Hiệu lực đến không được trước hiệu lực từ", "hieuLucDen");
        }
        String chu = (req.nguoiSoHuu == null || req.nguoiSoHuu.isBlank()) ? "QA_TAP_DOAN" : req.nguoiSoHuu.trim();
        if (chu.length() > 64) throw BusinessException.businessRule("Người sở hữu tối đa 64 ký tự", "nguoiSoHuu");
        jdbc.update("INSERT INTO ma_loi(ma_loi_id, nhom_loi_id, ten_ma_loi, muc_nghiem_trong_default, "
                        + "moi_nguy_default, bat_buoc_khoanh_vung, nhom_n_kiem_chung, nguoi_so_huu, hieu_luc_tu, hieu_luc_den) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?)",
                ma, nhom, ten, muc.name(), nguy.name(), Boolean.TRUE.equals(req.batBuocKhoanhVung) ? 1 : 0,
                nhomN.name(), chu, tu, den);
    }

    /** Sửa thuộc tính (KHÔNG sửa mã). Trường null = giữ nguyên. */
    @Transactional
    public void capNhat(MasterDataLoai loai, String ma, MasterDataRequest req) {
        Map<String, Object> cu = layMot(loai, ma);
        String ten = req.ten != null ? batBuoc(req.ten, "ten", "Tên là bắt buộc", 255) : (String) cu.get("ten");
        switch (loai) {
            case NHA_MAY -> jdbc.update("UPDATE nha_may SET ten = ? WHERE ma = ?", ten, ma);
            case MA_HANG -> {
                String nm = req.nhaMay != null ? batBuoc(req.nhaMay, "nhaMay", "Nhà máy là bắt buộc", 16)
                        : (String) cu.get("nhaMay");
                if (!nm.equals(cu.get("nhaMay"))) kiemNhaMayDangDung(nm);
                jdbc.update("UPDATE ma_hang SET ten = ?, nha_may = ? WHERE ma = ?", ten, nm, ma);
            }
            case NHOM_LOI -> {
                String nguon = req.theoNguon != null ? batBuoc(req.theoNguon, "theoNguon", "Theo nguồn là bắt buộc", 64)
                        : (String) cu.get("theoNguon");
                jdbc.update("UPDATE nhom_loi SET ten_nhom = ?, theo_nguon = ? WHERE nhom_loi_id = ?", ten, nguon, ma);
            }
            case NCC -> {
                boolean gcn = req.coGiayCn != null ? req.coGiayCn : (Boolean) cu.get("coGiayCn");
                Object het = req.cnHetHanNgay != null ? ngay(req.cnHetHanNgay, "cnHetHanNgay") : cu.get("cnHetHanNgay");
                jdbc.update("UPDATE ncc SET ten = ?, co_giay_cn = ?, cn_het_han_ngay = ? WHERE ma = ?",
                        ten, gcn ? 1 : 0, het, ma);
            }
            case MA_LOI -> capNhatMaLoi(ma, ten, req, cu);
        }
    }

    private void capNhatMaLoi(String ma, String ten, MasterDataRequest req, Map<String, Object> cu) {
        String nhom = req.nhomLoiId != null ? batBuoc(req.nhomLoiId, "nhomLoiId", "Nhóm lỗi là bắt buộc", 8)
                : (String) cu.get("nhomLoiId");
        if (!nhom.equals(cu.get("nhomLoiId"))) kiemNhomLoiDangDung(nhom);
        Severity muc = req.mucNghiemTrongDefault != null
                ? enumHopLe(Severity.class, req.mucNghiemTrongDefault, "mucNghiemTrongDefault", "Mức nghiêm trọng")
                : Severity.valueOf((String) cu.get("mucNghiemTrongDefault"));
        HazardType nguy = req.moiNguyDefault != null
                ? enumHopLe(HazardType.class, req.moiNguyDefault, "moiNguyDefault", "Mối nguy")
                : HazardType.valueOf((String) cu.get("moiNguyDefault"));
        NKiemChungGroup nhomN = req.nhomNKiemChung != null
                ? enumHopLe(NKiemChungGroup.class, req.nhomNKiemChung, "nhomNKiemChung", "Nhóm N kiểm chứng")
                : NKiemChungGroup.valueOf((String) cu.get("nhomNKiemChung"));
        boolean kv = req.batBuocKhoanhVung != null ? req.batBuocKhoanhVung : (Boolean) cu.get("batBuocKhoanhVung");
        String chu = req.nguoiSoHuu != null && !req.nguoiSoHuu.isBlank() ? req.nguoiSoHuu.trim()
                : (String) cu.get("nguoiSoHuu");
        if (chu.length() > 64) throw BusinessException.businessRule("Người sở hữu tối đa 64 ký tự", "nguoiSoHuu");
        LocalDate tu = req.hieuLucTu != null ? ngay(req.hieuLucTu, "hieuLucTu") : LocalDate.parse((String) cu.get("hieuLucTu"));
        if (tu == null) throw BusinessException.businessRule("Hiệu lực từ là bắt buộc", "hieuLucTu");
        LocalDate denCu = cu.get("hieuLucDen") == null ? null : LocalDate.parse((String) cu.get("hieuLucDen"));
        LocalDate den = req.hieuLucDen != null ? ngay(req.hieuLucDen, "hieuLucDen") : denCu;
        boolean denDoi = den != null && !den.equals(denCu);
        if (denDoi && den.isBefore(tu)) {
            throw BusinessException.businessRule("Hiệu lực đến không được trước hiệu lực từ", "hieuLucDen");
        }
        jdbc.update("UPDATE ma_loi SET nhom_loi_id = ?, ten_ma_loi = ?, muc_nghiem_trong_default = ?, "
                        + "moi_nguy_default = ?, bat_buoc_khoanh_vung = ?, nhom_n_kiem_chung = ?, nguoi_so_huu = ?, "
                        + "hieu_luc_tu = ?, hieu_luc_den = ? WHERE ma_loi_id = ?",
                nhom, ten, muc.name(), nguy.name(), kv ? 1 : 0, nhomN.name(), chu, tu, den, ma);
    }

    /** Ngừng dùng (thay cho xoá): ma_loi đặt hieu_luc_den = hôm qua (hết hiệu lực từ hôm nay); loại khác active=0. */
    @Transactional
    public void ngungDung(MasterDataLoai loai, String ma) {
        layMot(loai, ma);
        datTrangThai(loai, ma, false);
    }

    /** Dùng lại: ma_loi xoá hieu_luc_den; loại khác active=1. */
    @Transactional
    public void kichHoat(MasterDataLoai loai, String ma) {
        layMot(loai, ma);
        datTrangThai(loai, ma, true);
    }

    private void datTrangThai(MasterDataLoai loai, String ma, boolean dung) {
        switch (loai) {
            case NHA_MAY -> jdbc.update("UPDATE nha_may SET active = ? WHERE ma = ?", dung ? 1 : 0, ma);
            case MA_HANG -> jdbc.update("UPDATE ma_hang SET active = ? WHERE ma = ?", dung ? 1 : 0, ma);
            case NHOM_LOI -> jdbc.update("UPDATE nhom_loi SET active = ? WHERE nhom_loi_id = ?", dung ? 1 : 0, ma);
            case NCC -> jdbc.update("UPDATE ncc SET active = ? WHERE ma = ?", dung ? 1 : 0, ma);
            case MA_LOI -> jdbc.update("UPDATE ma_loi SET hieu_luc_den = ? WHERE ma_loi_id = ?",
                    dung ? null : LocalDate.now().minusDays(1), ma);
        }
    }

    // ---------------- Helper ----------------

    private Map<String, Object> layMot(MasterDataLoai loai, String ma) {
        return danhSach(loai, null).stream().filter(r -> ma.equals(r.get("ma"))).findFirst()
                .orElseThrow(() -> BusinessException.notFound("Không tìm thấy " + loai.getNhan() + ": " + ma));
    }

    private boolean tonTai(MasterDataLoai loai, String ma) {
        String sql = switch (loai) {
            case NHA_MAY -> "SELECT COUNT(*) FROM nha_may WHERE ma = ?";
            case MA_HANG -> "SELECT COUNT(*) FROM ma_hang WHERE ma = ?";
            case NHOM_LOI -> "SELECT COUNT(*) FROM nhom_loi WHERE nhom_loi_id = ?";
            case NCC -> "SELECT COUNT(*) FROM ncc WHERE ma = ?";
            case MA_LOI -> "SELECT COUNT(*) FROM ma_loi WHERE ma_loi_id = ?";
        };
        Integer n = jdbc.queryForObject(sql, Integer.class, ma);
        return n != null && n > 0;
    }

    private void kiemNhaMayDangDung(String nhaMay) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM nha_may WHERE ma = ? AND active = 1", Integer.class, nhaMay);
        if (n == null || n == 0) {
            throw BusinessException.businessRule("Nhà máy không tồn tại hoặc đã ngừng dùng: " + nhaMay, "nhaMay");
        }
    }

    private void kiemNhomLoiDangDung(String nhom) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM nhom_loi WHERE nhom_loi_id = ? AND active = 1",
                Integer.class, nhom);
        if (n == null || n == 0) {
            throw BusinessException.businessRule("Nhóm lỗi không tồn tại hoặc đã ngừng dùng: " + nhom, "nhomLoiId");
        }
    }

    private static String chuanMa(MasterDataLoai loai, String ma) {
        String m = batBuoc(ma, "ma", "Mã là bắt buộc", switch (loai) {
            case NHA_MAY, MA_LOI -> 16;
            case NHOM_LOI -> 8;
            case MA_HANG, NCC -> 32;
        });
        if (!MA_HOP_LE.matcher(m).matches()) {
            throw BusinessException.businessRule("Mã chỉ gồm chữ, số, '-', '_', '.'", "ma");
        }
        return m;
    }

    private static String batBuoc(String v, String field, String msg, int max) {
        if (v == null || v.isBlank()) throw BusinessException.businessRule(msg, field);
        String t = v.trim();
        if (t.length() > max) throw BusinessException.businessRule("Tối đa " + max + " ký tự", field);
        return t;
    }

    private static LocalDate ngay(String v, String field) {
        if (v == null || v.isBlank()) return null;
        try {
            return LocalDate.parse(v.trim());
        } catch (DateTimeParseException e) {
            throw BusinessException.businessRule("Ngày không hợp lệ (yyyy-MM-dd)", field);
        }
    }

    private static <E extends Enum<E>> E enumHopLe(Class<E> type, String v, String field, String nhan) {
        if (v == null || v.isBlank()) throw BusinessException.businessRule(nhan + " là bắt buộc", field);
        try {
            return Enum.valueOf(type, v.trim());
        } catch (IllegalArgumentException e) {
            throw BusinessException.businessRule(nhan + " không hợp lệ: " + v, field);
        }
    }
}
