-- PR: chỗ mở rộng (Đợt a không implement luồng duyệt), DataModel §6
CREATE TABLE pr (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  loai_thay_doi VARCHAR(32) NOT NULL,
  trang_thai VARCHAR(16) NOT NULL DEFAULT 'Draft',
  ma_hang VARCHAR(32) NULL,
  ma_loi_id VARCHAR(16) NULL,
  created_at DATETIME NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE pr_nc (
  pr_id BIGINT NOT NULL,
  nc_so_hieu VARCHAR(32) NOT NULL,
  PRIMARY KEY (pr_id, nc_so_hieu)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE pr_capa (
  pr_id BIGINT NOT NULL,
  capa_id BIGINT NOT NULL,
  PRIMARY KEY (pr_id, capa_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
