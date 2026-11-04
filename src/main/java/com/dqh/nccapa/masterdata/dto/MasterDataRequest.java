package com.dqh.nccapa.masterdata.dto;

/**
 * Body chung thêm/sửa master data (mỗi danh mục dùng một tập con trường).
 * Khi sửa: trường null = giữ nguyên; ngày rỗng "" = xoá giá trị. Mã (khoá chính) lấy từ URL, không sửa được.
 */
public class MasterDataRequest {
    public String ma;
    public String ten;
    /** ma_hang: nhà máy. */
    public String nhaMay;
    /** ma_loi: nhóm lỗi. */
    public String nhomLoiId;
    /** nhom_loi: theo nguồn. */
    public String theoNguon;
    /** ma_loi: NHE|NANG|NGHIEM_TRONG. */
    public String mucNghiemTrongDefault;
    /** ma_loi: VAT_LY|HOA_HOC|VI_SINH|DI_UNG|CAM_QUAN|KHONG. */
    public String moiNguyDefault;
    public Boolean batBuocKhoanhVung;
    /** ma_loi: SX_TP_NVL|HETHONG_TBDO_NCC_MT. */
    public String nhomNKiemChung;
    public String nguoiSoHuu;
    /** ISO yyyy-MM-dd. */
    public String hieuLucTu;
    public String hieuLucDen;
    /** ncc. */
    public Boolean coGiayCn;
    public String cnHetHanNgay;
}
