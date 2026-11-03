# Task: VERSIONING bản ghi đã xác nhận (REQ mục 7, I7)

## Phạm vi
- Cơ chế TỔNG QUÁT dùng chung: bảng `record_version` (V13) + `RecordVersionService.luuSnapshot(...)`.
- Áp cho MỘT loại tiêu biểu: kết quả kiểm chứng CAPA đã xác nhận (`doi_tuong_loai = CAPA_VERIFICATION`).
- Mở rộng cho loại khác (vd disposition NC đã duyệt) về sau chỉ cần gọi `luuSnapshot` với `doiTuongLoai` mới — KHÔNG làm trong task này (giữ gọn, tránh over-engineer).

## Đã làm
1. `V13__record_version.sql` — bảng dùng chung (TEXT snapshot_json, UNIQUE (loai,id,phien_ban)).
2. Entity `common/version/RecordVersion` + `RecordVersionRepository` (findBy...OrderByPhienBanDesc, count, maxPhienBan).
3. `RecordVersionService.luuSnapshot` — serialize bằng Jackson ObjectMapper injected, phien_ban = max+1 (bắt đầu 1). + `lichSu(...)`.
4. `CapaService.suaKetQuaKiemChung(capaId, req)` — điểm áp invariant: verification đã có ket_qua
   (HIEU_QUA/KHONG_HIEU_QUA) bị sửa → LƯU SNAPSHOT bản cũ TRƯỚC rồi mới áp giá trị mới.
   Lý do chọn điểm này: confirmEffective/markIneffective chỉ GHI LẦN ĐẦU (chưa có kết quả) nên không
   phải "sửa bản đã xác nhận"; method mới là luồng sửa-lại sau khi đã confirm.
5. Test `common/version/RecordVersionTest` (kế thừa MySqlTestBase): sửa 2 lần → phien_ban 1,2;
   snapshot JSON parse được + chứa giá trị CŨ; giá trị hiện tại là bản MỚI. KHÔNG so wall-clock.

## Verify
- Máy local chỉ có JRE 8 → verify qua CI GitHub Actions (`mvn -B -ntp clean test`) sau khi push main.
