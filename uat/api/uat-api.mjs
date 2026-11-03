#!/usr/bin/env node
/**
 * UAT chức năng (API) cho NC/CAPA — chạy trực tiếp trên môi trường đang deploy.
 *
 *   UAT_BASE=http://127.0.0.1:3110/nc-capa node uat/api/uat-api.mjs
 *
 * Nguyên tắc:
 *  - Mỗi test tạo dữ liệu riêng (lô có timestamp) để không phụ thuộc seed.
 *  - Test "phải chặn" kiểm tra đúng mã lỗi nghiệp vụ, không chỉ status != 200.
 *  - Không xoá dữ liệu: đây là UAT trên DB demo; lọc theo tiền tố lô LOT-UAT-*.
 */
import { writeFileSync, mkdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const BASE = (process.env.UAT_BASE || 'http://127.0.0.1:3110/nc-capa') + '/api/v1';
const RUN = process.env.UAT_RUN || new Date().toISOString().replace(/[-:TZ.]/g, '').slice(0, 14);
const lot = (n) => `LOT-UAT-${RUN}-${n}`;

const results = [];
let currentGroup = '';

function group(name) { currentGroup = name; console.log(`\n=== ${name}`); }

function record(id, name, ok, detail = '') {
  results.push({ id, group: currentGroup, name, status: ok ? 'PASS' : 'FAIL', detail });
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${id}  ${name}${detail ? '  — ' + detail : ''}`);
}

function assert(id, name, cond, detail = '') { record(id, name, !!cond, detail); return !!cond; }

async function call(method, path, body) {
  const opts = { method, headers: {} };
  if (body !== undefined) {
    opts.headers['Content-Type'] = 'application/json';
    opts.body = JSON.stringify(body);
  }
  const res = await fetch(BASE + path, opts);
  const text = await res.text();
  let json = null;
  try { json = JSON.parse(text); } catch { /* non-JSON */ }
  return { status: res.status, json, text };
}

const ok = (r) => r.json && r.json.success === true;
const errCode = (r) => (r.json && r.json.error && r.json.error.code) || null;
const errMsg = (r) => (r.json && r.json.error && r.json.error.message) || r.text?.slice(0, 120) || '';
const data = (r) => r.json && r.json.data;

// ---------- helpers ----------
async function taoNc({ maLoiId, maHang = 'SP001', loKiem, nhaMay = 'MGP', chiTieu }) {
  return call('POST', '/nc', {
    loaiNc: 'THANH_PHAM', nguonPhatSinh: 'THU_CONG', nhaMay,
    nguoiBaoCao: 'bcmgp', loKiem, moTaLoi: 'UAT automated',
    chiTieu: chiTieu || [{ maLoiId, maHang }],
  });
}

async function ncNangMoi(tag) {
  const r = await taoNc({ maLoiId: 'PRC-02', loKiem: lot(tag) });
  if (!ok(r)) throw new Error('không tạo được NC Nặng: ' + errMsg(r));
  return data(r).soHieu;
}

async function ncNghiemTrongMoi(tag) {
  const r = await taoNc({ maLoiId: 'FG-03', loKiem: lot(tag) });
  if (!ok(r)) throw new Error('không tạo được NC Nghiêm trọng: ' + errMsg(r));
  return data(r).soHieu;
}

// =====================================================================
// A. Tạo NC & phân loại mức
// =====================================================================
group('A. Tạo NC & phân loại');

const r01 = await taoNc({ maLoiId: 'FG-01', loKiem: lot('A1') });
const ncNhe = ok(r01) ? data(r01) : null;
assert('UAT-NC-01', 'Tạo NC Nhẹ (FG-01) → New/NHE, số hiệu đúng định dạng',
  ncNhe && /^KPH-\d{4}-MGP-\d{5}$/.test(ncNhe.soHieu) && ncNhe.mucNghiemTrong === 'NHE' && ncNhe.trangThai === 'New',
  ncNhe ? `${ncNhe.soHieu} · ${ncNhe.mucNghiemTrong}/${ncNhe.trangThai}` : errMsg(r01));

const r02 = await taoNc({ maLoiId: 'KHONG-CO-MA-NAY', loKiem: lot('A2') });
assert('UAT-NC-02', 'Mã lỗi không tồn tại → bị chặn', !ok(r02) && errCode(r02), `${r02.status} ${errCode(r02)} ${errMsg(r02)}`);

const r03 = await call('POST', '/nc', { loaiNc: 'THANH_PHAM', nguonPhatSinh: 'THU_CONG', nhaMay: 'MGP', nguoiBaoCao: 'bcmgp' });
assert('UAT-NC-03', 'Thiếu chiTieu → lỗi validation 400', r03.status === 400 || (r03.json && r03.json.success === false), `${r03.status} ${errMsg(r03)}`);

const r04 = await taoNc({ loKiem: lot('A4'), chiTieu: [{ maLoiId: 'FG-01', maHang: 'SP001' }, { maLoiId: 'FG-03', maHang: 'SP001' }] });
const nc04 = ok(r04) ? data(r04) : null;
assert('UAT-NC-04', 'Nhiều chỉ tiêu → mức = mã lỗi nặng nhất (NGHIEM_TRONG)', nc04 && nc04.mucNghiemTrong === 'NGHIEM_TRONG',
  nc04 ? `${nc04.soHieu} · ${nc04.mucNghiemTrong}` : errMsg(r04));

// =====================================================================
// B. Luồng nhanh (close-fast)
// =====================================================================
group('B. Luồng nhanh');

const nc05 = (await taoNc({ maLoiId: 'FG-01', loKiem: lot('B5') }));
const so05 = data(nc05)?.soHieu;
const r05 = await call('POST', `/nc/${so05}/close-fast`, { ketQuaXuLy: 'Xử lý tại chỗ', actor: 'qamgp' });
assert('UAT-NC-05', 'close-fast trên NC Nhẹ ở New → Closed', ok(r05) && data(r05).trangThai === 'Closed',
  ok(r05) ? data(r05).trangThai : errMsg(r05));

const so06 = await ncNangMoi('B6');
const r06 = await call('POST', `/nc/${so06}/close-fast`, { ketQuaXuLy: 'thử', actor: 'qamgp' });
assert('UAT-NC-06', 'close-fast trên NC Nặng → bị chặn', !ok(r06), `${errCode(r06)} ${errMsg(r06)}`);

const r07 = await call('POST', `/nc/${so05}/close-fast`, { ketQuaXuLy: 'lần 2', actor: 'qamgp' });
assert('UAT-NC-07', 'close-fast trên NC đã Closed → bị chặn', !ok(r07), `${errCode(r07)} ${errMsg(r07)}`);

// =====================================================================
// C. Nâng bậc
// =====================================================================
group('C. Nâng bậc');

const nc08 = (await taoNc({ maLoiId: 'FG-01', loKiem: lot('C8') }));
const so08 = data(nc08)?.soHieu;
const r08 = await call('POST', `/nc/${so08}/escalate`, { mucMoi: 'NANG', lyDo: 'UAT nâng bậc', actor: 'qamgp' });
assert('UAT-NC-08', 'Nhẹ→Nặng ở New: giữ New, khóa close-fast', ok(r08) && data(r08).mucNghiemTrong === 'NANG' && data(r08).trangThai === 'New',
  ok(r08) ? `${data(r08).mucNghiemTrong}/${data(r08).trangThai}` : errMsg(r08));
const r08b = await call('POST', `/nc/${so08}/close-fast`, { ketQuaXuLy: 'thử', actor: 'qamgp' });
assert('UAT-NC-08b', 'Sau nâng bậc, close-fast bị chặn', !ok(r08b), `${errCode(r08b)} ${errMsg(r08b)}`);

const nc09 = (await taoNc({ maLoiId: 'FG-01', loKiem: lot('C9') }));
const so09 = data(nc09)?.soHieu;
const r09 = await call('POST', `/nc/${so09}/escalate`, { mucMoi: 'NGHIEM_TRONG', lyDo: 'UAT', actor: 'qamgp' });
assert('UAT-NC-09', 'Nhẹ→Nghiêm trọng → Containment + foodSafety', ok(r09) && data(r09).trangThai === 'Containment' && data(r09).foodSafety === true,
  ok(r09) ? `${data(r09).trangThai}/foodSafety=${data(r09).foodSafety}` : errMsg(r09));

const r10 = await call('POST', `/nc/${so09}/escalate`, { mucMoi: 'NHE', lyDo: 'hạ bậc', actor: 'qamgp' });
assert('UAT-NC-10', 'Nâng xuống bậc thấp hơn → bị chặn', !ok(r10), `${errCode(r10)} ${errMsg(r10)}`);

// =====================================================================
// D. Duyệt NC Nặng 2 cấp + đóng
// =====================================================================
group('D. Duyệt NC Nặng 2 cấp');

const soD = await ncNangMoi('D11');

const r11 = await call('POST', `/nc/${soD}/submit-approval`, {
  nguyenNhanSoBo: 'UAT', dispositionDeXuat: 'REWORK', boPhanThucHien: 'SX', actor: 'sxmgp',
});
assert('UAT-NC-11', 'submit-approval thiếu chi phí ước tính → bị chặn', !ok(r11), `${errCode(r11)} ${errMsg(r11)}`);

const r12 = await call('POST', `/nc/${soD}/submit-approval`, {
  nguyenNhanSoBo: 'UAT', dispositionDeXuat: 'REWORK', chiPhiUocTinh: 150000, boPhanThucHien: 'SX', actor: 'sxmgp',
});
assert('UAT-NC-12', 'submit-approval đủ field → PendingApproval', ok(r12) && data(r12).trangThai === 'PendingApproval',
  ok(r12) ? `${data(r12).trangThai} · đề xuất=${data(r12).nguoiDeXuatDisposition}` : errMsg(r12));

const r13 = await call('POST', `/nc/${soD}/approve`, { actor: 'sxmgp' });
assert('UAT-NC-13', 'SoD: người đề xuất tự duyệt → bị chặn', !ok(r13) && errCode(r13) === 'ROLE_DENIED', `${errCode(r13)} ${errMsg(r13)}`);

const r14 = await call('POST', `/nc/${soD}/approve`, { actor: 'khomgp' });
assert('UAT-NC-14', 'Người không thuộc QA duyệt → bị chặn', !ok(r14) && errCode(r14) === 'ROLE_DENIED', `${errCode(r14)} ${errMsg(r14)}`);

const r15 = await call('POST', `/nc/${soD}/approve`, { actor: 'qamgp' });
assert('UAT-NC-15', 'QA duyệt cấp 1 → vẫn PendingApproval, ghi nhận người duyệt cấp 1',
  ok(r15) && data(r15).trangThai === 'PendingApproval' && data(r15).daDuyetCap1 === true,
  ok(r15) ? `${data(r15).trangThai} · cấp1=${data(r15).nguoiDuyetCap1}` : errMsg(r15));

const r16 = await call('POST', `/nc/${soD}/approve`, { actor: 'qamgp' });
assert('UAT-NC-16', 'Cùng người duyệt cấp 2 → bị chặn', !ok(r16), `${errCode(r16)} ${errMsg(r16)}`);

const r17 = await call('POST', `/nc/${soD}/approve`, { actor: 'qatd' });
assert('UAT-NC-17', 'QA thứ hai duyệt cấp 2 → Processing', ok(r17) && data(r17).trangThai === 'Processing',
  ok(r17) ? data(r17).trangThai : errMsg(r17));

const r18 = await call('POST', `/nc/${soD}/confirm-done`, { bangChung: 'biên bản', actor: 'sxmgp' });
assert('UAT-NC-18', 'confirm-done thiếu chi phí thực tế → bị chặn', !ok(r18), `${errCode(r18)} ${errMsg(r18)}`);

const r19 = await call('POST', `/nc/${soD}/confirm-done`, { bangChung: 'biên bản UAT', chiPhiThucTe: 120000, actor: 'sxmgp' });
assert('UAT-NC-19', 'confirm-done đủ → PendingVerification', ok(r19) && data(r19).trangThai === 'PendingVerification',
  ok(r19) ? data(r19).trangThai : errMsg(r19));

const r20 = await call('POST', `/nc/${soD}/verify-close`, { ketLuanThamTra: 'Đạt yêu cầu', actor: 'qatd' });
assert('UAT-NC-20', 'verify-close → Closed', ok(r20) && data(r20).trangThai === 'Closed',
  ok(r20) ? data(r20).trangThai : errMsg(r20));

// =====================================================================
// E. NC Nghiêm trọng: Containment → duyệt → đóng
// =====================================================================
group('E. Nghiêm trọng / Containment');

const soE = await ncNghiemTrongMoi('E21');
const r21 = await call('GET', `/nc/${soE}`);
assert('UAT-NC-21', 'NC từ FG-03 → NGHIEM_TRONG + foodSafety', ok(r21) && data(r21).mucNghiemTrong === 'NGHIEM_TRONG' && data(r21).foodSafety === true,
  ok(r21) ? `${data(r21).mucNghiemTrong}/foodSafety=${data(r21).foodSafety}` : errMsg(r21));

const r22 = await call('POST', `/nc/${soE}/submit-approval`, {
  nguyenNhanSoBo: 'UAT', dispositionDeXuat: 'TIEU_HUY', chiPhiUocTinh: 1, boPhanThucHien: 'SX', actor: 'sxmgp',
});
assert('UAT-NC-22', 'Trình duyệt trước Containment → bị chặn', !ok(r22), `${errCode(r22)} ${errMsg(r22)}`);

const r23 = await call('POST', `/nc/${soE}/containment`, { loKhoanh: [{ soLo: lot('E23') }], actor: 'qamgp' });
assert('UAT-NC-23', 'Containment thiếu chứng từ Hold → bị chặn', !ok(r23), `${errCode(r23)} ${errMsg(r23)}`);

const loE = lot('E24');
const r24 = await call('POST', `/nc/${soE}/containment`, { loKhoanh: [{ soLo: loE, chungTuHold: 'HOLD-UAT-001' }], actor: 'qamgp' });
assert('UAT-NC-24', 'Containment đủ → Containment + lô Held', ok(r24) && data(r24).trangThai === 'Containment',
  ok(r24) ? data(r24).trangThai : errMsg(r24));

const r24b = await call('GET', `/nc/${soE}/doi-tuong`);
const dts = ok(r24b) ? data(r24b) : [];
assert('UAT-NC-24b', 'Đối tượng lô được tạo với khoanhVung=1 / Held',
  dts.some((d) => d.giaTri === loE && d.khoanhVung === true && d.trangThaiDongLo === 'Held'),
  JSON.stringify(dts));

const r25 = await call('POST', `/nc/${soE}/submit-approval`, {
  nguyenNhanSoBo: 'UAT', dispositionDeXuat: 'TIEU_HUY', chiPhiUocTinh: 500000, boPhanThucHien: 'SX', actor: 'sxmgp',
});
assert('UAT-NC-25', 'Sau Containment, submit-approval → PendingApproval', ok(r25) && data(r25).trangThai === 'PendingApproval',
  ok(r25) ? data(r25).trangThai : errMsg(r25));

const r26 = await call('POST', `/nc/${soE}/approve`, { actor: 'qamgp' });
assert('UAT-NC-26', 'NC Nghiêm trọng: QA nhà máy duyệt → bị chặn (phải QA tập đoàn)',
  !ok(r26) && errCode(r26) === 'ROLE_DENIED', `${errCode(r26)} ${errMsg(r26)}`);

const r27 = await call('POST', `/nc/${soE}/approve`, { actor: 'qatd' });
assert('UAT-NC-27', 'QA tập đoàn duyệt (1 cấp) → Processing', ok(r27) && data(r27).trangThai === 'Processing',
  ok(r27) ? data(r27).trangThai : errMsg(r27));

// REQ 4.1: gỡ khoanh CHỈ trong Processing → làm trước confirm-done.
const dtE = dts.find((d) => d.giaTri === loE);
const r29a = dtE ? await call('POST', `/nc/${soE}/doi-tuong/${dtE.id}/unhold`, { chungTuUnhold: 'UNHOLD-UAT-001', trangThaiDongLo: 'Scrapped', actor: 'khomgp' }) : { status: 0, json: null };
assert('UAT-NC-29a', 'Gỡ khoanh dòng lô trong Processing → trạng thái cuối Scrapped', ok(r29a) && data(r29a).trangThaiDongLo === 'Scrapped',
  ok(r29a) ? data(r29a).trangThaiDongLo : errMsg(r29a));

const r27b = await call('POST', `/nc/${soE}/confirm-done`, { bangChung: 'biên bản tiêu hủy', chiPhiThucTe: 480000, actor: 'sxmgp' });
assert('UAT-NC-27b', 'confirm-done → PendingVerification', ok(r27b) && data(r27b).trangThai === 'PendingVerification',
  ok(r27b) ? data(r27b).trangThai : errMsg(r27b));

// Nhánh E2: NC nghiêm trọng KHÔNG gỡ khoanh → không thể đóng (guard N9).
const soE2 = await ncNghiemTrongMoi('E28');
await call('POST', `/nc/${soE2}/containment`, { loKhoanh: [{ soLo: lot('E28b'), chungTuHold: 'HOLD-UAT-002' }], actor: 'qamgp' });
await call('POST', `/nc/${soE2}/submit-approval`, { nguyenNhanSoBo: 'UAT', dispositionDeXuat: 'TIEU_HUY', chiPhiUocTinh: 100000, boPhanThucHien: 'SX', actor: 'sxmgp' });
await call('POST', `/nc/${soE2}/approve`, { actor: 'qatd' });
await call('POST', `/nc/${soE2}/confirm-done`, { bangChung: 'biên bản', chiPhiThucTe: 90000, actor: 'sxmgp' });
const r28 = await call('POST', `/nc/${soE2}/verify-close`, { ketLuanThamTra: 'thử đóng khi còn Held', actor: 'qatd' });
assert('UAT-NC-28', 'Đóng khi lô còn khoanh vùng → bị chặn (guard N9)', !ok(r28) && errCode(r28) === 'BUSINESS_RULE', `${errCode(r28)} ${errMsg(r28)}`);

const r29 = await call('POST', `/nc/${soE}/verify-close`, { ketLuanThamTra: 'Đã tiêu hủy lô', actor: 'qatd' });
assert('UAT-NC-29', 'Sau khi gỡ khoanh, verify-close → Closed', ok(r29) && data(r29).trangThai === 'Closed',
  ok(r29) ? data(r29).trangThai : errMsg(r29));

// =====================================================================
// F. CAPA
// =====================================================================
group('F. CAPA');

const soF = await ncNangMoi('F30');
const r30 = await call('POST', `/nc/${soF}/capa`, { actor: 'qamgp' });
const capaId = ok(r30) ? data(r30).id : null;
assert('UAT-CAPA-01', 'Tạo CAPA từ NC Nặng → Open', ok(r30) && data(r30).trangThai === 'Open',
  ok(r30) ? `CAPA#${capaId} · ${data(r30).trangThai}` : errMsg(r30));

const r31 = await call('POST', `/capa/${capaId}/to-investigating`, { actor: 'qamgp' });
assert('UAT-CAPA-02', 'NC Nặng: to-investigating thiếu kế hoạch → bị chặn', !ok(r31), `${errCode(r31)} ${errMsg(r31)}`);

const r32 = await call('POST', `/capa/${capaId}/to-investigating`, {
  khMucTieu: 'Tìm nguyên nhân MES chặn', khPhuongPhap: '5 Why', khNguonLuc: 'QA + SX', actor: 'qamgp',
});
assert('UAT-CAPA-03', 'to-investigating có kế hoạch → Investigating', ok(r32) && data(r32).trangThai === 'Investigating',
  ok(r32) ? data(r32).trangThai : errMsg(r32));

const r33 = await call('POST', `/capa/${capaId}/to-action-plan`, { actor: 'qamgp' });
assert('UAT-CAPA-04', 'NC Nặng: to-action-plan thiếu RCA/chi phí → bị chặn', !ok(r33), `${errCode(r33)} ${errMsg(r33)}`);

const r34 = await call('POST', `/capa/${capaId}/to-action-plan`, {
  rcaNoiDung: 'Sai tham số MES', rcaMethod: 'FIVE_WHY', nhomNguyenNhan6m: 'METHOD', chiPhiDauTu: 1000000, actor: 'qamgp',
});
assert('UAT-CAPA-05', 'to-action-plan đủ RCA → ActionPlan', ok(r34) && data(r34).trangThai === 'ActionPlan',
  ok(r34) ? data(r34).trangThai : errMsg(r34));

const r35 = await call('POST', `/capa/${capaId}/to-processing`, { khacPhucTucThoi: 'a', nganTaiDien: 'b', actor: 'qamgp' });
assert('UAT-CAPA-06', 'to-processing khi chưa có action → bị chặn', !ok(r35), `${errCode(r35)} ${errMsg(r35)}`);

const dl = new Date(Date.now() + 7 * 864e5).toISOString().slice(0, 10);
const r36 = await call('POST', `/capa/${capaId}/actions`, {
  loai: 'TUC_THOI', moTa: 'Sửa tham số MES', nguoiPhuTrach: 'sxmgp', deadline: dl,
  loaiHanhDong: 'CORRECTIVE', actor: 'qamgp',
});
const actionId = ok(r36) ? data(r36).id : null;
assert('UAT-CAPA-07', 'Thêm action corrective → OK', ok(r36), ok(r36) ? `action#${actionId}` : errMsg(r36));

const r37 = await call('POST', `/capa/${capaId}/to-processing`, { khacPhucTucThoi: 'a', actor: 'qamgp' });
assert('UAT-CAPA-08', 'NC Nặng: to-processing thiếu ô ngăn tái diễn → bị chặn', !ok(r37), `${errCode(r37)} ${errMsg(r37)}`);

const r38 = await call('POST', `/capa/${capaId}/actions`, {
  loai: 'NGAN_TAI_DIEN', moTa: 'Thêm kiểm tra MES', nguoiPhuTrach: 'qamgp', deadline: dl,
  loaiHanhDong: 'PREVENTIVE', actor: 'qamgp',
});
const action2 = ok(r38) ? data(r38).id : null;
assert('UAT-CAPA-09', 'Thêm action preventive (đủ 2 ô) → OK', ok(r38), ok(r38) ? `action#${action2}` : errMsg(r38));

const r39 = await call('POST', `/capa/${capaId}/to-processing`, { khacPhucTucThoi: 'Đã sửa', nganTaiDien: 'Đã thêm check', actor: 'qamgp' });
assert('UAT-CAPA-10', 'to-processing đủ điều kiện → Processing', ok(r39) && data(r39).trangThai === 'Processing',
  ok(r39) ? data(r39).trangThai : errMsg(r39));

const r40 = await call('POST', `/capa/${capaId}/to-monitoring`, { xNgay: 30, nLo: 2, actor: 'qamgp' });
assert('UAT-CAPA-11', 'to-monitoring khi action chưa Completed → bị chặn', !ok(r40), `${errCode(r40)} ${errMsg(r40)}`);

await call('POST', `/capa/actions/${actionId}/complete`, { bangChung: 'ảnh sửa MES', actor: 'sxmgp' });
await call('POST', `/capa/actions/${action2}/complete`, { bangChung: 'biên bản đào tạo', actor: 'qamgp' });
const r41 = await call('POST', `/capa/${capaId}/to-monitoring`, { xNgay: 30, nLo: 2, actor: 'qamgp' });
assert('UAT-CAPA-12', 'Đủ action Completed → Monitoring', ok(r41) && data(r41).trangThai === 'Monitoring',
  ok(r41) ? data(r41).trangThai : errMsg(r41));

const r42 = await call('GET', `/capa/${capaId}/verification-status`);
assert('UAT-CAPA-13', 'verification-status trả mốc X/N (chưa đạt)', ok(r42),
  ok(r42) ? JSON.stringify(data(r42)) : errMsg(r42));

const r43 = await call('POST', `/capa/${capaId}/confirm-effective`, { actor: 'qatd' });
assert('UAT-CAPA-14', 'confirm-effective khi chưa đủ mốc → bị chặn', !ok(r43), `${errCode(r43)} ${errMsg(r43)}`);

// =====================================================================
// G. Auto-aggregation
// =====================================================================
group('G. Auto-aggregation OOS / Ticket');

const loG = lot('G45');
const r45 = await call('POST', '/ingest/oos', { loKiem: loG, maLoiId: 'FG-04', maHang: 'SP001', inOutSpec: 2, giaTriDo: '>10', nhaMay: 'MGP' });
const agg1 = ok(r45) ? data(r45) : null;
assert('UAT-AGG-01', 'OOS lần 1 → tạo NC mới (CREATED)', agg1 && (agg1.action === 'CREATED' || agg1.soHieu),
  ok(r45) ? JSON.stringify(agg1) : errMsg(r45));

const r46 = await call('POST', '/ingest/oos', { loKiem: loG, maLoiId: 'FG-04', maHang: 'SP001', inOutSpec: 2, giaTriDo: '>12', nhaMay: 'MGP' });
const agg2 = ok(r46) ? data(r46) : null;
assert('UAT-AGG-02', 'OOS lần 2 cùng lô → gom vào NC sẵn có (APPENDED)',
  agg2 && String(agg2.action || '').toUpperCase().includes('APPEND') && String(agg2.soHieu) === String(agg1?.soHieu),
  ok(r46) ? JSON.stringify(agg2) : errMsg(r46));

const r47 = await call('POST', '/ingest/ticket', {
  ticketId: `TK-UAT-${RUN}`, ticketCategory: 'Food_Safety', productCode: 'SP001',
  lotNumber: lot('G47'), defectDescription: 'Dị vật', nhaMay: 'MGP',
});
const agg3 = ok(r47) ? data(r47) : null;
const ticketNc = agg3 && agg3.soHieu ? (await call('GET', `/nc/${agg3.soHieu}`)) : null;
assert('UAT-AGG-03', 'Ticket Food_Safety → NC Nghiêm trọng + foodSafety',
  ticketNc && ok(ticketNc) && data(ticketNc).mucNghiemTrong === 'NGHIEM_TRONG' && data(ticketNc).foodSafety === true,
  ticketNc ? JSON.stringify({ so: data(ticketNc)?.soHieu, muc: data(ticketNc)?.mucNghiemTrong, fs: data(ticketNc)?.foodSafety }) : errMsg(r47));

// =====================================================================
// H. File đính kèm
// =====================================================================
group('H. Attachment');

let attOk = false, attDetail = '';
try {
  const fd = new FormData();
  fd.append('doiTuong', 'NC');
  fd.append('doiTuongId', so05);
  fd.append('actor', 'qamgp');
  fd.append('file', new Blob([`UAT evidence ${RUN}\n`], { type: 'text/plain' }), `uat-${RUN}.txt`);
  const res = await fetch(BASE + '/attachment', { method: 'POST', body: fd });
  const j = await res.json();
  const listRes = await fetch(`${BASE}/attachment?doiTuong=NC&doiTuongId=${encodeURIComponent(so05)}`);
  const list = await listRes.json();
  const found = (list.data || []).find((a) => a.tenFile === `uat-${RUN}.txt`);
  const dl = found ? await fetch(`${BASE}/attachment/${found.id}/download`) : null;
  attOk = j.success === true && !!found && dl && dl.status === 200;
  attDetail = `upload=${j.success} list=${(list.data || []).length} download=${dl ? dl.status : 'n/a'}`;
} catch (e) {
  attDetail = String(e);
}
assert('UAT-ATT-01', 'Upload → danh sách → download file đính kèm', attOk, attDetail);

// =====================================================================
// I. Versioning bản ghi đã xác nhận (I7) — tính năng mới
// =====================================================================
group('I. Versioning (I7)');

const r50 = await call('POST', `/capa/${capaId}/verification/sua-ket-qua`, {
  ketQua: 'HIEU_QUA', lyDo: 'UAT sửa kết quả', actor: 'qatd',
});
assert('UAT-VER-01', 'API sửa kết quả kiểm chứng đã xác nhận (I7) tồn tại', r50.status !== 404,
  r50.status === 404 ? 'HTTP 404 — chưa expose endpoint (service có, controller không)' : `${r50.status} ${errMsg(r50)}`);

// =====================================================================
// J. Dashboard / báo cáo
// =====================================================================
group('J. Dashboard');

const r60 = await call('GET', '/report/dashboard');
assert('UAT-RPT-01', 'Dashboard API trả dữ liệu tổng hợp', ok(r60), ok(r60) ? Object.keys(data(r60)).join(',') : errMsg(r60));

// =====================================================================
// Tổng kết
// =====================================================================
const pass = results.filter((r) => r.status === 'PASS').length;
const fail = results.length - pass;
console.log(`\n================ KẾT QUẢ: ${pass}/${results.length} PASS, ${fail} FAIL ================`);
if (fail) {
  console.log('\nCác case FAIL:');
  results.filter((r) => r.status === 'FAIL').forEach((r) => console.log(` - ${r.id} ${r.name} :: ${r.detail}`));
}

const __dirname = dirname(fileURLToPath(import.meta.url));
const outDir = resolve(__dirname, '../results');
mkdirSync(outDir, { recursive: true });
const outFile = resolve(outDir, `api-uat-${RUN}.json`);
writeFileSync(outFile, JSON.stringify({ run: RUN, base: BASE, pass, fail, total: results.length, results }, null, 2));
console.log(`\nJSON: ${outFile}`);

process.exit(fail > 0 ? 1 : 0);
