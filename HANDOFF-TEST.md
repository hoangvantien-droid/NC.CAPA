# HANDOFF — Yêu cầu build & test (gửi coding agent máy khác)

App mẫu NC/CAPA (Đợt a), lát cắt 1–3. Code viết trên máy **không có JDK/Maven** nên **chưa từng build/test**. Nhiệm vụ của bạn: build, chạy test, sửa lỗi biên dịch/test nếu có, báo lại kết quả.

## 0. Bối cảnh ngắn
- Stack: Java 17 + Spring Boot 3.2.5 + Spring Data JPA + JdbcTemplate + MySQL 8 (JDBC) + Flyway + JUnit 5 + Testcontainers.
- Phạm vi đã code: toàn bộ vòng đời **NC** (3 luồng) — chưa có module CAPA (để lát sau).
- Baseline tài liệu: `REQ_NC-CAPA-QLone_v1.0` + 5 tài liệu `tech/` (nếu được gửi kèm). Nếu không có, README này đủ để test.

## 1. Yêu cầu môi trường
- JDK 17+ (BẮT BUỘC — Spring Boot 3 không chạy trên Java 8).
- Maven 3.9+ (hoặc sinh wrapper: `mvn -N wrapper:wrapper -Dmaven=3.9.6`).
- **Docker** đang chạy (các integration test dùng Testcontainers MySQL 8). Không có Docker thì test hạ tầng DB không chạy được.

## 2. Lệnh test
```bash
cd app
mvn clean test
```
- Không cần tạo DB tay: Testcontainers tự dựng MySQL 8, Flyway tự migrate V1–V6.
- Nếu muốn chạy app thật: xem `README.md`.

## 3. Kỳ vọng: 4 test class phải XANH
| Test class | Kiểm gì | AC |
|---|---|---|
| `NcLifecycleTest` | Tạo NC, format số hiệu `KPH-\d{4}-MGP-\d{5}`, mức = mã lỗi nặng nhất, luồng Nhanh New→Closed, close-fast chặn NC không Nhẹ | AC1, AC2 |
| `EscalationTest` | Nhẹ(New)→Nặng giữ New + khóa close-fast; Nhẹ(New)→Nghiêm trọng sang Containment; chặn nâng xuống bậc | AC5, AC24 |
| `NcReviewFlowTest` | Luồng Nặng 5 bước; duyệt 2 cấp bởi 2 user khác nhau; cùng user duyệt 2 lần bị chặn; SoD (người đề xuất ≠ người duyệt); confirm-done thiếu chi phí bị chặn; N9 KHÔNG chặn NC Nặng không khoanh vùng | AC2, AC8, AC21, AC25 |
| `NcContainmentFlowTest` | NC Nghiêm trọng bắt buộc Containment; thiếu chứng từ Hold bị chặn; lô khoanh chưa gỡ → không đóng; unhold dòng lô → đóng được | AC2, AC19 |
| `AutoAggregationTest` | OOS thứ 2 cùng lô → APPENDED + nâng mức (không tạo NC mới); OOS Nghiêm trọng → food safety; **2 OOS cùng lô đồng thời → đúng 1 NC** (mutex nc_agg_lock, cần Docker) | AC3, AC22 |
| `CapaFlowTest` | CAPA từ NC Nhẹ qua Investigating→ActionPlan không kẹt; NC Nặng thiếu kế hoạch bị chặn; luồng đầy đủ tới Monitoring; gia hạn cần duyệt (người duyệt ≠ người yêu cầu) mới đổi deadline | AC14, AC10 |
| `CapaVerificationTest` | N=0+biên bản đóng được (chưa đủ X bị chặn); N≥1 chưa đủ lô → không confirm; quá M lần dời → tự Ineffective; mark-ineffective sinh NC kế thừa (Nặng→Nghiêm trọng) + mở PR + NC gốc đóng được (handover, không treo) | AC9, AC11, AC12 |
| `EarlyFailTh2Test` | NC mới cùng (mã hàng+mã lỗi) trong Monitoring → CAPA tự Ineffective, NC mới là kế thừa nâng 1 bậc, KHÔNG đẻ NC thứ 3; khác mã lỗi → không fail sớm | AC15 |
| `TicketIngestTest` | Food_Safety → ép Nghiêm trọng + Containment + link ticket (không cho tự phân loại); nhiều ticket cùng lô → gom 1 NC (nhiều-một) | REQ §5, §7#5 |

Tổng: ~31 test method. Tất cả phải pass.

### Lưu ý đợt bổ sung (TH2 + ticket + seed)
- TH2 qua hook `EarlyFailHook` (interface ở `nc.service`) + impl `EarlyFailDetector` (@Service ở `capa.service`), bean no-op `NoEarlyFailHook` dùng @ConditionalOnMissingBean — giống pattern CapaClosureChecker. Gọi sau khi tạo NC (thủ công + OOS + ticket).
- Không có vòng phụ thuộc bean: NcService → EarlyFailHook/CapaClosureChecker (impl không trỏ ngược NcService). Nếu Spring báo circular dependency → kiểm lại.
- `DemoSeeder` (ApplicationRunner) seed khi `app.seed.enabled=true` + bảng `nha_may` rỗng. Test chạy `seed.enabled=false` nên KHÔNG seed — test tự nạp qua `DefectSeed`. Bộ mã lỗi demo theo APPX.
- `DefectSeed` (test helper) đã thêm CMP-01/CMP-02 cho test ticket.

### Lưu ý lát 6 (kiểm chứng CAPA)
- `CapaVerificationTest` lùi `monitoring_bat_dau` và `han_kiem_chung_tiep_theo` về quá khứ bằng `UPDATE ... DATE_SUB(NOW(), INTERVAL ...)` để mô phỏng "đã đủ X ngày" mà không chờ thật — cú pháp MySQL, cần Testcontainers.
- `CapaClosureCheckerImpl` (@Service) thay bean no-op mặc định (@ConditionalOnMissingBean). Từ lát 6, guard N9 của NC THẬT SỰ chặn khi CAPA chưa kết thúc. Nếu test lát 2–3 (NC không có CAPA) vẫn phải xanh — vì NC không link CAPA nào thì checker trả null.
- `MesLotCounter`/`MockMesLotCounter` đếm trên `mes_lot_event` (rỗng trong test → N chưa đạt, đúng kỳ vọng `chuaDuLo_khongConfirm`).
- postpone quá M lần: KHÔNG ném exception (tránh rollback side-effect) — trả CAPA đã Ineffective.

### Lưu ý lát 5 (CAPA)
- Module CAPA mới: entity `Capa/CapaAction/CapaActionExtension`, enum `CapaStatus/ActionStatus/ActionType/ExtensionStatus` đều gắn `@JdbcTypeCode(SqlTypes.VARCHAR)` theo quy ước §6b.
- `NcCapaLinkDao` dùng JdbcTemplate cho bảng `nc_capa` (không map entity khóa phức hợp).
- `CapaClosureChecker` VẪN là bean mặc định (no-op) ở lát 5 — impl thật đến lát 6. Guard N9 đóng NC chưa chặn theo CAPA ở lát này.

### Lưu ý lát 4 (auto-aggregation)
- `AutoAggregationTest.haiOosCungLoDongThoi_chiMotNc` là test concurrency 2 thread — **bắt buộc MySQL thật** (Testcontainers) + isolation READ COMMITTED (đã cấu hình). Kỳ vọng: 1 CREATED + 1 APPENDED, cùng 1 số hiệu.
- Mutex `nc_agg_lock` upsert ở đầu `onOos` (không retry đệ quy). Nếu test race flaky → kiểm `transaction-isolation: TRANSACTION_READ_COMMITTED` trong `application.yml` còn nguyên.

## 4. NHỮNG ĐIỂM DỄ LỖI — soi kỹ (vì chưa từng biên dịch)
1. **Chữ ký constructor `NcService`** (8 tham số): `NcRepository, NcChiTieuRepository, NcDoiTuongRepository, DefectCatalogService, SequenceGenerator, AuditService, CapaClosureChecker, AppProperties`. Kiểm Spring DI inject đủ.
2. **`SequenceGenerator`** dùng SQL MySQL thuần: `INSERT ... VALUES (?, ?, LAST_INSERT_ID(1)) ON DUPLICATE KEY UPDATE last_no = LAST_INSERT_ID(last_no + 1)` rồi `SELECT LAST_INSERT_ID()`. **Chỉ chạy đúng trên MySQL** (không phải H2) — đó là lý do test dùng Testcontainers. Xác nhận trả về số >0 ngay từ bản ghi đầu tiên của (năm, nhà máy) — KHÔNG được trả 0.
3. **`@Transactional(propagation = MANDATORY)` trên `SequenceGenerator.nextSoHieu`**: bắt buộc gọi trong transaction của `NcService.taoNc`. Nếu ném `IllegalTransactionStateException` nghĩa là thiếu `@Transactional` ở caller.
4. **`ddl-auto: validate`**: Hibernate validate entity khớp schema Flyway. Nếu app không khởi động với lỗi "schema validation" → có cột entity không khớp DDL (đối chiếu `V3__nc_tables.sql` với `Nc.java`). Chú ý các cột: `ma_hang_chinh`, `ma_loi_chinh`, `nguoi_de_xuat_disposition`, `nguoi_duyet_cap_1`, `da_duyet_cap_1`.
5. **`CapaClosureChecker`**: lát này dùng bean mặc định (lambda `ncSoHieu -> null`) qua `NoCapaClosureChecker` (`@ConditionalOnMissingBean`). Guard N9 phải cho đóng NC khi không có CAPA.
6. **SoD mặc định bật** (`app.sod.enabled=true`): test NcReviewFlowTest dùng người đề xuất `proposer` khác người duyệt `qaNM1/qaNM2`. Nếu đổi test nhớ giữ nguyên tắc này.
7. **Isolation READ COMMITTED** cấu hình ở `application.yml` (Hikari). Không bắt buộc cho lát 1–3 nhưng giữ nguyên cho lát auto-aggregation sau.
8. **Charset tiếng Việt**: dữ liệu test có dấu; DDL để `utf8mb4`. Nếu lỗi encoding, kiểm connection string Testcontainers.

## 5. Nếu phải sửa, giữ các bất biến nghiệp vụ (đừng đổi hành vi)
- Số hiệu NC: `KPH-{năm}-{nhàmáy}-{5 chữ số}`, nguyên tử, không trùng/nhảy.
- close-fast CHỈ cho NC Nhẹ ở New.
- Nâng bậc Nhẹ→Nặng: GIỮ trạng thái New (không nhảy PendingApproval), khóa close-fast.
- Nâng lên Nghiêm trọng: sang Containment, bật food safety.
- NC Nghiêm trọng/food safety: bắt buộc Containment trước PendingApproval; Containment cần ≥1 lô khoanh + chứng từ Hold.
- Duyệt NC Nặng: 2 lượt bởi 2 user khác nhau; NC Nghiêm trọng: 1 lượt (QA TĐ).
- confirm-done: bắt buộc chi phí thực tế.
- verify-close (N9): chặn nếu còn CAPA chưa kết thúc HOẶC còn dòng lô `khoanh_vung=1` chưa đạt trạng thái cuối (Released/Scrapped/Reworked). Dòng KHÔNG khoanh vùng (trạng thái NULL) KHÔNG chặn.

## 6. Báo lại giúp
- Kết quả `mvn clean test` (số test pass/fail).
- Mọi lỗi biên dịch đã sửa (file + dòng + nguyên nhân).
- Mọi test phải chỉnh và lý do (nếu có) — để đồng bộ ngược về máy gốc.
- Đầu ra `mvn -version` + `java -version` + có Docker hay không.

## 6b. Sửa đổi đã áp sau vòng test đầu (đồng bộ máy gốc — KHÔNG cần làm lại)
1. `testcontainers.version` 1.19.8 → **1.21.3** + thêm `src/test/resources/docker-java.properties` (`api.version=1.44`) để tương thích Docker 29 (API 1.32 < 1.40).
2. Thêm `@JdbcTypeCode(SqlTypes.VARCHAR)` cho 6 field enum (`MaLoi`: 3; `Nc`: 3) để Hibernate validate khớp cột VARCHAR (không phải ENUM native).
3. Thêm `@JdbcTypeCode(SqlTypes.TINYINT)` cho `NcChiTieu.in_out_spec` + `Capa.rpn_s/rpn_o/rpn_d` (Integer ↔ TINYINT).
> Quy ước: field enum → `@JdbcTypeCode(SqlTypes.VARCHAR)`; field Integer ánh xạ cột TINYINT → `@JdbcTypeCode(SqlTypes.TINYINT)`.
> Lát 6 thêm import `NcSource` trong `NcService` (dùng `NcSource.NOI_BO` ở `taoNcKeThua`).
> Đợt bổ sung: thêm getter `Nc.getNcKeThuaTuCapaId()`; `ViewController.hanhDongChoPhep` trả `record HanhDong(ma, nhan)` (template dùng `h.ma`/`h.nhan`).
> **`MySqlTestBase` có `@BeforeEach resetDatabase()`** TRUNCATE mọi bảng (trừ flyway_schema_history, tắt FK check) — BẮT BUỘC vì TH2 quét toàn DB, CAPA Monitoring sót lại từ test trước sẽ kích hoạt fail sớm nhầm. Chạy trước seed của lớp con (JUnit 5: superclass @BeforeEach trước).

## 6c. Giao diện Thymeleaf (mới)
- Thêm `spring-boot-starter-thymeleaf`; `ViewController` + templates `nc-list/nc-detail/nc-create` + `static/css/app.css`.
- UI chỉ đọc/điều hướng; thao tác gọi REST API hiện có qua fetch. KHÔNG có test tự động cho UI (chỉ cần build compile sạch + app khởi động).
- Kiểm tra giúp: `mvn clean test` vẫn xanh (UI không ảnh hưởng test backend); `mvn spring-boot:run` rồi mở `http://localhost:8080/` (seed bật mặc định → có dữ liệu demo). Màn: `/` danh sách, `/nc/tao` tạo, `/nc/{soHieu}` chi tiết.
- Font Google (Inter + IBM Plex Mono) nạp qua CDN — nếu máy test offline, UI vẫn chạy (fallback system font), không ảnh hưởng build/test.

## 7. KHÔNG nằm trong phạm vi lát này (đừng báo là thiếu)
- Module CAPA, auto-aggregation (OOS), seed master data từ CSV, security/login, luồng PR/8D, callback ticket. Các bảng/cờ liên quan đã có trong schema nhưng CHƯA nối luồng — đúng thiết kế.
