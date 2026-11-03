import { test, expect } from '@playwright/test';
import { ROOT, thuLoiConsole } from './helpers';

test.describe('UAT-UI-01..05 — Smoke các màn hình', () => {
  test('UAT-UI-01 Trang danh sách NC hiển thị đúng khung', async ({ page }) => {
    const errors = thuLoiConsole(page);
    await page.goto(ROOT + '/');

    await expect(page).toHaveTitle(/NC\/CAPA/);
    await expect(page.locator('.brand')).toContainText('NC/CAPA');
    await expect(page.locator('nav a', { hasText: 'Danh sách' })).toBeVisible();
    await expect(page.locator('nav a', { hasText: 'Dashboard' })).toBeVisible();

    // 3 chỉ số đầu trang
    const stats = page.locator('.statusbar .stat');
    await expect(stats).toHaveCount(3);
    for (const s of await stats.all()) {
      await expect(s.locator('.num')).toHaveText(/^\d+$/);
    }

    // bảng danh sách có header đúng
    const headers = page.locator('table thead th');
    await expect(headers).toHaveCount(7);
    await expect(headers.nth(0)).toHaveText('Số hiệu');
    await expect(headers.nth(1)).toHaveText('Mức');
    await expect(headers.nth(2)).toHaveText('Trạng thái');

    expect(errors, 'lỗi console khi tải trang danh sách').toEqual([]);
  });

  test('UAT-UI-02 Danh sách có dữ liệu và mỗi dòng có link chi tiết', async ({ page }) => {
    await page.goto(ROOT + '/');
    const rows = page.locator('table tbody tr');
    const count = await rows.count();
    expect(count).toBeGreaterThan(0);

    const firstId = (await rows.first().locator('td.id a').textContent())?.trim() || '';
    expect(firstId).toMatch(/^KPH-\d{4}-[A-Z]+-\d{5}$/);

    await rows.first().locator('td.id a').click();
    await page.waitForLoadState('domcontentloaded');
    await expect(page.locator('.crumb .id')).toHaveText(firstId);
  });

  test('UAT-UI-03 Dashboard hiển thị số liệu tổng hợp', async ({ page }) => {
    const errors = thuLoiConsole(page);
    await page.goto(ROOT + '/dashboard');
    await expect(page.locator('body')).toContainText(/Dashboard|Tổng quan/i);
    await expect(page.locator('.stat, .card, .panel').first()).toBeVisible();
    expect(errors).toEqual([]);
  });

  test('UAT-UI-04 Trang PR và Ticket tải được', async ({ page }) => {
    await page.goto(ROOT + '/pr');
    await expect(page.locator('table thead')).toBeVisible();

    await page.goto(ROOT + '/ticket');
    await expect(page.locator('table thead')).toBeVisible();
  });

  test('UAT-UI-05 Chuyển tài khoản demo thay đổi phiên làm việc', async ({ page }) => {
    await page.goto(ROOT + '/');
    const sel = page.locator('select[name="username"]').first();
    await expect(sel).toBeVisible();
    await expect(sel.locator('option')).not.toHaveCount(0);

    const before = await sel.inputValue();
    const options = await sel.locator('option').evaluateAll((os) => os.map((o: any) => o.value));
    const target = options.find((o) => o !== before)!;
    expect(target).toBeTruthy();

    await sel.selectOption(target);
    await page.waitForLoadState('networkidle');
    await expect(page.locator('select[name="username"]').first()).toHaveValue(target);
  });
});
