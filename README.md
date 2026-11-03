# App mẫu NC/CAPA (Đợt a)

Baseline nghiệp vụ: `REQ_NC-CAPA-QLone_v1.0`. Tài liệu kỹ thuật: thư mục `../tech/`.
Stack: Java 17 + Spring Boot 3.2 + JDBC MySQL 8 (JPA + JdbcTemplate) + Flyway + JUnit 5/Testcontainers.

## Yêu cầu môi trường
- JDK 17+ (bắt buộc — Spring Boot 3 không chạy trên Java 8).
- MySQL 8 (để chạy app). Docker (để chạy integration test Testcontainers).
- Maven 3.9+ hoặc dùng Maven Wrapper.

## Tạo DB (chạy 1 lần)
```sql
CREATE DATABASE nc_capa CHARACTER SET utf8mb4;
CREATE USER 'nc_capa'@'%' IDENTIFIED BY 'nc_capa';
GRANT ALL ON nc_capa.* TO 'nc_capa'@'%';
```

## Build & chạy
```powershell
# nếu đã cài Maven:
mvn clean package
mvn spring-boot:run

# cấu hình kết nối qua biến môi trường (tuỳ chọn):
$env:DB_URL="jdbc:mysql://localhost:3306/nc_capa?useSSL=false&serverTimezone=Asia/Ho_Chi_Minh&allowPublicKeyRetrieval=true"
$env:DB_USER="nc_capa"; $env:DB_PASS="nc_capa"
```
- Swagger UI: http://localhost:8080/swagger-ui.html
- Flyway tự migrate toàn bộ (V1 trở đi) khi khởi động.

## Maven Wrapper
Repo kèm `.mvn/wrapper/maven-wrapper.properties`. Nếu chưa có `mvnw`/`mvnw.cmd`, sinh bằng:
```powershell
mvn -N wrapper:wrapper -Dmaven=3.9.6
```
Sau đó dùng `./mvnw clean package` thay cho `mvn`.

## Test
```powershell
mvn test        # unit + integration (các IT cần Docker đang chạy cho Testcontainers MySQL)
```
- Test chạm DB kế thừa `MySqlTestBase` → dùng Testcontainers MySQL 8 (giống production). Không có Docker thì các test này không chạy được.

## Trạng thái hiện tại (lát cắt 1–6 — hoàn tất Đợt a core)
Đã có:
- Khung project, cấu hình, Flyway V1–V6 (toàn bộ schema Đợt a).
- Tạo NC thủ công + sinh số hiệu an toàn concurrency (`KPH-{năm}-{nhà máy}-{số}`).
- Luồng Nhanh `New → Closed` (close-fast, chỉ NC Nhẹ).
- Luồng Có xem xét (NC Nặng) 5 bước: submit-approval → approve (2 cấp, 2 user khác nhau) → confirm-done (chi phí thực tế bắt buộc) → verify-close.
- Luồng Có khoanh vùng (NC Nghiêm trọng / an toàn TP): containment (≥1 lô khoanh + chứng từ Hold bắt buộc) → submit-approval (chặn bỏ qua Containment) → approve (QA TĐ 1 lần) → unhold dòng lô trong Processing → confirm-done → verify-close (chỉ đóng khi mọi dòng khoanh có trạng thái cuối).
- Duyệt 2 cấp + SoD (người đề xuất không tự duyệt) + reject/resubmit reset duyệt cấp 1.
- Guard N9 chống deadlock (CapaClosureChecker) + chỉ kiểm dòng khoanh_vung=1 (vá 2.1).
- Nâng bậc: Nhẹ→Nặng (giữ New, khóa close-fast — vá #4); Nhẹ/Nặng→Nghiêm trọng (sang Containment — vá vênh 3.1).
- Audit log cho mọi hành động trọng yếu.
- Test: `NcLifecycleTest`, `EscalationTest`, `NcReviewFlowTest`, `NcContainmentFlowTest` (AC1, AC2, AC5, AC8, AC19, AC21, AC24, AC25, SoD).

- Auto-aggregation (OOS → NC): mutex `nc_agg_lock` (upsert đầu `onOos`), append `nc_chi_tieu` + nâng mức theo chỉ tiêu nặng nhất nếu có NC mở cùng lô, ngược lại tạo NC mới; endpoint `POST /ingest/oos`.
- CAPA (lát 5): tạo từ NC (bật is_escalated, link nc_capa) → Open → Investigating (kế hoạch bắt buộc nếu NC≥Nặng — vá #3) → ActionPlan (RCA/nhóm nguyên nhân/chi phí nếu Nặng) → action con → Processing (≥1 action, 2 ô nếu Nặng, callback mốc sớm mô phỏng) → complete action → Monitoring (đặt X ngày + N lô). Gia hạn action: yêu cầu → duyệt (người duyệt ≠ người yêu cầu) → mới đổi deadline.
- CAPA kiểm chứng (lát 6): verification-status (xDat/nDat theo `han_kiem_chung_tiep_theo` + đếm lô MES mô phỏng theo từng mã hàng); postpone-reminder (dời mốc chưa đạt, quá M lần → tự Ineffective); confirm-effective (chỉ khi đủ cả 2 mốc / N=0+biên bản → Closed); mark-ineffective (TH1: sinh NC kế thừa nâng 1 bậc, set handover_done, trần Nghiêm trọng/fail lần 2 → cờ mở PR). `CapaClosureCheckerImpl` thật → guard N9 chặn/đóng NC đúng vá #1.
- Test: thêm `AutoAggregationTest`, `CapaFlowTest`, `CapaVerificationTest` (AC3, AC22, AC14, AC10, AC9, AC11, AC12).

- TH2 fail sớm tự động: NC mới cùng (mã hàng+mã lỗi) phát sinh trong lúc CAPA Monitoring → CAPA tự Ineffective, NC mới là kế thừa (nâng 1 bậc, không đẻ NC thứ 3) — hook `EarlyFailHook`/`EarlyFailDetector`.
- Ticket ingest (`POST /ingest/ticket`): Food_Safety ép Nghiêm trọng + Containment, không cho tự phân loại; nhiều ticket cùng lô gom 1 NC (nhiều-một qua nc_ticket).
- Seed demo: `DemoSeeder` (ApplicationRunner) nạp master data + bộ mã lỗi APPX + MES mô phỏng khi `app.seed.enabled=true`.
- Test: thêm `EarlyFailTh2Test`, `TicketIngestTest`.

## Đã bổ sung sau Đợt a (các đợt b/c/d + hardening)
- PR nhiều cấp (ma trận định tuyến theo `LoaiThayDoi`) + 8D (D0–D8).
- Callback ticket 2 chiều có idempotency + retry + dead-letter (outbox) + scheduler flush.
- Dashboard + báo cáo chuyên sâu: NC theo mức/trạng thái/nguồn/nhà máy, NC quá hạn, tỷ lệ CAPA hiệu quả, tuổi NC đang mở, thời gian đóng trung bình, cost-of-quality, top mã lỗi/mã hàng tái diễn.
- RCA cấu trúc (FIVE_WHY/FISHBONE/IS_IS_NOT) + nhóm nguyên nhân 6M + cờ tái diễn + kế hoạch điều tra tách 3 trường.
- Loại hành động CAPA Corrective/Preventive.
- Upload file đính kèm (lưu local `uploads/`) + whitelist content-type + sniff magic-number + giới hạn tổng dung lượng.
- Tự động mở PR khi CAPA Ineffective/fail lần 2/trần Nghiêm trọng, và khi NC tái diễn lần 3 trong cửa sổ 90 ngày (idempotent, chống trùng 1 PR/vấn đề).
- SoD theo role thật (tra `app_user_role`): duyệt PR và duyệt disposition NC xác minh vai trò thật thay vì tin client khai; dời hạn mốc kiểm chứng NC Nặng+ chỉ QA tập đoàn.
- Scope theo nhà máy: danh sách NC lọc theo nhà máy của người dùng; duyệt NC chặn thao tác xuyên nhà máy (QA tập đoàn toàn quyền).
- Nút chuyển tài khoản demo (giả lập user hiện tại trong session, không login thật).
- Seed demo: gán `app_user_role` + phiếu NC/CAPA/PR mẫu đủ trạng thái (tắt ở profile test).
- Docker (Dockerfile + docker-compose) + CI GitHub Actions (`mvn -B -ntp clean test`).
- Hệ thống thông báo (outbox): nhắc mốc kiểm chứng CAPA sắp/đến hạn + cảnh báo action CAPA quá hạn; lưu bảng `notification` (PENDING→SENT) + log mô phỏng gửi email (chỗ cắm SMTP sau), có scheduler quét/flush định kỳ (tắt ở profile test).

Chưa làm (ngoài phạm vi app mẫu hiện tại): Spring Security / login thật (hiện dùng cơ chế demo + SoD theo role ở tầng service); import master data từ file xlsx thật (hiện seed tay theo APPX); versioning bản ghi đã xác nhận (hiện audit log che phần lớn nhu cầu truy vết).

## Lưu ý verify
Tài liệu code được viết cho JDK 17; máy phát triển hiện tại là Java 8 nên **chưa build/test tại chỗ**. Cần JDK 17 + Maven (+ Docker cho IT) để `mvn test` xanh.
