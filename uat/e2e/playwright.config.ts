import { defineConfig, devices } from '@playwright/test';

/**
 * UAT E2E cho NC/CAPA.
 * Mặc định chạy trên môi trường PUBLIC (nginx rewrite /api/v1 → /nc-capa/api/v1).
 * Đổi bằng biến môi trường: UAT_BASE_URL=https://.../nc-capa
 */
const baseURL = process.env.UAT_ORIGIN || 'https://tienhv.io.vn';

export default defineConfig({
  testDir: './tests',
  timeout: 60_000,
  expect: { timeout: 10_000 },
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [
    ['list'],
    ['json', { outputFile: '../results/e2e-latest.json' }],
    ['html', { outputFolder: '../results/e2e-html', open: 'never' }],
  ],
  use: {
    baseURL,
    locale: 'vi-VN',
    timezoneId: 'Asia/Ho_Chi_Minh',
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure',
    video: 'off',
    ignoreHTTPSErrors: true,
  },
  projects: [
    { name: 'desktop', use: { ...devices['Desktop Chrome'], viewport: { width: 1440, height: 900 } } },
    { name: 'mobile', use: { ...devices['Pixel 7'] }, testMatch: /.*responsive\.spec\.ts/ },
  ],
});
