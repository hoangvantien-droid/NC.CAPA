-- Outbox thông báo (nhắc mốc kiểm chứng CAPA + cảnh báo action quá hạn).
-- App mẫu KHÔNG có SMTP thật: lưu thông báo vào bảng này + log (cấm SMTP sau),
-- giống pattern ticket_callback_outbox (V9).
CREATE TABLE notification (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  loai          VARCHAR(32) NOT NULL,   -- ACTION_QUA_HAN | MOC_SAP_DEN | MOC_QUA_HAN
  doi_tuong_loai VARCHAR(16) NOT NULL,  -- ACTION | CAPA
  doi_tuong_id  VARCHAR(64) NOT NULL,
  nguoi_nhan    VARCHAR(64) NULL,
  tieu_de       VARCHAR(255) NOT NULL,
  noi_dung      TEXT NOT NULL,
  trang_thai    VARCHAR(16) NOT NULL DEFAULT 'PENDING', -- PENDING | SENT
  created_at    DATETIME NOT NULL,
  sent_at       DATETIME NULL,
  INDEX idx_notif_trangthai (trang_thai)
  -- Chống trùng cùng loại/đối tượng trong cùng ngày thực hiện bằng truy vấn trước khi
  -- insert ở NotificationService (MySQL không cho UNIQUE trên biểu thức DATE(created_at)).
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
