-- Nhóm 1: RCA có cấu trúc + nhóm nguyên nhân 6M + cờ tái diễn + tách kế hoạch điều tra.
ALTER TABLE capa
  ADD COLUMN rca_method VARCHAR(16) NULL,          -- 5WHY | FISHBONE | IS_IS_NOT
  ADD COLUMN rca_data   TEXT NULL,                 -- JSON các bước why / nhánh fishbone / is-is-not
  ADD COLUMN nhom_nguyen_nhan_6m VARCHAR(16) NULL, -- MAN|MACHINE|MATERIAL|METHOD|MEASURE|ENVIRONMENT
  ADD COLUMN tai_dien TINYINT(1) NOT NULL DEFAULT 0,
  ADD COLUMN kh_muc_tieu    TEXT NULL,             -- kế hoạch điều tra: mục tiêu
  ADD COLUMN kh_phuong_phap TEXT NULL,             -- phương pháp
  ADD COLUMN kh_nguon_luc   TEXT NULL;             -- nguồn lực
