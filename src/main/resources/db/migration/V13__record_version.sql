-- VERSIONING bản ghi đã xác nhận (REQ mục 7, invariant I7).
-- Khi sửa một bản ghi ĐÃ XÁC NHẬN: KHÔNG ghi đè trực tiếp mà LƯU SNAPSHOT bản cũ
-- (giữ cả hai, có liên kết version qua phien_ban) rồi mới áp giá trị mới.
-- BỔ SUNG cho audit_log (V5): audit ghi ai/khi/gì, bảng này giữ SNAPSHOT giá trị đầy đủ (JSON).
--
-- PHẠM VI: cơ chế TỔNG QUÁT (bảng dùng chung) — hiện áp cho MỘT loại tiêu biểu là
-- kết quả kiểm chứng CAPA đã xác nhận (doi_tuong_loai = 'CAPA_VERIFICATION'). Mở rộng cho
-- loại khác về sau chỉ cần gọi RecordVersionService.luuSnapshot với doi_tuong_loai mới.
CREATE TABLE record_version (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  doi_tuong_loai  VARCHAR(24)  NOT NULL,   -- ví dụ: CAPA_VERIFICATION
  doi_tuong_id    VARCHAR(64)  NOT NULL,   -- id bản ghi gốc (dạng chuỗi)
  phien_ban       INT          NOT NULL,   -- phiên bản snapshot, bắt đầu 1, tăng dần
  snapshot_json   TEXT         NOT NULL,   -- giá trị bản CŨ dạng JSON (trước khi đổi)
  ly_do           VARCHAR(512) NULL,
  nguoi_thuc_hien VARCHAR(64)  NOT NULL,
  created_at      DATETIME     NOT NULL,
  INDEX idx_rv_doituong (doi_tuong_loai, doi_tuong_id),
  UNIQUE KEY uk_rv (doi_tuong_loai, doi_tuong_id, phien_ban)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
