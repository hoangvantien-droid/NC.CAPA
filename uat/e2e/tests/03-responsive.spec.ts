import { test, expect } from '@playwright/test';
import { ROOT, RUN, taoNcQuaApi } from './helpers';

test.describe('UAT-UI-20..23 — Hiển thị trên thiết bị di động', () => {
  test('UAT-UI-20 Danh sách NC dùng được trên mobile', async ({ page }) => {
    await page.goto(ROOT + '/');
    await expect(page.locator('.brand')).toBeVisible();
    await expect(page.locator('table')).toBeVisible();
    // Nút tạo NC vẫn bấm được (không bị header che)
    const btn = page.locator('a.btn-primary', { hasText: 'Tạo NC' });
    await expect(btn).toBeVisible();
  });

  test('UAT-UI-21 Trang chi tiết NC không tràn ngang', async ({ page }) => {
    const nc = await taoNcQuaApi(page, { maLoiId: 'FG-01', loKiem: `LOT-UATUI-${RUN}-MOBILE` });
    await page.goto(`${ROOT}/nc/${nc.soHieu}`);
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
    expect(overflow, 'trang chi tiết bị tràn ngang trên mobile').toBeLessThanOrEqual(2);
  });

  test('UAT-UI-22 Form tạo NC nhập được trên mobile', async ({ page }) => {
    await page.goto(ROOT + '/nc/tao');
    await page.locator('#loKiem').fill(`LOT-UATUI-${RUN}-MOBILE2`);
    await page.locator('#maLoiId').fill('FG-01');
    await expect(page.locator('#f button[type="submit"]')).toBeVisible();
  });
});
