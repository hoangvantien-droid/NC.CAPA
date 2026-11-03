import { test, expect } from '@playwright/test';
import { ROOT, RUN, traLoiDialog, taoNcQuaApi, doiUser } from './helpers';

const lot = (n: string) => `LOT-UATUI-${RUN}-${n}`;

test.describe('UAT-UI-10..17 — Vòng đời NC trên giao diện', () => {
  test('UAT-UI-10 Tạo NC từ form → phải mở trang chi tiết', async ({ page }) => {
    await page.goto(ROOT + '/nc/tao');
    await expect(page.locator('#f')).toBeVisible();

    const lo = lot('CREATE');
    await page.locator('#loKiem').fill(lo);
    await page.locator('#maLoiId').fill('FG-01');
    await page.locator('#maHang').fill('SP001');
    await page.locator('#moTaLoi').fill('UAT tạo từ giao diện');

    await page.locator('#f button[type="submit"]').click();
    await page.waitForURL((u) => u.pathname !== '/nc-capa/nc/tao', { timeout: 15_000 });
    const urlSauKhiTao = page.url();

    // 1) Bản ghi phải được tạo thật (kiểm qua danh sách, cột "Lô kiểm").
    await page.goto(ROOT + '/', { waitUntil: 'domcontentloaded' });
    await expect(page.locator('table tbody tr', { hasText: lo })).toHaveCount(1);

    // 2) Kỳ vọng nghiệp vụ: điều hướng tới trang chi tiết trong app.
    expect(urlSauKhiTao, 'sau khi tạo, phải mở trang chi tiết trong app (không phải 404)')
      .toMatch(new RegExp(ROOT + '/nc/KPH-'));
  });

  test('UAT-UI-11 Tạo NC xong thì phiếu tồn tại và mở được bằng URL đúng', async ({ page }) => {
    await page.goto(ROOT + '/nc/tao');
    const lo = lot('PERSIST');
    await page.locator('#loKiem').fill(lo);
    await page.locator('#maLoiId').fill('FG-01');
    await page.locator('#maHang').fill('SP001');
    await page.locator('#f button[type="submit"]').click();
    await page.waitForURL((u) => !u.pathname.endsWith('/nc/tao'), { timeout: 15_000 });

    // Dù điều hướng sai, bản ghi vẫn phải được tạo: kiểm qua danh sách.
    await page.goto(ROOT + '/', { waitUntil: 'domcontentloaded' });
    await expect(page.locator('table tbody tr td.id a').first()).toBeVisible();

    // Truy cập trực tiếp bằng đường dẫn có context path.
    const link = page.locator('table tbody tr td.id a').first();
    const soHieu = (await link.textContent())!.trim();
    const res = await page.goto(`${ROOT}/nc/${soHieu}`);
    expect(res?.status()).toBe(200);
    await expect(page.locator('.crumb .id')).toHaveText(soHieu);
    await expect(page.locator('text=' + lo)).toBeVisible();
  });

  test('UAT-UI-12 Trang chi tiết hiển thị thông tin, chỉ tiêu và nút hành động đúng trạng thái', async ({ page }) => {
    const nc = await taoNcQuaApi(page, { maLoiId: 'FG-01', loKiem: lot('DETAIL') });
    const res = await page.goto(`${ROOT}/nc/${nc.soHieu}`);
    expect(res?.status()).toBe(200);

    await expect(page.locator('.crumb .id')).toHaveText(nc.soHieu);
    await expect(page.locator('.panel', { hasText: 'Thông tin phiếu' })).toContainText('FG-01');
    await expect(page.locator('.panel', { hasText: 'Chỉ tiêu vi phạm' })).toContainText('FG-01');

    // NC Nhẹ ở New: phải có nút Đóng nhanh, không có nút Khoanh vùng
    await expect(page.locator('button[data-action="close-fast"]')).toBeVisible();
    await expect(page.locator('button[data-action="containment"]')).toHaveCount(0);
  });

  test('UAT-UI-13 Đóng nhanh NC Nhẹ qua UI → trạng thái Closed', async ({ page }) => {
    const nc = await taoNcQuaApi(page, { maLoiId: 'FG-01', loKiem: lot('CLOSEFAST') });
    traLoiDialog(page, ['qamgp', 'Đóng nhanh UAT']);
    await page.goto(`${ROOT}/nc/${nc.soHieu}`);

    await page.locator('button[data-action="close-fast"]').click();
    await page.waitForLoadState('networkidle');

    await expect(page.locator('.panel', { hasText: 'Thông tin phiếu' }).locator('.pill.st')).toHaveText('Closed');
    await expect(page.locator('button[data-action="close-fast"]')).toHaveCount(0);
    await expect(page.locator('text=Phiếu đã ở trạng thái cuối.')).toBeVisible();
  });

  test('UAT-UI-14 Trình duyệt NC Nặng qua UI (5 prompt) → PendingApproval', async ({ page }) => {
    const nc = await taoNcQuaApi(page, { maLoiId: 'PRC-02', loKiem: lot('SUBMIT') });
    traLoiDialog(page, ['sxmgp', 'Lỗi cấu hình MES', 'REWORK', '250000', 'SX']);
    await page.goto(`${ROOT}/nc/${nc.soHieu}`);

    await page.locator('button[data-action="submit-approval"]').click();
    await page.waitForLoadState('networkidle');

    await expect(page.locator('.panel', { hasText: 'Thông tin phiếu' }).locator('.pill.st')).toHaveText('PendingApproval');
    await expect(page.locator('button[data-action="approve"]')).toBeVisible();
  });

  test('UAT-UI-15 Guard hiển thị lý do khi thao tác không hợp lệ', async ({ page }) => {
    const nc = await taoNcQuaApi(page, { maLoiId: 'PRC-02', loKiem: lot('GUARD') });
    traLoiDialog(page, ['qamgp', 'thử đóng nhanh NC Nặng']);
    await page.goto(`${ROOT}/nc/${nc.soHieu}`);

    // NC Nặng không có nút close-fast; gọi thẳng API để chắc guard trả lỗi rõ ràng.
    const res = await page.request.post(`${ROOT}/api/v1/nc/${nc.soHieu}/close-fast`, { data: { ketQuaXuLy: 'x', actor: 'qamgp' } });
    const body = await res.json();
    expect(body.success).toBe(false);
    expect(body.error.message).toMatch(/Chỉ NC Nhẹ/);
  });

  test('UAT-UI-16 Tải file đính kèm qua UI và thấy trong danh sách', async ({ page }) => {
    const nc = await taoNcQuaApi(page, { maLoiId: 'FG-01', loKiem: lot('ATT') });
    traLoiDialog(page, ['qamgp']);
    await page.goto(`${ROOT}/nc/${nc.soHieu}`);

    const tenFile = `uat-ui-${RUN}.txt`;
    await page.locator('#att-file').setInputFiles({
      name: tenFile, mimeType: 'text/plain', buffer: Buffer.from('UAT UI attachment\n'),
    });
    const [resp] = await Promise.all([
      page.waitForResponse((r) => r.url().includes('/api/v1/attachment') && r.request().method() === 'POST'),
      page.locator('#att-form button[type="submit"]').click(),
    ]);
    expect((await resp.json()).success).toBe(true);

    await expect(page.locator('#att-list')).toContainText(tenFile);
  });

  test('UAT-UI-17 Lọc theo nhà máy của tài khoản đang đăng nhập', async ({ page }) => {
    await page.goto(ROOT + '/', { waitUntil: 'domcontentloaded' });
    await doiUser(page, 'qamgp'); // QA nhà máy MGP
    await page.waitForLoadState('networkidle');
    await expect(page.locator('table tbody tr').first()).toBeVisible();
    const rows = page.locator('table tbody tr');
    const n = await rows.count();
    expect(n).toBeGreaterThan(0);
    for (let i = 0; i < n; i++) {
      await expect(rows.nth(i).locator('td').last()).toHaveText('MGP');
    }

    await doiUser(page, 'qatd'); // QA tập đoàn: thấy mọi nhà máy
    await expect(page.locator('table tbody tr').first()).toBeVisible();
  });
});
