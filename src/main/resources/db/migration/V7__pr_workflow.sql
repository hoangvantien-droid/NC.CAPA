-- PR: bổ sung cột cho máy trạng thái nhiều cấp (Đợt d). V6 chỉ là stub.
ALTER TABLE pr
  ADD COLUMN cap_hien_tai INT NOT NULL DEFAULT 0,          -- vị trí trong chuỗi duyệt (0-based)
  ADD COLUMN ly_do_tu_choi VARCHAR(512) NULL,
  ADD COLUMN created_by VARCHAR(64) NULL;

-- Lịch sử duyệt từng cấp
CREATE TABLE pr_approval (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  pr_id BIGINT NOT NULL,
  cap INT NOT NULL,                 -- cấp thứ mấy (0-based)
  vai_tro VARCHAR(32) NOT NULL,     -- vai trò duyệt tại cấp này
  ket_qua VARCHAR(16) NOT NULL,     -- APPROVED | REJECTED
  nguoi_duyet VARCHAR(64) NOT NULL,
  ly_do VARCHAR(512) NULL,
  duyet_luc DATETIME NOT NULL,
  CONSTRAINT fk_prapp_pr FOREIGN KEY (pr_id) REFERENCES pr(id),
  INDEX idx_prapp_pr (pr_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
