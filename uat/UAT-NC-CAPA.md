# UAT — NC/CAPA (Đợt a)

- **Ứng dụng:** NC/CAPA — Spring Boot 3.2.5 + MySQL 8
- **Bản test:** commit `cf7bad5` (feat: versioning bản ghi đã xác nhận — REQ I7)
- **Môi trường:** production nội bộ `https://tienhv.io.vn/nc-capa/` (app `127.0.0.1:3110/nc-capa`, DB `nc-capa-mysql`)
- **Ngày chạy:** 2026-11-03 22:37–23:05 (GMT+7)
- **Người chạy:** Tay sai (tự động hoá) — dữ liệu tạo tự động có tiền tố `LOT-UAT-*`

## 1. Kết quả tổng hợp

| Lớp test | Số case | Pass | Fail | Ghi chú |
|---|---:|---:|---:|---|
| API chức năng (`uat/api/uat-api.mjs`) | 53 | 52 | 1 | Fail: UAT-VER-01 (thiếu endpoint I7) |
| E2E giao diện (`uat/e2e`) — desktop + mobile | 19 | 17 | 2 | Fail: UAT-UI-10 (điều hướng 404), UAT-UI-21 (tràn ngang mobile) |
| **Tổng** | **72** | **69** | **3** | 95,8% pass |

## 2. Phạm vi đã kiểm

- Tạo NC, phân loại mức theo mã lỗi, sinh số hiệu `KPH-{năm}-{nhà máy}-{5 số}`
- Luồng Nhanh (Nhẹ → Closed), chặn close-fast với NC Nặng/đã đóng
- Nâng bậc Nhẹ→Nặng (giữ New), Nhẹ→Nghiêm trọng (sang Containment), chặn hạ bậc
- Duyệt NC Nặng 2 cấp + SoD + kiểm tra vai trò QA thật từ `app_user_role`
- Duyệt NC Nghiêm trọng 1 cấp (chỉ QA tập đoàn), bắt buộc Containment trước khi trình
- Containment: bắt buộc lô + chứng từ Hold; gỡ khoanh theo dòng lô; guard N9 chặn đóng khi còn lô Held
- confirm-done bắt buộc chi phí thực tế; verify-close
- CAPA: Open → Investigating → ActionPlan → Processing → Monitoring, các guard bắt buộc (kế hoạch điều tra, RCA/6M/chi phí, đủ 2 ô khắc phục + ngăn tái diễn, action Completed)
- Kiểm chứng CAPA: verification-status, confirm-effective khi chưa đủ mốc
- Auto-aggregation: OOS lần 2 cùng lô → APPENDED; ticket Food_Safety → Nghiêm trọng + foodSafety
- File đính kèm: upload → danh sách → download
- Dashboard/báo cáo tổng hợp
- Giao diện: danh sách, chi tiết, tạo phiếu, đóng nhanh qua UI, trình duyệt qua UI (5 prompt), upload qua UI, chuyển tài khoản demo, responsive mobile

## 3. Lỗi phát hiện

### D-01 — Seed không idempotent: DB cũ thiếu vai trò → toàn bộ luồng duyệt bị chặn (High)

**Hiện tượng:** `qamgp` (QA MGP) duyệt NC Nặng bị trả `ROLE_DENIED: NC Nặng: người duyệt disposition phải thuộc nhóm QA`, dù tài khoản này đúng là QA nhà máy.

**Nguyên nhân:** `DemoSeeder` chỉ chạy khi bảng `nha_may` rỗng. DB hiện tại được seed bằng phiên bản seeder cũ (chỉ 3 user, 5 role), nên khi nâng cấp code:
- `app_user` thiếu `khomgp`, `bcmgp`, `rndtd`
- `app_user_role` **rỗng hoàn toàn**
- bảng `role` thiếu `R_AND_D`

`RoleChecker` đọc vai trò thật từ `app_user_role` → mọi approve/reject đều fail. Đây là lỗi dữ liệu môi trường, nhưng chặn 100% nghiệp vụ duyệt.

**Đã xử lý để chạy tiếp UAT:** thêm bù role + user còn thiếu bằng SQL idempotent (`INSERT IGNORE ... SELECT`).

**Đề xuất sửa gốc:** tách seed master data khỏi điều kiện "bảng rỗng" — dùng `INSERT IGNORE`/`ON DUPLICATE KEY` cho `role`, `app_user`, `app_user_role` ở mọi lần khởi động, hoặc thêm migration Flyway `V14__seed_roles_backfill.sql`.

### D-02 — Tạo NC trên giao diện xong bị 404 (High)

**Tái hiện:** mở `https://tienhv.io.vn/nc-capa/nc/tao`, nhập lô + mã lỗi, bấm **Tạo phiếu NC**. Phiếu được tạo thành công nhưng trình duyệt nhảy tới `https://tienhv.io.vn/nc/KPH-2026-MGP-000xx` → **404 Not Found (nginx)**.

**Nguyên nhân:** trong `nc-create.html`, sau khi API trả về thành công:
```js
location.href = '/nc/' + encodeURIComponent(res.data.soHieu);   // thiếu context path
```
App chạy dưới context path `/nc-capa`, nên phải là `/nc-capa/nc/...`. nginx `sub_filter` hiện chỉ rewrite chuỗi `'/api/v1/` và `"/css/app.css"`, không rewrite `'/nc/'`.

**Ảnh hưởng:** người dùng tưởng tạo phiếu thất bại (trang 404), trong khi dữ liệu đã vào DB → dễ tạo trùng phiếu.

**Đề xuất sửa:** dùng biến context path Thymeleaf:
```html
<script th:inline="javascript">
  const CTX = /*[[@{/}]]*/ '/';
  location.href = CTX + 'nc/' + encodeURIComponent(res.data.soHieu);
</script>
```
(tương tự cho `location.href='/capa/'+...` trong `nc-detail.html`).

### D-03 — Trang chi tiết NC tràn ngang trên mobile (Medium)

Đo trên Pixel 7 (412×915): `scrollWidth - clientWidth = 192px`. Bảng "Chỉ tiêu vi phạm"/"Đối tượng ảnh hưởng" và layout 2 cột `.detail` không co theo viewport.

**Đề xuất:** `@media (max-width: 720px)` cho `.detail { grid-template-columns: 1fr }` và bọc bảng trong `.table-wrap { overflow-x: auto }`.

### D-04 — Tính năng I7 (versioning bản ghi đã xác nhận) chưa dùng được (Medium)

Commit `cf7bad5` thêm:
- bảng `record_version` (Flyway V13) ✔ đã migrate
- `RecordVersionService.luuSnapshot(...)` ✔
- `CapaService.suaKetQuaKiemChung(...)` ✔
- unit test `RecordVersionTest` ✔

Nhưng **không có endpoint REST và không có nút trên UI**. Kiểm tra:
```
POST /api/v1/capa/{id}/verification/sua-ket-qua  → 404
SELECT COUNT(*) FROM record_version               → 0
```
→ Trong bản deploy, không ai có thể sửa kết quả kiểm chứng để sinh snapshot; tính năng chỉ tồn tại ở tầng service và test.

**Đề xuất:** thêm mapping vào `CapaController` (đúng DTO đã có sẵn) + nút "Sửa kết quả" ở `capa-detail.html` khi CAPA đã có `ket_qua`.

## 4. Bảng case chi tiết (API)

| ID | Nội dung | Kỳ vọng | Kết quả |
|---|---|---|---|
| UAT-NC-01 | Tạo NC Nhẹ FG-01 | New/NHE, số hiệu đúng format | PASS |
| UAT-NC-02 | Mã lỗi không tồn tại | Bị chặn | PASS |
| UAT-NC-03 | Thiếu `chiTieu` | 400 validation | PASS |
| UAT-NC-04 | Nhiều chỉ tiêu (FG-01 + FG-03) | Mức = nặng nhất | PASS |
| UAT-NC-05 | close-fast NC Nhẹ | Closed | PASS |
| UAT-NC-06 | close-fast NC Nặng | Bị chặn | PASS |
| UAT-NC-07 | close-fast NC đã đóng | Bị chặn | PASS |
| UAT-NC-08/08b | Nhẹ→Nặng ở New | Giữ New, khóa close-fast | PASS |
| UAT-NC-09 | Nhẹ→Nghiêm trọng | Containment + foodSafety | PASS |
| UAT-NC-10 | Hạ bậc | Bị chặn | PASS |
| UAT-NC-11 | submit thiếu chi phí ước tính | Bị chặn | PASS |
| UAT-NC-12 | submit đủ | PendingApproval | PASS |
| UAT-NC-13 | Người đề xuất tự duyệt (SoD) | ROLE_DENIED | PASS |
| UAT-NC-14 | Người không thuộc QA duyệt | ROLE_DENIED | PASS |
| UAT-NC-15 | QA duyệt cấp 1 | Vẫn PendingApproval, ghi cấp 1 | PASS |
| UAT-NC-16 | Cùng người duyệt cấp 2 | Bị chặn | PASS |
| UAT-NC-17 | QA thứ hai duyệt cấp 2 | Processing | PASS |
| UAT-NC-18 | confirm-done thiếu chi phí thực tế | Bị chặn | PASS |
| UAT-NC-19/20 | confirm-done → verify-close | PendingVerification → Closed | PASS |
| UAT-NC-21 | NC từ FG-03 | NGHIEM_TRONG + foodSafety | PASS |
| UAT-NC-22 | Trình duyệt trước Containment | Bị chặn | PASS |
| UAT-NC-23 | Containment thiếu chứng từ Hold | Bị chặn | PASS |
| UAT-NC-24/24b | Containment đủ | Containment + lô Held | PASS |
| UAT-NC-25 | submit sau Containment | PendingApproval | PASS |
| UAT-NC-26 | QA nhà máy duyệt NC Nghiêm trọng | ROLE_DENIED | PASS |
| UAT-NC-27/27b | QA tập đoàn duyệt → confirm-done | Processing → PendingVerification | PASS |
| UAT-NC-28 | Đóng khi lô còn Held | BUSINESS_RULE | PASS |
| UAT-NC-29a/29 | Gỡ khoanh trong Processing → đóng | Scrapped → Closed | PASS |
| UAT-CAPA-01..14 | Vòng đời CAPA + guard | Đúng state machine | PASS (14/14) |
| UAT-AGG-01/02 | OOS cùng lô 2 lần | CREATED → APPENDED cùng số hiệu | PASS |
| UAT-AGG-03 | Ticket Food_Safety | Nghiêm trọng + foodSafety | PASS |
| UAT-ATT-01 | Upload/list/download | 200, file xuất hiện | PASS |
| **UAT-VER-01** | **Endpoint sửa kết quả kiểm chứng (I7)** | **Tồn tại** | **FAIL — D-04** |
| UAT-RPT-01 | Dashboard API | Trả đủ 21 khoá số liệu | PASS |

## 5. Bảng case chi tiết (E2E)

| ID | Nội dung | Kết quả |
|---|---|---|
| UAT-UI-01 | Danh sách NC: title, nav, 3 chỉ số, 7 cột | PASS |
| UAT-UI-02 | Dòng dữ liệu + link chi tiết đúng số hiệu | PASS |
| UAT-UI-03 | Dashboard hiển thị số liệu | PASS |
| UAT-UI-04 | Trang PR và Ticket tải được | PASS |
| UAT-UI-05 | Chuyển tài khoản demo đổi phiên | PASS |
| **UAT-UI-10** | **Tạo NC từ form → mở chi tiết** | **FAIL — D-02 (404)** |
| UAT-UI-11 | Phiếu vẫn được tạo, mở được bằng URL đúng context path | PASS |
| UAT-UI-12 | Chi tiết: thông tin, chỉ tiêu, nút theo trạng thái | PASS |
| UAT-UI-13 | Đóng nhanh qua UI (2 prompt) → Closed | PASS |
| UAT-UI-14 | Trình duyệt qua UI (5 prompt) → PendingApproval | PASS |
| UAT-UI-15 | Guard trả lý do rõ ràng | PASS |
| UAT-UI-16 | Upload file qua UI → hiện trong danh sách | PASS |
| UAT-UI-17 | Lọc theo nhà máy của tài khoản | PASS |
| UAT-UI-20 | Danh sách dùng được trên mobile | PASS |
| **UAT-UI-21** | **Chi tiết NC không tràn ngang (mobile)** | **FAIL — D-03 (192px)** |
| UAT-UI-22 | Form tạo NC nhập được trên mobile | PASS |

## 6. Ghi chú vận hành

- Bộ test tạo dữ liệu thật trên DB demo. Lọc dọn: `SELECT * FROM nc WHERE lo_kiem LIKE 'LOT-UAT%';`
- Mã lỗi dùng trong test: `FG-01` (Nhẹ), `PRC-02`/`CMP-02` (Nặng), `FG-03`/`FG-04`/`CMP-01` (Nghiêm trọng).
- Tài khoản: `sxmgp` (SX), `khomgp` (Kho), `qamgp` (QA nhà máy), `qatd` (QA tập đoàn).
- Test E2E chạy qua nginx public để `fetch('/api/v1/...')` được rewrite đúng; chạy trực tiếp cổng 3110 sẽ hỏng API do thiếu context path.
