import { test, expect, Page } from '@playwright/test';

/**
 * Tiện ích dùng chung cho UAT E2E.
 */

export const RUN = new Date().toISOString().replace(/[-:TZ.]/g, '').slice(0, 14);

/** Context path của app trên máy chủ (nginx: /nc-capa/). */
export const ROOT = process.env.UAT_ROOT || '/nc-capa';

/** Trả lời tuần tự các hộp thoại prompt/confirm của app (null = dismiss). */
export function traLoiDialog(page: Page, answers: (string | null)[]) {
  const queue = [...answers];
  const seen: string[] = [];
  page.on('dialog', async (d) => {
    seen.push(`${d.type()}: ${d.message().slice(0, 60)}`);
    const a = queue.length ? queue.shift()! : null;
    if (a === null) await d.dismiss();
    else await d.accept(a);
  });
  return seen;
}

/** Tạo NC qua API (dùng request context của trang để đi qua đúng proxy). */
export async function taoNcQuaApi(page: Page, opts: { maLoiId: string; loKiem: string; maHang?: string }) {
  const res = await page.request.post(ROOT + '/api/v1/nc', {
    data: {
      loaiNc: 'THANH_PHAM',
      nguonPhatSinh: 'THU_CONG',
      nhaMay: 'MGP',
      nguoiBaoCao: 'bcmgp',
      loKiem: opts.loKiem,
      moTaLoi: 'UAT e2e',
      chiTieu: [{ maLoiId: opts.maLoiId, maHang: opts.maHang || 'SP001' }],
    },
  });
  expect(res.ok()).toBeTruthy();
  const body = await res.json();
  expect(body.success, JSON.stringify(body)).toBe(true);
  return body.data as { soHieu: string; mucNghiemTrong: string; trangThai: string };
}

/** Đổi tài khoản demo qua select trong header. */
export async function doiUser(page: Page, username: string) {
  const sel = page.locator('select[name="username"]').first();
  await sel.selectOption(username);
  await page.waitForLoadState('domcontentloaded');
}

export function thuLoiConsole(page: Page) {
  const errors: string[] = [];
  page.on('console', (m) => { if (m.type() === 'error') errors.push(m.text()); });
  page.on('pageerror', (e) => errors.push('pageerror: ' + e.message));
  return errors;
}
