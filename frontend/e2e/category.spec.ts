import { test, expect } from '@playwright/test';

async function mockCalendar(page: import('@playwright/test').Page) {
  await page.clock.setFixedTime(new Date('2026-10-02T03:00:00Z'));
  await page.route('**/api/**', async route => {
    const url = new URL(route.request().url());
    if (url.pathname === '/api/auth/me') return route.fulfill({ json: { ownerId: 1, username: 'owner' } });
    if (url.pathname === '/api/categories') return route.fulfill({ json: [
      { id: 1, name: '아침기록', color: '#9872ea', displayOrder: 1, isPrivate: false },
      { id: 2, name: '운동', color: '#df7ea6', displayOrder: 2, isPrivate: true },
    ] });
    if (url.pathname.endsWith('/annual')) return route.fulfill({ json: [{ month: 10, day: 1, status: 'RECORDED' }] });
    if (url.pathname.endsWith('/records')) return route.fulfill({ json: [{ id: 11, recordDate: `${url.searchParams.get('year')}-${url.searchParams.get('month')!.padStart(2,'0')}-01`, recordTime: '08:30:00', imageKey: null }] });
    return route.fulfill({ status: 404, json: {} });
  });
}

test('category calendar supports dates, month jumps, category switching and annual sheet', async ({ page }, testInfo) => {
  await mockCalendar(page);
  await page.goto('/#/categories/1');
  await expect(page.getByRole('heading', { name: '아침기록', exact: true })).toBeVisible();
  const recorded = page.getByRole('link', { name: '2026-10-01, 08:30 기록 보기', exact: true });
  await expect(recorded).toBeVisible();
  await expect(recorded).toHaveAttribute('href', '#/records/11');
  await expect(page.getByRole('link', { name: '2026-10-02, 기록 작성', exact: true })).toHaveAttribute('href', '#/categories/1/new?date=2026-10-02');
  await expect(page.getByRole('link', { name: /2026-10-03/ })).toHaveCount(0);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.screenshot({ path: testInfo.outputPath('category-calendar.png') });
  await page.getByRole('button', { name: '연간 보기', exact: true }).click();
  await expect(page.getByRole('dialog')).toBeVisible();
  await expect(page.locator('.heat-cell.recorded').first()).toBeVisible();
  expect(await page.locator('.annual-grid .heat-cell.absent').count()).toBe(7);
  await page.getByRole('button', { name: '이전 연도' }).click();
  await expect(page.getByRole('heading', { name: '2025년 연간 기록' })).toBeVisible();
  await expect(page.getByRole('button', { name: '이전 연도' })).toBeDisabled();
  await page.keyboard.press('Escape');
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await expect(page.getByRole('button', { name: '연간 보기', exact: true })).toBeFocused();
  await page.getByRole('button', { name: '월 선택⌄' }).click();
  await page.getByLabel('이동할 월').selectOption('2025-02');
  await page.getByRole('button', { name: '이동', exact: true }).click();
  await expect(page.getByRole('button', { name: '2025년 2월', exact: true })).toBeInViewport();
  await page.getByRole('button', { name: '오늘', exact: true }).click();
  await expect(page.locator('.full-day.is-today')).toBeInViewport();
  await page.getByRole('navigation', { name: '카테고리 전환' }).getByRole('link', { name: '운동', exact: true }).click();
  await expect(page.getByRole('heading', { name: '운동', exact: true })).toBeVisible();
  await expect(page.getByRole('link', { name: '운동', exact: true })).toHaveAttribute('aria-current', 'page');
});

test('month failure can retry and expired sessions return to login', async ({ page }) => {
  await mockCalendar(page);
  let attempts = 0;
  await page.route('**/api/categories/1/records?year=2026&month=10', route => {
    if (attempts++ === 0) return route.fulfill({ status: 500, json: {} });
    if (attempts === 2) return route.fulfill({ json: [] });
    return route.fulfill({ status: 401, json: {} });
  });
  await page.goto('/#/categories/1');
  await expect(page.locator('#month-2026-10').getByRole('alert')).toBeVisible();
  await page.locator('#month-2026-10').getByRole('button', { name: '다시 시도' }).click();
  await expect(page.getByRole('link', { name: '2026-10-01, 기록 작성', exact: true })).toBeVisible();
  await page.reload();
  await expect(page.getByText('세션이 만료됐어요. 다시 로그인해 주세요.')).toBeVisible();
});

test('photo records load their image and keep the time when the image fails', async ({ page }) => {
  await mockCalendar(page);
  await page.route('**/api/categories/1/records?year=2026&month=10', route => route.fulfill({ json: [{ id: 11, recordDate: '2026-10-01', recordTime: '08:30:00', imageKey: 'test-photo' }] }));
  await page.route('**/api/records/11/photo-url', route => route.fulfill({ json: { imageUrl: '/test-photo.svg', expiresAt: '2026-10-02T04:00:00Z' } }));
  await page.route('**/test-photo.svg', route => route.fulfill({ contentType: 'image/svg+xml', body: '<svg xmlns="http://www.w3.org/2000/svg" width="40" height="60"><rect width="40" height="60" fill="#9872ea"/></svg>' }));
  await page.goto('/#/categories/1');
  const cell = page.getByRole('link', { name: '2026-10-01, 08:30 기록 보기', exact: true });
  await expect(cell.locator('img')).toBeVisible();
  await expect.poll(() => cell.locator('img').evaluate((img: HTMLImageElement) => img.naturalWidth)).toBeGreaterThan(0);
  await cell.locator('img').evaluate(img => img.dispatchEvent(new Event('error')));
  await expect(cell.locator('img')).toHaveCount(0);
  await expect(cell.locator('.record-time')).toHaveText('08:30');
});
