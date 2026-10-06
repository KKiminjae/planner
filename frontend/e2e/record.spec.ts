import { test, expect, type Page } from '@playwright/test';

const category = { id: 1, name: '아침기록', color: '#9872ea', displayOrder: 1, isPrivate: false };
const imageKey = 'photos/00000000-0000-0000-0000-000000000001.png';
async function setup(page: Page, options: { failCreate?: boolean; expire?: boolean } = {}) {
  await page.clock.setFixedTime(new Date('2026-10-02T03:00:00Z'));
  let record = { id: 11, categoryId: 1, categoryName: '아침기록', recordDate: '2026-10-02', recordTime: '08:30:00', memo: '첫 기록', imageKey: null as string | null };
  const writes: { method: string; body: Record<string, unknown> }[] = [];
  let uploads = 0;
  let creates = 0;
  await page.route('**/api/**', async route => {
    const request = route.request();
    const url = new URL(request.url());
    const method = request.method();
    if (url.pathname === '/api/auth/me') return route.fulfill({ json: { ownerId: 1, username: 'owner' } });
    if (url.pathname === '/api/auth/csrf') return route.fulfill({ json: { headerName: 'X-CSRF-TOKEN', token: 'test-csrf' } });
    if (method !== 'GET') {
      expect(request.headers()['x-csrf-token']).toBe('test-csrf');
      if (options.expire) return route.fulfill({ status: 401, json: {} });
      if (url.pathname === '/api/photos') { uploads++; return route.fulfill({ status: 201, json: { imageKey } }); }
      const body = method === 'DELETE' ? {} : request.postDataJSON();
      writes.push({ method, body });
      if (method === 'POST') {
        creates++;
        if (options.failCreate && creates === 1) return route.fulfill({ status: 500, json: {} });
        record = { ...record, ...body };
      } else if (method === 'PATCH') {
        record = { ...record, memo: body.memo, recordTime: body.recordTime, imageKey: body.removeImage ? null : body.imageKey || record.imageKey };
      } else if (method === 'DELETE') return route.fulfill({ status: 204 });
      return route.fulfill({ status: method === 'POST' ? 201 : 200, json: record });
    }
    if (url.pathname === '/api/categories') return route.fulfill({ json: [category] });
    if (url.pathname.endsWith('/records')) return route.fulfill({ json: [] });
    if (url.pathname.endsWith('/photo-url')) return route.fulfill({ json: { imageUrl: '/record-photo.png', expiresAt: '2026-10-02T04:00:00Z' } });
    if (url.pathname === '/api/records/11') return route.fulfill({ json: record });
    return route.fulfill({ status: 404, json: {} });
  });
  await page.route('**/record-photo.png', route => route.fulfill({ contentType: 'image/png', body: Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+j8ioAAAAASUVORK5CYII=', 'base64') }));
  return { writes, uploads: () => uploads };
}

test('create without a photo, view, edit, cancel deletion and delete with CSRF', async ({ page }, testInfo) => {
  const state = await setup(page);
  await page.goto('/#/categories/1/new?date=2026-10-02');
  await expect(page.getByRole('heading', { name: '기록 작성' })).toBeVisible();
  await page.getByRole('button', { name: '저장', exact: true }).click();
  expect(state.writes).toHaveLength(0);
  await page.getByLabel('기록 시간', { exact: true }).fill('08:30');
  await page.getByLabel('메모', { exact: true }).fill('오늘 아침 기록\n사진 없이 남기기');
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.screenshot({ path: testInfo.outputPath('record-form.png'), fullPage: true });
  await page.getByRole('button', { name: '저장', exact: true }).click();
  await expect(page.getByRole('heading', { name: '상세 기록' })).toBeVisible();
  await expect(page.locator('.detail-memo')).toContainText('오늘 아침 기록');
  await expect(page.locator('.detail-photo')).toHaveCount(0);
  expect(state.writes[0].body).toMatchObject({ categoryId: 1, recordDate: '2026-10-02', recordTime: '08:30', imageKey: null });
  await page.getByRole('button', { name: '기록 메뉴' }).click();
  await page.getByRole('link', { name: '수정', exact: true }).click();
  await expect(page.getByLabel('메모', { exact: true })).toHaveValue('오늘 아침 기록\n사진 없이 남기기');
  await page.getByLabel('메모', { exact: true }).fill('수정한 기록');
  await page.getByRole('button', { name: '저장', exact: true }).click();
  await expect(page.locator('.detail-memo')).toContainText('수정한 기록');
  await page.getByRole('button', { name: '기록 메뉴' }).click();
  await page.getByRole('button', { name: '삭제', exact: true }).click();
  await page.getByRole('button', { name: '취소', exact: true }).click();
  expect(state.writes.filter(write => write.method === 'DELETE')).toHaveLength(0);
  await page.getByRole('button', { name: '기록 메뉴' }).click();
  await page.getByRole('button', { name: '삭제', exact: true }).click();
  await page.getByRole('dialog').getByRole('button', { name: '삭제', exact: true }).click();
  await expect(page.getByRole('heading', { name: '아침기록', exact: true })).toBeVisible();
  expect(state.writes.filter(write => write.method === 'DELETE')).toHaveLength(1);
});

test('upload once across save retry, preserve a photo on memo edit and remove it explicitly', async ({ page }) => {
  const state = await setup(page, { failCreate: true });
  await page.goto('/#/categories/1/new?date=2026-10-02');
  await page.getByLabel('메모', { exact: true }).fill('사진 기록');
  await page.getByLabel('기록 사진 선택', { exact: true }).setInputFiles({ name: 'photo.heic', mimeType: 'image/heic', buffer: Buffer.from('unsupported') });
  await expect(page.getByRole('alert')).toContainText('JPEG 또는 PNG');
  await page.getByLabel('기록 사진 선택', { exact: true }).setInputFiles({ name: 'photo.png', mimeType: 'image/png', buffer: Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+j8ioAAAAASUVORK5CYII=', 'base64') });
  await expect(page.getByRole('img', { name: '선택한 기록 사진' })).toBeVisible();
  await page.getByRole('button', { name: '저장', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('저장하지 못했어요');
  await expect(page.getByLabel('메모', { exact: true })).toHaveValue('사진 기록');
  await page.getByRole('button', { name: '저장', exact: true }).click();
  await expect(page.getByRole('heading', { name: '상세 기록' })).toBeVisible();
  await expect(page.getByRole('img', { name: '아침기록 기록 사진' })).toBeVisible();
  expect(state.uploads()).toBe(1);
  expect(state.writes[1].body.imageKey).toBe(imageKey);
  await page.getByRole('button', { name: '기록 메뉴' }).click();
  await page.getByRole('link', { name: '수정', exact: true }).click();
  await page.getByLabel('메모', { exact: true }).fill('사진 유지');
  await page.getByRole('button', { name: '저장', exact: true }).click();
  await expect(page.getByRole('heading', { name: '상세 기록' })).toBeVisible();
  expect(state.writes[2].body).toMatchObject({ imageKey: null, removeImage: false });
  await expect(page.locator('.detail-photo')).toBeVisible();
  await page.getByRole('button', { name: '기록 메뉴' }).click();
  await page.getByRole('link', { name: '수정', exact: true }).click();
  await page.getByRole('button', { name: '사진 제거', exact: true }).click();
  await page.getByRole('button', { name: '저장', exact: true }).click();
  await expect(page.getByRole('heading', { name: '상세 기록' })).toBeVisible();
  await expect(page.locator('.detail-photo')).toHaveCount(0);
  expect(state.writes[3].body).toMatchObject({ imageKey: null, removeImage: true });
});

test('session expiry while saving returns to login', async ({ page }) => {
  await setup(page, { expire: true });
  await page.goto('/#/categories/1/new?date=2026-10-02');
  await page.getByLabel('메모', { exact: true }).fill('기록');
  await page.getByRole('button', { name: '저장', exact: true }).click();
  await expect(page.getByText('세션이 만료됐어요. 다시 로그인해 주세요.')).toBeVisible();
});

test('create and edit a record with an empty memo', async ({ page }) => {
  const state = await setup(page);
  await page.goto('/#/categories/1/new?date=2026-10-02');
  await page.getByLabel('기록 시간', { exact: true }).fill('08:30');
  await page.getByRole('button', { name: '저장', exact: true }).click();
  await expect(page.getByRole('heading', { name: '상세 기록' })).toBeVisible();
  expect(state.writes[0].body.memo).toBe('');
  await page.getByRole('button', { name: '기록 메뉴' }).click();
  await page.getByRole('link', { name: '수정', exact: true }).click();
  await expect(page.getByLabel('메모', { exact: true })).toHaveValue('');
  await page.getByRole('button', { name: '저장', exact: true }).click();
  await expect(page.getByRole('heading', { name: '상세 기록' })).toBeVisible();
  expect(state.writes.at(-1)?.body.memo).toBe('');
});

test('create a record for a past date', async ({ page }) => {
  const state = await setup(page);
  await page.goto('/#/categories/1/new?date=2025-02-01');
  await expect(page.getByRole('heading', { name: '기록 작성' })).toBeVisible();
  await page.getByLabel('기록 시간', { exact: true }).fill('12:00');
  await page.getByRole('button', { name: '저장', exact: true }).click();
  await expect(page.getByRole('heading', { name: '상세 기록' })).toBeVisible();
  expect(state.writes[0].body.recordDate).toBe('2025-02-01');
});

test('photo capture time fills the clock, remains editable and missing metadata preserves it', async ({ page }) => {
  const state = await setup(page);
  await page.goto('/#/categories/1/new?date=2026-10-02');
  const clock = page.getByLabel('기록 시간', { exact: true });
  await expect(clock).toHaveValue('12:00');
  const jpeg = await page.evaluate(() => {
    const canvas = document.createElement('canvas'); canvas.width = 2; canvas.height = 2;
    return canvas.toDataURL('image/jpeg').split(',')[1];
  });
  const tiff = Buffer.alloc(64);
  tiff.set([0x49,0x49,42,0,8,0,0,0]);
  tiff.writeUInt16LE(1,8); tiff.writeUInt16LE(0x8769,10); tiff.writeUInt16LE(4,12);
  tiff.writeUInt32LE(1,14); tiff.writeUInt32LE(26,18);
  tiff.writeUInt16LE(1,26); tiff.writeUInt16LE(0x9003,28); tiff.writeUInt16LE(2,30);
  tiff.writeUInt32LE(20,32); tiff.writeUInt32LE(44,36);
  tiff.write('2026:10:01 23:47:12',44);
  const original = Buffer.from(jpeg,'base64');
  const photo = Buffer.concat([original.subarray(0,2), Buffer.from([0xff,0xe1,0,72,69,120,105,102,0,0]),tiff,original.subarray(2)]);
  await page.getByLabel('기록 사진 선택', { exact:true }).setInputFiles({name:'capture.jpg',mimeType:'image/jpeg',buffer:photo});
  await expect(clock).toHaveValue('23:47');
  await expect(page.locator('.record-date')).toHaveText('2026.10.02');
  await clock.fill('09:15');
  await page.getByLabel('기록 사진 선택', { exact:true }).setInputFiles({name:'no-exif.jpg',mimeType:'image/jpeg',buffer:original});
  await expect(page.getByRole('status')).toHaveCount(0);
  await expect(clock).toHaveValue('09:15');
  await page.getByRole('button',{name:'저장',exact:true}).click();
  await expect(page.getByRole('heading',{name:'상세 기록'})).toBeVisible();
  expect(state.writes[0].body).toMatchObject({recordDate:'2026-10-02',recordTime:'09:15'});
  await page.goto('/#/records/11/edit');
  await expect(clock).toHaveValue('09:15');
  await page.getByLabel('기록 사진 선택', { exact:true }).setInputFiles({name:'replacement.jpg',mimeType:'image/jpeg',buffer:photo});
  await expect(clock).toHaveValue('23:47');
});
