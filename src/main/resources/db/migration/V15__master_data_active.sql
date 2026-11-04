-- Cờ "ngừng dùng" thay cho xoá cứng master data (ma_loi dùng sẵn hieu_luc_den).
-- DEFAULT 1 để dữ liệu cũ vẫn hợp lệ (đang dùng).
ALTER TABLE nha_may  ADD COLUMN active TINYINT(1) NOT NULL DEFAULT 1;
ALTER TABLE ma_hang  ADD COLUMN active TINYINT(1) NOT NULL DEFAULT 1;
ALTER TABLE nhom_loi ADD COLUMN active TINYINT(1) NOT NULL DEFAULT 1;
ALTER TABLE ncc      ADD COLUMN active TINYINT(1) NOT NULL DEFAULT 1;
