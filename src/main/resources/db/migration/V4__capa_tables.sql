CREATE TABLE capa (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  trang_thai     VARCHAR(16) NOT NULL,
  khac_phuc_tuc_thoi TEXT NULL,
  ngan_tai_dien      TEXT NULL,
  rpn_s TINYINT NULL, rpn_o TINYINT NULL, rpn_d TINYINT NULL,
  rca_noi_dung   TEXT NULL,
  nhom_nguyen_nhan VARCHAR(64) NULL,
  ke_hoach_dieu_tra TEXT NULL,
  chi_phi_dau_tu DECIMAL(18,2) NULL,
  x_ngay INT NULL,
  n_lo   INT NULL,
  dem_gop_nlo TINYINT(1) NOT NULL DEFAULT 0,
  is_8d  TINYINT(1) NOT NULL DEFAULT 0,
  co_yeu_cau_phap_ly TINYINT(1) NOT NULL DEFAULT 0,
  handover_done TINYINT(1) NOT NULL DEFAULT 0,
  monitoring_bat_dau DATETIME NULL,
  nguoi_dieu_tra VARCHAR(64) NULL,
  created_at DATETIME NOT NULL,
  created_by VARCHAR(64) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE nc_capa (
  nc_so_hieu VARCHAR(32) NOT NULL,
  capa_id    BIGINT NOT NULL,
  PRIMARY KEY (nc_so_hieu, capa_id),
  CONSTRAINT fk_nccapa_nc FOREIGN KEY (nc_so_hieu) REFERENCES nc(so_hieu),
  CONSTRAINT fk_nccapa_capa FOREIGN KEY (capa_id) REFERENCES capa(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE capa_action (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  capa_id BIGINT NOT NULL,
  loai VARCHAR(16) NOT NULL,
  mo_ta TEXT NOT NULL,
  nguoi_phu_trach VARCHAR(64) NOT NULL,
  deadline DATE NOT NULL,
  trang_thai VARCHAR(16) NOT NULL,
  bang_chung VARCHAR(255) NULL,
  CONSTRAINT fk_action_capa FOREIGN KEY (capa_id) REFERENCES capa(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE capa_action_extension (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  action_id BIGINT NOT NULL,
  deadline_moi DATE NOT NULL,
  ly_do VARCHAR(512) NOT NULL,
  trang_thai VARCHAR(16) NOT NULL,
  nguoi_yeu_cau VARCHAR(64) NOT NULL,
  nguoi_duyet VARCHAR(64) NULL,
  created_at DATETIME NOT NULL,
  CONSTRAINT fk_ext_action FOREIGN KEY (action_id) REFERENCES capa_action(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE capa_verification (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  capa_id BIGINT NOT NULL,
  moc_x_dat_luc DATETIME NULL,
  moc_n_dat_luc DATETIME NULL,
  han_kiem_chung_tiep_theo DATETIME NULL,
  so_lan_doi_han INT NOT NULL DEFAULT 0,
  bien_ban_kiem_tra VARCHAR(255) NULL,
  ket_qua VARCHAR(16) NULL,
  xac_nhan_boi VARCHAR(64) NULL,
  xac_nhan_luc DATETIME NULL,
  CONSTRAINT fk_ver_capa FOREIGN KEY (capa_id) REFERENCES capa(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
