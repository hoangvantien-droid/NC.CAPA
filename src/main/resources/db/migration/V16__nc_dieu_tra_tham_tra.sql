-- Nhóm 1: nguyên nhân sau điều tra (có thể khác sơ bộ), giải pháp/ghi chú xử lý, kết quả thẩm tra Đạt/Không đạt.
ALTER TABLE nc
  ADD COLUMN nguyen_nhan_dieu_tra TEXT NULL AFTER nguyen_nhan_so_bo,
  ADD COLUMN giai_phap_xu_ly      TEXT NULL AFTER nguyen_nhan_dieu_tra,
  ADD COLUMN ket_qua_tham_tra     VARCHAR(16) NULL AFTER ket_luan_tham_tra;
