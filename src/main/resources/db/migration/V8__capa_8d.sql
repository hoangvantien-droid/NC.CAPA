-- 8D checklist gắn CAPA (Đợt d). Cờ is_8d đã có trong bảng capa (V4).
CREATE TABLE capa_8d_step (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  capa_id BIGINT NOT NULL,
  buoc VARCHAR(4) NOT NULL,            -- D0..D8
  noi_dung TEXT NULL,
  hoan_thanh TINYINT(1) NOT NULL DEFAULT 0,
  nguoi_thuc_hien VARCHAR(64) NULL,
  hoan_thanh_luc DATETIME NULL,
  UNIQUE KEY uk_8d_capa_buoc (capa_id, buoc),
  CONSTRAINT fk_8d_capa FOREIGN KEY (capa_id) REFERENCES capa(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
