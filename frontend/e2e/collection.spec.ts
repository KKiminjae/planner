import { test, expect, type Page } from '@playwright/test';
const categories = [
  {id:1,name:'아침기록',color:'#9872ea',displayOrder:1,isPrivate:false},
  {id:2,name:'점심기록',color:'#81afe4',displayOrder:2,isPrivate:false},
  {id:3,name:'산책',color:'#89aa7b',displayOrder:3,isPrivate:false},
  {id:4,name:'저녁기록',color:'#bf9a6d',displayOrder:4,isPrivate:false},
  {id:5,name:'운동',color:'#df7ea6',displayOrder:5,isPrivate:true},
  {id:6,name:'독서',color:'#D1BDF0',displayOrder:6,isPrivate:false},
  {id:7,name:'물 마시기',color:'#BDE5D5',displayOrder:7,isPrivate:false},
];
const records = [
  {id:15,categoryId:5,categoryName:'운동',recordDate:'2026-10-02',recordTime:'20:10:00',memo:'운동한 하루',imageKey:null},
  {id:11,categoryId:1,categoryName:'아침기록',recordDate:'2026-10-02',recordTime:'08:30:00',memo:'아침 메모\n두 번째 줄',imageKey:'photo-1'},
  {id:13,categoryId:3,categoryName:'산책',recordDate:'2026-10-02',recordTime:'18:00:00',memo:'노을 보며 산책',imageKey:null},
  {id:12,categoryId:2,categoryName:'점심기록',recordDate:'2026-10-02',recordTime:'12:10:00',memo:'점심 메모',imageKey:'photo-2'},
  {id:14,categoryId:4,categoryName:'저녁기록',recordDate:'2026-10-02',recordTime:'19:00:00',memo:'저녁 메모',imageKey:null},
];
async function setup(page: Page) {
  const addedRecords: typeof records = [];
  const dailyCategories = [...categories.map(item => ({...item,isDeleted:false})), {id:8,name:'명상',color:'#DADDE5',displayOrder:8,isPrivate:false,isDeleted:true}];
  await page.clock.setFixedTime(new Date('2026-10-02T03:00:00Z'));
  await page.route('**/api/**', async route => {
    const url = new URL(route.request().url());
    if (url.pathname.endsWith('/me')) return route.fulfill({json:{ownerId:1,username:'owner'}});
    if (url.pathname === '/api/auth/csrf') return route.fulfill({json:{headerName:'X-CSRF-TOKEN',token:'test-csrf'}});
    if (url.pathname === '/api/categories/daily') return route.fulfill({json:['2026-10-01','2026-10-02'].includes(url.searchParams.get('date')!) ? dailyCategories : []});
    if (url.pathname === '/api/records' && route.request().method() === 'POST') {
      expect(route.request().headers()['x-csrf-token']).toBe('test-csrf');
      const input=route.request().postDataJSON();
      const record={...input,id:16,categoryName:categories.find(item=>item.id===input.categoryId)!.name};
      addedRecords.push(record);return route.fulfill({status:201,json:record});
    }
    if (url.pathname === '/api/categories') return route.fulfill({json:categories});
    if (url.pathname === '/api/calendar/integrated') {
      const prefix = `${url.searchParams.get('year')}-${url.searchParams.get('month')!.padStart(2,'0')}`;
      return route.fulfill({json:[{date:`${prefix}-01`,completedCount:3,totalCount:6},{date:`${prefix}-02`,completedCount:5,totalCount:8}]});
    }
    if (url.pathname === '/api/records') return route.fulfill({json:url.searchParams.get('date') === '2026-10-02' ? [...records,...addedRecords] : []});
    if (url.pathname.endsWith('/photo-url')) return route.fulfill({json:{imageUrl:'/collection-photo.png',expiresAt:'2026-10-02T04:00:00Z'}});
    const record = [...records,...addedRecords].find(item => url.pathname === `/api/records/${item.id}`);
    if (record) return route.fulfill({json:record});
    if (url.pathname.endsWith('/records')) return route.fulfill({json:[]});
    return route.fulfill({status:404,json:{}});
  });
  await page.route('**/collection-photo.png', route => route.fulfill({contentType:'image/png',body:Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+j8ioAAAAASUVORK5CYII=','base64')}));
}

test('historical denominators, daily grid, chronological feed and detail return navigation', async ({page},testInfo) => {
  await setup(page);
  await page.goto('/#/calendar');
  const day = page.getByRole('link',{name:'2026-10-02, 5/8 카테고리 완료',exact:true});
  await expect(day).toBeVisible();
  await expect(day).toContainText('5/8');
  await expect(page.getByRole('link',{name:'2026-10-01, 3/6 카테고리 완료',exact:true})).toContainText('3/6');
  await expect(page.getByRole('link',{name:/2026-10-03/})).toHaveCount(0);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.screenshot({path:testInfo.outputPath('integrated.png')});
  await day.click();
  await expect(page.getByRole('heading',{name:'모아보기'})).toBeVisible();
  const cards = page.locator('.daily-record-card');
  await expect(cards).toHaveCount(8);
  await expect(page.getByRole('link',{name:'독서, 기록 없음, 기록 작성'})).toBeVisible();
  await expect(page.getByRole('link',{name:/명상/})).toHaveCount(0);
  await expect(page.getByLabel('명상, 삭제된 카테고리')).toBeVisible();
  const first = await cards.nth(0).boundingBox();
  const fourth = await cards.nth(3).boundingBox();
  expect(fourth!.x).toBe(first!.x);
  expect(fourth!.y).toBeGreaterThan(first!.y);
  const boxes = await page.locator('.daily-record-image').evaluateAll(elements => elements.map(element => ({width:element.getBoundingClientRect().width,height:element.getBoundingClientRect().height})));
  for (const box of boxes) { expect(Math.abs(box.width-boxes[0].width)).toBeLessThan(1); expect(Math.abs(box.height-boxes[0].height)).toBeLessThan(1); }
  await page.screenshot({path:testInfo.outputPath('daily-grid.png'),fullPage:true});
  await page.getByRole('link',{name:'아침기록, 08:30 기록 보기'}).click();
  await expect(page.getByRole('heading',{name:'상세 기록'})).toBeVisible();
  await page.getByRole('link',{name:'모아보기로 돌아가기'}).click();
  await page.getByRole('link',{name:'피드보기',exact:true}).click();
  await expect(page.getByRole('heading',{name:'일간 피드',exact:true})).toBeVisible();
  await expect(page.locator('.feed-content time')).toHaveText(['08:30','12:10','18:00','19:00','20:10']);
  await expect(page.locator('.daily-feed-card').filter({hasText:'운동'}).locator('.feed-photo')).toHaveCount(0);
  await expect(page.locator('.feed-photo time')).toHaveCount(0);
  await expect(page.getByRole('button',{name:'다음 날짜',exact:true})).toBeDisabled();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.screenshot({path:testInfo.outputPath('daily-feed.png'),fullPage:true});
  await page.getByRole('link',{name:'아침기록, 08:30 기록 보기'}).click();
  await page.getByRole('link',{name:'일간 피드로 돌아가기'}).click();
  await page.getByRole('button',{name:'이전 날짜'}).click();
  await expect(page.getByRole('heading',{name:'이날의 기록이 없어요'})).toBeVisible();
});

test('month jump, today return, date picker and calendar month restoration', async ({page}) => {
  await setup(page);
  await page.goto('/#/calendar');
  await page.getByRole('button',{name:'월 선택⌄'}).click();
  await page.getByLabel('이동할 월').selectOption('2025-02');
  await page.getByRole('button',{name:'이동',exact:true}).click();
  await expect(page.getByRole('button',{name:'2025년 2월',exact:true})).toBeInViewport();
  await page.getByRole('button',{name:'오늘',exact:true}).click();
  await expect(page.locator('.integrated-day.is-today')).toBeInViewport();
  await page.getByRole('link',{name:'2026-10-02, 5/8 카테고리 완료',exact:true}).click();
  await page.getByRole('button',{name:'2026.10.02⌄'}).click();
  await page.getByLabel('이동할 날짜').fill('2025-02-01');
  await page.getByRole('button',{name:'이동',exact:true}).click();
  await expect(page.getByRole('heading',{name:'이날의 카테고리가 없어요'})).toBeVisible();
  await page.getByRole('link',{name:'통합 캘린더로 돌아가기',exact:true}).click();
  await expect(page.getByRole('button',{name:'2025년 2월',exact:true})).toBeInViewport();
});

test('failed monthly and daily reads retry without replacing data, expired session returns to login', async ({page}) => {
  await setup(page);
  let attempts = 0;
  await page.route('**/api/calendar/integrated?year=2026&month=10', route => attempts++ === 0 ? route.fulfill({status:500,json:{}}) : route.fulfill({json:[{date:'2026-10-02',completedCount:5,totalCount:8}]}));
  await page.goto('/#/calendar');
  const month = page.locator('#integrated-2026-10');
  await expect(month.getByRole('alert')).toBeVisible();
  await month.getByRole('button',{name:'다시 시도'}).click();
  await expect(page.getByRole('link',{name:'2026-10-02, 5/8 카테고리 완료',exact:true})).toBeVisible();
  let dailyReady = false;
  await page.route('**/api/records?date=2026-10-02', route => !dailyReady ? route.fulfill({status:500,json:{}}) : route.fulfill({json:records}));
  await page.getByRole('link',{name:'2026-10-02, 5/8 카테고리 완료',exact:true}).click();
  await expect(page.getByRole('alert')).toBeVisible();
  dailyReady = true;
  await page.getByRole('button',{name:'다시 시도'}).click();
  await expect(page.locator('.daily-record-card')).toHaveCount(8);
  await page.route('**/api/records?date=2026-10-02', route => route.fulfill({status:401,json:{}}));
  await page.reload();
  await expect(page.getByText('세션이 만료됐어요. 다시 로그인해 주세요.')).toBeVisible();
});


test('unrecorded categories remain visible on a day with no records, and a blank card creates a record', async ({page}) => {
  await setup(page);
  await page.goto('/#/daily/2026-10-01');
  await expect(page.locator('.daily-record-card')).toHaveCount(8);
  await expect(page.getByRole('link',{name:'독서, 기록 없음, 기록 작성'})).toHaveAttribute('href', /date=2026-10-01/);
  await expect(page.locator('.daily-record-image time')).toHaveCount(0);
  await page.goto('/#/daily/2026-10-02');
  await page.getByRole('link',{name:'독서, 기록 없음, 기록 작성'}).click();
  await expect(page.getByRole('heading',{name:'기록 작성',exact:true})).toBeVisible();
  await expect(page.locator('.record-date')).toHaveText('2026.10.02');
  await page.getByLabel('기록 시간',{exact:true}).fill('21:00');
  await page.getByLabel('메모',{exact:true}).fill('책을 읽었어요.');
  await page.getByRole('button',{name:'저장',exact:true}).click();
  await expect(page.getByRole('heading',{name:'상세 기록',exact:true})).toBeVisible();
  await page.getByRole('link',{name:'모아보기로 돌아가기'}).click();
  await expect(page.getByRole('link',{name:'독서, 21:00 기록 보기'})).toBeVisible();
  await expect(page.getByRole('link',{name:'독서, 기록 없음, 기록 작성'})).toHaveCount(0);
  await expect(page.locator('.daily-record-card')).toHaveCount(8);
});
