-- Outbox callback ticket 2 chiều (Đợt c, REQ mục 6).
CREATE TABLE ticket_callback_outbox (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  ticket_id     VARCHAR(64) NOT NULL,
  callback_type VARCHAR(16) NOT NULL,   -- early_response | case_closed
  event_id      VARCHAR(64) NOT NULL,   -- định danh sự kiện (idempotency)
  nc_so_hieu    VARCHAR(32) NULL,
  payload       TEXT NOT NULL,          -- JSON callback
  trang_thai    VARCHAR(16) NOT NULL DEFAULT 'PENDING', -- PENDING | SENT | DEAD_LETTER
  so_lan_thu    INT NOT NULL DEFAULT 0,
  thu_ke_tiep_luc DATETIME NULL,
  gui_luc       DATETIME NULL,
  created_at    DATETIME NOT NULL,
  UNIQUE KEY uk_outbox_idem (ticket_id, callback_type, event_id),
  INDEX idx_outbox_trangthai (trang_thai, thu_ke_tiep_luc)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
