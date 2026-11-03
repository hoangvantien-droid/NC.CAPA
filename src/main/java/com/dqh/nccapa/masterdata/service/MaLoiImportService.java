package com.dqh.nccapa.masterdata.service;

import com.dqh.nccapa.common.enums.HazardType;
import com.dqh.nccapa.common.enums.NKiemChungGroup;
import com.dqh.nccapa.common.enums.Severity;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Import bộ mã lỗi 2 cấp (nhóm lỗi + mã lỗi) từ file Excel (.xlsx) qua Apache POI.
 *
 * <p>Đây là kênh BỔ SUNG/thay thế seed tay trong {@code DemoSeeder}: ghi vào bảng master data
 * có sẵn ({@code nhom_loi}, {@code ma_loi}) qua {@link JdbcTemplate}, KHÔNG đổi schema, KHÔNG migration.
 *
 * <p>Quy ước workbook (đọc theo TÊN HEADER ở dòng đầu mỗi sheet, không hardcode chỉ số cột):
 * <ul>
 *   <li>Sheet {@code NhomLoi}: {@code nhom_loi_id}, {@code ten_nhom}, {@code theo_nguon}.</li>
 *   <li>Sheet {@code MaLoi}: {@code ma_loi_id}, {@code nhom_loi_id}, {@code ten_ma_loi},
 *       {@code muc_nghiem_trong_default}, {@code moi_nguy_default}, {@code bat_buoc_khoanh_vung},
 *       {@code nhom_n_kiem_chung}, {@code nguoi_so_huu} (tuỳ chọn, mặc định {@code QA_TAP_DOAN}),
 *       {@code hieu_luc_tu} (tuỳ chọn, mặc định ngày hiện tại).</li>
 * </ul>
 * Dòng trống được bỏ qua.
 *
 * <p>GHI CHÚ lựa chọn xử lý lỗi: <b>all-or-nothing</b>. Service validate toàn bộ trước; nếu có bất kỳ
 * dòng lỗi nào thì KHÔNG ghi gì vào DB (rollback cả transaction) và trả về {@link ImportResult}
 * chứa danh sách lỗi. Chỉ khi mọi dòng hợp lệ mới upsert. Lựa chọn này an toàn hơn kiểu skip từng
 * dòng vì bộ mã lỗi là master data dùng chung, tránh để lại trạng thái nửa vời.
 *
 * <p>Upsert idempotent: {@code INSERT ... ON DUPLICATE KEY UPDATE} theo khoá {@code nhom_loi_id} /
 * {@code ma_loi_id} (cùng pattern INSERT của {@code DemoSeeder}) nên import lại không nhân đôi.
 */
@Service
public class MaLoiImportService {

    private static final String SHEET_NHOM = "NhomLoi";
    private static final String SHEET_MA = "MaLoi";
    private static final String NGUOI_SO_HUU_MAC_DINH = "QA_TAP_DOAN";

    private static final DataFormatter FORMATTER = new DataFormatter(Locale.US);

    private final JdbcTemplate jdbc;

    public MaLoiImportService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Đọc workbook xlsx từ {@code in}, validate và (nếu không lỗi) upsert vào DB.
     * all-or-nothing: có lỗi → không ghi gì, trả về danh sách lỗi.
     */
    @Transactional
    public ImportResult importXlsx(InputStream in) {
        ImportResult result = new ImportResult();

        List<NhomRow> nhomRows = new ArrayList<>();
        List<MaRow> maRows = new ArrayList<>();

        try (Workbook wb = new XSSFWorkbook(in)) {
            docSheetNhom(wb, result, nhomRows);
            docSheetMa(wb, result, maRows);
        } catch (IOException e) {
            throw new UncheckedIOException("Không đọc được file xlsx", e);
        }

        // Tập nhóm hợp lệ để kiểm FK cho mã lỗi: gồm nhóm đã có trong DB + nhóm từ sheet NhomLoi.
        Set<String> nhomHopLe = new HashSet<>(
                jdbc.queryForList("SELECT nhom_loi_id FROM nhom_loi", String.class));
        for (NhomRow nr : nhomRows) {
            nhomHopLe.add(nr.nhomLoiId);
        }

        for (MaRow mr : maRows) {
            if (!nhomHopLe.contains(mr.nhomLoiId)) {
                result.themLoi(SHEET_MA, mr.dongExcel,
                        "nhom_loi_id '" + mr.nhomLoiId + "' không tồn tại (DB hoặc sheet NhomLoi)");
            }
        }

        // all-or-nothing: còn lỗi thì KHÔNG ghi gì.
        if (result.coLoi()) {
            return result;
        }

        for (NhomRow nr : nhomRows) {
            upsertNhom(nr);
        }
        for (MaRow mr : maRows) {
            upsertMaLoi(mr);
        }
        result.setSoNhomImport(nhomRows.size());
        result.setSoMaLoiImport(maRows.size());
        return result;
    }

    // ----------------------------------------------------------------- đọc sheet

    private void docSheetNhom(Workbook wb, ImportResult result, List<NhomRow> out) {
        Sheet sheet = wb.getSheet(SHEET_NHOM);
        if (sheet == null) return; // sheet nhóm tuỳ chọn: có thể chỉ import mã lỗi vào nhóm có sẵn

        Map<String, Integer> col = header(sheet);
        if (!col.keySet().containsAll(List.of("nhom_loi_id", "ten_nhom", "theo_nguon"))) {
            result.themLoi(SHEET_NHOM, 1, "Thiếu cột header bắt buộc: nhom_loi_id, ten_nhom, theo_nguon");
            return;
        }

        Set<String> daThay = new HashSet<>();
        int last = sheet.getLastRowNum();
        for (int i = 1; i <= last; i++) {
            Row row = sheet.getRow(i);
            if (laDongTrong(row)) continue;
            int dongExcel = i + 1;

            String id = cell(row, col, "nhom_loi_id");
            String ten = cell(row, col, "ten_nhom");
            String nguon = cell(row, col, "theo_nguon");

            if (id.isBlank()) {
                result.themLoi(SHEET_NHOM, dongExcel, "nhom_loi_id rỗng");
                continue;
            }
            if (ten.isBlank()) {
                result.themLoi(SHEET_NHOM, dongExcel, "ten_nhom rỗng");
                continue;
            }
            if (nguon.isBlank()) {
                result.themLoi(SHEET_NHOM, dongExcel, "theo_nguon rỗng");
                continue;
            }
            if (!daThay.add(id)) {
                result.themLoi(SHEET_NHOM, dongExcel, "nhom_loi_id '" + id + "' bị lặp trong sheet");
                continue;
            }
            out.add(new NhomRow(dongExcel, id, ten, nguon));
        }
    }

    private void docSheetMa(Workbook wb, ImportResult result, List<MaRow> out) {
        Sheet sheet = wb.getSheet(SHEET_MA);
        if (sheet == null) {
            result.themLoi(SHEET_MA, 0, "Không tìm thấy sheet '" + SHEET_MA + "'");
            return;
        }

        Map<String, Integer> col = header(sheet);
        List<String> batBuoc = List.of("ma_loi_id", "nhom_loi_id", "ten_ma_loi",
                "muc_nghiem_trong_default", "moi_nguy_default", "bat_buoc_khoanh_vung", "nhom_n_kiem_chung");
        if (!col.keySet().containsAll(batBuoc)) {
            result.themLoi(SHEET_MA, 1, "Thiếu cột header bắt buộc: " + String.join(", ", batBuoc));
            return;
        }

        Set<String> daThay = new HashSet<>();
        int last = sheet.getLastRowNum();
        for (int i = 1; i <= last; i++) {
            Row row = sheet.getRow(i);
            if (laDongTrong(row)) continue;
            int dongExcel = i + 1;

            String id = cell(row, col, "ma_loi_id");
            String nhomId = cell(row, col, "nhom_loi_id");
            String ten = cell(row, col, "ten_ma_loi");

            if (id.isBlank()) {
                result.themLoi(SHEET_MA, dongExcel, "ma_loi_id rỗng");
                continue;
            }
            if (nhomId.isBlank()) {
                result.themLoi(SHEET_MA, dongExcel, "nhom_loi_id rỗng");
                continue;
            }
            if (ten.isBlank()) {
                result.themLoi(SHEET_MA, dongExcel, "ten_ma_loi rỗng");
                continue;
            }
            if (!daThay.add(id)) {
                result.themLoi(SHEET_MA, dongExcel, "ma_loi_id '" + id + "' bị lặp trong sheet");
                continue;
            }

            Severity muc = parseEnum(Severity.class, cell(row, col, "muc_nghiem_trong_default"));
            if (muc == null) {
                result.themLoi(SHEET_MA, dongExcel,
                        "muc_nghiem_trong_default phải thuộc {NHE, NANG, NGHIEM_TRONG}");
                continue;
            }
            HazardType nguy = parseEnum(HazardType.class, cell(row, col, "moi_nguy_default"));
            if (nguy == null) {
                result.themLoi(SHEET_MA, dongExcel,
                        "moi_nguy_default phải thuộc {VAT_LY, HOA_HOC, VI_SINH, DI_UNG, CAM_QUAN, KHONG}");
                continue;
            }
            NKiemChungGroup nhomN = parseEnum(NKiemChungGroup.class, cell(row, col, "nhom_n_kiem_chung"));
            if (nhomN == null) {
                result.themLoi(SHEET_MA, dongExcel,
                        "nhom_n_kiem_chung phải thuộc {SX_TP_NVL, HETHONG_TBDO_NCC_MT}");
                continue;
            }
            Boolean kv = parseBool(cell(row, col, "bat_buoc_khoanh_vung"));
            if (kv == null) {
                result.themLoi(SHEET_MA, dongExcel,
                        "bat_buoc_khoanh_vung phải là 0/1 hoặc true/false");
                continue;
            }

            String nguoiSoHuu = cell(row, col, "nguoi_so_huu");
            if (nguoiSoHuu.isBlank()) nguoiSoHuu = NGUOI_SO_HUU_MAC_DINH;

            LocalDate hieuLucTu;
            String hieuLucRaw = cell(row, col, "hieu_luc_tu");
            if (hieuLucRaw.isBlank()) {
                hieuLucTu = LocalDate.now();
            } else {
                hieuLucTu = parseDate(hieuLucRaw);
                if (hieuLucTu == null) {
                    result.themLoi(SHEET_MA, dongExcel, "hieu_luc_tu không đúng định dạng (yyyy-MM-dd)");
                    continue;
                }
            }

            out.add(new MaRow(dongExcel, id, nhomId, ten, muc, nguy, kv, nhomN, nguoiSoHuu, hieuLucTu));
        }
    }

    // ----------------------------------------------------------------- upsert

    private void upsertNhom(NhomRow nr) {
        jdbc.update("INSERT INTO nhom_loi(nhom_loi_id, ten_nhom, theo_nguon) VALUES (?,?,?) " +
                        "ON DUPLICATE KEY UPDATE ten_nhom = VALUES(ten_nhom), theo_nguon = VALUES(theo_nguon)",
                nr.nhomLoiId, nr.tenNhom, nr.theoNguon);
    }

    private void upsertMaLoi(MaRow mr) {
        jdbc.update("INSERT INTO ma_loi(ma_loi_id, nhom_loi_id, ten_ma_loi, muc_nghiem_trong_default, " +
                        "moi_nguy_default, bat_buoc_khoanh_vung, nhom_n_kiem_chung, nguoi_so_huu, hieu_luc_tu) " +
                        "VALUES (?,?,?,?,?,?,?,?,?) " +
                        "ON DUPLICATE KEY UPDATE nhom_loi_id = VALUES(nhom_loi_id), " +
                        "ten_ma_loi = VALUES(ten_ma_loi), " +
                        "muc_nghiem_trong_default = VALUES(muc_nghiem_trong_default), " +
                        "moi_nguy_default = VALUES(moi_nguy_default), " +
                        "bat_buoc_khoanh_vung = VALUES(bat_buoc_khoanh_vung), " +
                        "nhom_n_kiem_chung = VALUES(nhom_n_kiem_chung), " +
                        "nguoi_so_huu = VALUES(nguoi_so_huu), " +
                        "hieu_luc_tu = VALUES(hieu_luc_tu)",
                mr.maLoiId, mr.nhomLoiId, mr.tenMaLoi, mr.muc.name(), mr.nguy.name(),
                mr.batBuocKhoanhVung ? 1 : 0, mr.nhomN.name(), mr.nguoiSoHuu, mr.hieuLucTu);
    }

    // ----------------------------------------------------------------- tiện ích POI

    /** Map tên header (chữ thường, trim) → chỉ số cột, đọc từ dòng đầu của sheet. */
    private static Map<String, Integer> header(Sheet sheet) {
        Map<String, Integer> map = new HashMap<>();
        Row head = sheet.getRow(sheet.getFirstRowNum());
        if (head == null) return map;
        for (int c = head.getFirstCellNum(); c < head.getLastCellNum(); c++) {
            Cell cell = head.getCell(c);
            if (cell == null) continue;
            String name = FORMATTER.formatCellValue(cell).trim().toLowerCase(Locale.ROOT);
            if (!name.isEmpty()) map.putIfAbsent(name, c);
        }
        return map;
    }

    /** Đọc ô theo tên header; trả chuỗi đã trim (chuỗi rỗng nếu thiếu). */
    private static String cell(Row row, Map<String, Integer> col, String name) {
        Integer idx = col.get(name);
        if (idx == null || row == null) return "";
        Cell cell = row.getCell(idx);
        if (cell == null) return "";
        return FORMATTER.formatCellValue(cell).trim();
    }

    private static boolean laDongTrong(Row row) {
        if (row == null) return true;
        for (int c = row.getFirstCellNum(); c < row.getLastCellNum(); c++) {
            Cell cell = row.getCell(c);
            if (cell != null && !FORMATTER.formatCellValue(cell).trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Parse 0/1 hoặc true/false (không phân biệt hoa thường); null nếu không hợp lệ. */
    private static Boolean parseBool(String raw) {
        if (raw == null) return null;
        String v = raw.trim().toLowerCase(Locale.ROOT);
        return switch (v) {
            case "1", "true", "y", "yes" -> Boolean.TRUE;
            case "0", "false", "n", "no" -> Boolean.FALSE;
            default -> null;
        };
    }

    private static LocalDate parseDate(String raw) {
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    // ----------------------------------------------------------------- row DTO nội bộ

    private static final class NhomRow {
        final int dongExcel;
        final String nhomLoiId;
        final String tenNhom;
        final String theoNguon;

        NhomRow(int dongExcel, String nhomLoiId, String tenNhom, String theoNguon) {
            this.dongExcel = dongExcel;
            this.nhomLoiId = nhomLoiId;
            this.tenNhom = tenNhom;
            this.theoNguon = theoNguon;
        }
    }

    private static final class MaRow {
        final int dongExcel;
        final String maLoiId;
        final String nhomLoiId;
        final String tenMaLoi;
        final Severity muc;
        final HazardType nguy;
        final boolean batBuocKhoanhVung;
        final NKiemChungGroup nhomN;
        final String nguoiSoHuu;
        final LocalDate hieuLucTu;

        MaRow(int dongExcel, String maLoiId, String nhomLoiId, String tenMaLoi, Severity muc,
              HazardType nguy, boolean batBuocKhoanhVung, NKiemChungGroup nhomN,
              String nguoiSoHuu, LocalDate hieuLucTu) {
            this.dongExcel = dongExcel;
            this.maLoiId = maLoiId;
            this.nhomLoiId = nhomLoiId;
            this.tenMaLoi = tenMaLoi;
            this.muc = muc;
            this.nguy = nguy;
            this.batBuocKhoanhVung = batBuocKhoanhVung;
            this.nhomN = nhomN;
            this.nguoiSoHuu = nguoiSoHuu;
            this.hieuLucTu = hieuLucTu;
        }
    }
}
