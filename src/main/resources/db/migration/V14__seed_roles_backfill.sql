-- V14 — Backfill role/user/app_user_role (sửa gốc D-01).
--
-- Bối cảnh: DB demo được seed bằng DemoSeeder phiên bản cũ (chỉ 3 user, 5 role, app_user_role RỖNG).
-- DemoSeeder chỉ chạy khi bảng nha_may rỗng nên KHÔNG tự bù khi nâng cấp code → RoleChecker đọc
-- app_user_role rỗng → mọi approve/reject fail ROLE_DENIED.
--
-- Migration này backfill IDEMPOTENT bằng SQL thuần, độc lập với DemoSeeder:
--   * INSERT IGNORE đủ role mà nghiệp vụ dùng (DemoSeeder.seedMasterData + LoaiThayDoi.chuoiDuyet).
--   * INSERT IGNORE các user còn thiếu (khomgp/bcmgp/rndtd) khớp nhà máy như DemoSeeder.
--   * INSERT IGNORE ... SELECT từ app_user để gán vai trò cho mọi cặp user→role của DemoSeeder.
--
-- AN TOÀN trên DB trống (CI/test Testcontainers, seed TẮT ở profile test):
--   * app_user rỗng → các câu "INSERT IGNORE ... SELECT FROM app_user" không chèn gì (đúng ý đồ).
--   * nha_may rỗng → "INSERT IGNORE INTO app_user(... nha_may='MGP')" bị FK chặn nhưng INSERT IGNORE
--     chỉ cảnh báo và BỎ QUA dòng, không fail migration.
-- Lặp lại nhiều lần đều an toàn (INSERT IGNORE theo khoá chính/unique).

-- 1) Role: đủ bộ DemoSeeder + các vai trò trong LoaiThayDoi.chuoiDuyet.
INSERT IGNORE INTO role(ma, ten) VALUES
  ('QA_TAP_DOAN','QA tap doan'),
  ('QA_NHA_MAY','QA nha may'),
  ('BO_PHAN_SX','Bo phan san xuat'),
  ('BO_PHAN_KHO','Bo phan kho'),
  ('NGUOI_BAO_CAO','Nguoi bao cao'),
  ('R_AND_D','R&D'),
  ('PHAP_CHE','Phap che'),
  ('THU_MUA','Thu mua'),
  ('GD_NHA_MAY_KY_THUAT','GD nha may ky thuat');

-- 2) User còn thiếu (khớp nhà máy như DemoSeeder). INSERT IGNORE: user đã có thì bỏ qua.
INSERT IGNORE INTO app_user(username, full_name, nha_may, active) VALUES
  ('khomgp','Kho MGP','MGP',1),
  ('bcmgp','Nguoi bao cao MGP','MGP',1),
  ('rndtd','R&D Tap doan', NULL, 1);

-- 3) Gán vai trò cho mọi cặp user→role của DemoSeeder.seedUserRole (idempotent).
INSERT IGNORE INTO app_user_role(user_id, role_ma)
  SELECT id, 'QA_TAP_DOAN'  FROM app_user WHERE username = 'qatd';
INSERT IGNORE INTO app_user_role(user_id, role_ma)
  SELECT id, 'QA_NHA_MAY'   FROM app_user WHERE username = 'qamgp';
INSERT IGNORE INTO app_user_role(user_id, role_ma)
  SELECT id, 'BO_PHAN_SX'   FROM app_user WHERE username = 'sxmgp';
INSERT IGNORE INTO app_user_role(user_id, role_ma)
  SELECT id, 'BO_PHAN_KHO'  FROM app_user WHERE username = 'khomgp';
INSERT IGNORE INTO app_user_role(user_id, role_ma)
  SELECT id, 'NGUOI_BAO_CAO' FROM app_user WHERE username = 'bcmgp';
INSERT IGNORE INTO app_user_role(user_id, role_ma)
  SELECT id, 'R_AND_D'      FROM app_user WHERE username = 'rndtd';
