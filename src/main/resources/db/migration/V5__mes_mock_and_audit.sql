CREATE TABLE mes_lot_event (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  ma_hang VARCHAR(32) NOT NULL,
  so_lo VARCHAR(64) NOT NULL,
  san_xuat_luc DATETIME NOT NULL,
  nha_may VARCHAR(16) NOT NULL,
  INDEX idx_mes_mahang_time (ma_hang, san_xuat_luc)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE audit_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  doi_tuong VARCHAR(16) NOT NULL,
  doi_tuong_id VARCHAR(64) NOT NULL,
  hanh_dong VARCHAR(32) NOT NULL,
  gia_tri_cu VARCHAR(255) NULL,
  gia_tri_moi VARCHAR(255) NULL,
  ly_do VARCHAR(512) NULL,
  nguoi_thuc_hien VARCHAR(64) NOT NULL,
  thuc_hien_luc DATETIME NOT NULL,
  INDEX idx_audit_doituong (doi_tuong, doi_tuong_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
