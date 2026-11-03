package com.dqh.nccapa.report;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Map;

/**
 * Xuất báo cáo dashboard NC/CAPA ra file Excel (xlsx, Apache POI) và PDF (OpenPDF).
 * Chỉ ĐỌC dữ liệu từ {@link ReportService#dashboard(String)} — không đổi schema, không ghi DB.
 *
 * <p>Ghi chú tiếng Việt cho PDF: OpenPDF chỉ có sẵn font base-14 (Helvetica) không hiển thị
 * được dấu tiếng Việt. Để báo cáo gọn và không phải nhúng file font TTF, phần PDF <b>bỏ dấu</b>
 * (ASCII-fold) các chuỗi tiếng Việt trước khi vẽ. Excel giữ nguyên tiếng Việt có dấu.
 */
@Service
public class ReportExportService {

    private final ReportService reportService;

    public ReportExportService(ReportService reportService) {
        this.reportService = reportService;
    }

    // ------------------------------------------------------------------ Excel

    /** Dựng workbook XLSX từ DashboardData. Header in đậm, số format gọn. */
    public byte[] excel(String nhaMay) {
        ReportService.DashboardData d = reportService.dashboard(nhaMay);
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            CellStyle headerStyle = wb.createCellStyle();
            org.apache.poi.ss.usermodel.Font bold = wb.createFont();
            bold.setBold(true);
            headerStyle.setFont(bold);

            CellStyle numberStyle = wb.createCellStyle();
            DataFormat fmt = wb.createDataFormat();
            numberStyle.setDataFormat(fmt.getFormat("#,##0.##"));

            // --- Sheet "Tổng quan" ---
            Sheet tq = wb.createSheet("Tổng quan");
            int r = 0;
            r = title(tq, r, "Báo cáo NC/CAPA" + (nhaMay != null && !nhaMay.isBlank() ? " — nhà máy " + nhaMay : " — toàn bộ"), headerStyle);
            r++;
            r = kvHeader(tq, r, headerStyle);
            r = kv(tq, r, "Phiếu đang mở", d.tongMo, numberStyle);
            r = kv(tq, r, "Quá hạn xử lý", d.quaHan, numberStyle);
            r = kv(tq, r, "Đang khoanh vùng", d.khoanhVung, numberStyle);
            r = kv(tq, r, "CAPA hiệu quả", d.capaHieuQua, numberStyle);
            r = kv(tq, r, "CAPA không hiệu quả", d.capaKhongHieuQua, numberStyle);
            r = kvDouble(tq, r, "Tỷ lệ CAPA hiệu quả (%)", d.tyLeHieuQua, numberStyle);
            tq.setColumnWidth(0, 8000);
            tq.setColumnWidth(1, 5000);

            // --- Các sheet nhóm ---
            mapSheet(wb, "NC theo mức", d.theoMuc, headerStyle, numberStyle);
            mapSheet(wb, "NC theo trạng thái", d.theoTrangThai, headerStyle, numberStyle);
            mapSheet(wb, "NC theo nguồn", d.theoNguon, headerStyle, numberStyle);
            mapSheet(wb, "NC theo loại", d.theoLoai, headerStyle, numberStyle);
            mapSheet(wb, "NC theo nhà máy", d.theoNhaMay, headerStyle, numberStyle);
            mapSheet(wb, "Top mã lỗi", d.topMaLoi, headerStyle, numberStyle);
            mapSheet(wb, "Top mã hàng", d.topMaHang, headerStyle, numberStyle);

            // --- Cost of quality ---
            Sheet cq = wb.createSheet("Cost of quality");
            int rc = kvHeader(cq, 0, headerStyle);
            rc = kvMoney(cq, rc, "Chi phí NC", d.cophiNc, numberStyle);
            rc = kvMoney(cq, rc, "Đầu tư CAPA", d.cophiCapa, numberStyle);
            rc = kvMoney(cq, rc, "Tổng chi phí chất lượng", d.costOfQuality, numberStyle);
            cq.setColumnWidth(0, 8000);
            cq.setColumnWidth(1, 5000);

            // --- Tuổi NC / thời gian đóng ---
            Sheet age = wb.createSheet("Tuổi NC & đóng TB");
            int ra = kvHeader(age, 0, headerStyle);
            ra = kvDouble(age, ra, "Tuổi trung bình NC đang mở (ngày)", d.tuoiTrungBinhMo, numberStyle);
            ra = kv(age, ra, "NC mở ≤ 7 ngày", d.tuoiMoDuoi7, numberStyle);
            ra = kv(age, ra, "NC mở 8–30 ngày", d.tuoiMo8Den30, numberStyle);
            ra = kv(age, ra, "NC mở > 30 ngày", d.tuoiMoTren30, numberStyle);
            ra = kvDouble(age, ra, "Thời gian đóng trung bình (ngày)", d.thoiGianDongTrungBinh, numberStyle);
            age.setColumnWidth(0, 9000);
            age.setColumnWidth(1, 5000);

            wb.write(out);
            return out.toByteArray();
        } catch (java.io.IOException e) {
            throw new UncheckedIOException("Lỗi dựng file Excel báo cáo", e);
        }
    }

    private int title(Sheet s, int r, String text, CellStyle style) {
        Row row = s.createRow(r);
        Cell c = row.createCell(0);
        c.setCellValue(text);
        c.setCellStyle(style);
        return r + 1;
    }

    private int kvHeader(Sheet s, int r, CellStyle style) {
        Row row = s.createRow(r);
        Cell k = row.createCell(0);
        k.setCellValue("Chỉ tiêu");
        k.setCellStyle(style);
        Cell v = row.createCell(1);
        v.setCellValue("Giá trị");
        v.setCellStyle(style);
        return r + 1;
    }

    private int kv(Sheet s, int r, String k, long v, CellStyle numberStyle) {
        Row row = s.createRow(r);
        row.createCell(0).setCellValue(k);
        Cell c = row.createCell(1);
        c.setCellValue(v);
        c.setCellStyle(numberStyle);
        return r + 1;
    }

    private int kvDouble(Sheet s, int r, String k, Double v, CellStyle numberStyle) {
        Row row = s.createRow(r);
        row.createCell(0).setCellValue(k);
        Cell c = row.createCell(1);
        if (v != null) {
            c.setCellValue(v);
            c.setCellStyle(numberStyle);
        } else {
            c.setCellValue("—");
        }
        return r + 1;
    }

    private int kvMoney(Sheet s, int r, String k, BigDecimal v, CellStyle numberStyle) {
        Row row = s.createRow(r);
        row.createCell(0).setCellValue(k);
        Cell c = row.createCell(1);
        c.setCellValue(v == null ? 0.0 : v.doubleValue());
        c.setCellStyle(numberStyle);
        return r + 1;
    }

    private void mapSheet(XSSFWorkbook wb, String name, Map<String, Long> data,
                          CellStyle headerStyle, CellStyle numberStyle) {
        Sheet s = wb.createSheet(name);
        Row h = s.createRow(0);
        Cell hk = h.createCell(0);
        hk.setCellValue(name);
        hk.setCellStyle(headerStyle);
        Cell hc = h.createCell(1);
        hc.setCellValue("Số lượng");
        hc.setCellStyle(headerStyle);
        int r = 1;
        if (data != null) {
            for (Map.Entry<String, Long> e : data.entrySet()) {
                Row row = s.createRow(r++);
                row.createCell(0).setCellValue(e.getKey());
                Cell c = row.createCell(1);
                c.setCellValue(e.getValue() == null ? 0 : e.getValue());
                c.setCellStyle(numberStyle);
            }
        }
        s.setColumnWidth(0, 8000);
        s.setColumnWidth(1, 4000);
    }

    // -------------------------------------------------------------------- PDF

    /**
     * Dựng PDF báo cáo tóm tắt (tiêu đề + các bảng chính). Chuỗi tiếng Việt được bỏ dấu
     * (xem ghi chú trên đầu lớp) để hiển thị đúng với font base-14 của OpenPDF.
     */
    public byte[] pdf(String nhaMay) {
        ReportService.DashboardData d = reportService.dashboard(nhaMay);
        Document doc = new Document(PageSize.A4, 36, 36, 48, 36);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfWriter.getInstance(doc, out);
            doc.open();

            Font h1 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
            Font h2 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
            Font normal = FontFactory.getFont(FontFactory.HELVETICA, 10);

            String phamVi = (nhaMay != null && !nhaMay.isBlank()) ? "nha may " + nhaMay : "toan bo";
            Paragraph tieuDe = new Paragraph(fold("Báo cáo NC/CAPA — " + phamVi), h1);
            tieuDe.setAlignment(Element.ALIGN_CENTER);
            tieuDe.setSpacingAfter(16);
            doc.add(tieuDe);

            // Tổng quan
            doc.add(section("Tong quan", h2));
            PdfPTable tq = kvTable();
            kvRow(tq, "Phieu dang mo", String.valueOf(d.tongMo), normal);
            kvRow(tq, "Qua han xu ly", String.valueOf(d.quaHan), normal);
            kvRow(tq, "Dang khoanh vung", String.valueOf(d.khoanhVung), normal);
            kvRow(tq, "CAPA hieu qua", String.valueOf(d.capaHieuQua), normal);
            kvRow(tq, "CAPA khong hieu qua", String.valueOf(d.capaKhongHieuQua), normal);
            kvRow(tq, "Ty le CAPA hieu qua (%)", d.tyLeHieuQua == null ? "—" : String.valueOf(d.tyLeHieuQua), normal);
            doc.add(tq);

            // Cost of quality
            doc.add(section("Cost of quality", h2));
            PdfPTable cq = kvTable();
            kvRow(cq, "Chi phi NC", money(d.cophiNc), normal);
            kvRow(cq, "Dau tu CAPA", money(d.cophiCapa), normal);
            kvRow(cq, "Tong chi phi chat luong", money(d.costOfQuality), normal);
            doc.add(cq);

            // Tuổi NC / đóng TB
            doc.add(section("Tuoi NC dang mo & thoi gian dong", h2));
            PdfPTable age = kvTable();
            kvRow(age, "Tuoi trung binh (ngay)", d.tuoiTrungBinhMo == null ? "—" : String.valueOf(d.tuoiTrungBinhMo), normal);
            kvRow(age, "NC mo <= 7 ngay", String.valueOf(d.tuoiMoDuoi7), normal);
            kvRow(age, "NC mo 8-30 ngay", String.valueOf(d.tuoiMo8Den30), normal);
            kvRow(age, "NC mo > 30 ngay", String.valueOf(d.tuoiMoTren30), normal);
            kvRow(age, "Thoi gian dong TB (ngay)", d.thoiGianDongTrungBinh == null ? "—" : String.valueOf(d.thoiGianDongTrungBinh), normal);
            doc.add(age);

            // Các nhóm chính
            doc.add(mapTable("NC theo muc nghiem trong", d.theoMuc, h2, normal));
            doc.add(mapTable("NC theo trang thai", d.theoTrangThai, h2, normal));
            doc.add(mapTable("Top ma loi", d.topMaLoi, h2, normal));
            doc.add(mapTable("Top ma hang", d.topMaHang, h2, normal));

            doc.close();
            return out.toByteArray();
        } catch (java.io.IOException e) {
            throw new UncheckedIOException("Lỗi dựng file PDF báo cáo", e);
        } catch (com.lowagie.text.DocumentException e) {
            throw new IllegalStateException("Lỗi dựng file PDF báo cáo", e);
        }
    }

    private Paragraph section(String text, Font font) {
        Paragraph p = new Paragraph(fold(text), font);
        p.setSpacingBefore(12);
        p.setSpacingAfter(6);
        return p;
    }

    private PdfPTable kvTable() {
        PdfPTable t = new PdfPTable(2);
        t.setWidthPercentage(100);
        try {
            t.setWidths(new float[]{3f, 1f});
        } catch (com.lowagie.text.DocumentException ignored) {
            // tỉ lệ cột cố định, không thể lỗi
        }
        return t;
    }

    private void kvRow(PdfPTable t, String k, String v, Font font) {
        t.addCell(cell(fold(k), font, Element.ALIGN_LEFT));
        t.addCell(cell(fold(v), font, Element.ALIGN_RIGHT));
    }

    private PdfPCell cell(String text, Font font, int align) {
        PdfPCell c = new PdfPCell(new Phrase(text, font));
        c.setHorizontalAlignment(align);
        c.setPadding(4);
        return c;
    }

    private PdfPTable mapTable(String title, Map<String, Long> data, Font h2, Font normal) {
        PdfPTable t = kvTable();
        PdfPCell head = new PdfPCell(new Phrase(fold(title), h2));
        head.setColspan(2);
        head.setPadding(5);
        head.setBorder(com.lowagie.text.Rectangle.BOTTOM);
        t.addCell(head);
        if (data == null || data.isEmpty()) {
            PdfPCell empty = new PdfPCell(new Phrase(fold("Chua co du lieu."), normal));
            empty.setColspan(2);
            empty.setPadding(4);
            t.addCell(empty);
        } else {
            for (Map.Entry<String, Long> e : data.entrySet()) {
                kvRow(t, e.getKey(), String.valueOf(e.getValue()), normal);
            }
        }
        t.setSpacingBefore(10);
        return t;
    }

    private String money(BigDecimal v) {
        if (v == null) return "0";
        return v.stripTrailingZeros().toPlainString();
    }

    /**
     * Bỏ dấu tiếng Việt (ASCII-fold) để hiển thị đúng với font base-14 của OpenPDF.
     * Xử lý riêng đ/Đ (không tách được bằng Normalizer) và chuẩn hoá vài ký tự gạch ngang.
     */
    private String fold(String s) {
        if (s == null) return "";
        String x = s.replace('đ', 'd').replace('Đ', 'D')
                .replace('–', '-').replace('—', '-').replace('≤', '<').replace('≥', '>');
        x = Normalizer.normalize(x, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return x;
    }
}
