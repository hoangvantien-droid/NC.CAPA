# UAT NC/CAPA

Bộ kiểm thử chấp nhận (UAT) cho app NC/CAPA: **kiểm thử chức năng qua API** + **E2E giao diện bằng Playwright**.

## Cấu trúc

```
uat/
├── UAT-NC-CAPA.md        # Kế hoạch + bảng case + kết quả + danh sách lỗi
├── run-uat.sh            # Chạy cả 2 lớp, ghi kết quả vào results/
├── api/uat-api.mjs       # 53 case chức năng (Node 20+, không cần thư viện)
├── e2e/                  # Playwright: 16 case desktop + 3 case mobile
│   ├── playwright.config.ts
│   └── tests/{helpers,01-smoke,02-nc,03-responsive}.spec.ts
└── results/              # JSON + HTML report mỗi lần chạy
```

## Chạy nhanh

```bash
cd uat && ./run-uat.sh
```

Hoặc chạy riêng:

```bash
# API (mặc định gọi thẳng app local)
UAT_BASE=http://127.0.0.1:3110/nc-capa node uat/api/uat-api.mjs

# E2E (mặc định gọi bản public qua nginx để có rewrite /api/v1)
cd uat/e2e && npx playwright test
```

## Biến môi trường

| Biến | Mặc định | Ý nghĩa |
|---|---|---|
| `UAT_BASE` | `http://127.0.0.1:3110/nc-capa` | Gốc URL cho test API |
| `UAT_ORIGIN` | `https://tienhv.io.vn` | Origin cho Playwright |
| `UAT_ROOT` | `/nc-capa` | Context path của app |
| `UAT_RUN` | timestamp | Nhãn lần chạy (dùng trong tên lô/file) |

## Yêu cầu môi trường

- Node 20+ (dùng `fetch`/`FormData` built-in)
- Playwright: `cd uat/e2e && npm install && npx playwright install chromium`
- App đang chạy + DB demo đã có master data và `app_user_role` (xem lỗi D-01 trong `UAT-NC-CAPA.md`)

## Quy ước dữ liệu test

- Mọi lô sinh ra có tiền tố `LOT-UAT-<run>` để lọc/dọn.
- File đính kèm test đặt tên `uat-<run>.txt`.
- Không xoá dữ liệu tự động — UAT chạy trên DB demo dùng chung.

## Đọc kết quả

- `results/api-uat-<run>.json` — từng case PASS/FAIL kèm thông điệp lỗi thật.
- `results/e2e-latest.json` + `results/e2e-html/index.html` — report Playwright (kèm trace, screenshot khi fail).
- Tổng hợp và phân tích lỗi: `UAT-NC-CAPA.md`.
