-- Nhóm 2: đính kèm file/ảnh (bằng chứng) cho NC và CAPA action.
CREATE TABLE attachment (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  doi_tuong VARCHAR(16) NOT NULL,      -- NC | ACTION
  doi_tuong_id VARCHAR(64) NOT NULL,   -- so_hieu NC hoặc id action
  ten_file VARCHAR(255) NOT NULL,      -- tên gốc
  ten_luu  VARCHAR(255) NOT NULL,      -- tên file trên disk (duy nhất)
  content_type VARCHAR(128) NULL,
  kich_thuoc BIGINT NOT NULL DEFAULT 0,
  nguoi_tai VARCHAR(64) NULL,
  created_at DATETIME NOT NULL,
  INDEX idx_att_doituong (doi_tuong, doi_tuong_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- F: loại hành động Corrective/Preventive
ALTER TABLE capa_action
  ADD COLUMN loai_hanh_dong VARCHAR(16) NOT NULL DEFAULT 'CORRECTIVE';  -- CORRECTIVE | PREVENTIVE
