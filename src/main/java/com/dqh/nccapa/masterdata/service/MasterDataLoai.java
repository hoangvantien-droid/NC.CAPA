package com.dqh.nccapa.masterdata.service;

import com.dqh.nccapa.common.error.BusinessException;

/** 5 danh mục master data quản lý được trên UI (slug dùng trong URL). */
public enum MasterDataLoai {
    NHA_MAY("nha-may", "Nhà máy"),
    MA_HANG("ma-hang", "Mã hàng"),
    NHOM_LOI("nhom-loi", "Nhóm lỗi"),
    MA_LOI("ma-loi", "Mã lỗi"),
    NCC("ncc", "Nhà cung cấp");

    private final String slug;
    private final String nhan;

    MasterDataLoai(String slug, String nhan) {
        this.slug = slug;
        this.nhan = nhan;
    }

    public String getSlug() { return slug; }
    public String getNhan() { return nhan; }

    public static MasterDataLoai tuSlug(String slug) {
        for (MasterDataLoai l : values()) {
            if (l.slug.equals(slug)) return l;
        }
        throw BusinessException.notFound("Không có danh mục master data: " + slug);
    }
}
