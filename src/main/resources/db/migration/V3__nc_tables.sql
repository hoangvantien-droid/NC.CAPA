CREATE TABLE nc (
  so_hieu            VARCHAR(32) PRIMARY KEY,
  loai_nc            VARCHAR(24) NOT NULL,
  muc_nghiem_trong   VARCHAR(16) NOT NULL,
  ma_hang_chinh      VARCHAR(32) NULL,
  ma_loi_chinh       VARCHAR(16) NULL,
  nguon_phat_sinh    VARCHAR(24) NOT NULL,
  trang_thai         VARCHAR(24) NOT NULL,
  is_escalated       TINYINT(1)  NOT NULL DEFAULT 0,
  is_food_safety     TINYINT(1)  NOT NULL DEFAULT 0,
  nha_may            VARCHAR(16) NOT NULL,
  lo_kiem            VARCHAR(64) NULL,
  lenh_sx            VARCHAR(64) NULL,
  cong_doan          VARCHAR(64) NULL,
  ngay_sx            DATE NULL,
  khu_vuc            VARCHAR(64) NULL,
  mo_ta_loi          TEXT NULL,
  so_luong           DECIMAL(18,2) NULL,
  nguyen_nhan_so_bo  TEXT NULL,
  disposition_de_xuat VARCHAR(24) NULL,
  chi_phi_uoc_tinh   DECIMAL(18,2) NULL,
  bo_phan_thuc_hien  VARCHAR(64) NULL,
  nguoi_de_xuat_disposition VARCHAR(64) NULL,
  ket_qua_xu_ly      TEXT NULL,
  bang_chung         VARCHAR(255) NULL,
  chi_phi_thuc_te    DECIMAL(18,2) NULL,
  ket_luan_tham_tra  TEXT NULL,
  da_duyet_cap_1     TINYINT(1) NOT NULL DEFAULT 0,
  nguoi_duyet_cap_1  VARCHAR(64) NULL,
  nc_ke_thua_tu_capa_id BIGINT NULL,
  nc_gop_vao_id      VARCHAR(32) NULL,
  ly_do_huy          VARCHAR(512) NULL,
  nguoi_bao_cao      VARCHAR(64) NOT NULL,
  nguoi_phu_trach    VARCHAR(64) NULL,
  han_xu_ly          DATE NULL,
  created_at         DATETIME NOT NULL,
  created_by         VARCHAR(64) NOT NULL,
  INDEX idx_nc_lo (lo_kiem),
  INDEX idx_nc_trangthai (trang_thai),
  INDEX idx_nc_nhamay (nha_may),
  INDEX idx_nc_recur (ma_hang_chinh, ma_loi_chinh, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE nc_chi_tieu (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  nc_so_hieu   VARCHAR(32) NOT NULL,
  ma_loi_id    VARCHAR(16) NOT NULL,
  ma_hang      VARCHAR(32) NULL,
  gia_tri_do   VARCHAR(64) NULL,
  spec_min     VARCHAR(64) NULL,
  spec_max     VARCHAR(64) NULL,
  in_out_spec  TINYINT NULL,
  nguon_su_kien VARCHAR(24) NULL,
  created_at   DATETIME NOT NULL,
  CONSTRAINT fk_ct_nc FOREIGN KEY (nc_so_hieu) REFERENCES nc(so_hieu),
  CONSTRAINT fk_ct_maloi FOREIGN KEY (ma_loi_id) REFERENCES ma_loi(ma_loi_id),
  INDEX idx_ct_mahang_maloi (ma_hang, ma_loi_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE nc_doi_tuong (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  nc_so_hieu   VARCHAR(32) NOT NULL,
  loai_doi_tuong VARCHAR(16) NOT NULL,
  gia_tri      VARCHAR(64) NOT NULL,
  khoanh_vung  TINYINT(1) NOT NULL DEFAULT 0,
  trang_thai_dong_lo VARCHAR(16) NULL,
  chung_tu_hold VARCHAR(255) NULL,
  chung_tu_unhold VARCHAR(255) NULL,
  CONSTRAINT fk_dt_nc FOREIGN KEY (nc_so_hieu) REFERENCES nc(so_hieu)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE nc_ticket (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  nc_so_hieu VARCHAR(32) NOT NULL,
  ticket_id  VARCHAR(64) NOT NULL,
  created_at DATETIME NOT NULL,
  UNIQUE KEY uk_nc_ticket (nc_so_hieu, ticket_id),
  CONSTRAINT fk_nt_nc FOREIGN KEY (nc_so_hieu) REFERENCES nc(so_hieu)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE nc_sequence (
  nam     INT NOT NULL,
  nha_may VARCHAR(16) NOT NULL,
  last_no INT NOT NULL DEFAULT 0,
  PRIMARY KEY (nam, nha_may)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE nc_agg_lock (
  lo_kiem    VARCHAR(64) PRIMARY KEY,
  created_at DATETIME NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
