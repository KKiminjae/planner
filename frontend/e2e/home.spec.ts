import { test, expect } from '@playwright/test';

test('main screen fits viewport and supports navigation and keyboard focus', async ({ page }, testInfo) => {
  await page.route('**/api/**', async route => {
    const url = new URL(route.request().url());
    let body: unknown = { ownerId: 1, username: 'owner' };
    if (url.pathname === '/api/calendar/integrated') body = [];
    else if (url.pathname === '/api/categories') body = [
      { id: 1, name: '아침기록', color: '#9872ea', displayOrder: 1, isPrivate: false },
      { id: 2, name: '점심기록', color: '#81afe4', displayOrder: 2, isPrivate: false },
      { id: 3, name: '저녁기록', color: '#bf9a6d', displayOrder: 3, isPrivate: false },
      { id: 4, name: '운동', color: '#df7ea6', displayOrder: 4, isPrivate: true },
    ];
    else if (url.pathname.endsWith('/records')) body = [1, 2, 4, 6, 8, 10, 14, 16, 18, 21, 24, 26, 29].map(day => ({ id: day, recordDate: `${url.searchParams.get('year')}-${url.searchParams.get('month')!.padStart(2, '0')}-${String(day).padStart(2, '0')}`, recordTime: '08:30', imageKey: null }));
    await route.fulfill({ json: body });
  });
  await page.goto('/');
  await expect(page.getByRole('link', { name: /아침기록.*13일 기록/ })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  const cards = page.locator('.category-card');
  const first = await cards.nth(0).boundingBox();
  const second = await cards.nth(1).boundingBox();
  expect(first!.y).toBe(second!.y);
  expect(second!.x).toBeGreaterThan(first!.x);
  await page.evaluate(() => document.fonts.ready);
  expect(await page.evaluate(() => document.fonts.check('600 22px Pretendard', '나의 기록'))).toBe(true);
  const title = page.getByRole('heading', { name: '나의 기록' });
  await expect(title).toHaveCSS('font-size', '22px');
  await expect(title).toHaveCSS('font-weight', '600');
  await expect(title).toHaveCSS('letter-spacing', '-0.6px');
  await page.screenshot({ path: testInfo.outputPath('home.png'), fullPage: true });
  await page.getByRole('link', { name: '카테고리 관리' }).focus();
  await expect(page.getByRole('link', { name: '카테고리 관리' })).toBeFocused();
  await page.keyboard.press('Enter');
  await expect(page.getByRole('heading', { name: '카테고리 관리' })).toBeFocused();
  await page.getByRole('link', { name: '나의 기록으로 돌아가기' }).last().click();
  await expect(page.getByRole('heading', { name: '나의 기록' })).toBeVisible();
  await page.getByRole('link', { name: '모아보기' }).click();
  await expect(page.getByRole('heading', { name: '통합 캘린더' })).toBeVisible();
  await page.goBack();
  await expect(page.getByRole('heading', { name: '나의 기록' })).toBeVisible();
});

test('login fits the screen with readable inputs and touch targets', async ({ page }) => {
  await page.route('**/api/**', route => route.fulfill({ status: 401, json: { code: 'AUTHENTICATION_REQUIRED' } }));
  await page.goto('/');
  await expect(page.getByRole('heading', { name: '로그인' })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  for (const id of ['username', 'password']) {
    const input = page.locator(`#${id}`);
    expect(await input.evaluate(element => parseFloat(getComputedStyle(element).fontSize))).toBeGreaterThanOrEqual(16);
    expect((await input.boundingBox())!.height).toBeGreaterThanOrEqual(44);
  }
  const submit = page.getByRole('button', { name: '내 기록 보러 가기' });
  await submit.scrollIntoViewIfNeeded();
  await expect(submit).toBeInViewport();
  expect((await submit.boundingBox())!.height).toBeGreaterThanOrEqual(44);
});
