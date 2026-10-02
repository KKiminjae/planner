import { test, expect, type Page } from '@playwright/test';

async function setup(page:Page, options:{failReorder?:boolean;failSave?:boolean;empty?:boolean}={}) {
  let categories=options.empty ? [] : [
    {id:1,name:'아침기록',color:'#9872ea',displayOrder:1,isPrivate:false},
    {id:2,name:'점심기록',color:'#81afe4',displayOrder:2,isPrivate:false},
    {id:3,name:'운동',color:'#df7ea6',displayOrder:3,isPrivate:true},
  ];
  const writes:{method:string;path:string;body:Record<string,unknown>}[]=[];
  let failSave=options.failSave;
  await page.route('**/api/**',async route=>{
    const request=route.request();const url=new URL(request.url());const method=request.method();
    if(url.pathname==='/api/auth/me')return route.fulfill({json:{ownerId:1,username:'owner'}});
    if(url.pathname==='/api/auth/csrf')return route.fulfill({json:{headerName:'X-CSRF-TOKEN',token:'test-csrf'}});
    if(method==='GET'){
      if(url.pathname==='/api/categories')return route.fulfill({json:categories});
      return route.fulfill({json:[]});
    }
    expect(request.headers()['x-csrf-token']).toBe('test-csrf');
    const body=method==='DELETE' ? {} : request.postDataJSON();writes.push({method,path:url.pathname,body});
    if(url.pathname==='/api/categories/reorder'){
      if(options.failReorder)return route.fulfill({status:500,json:{}});
      expect(body.categoryIds).toHaveLength(categories.length);
      expect(new Set(body.categoryIds).size).toBe(categories.length);
      categories=body.categoryIds.map((id:number,i:number)=>({...categories.find(item=>item.id===id)!,displayOrder:i+1}));
      return route.fulfill({status:204});
    }
    if(failSave){failSave=false;return route.fulfill({status:500,json:{}});}
    if(method==='POST'){
      const category={id:4,...body,displayOrder:categories.length+1};categories.push(category);
      return route.fulfill({status:201,json:category});
    }
    const id=Number(url.pathname.split('/').pop());
    if(method==='DELETE'){categories=categories.filter(item=>item.id!==id);return route.fulfill({status:204});}
    categories=categories.map(item=>item.id===id ? {...item,...body} : item);
    return route.fulfill({json:categories.find(item=>item.id===id)});
  });
  return {writes};
}

test('create and edit category, choose color/privacy, cancel and confirm deletion',async({page},testInfo)=>{
  const state=await setup(page);
  await page.goto('/#/settings/categories');
  await expect(page.getByRole('heading',{name:'카테고리 관리',exact:true})).toBeVisible();
  await expect(page.locator('.managed-row')).toHaveCount(3);
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
  await page.screenshot({path:testInfo.outputPath('category-management.png'),fullPage:true});
  await page.getByRole('link',{name:'카테고리 추가',exact:true}).click();
  await page.getByRole('button',{name:'저장',exact:true}).click();expect(state.writes).toHaveLength(0);
  await page.getByLabel('이름',{exact:true}).fill('산책');
  await page.getByRole('radio',{name:'초록',exact:true}).check();
  await page.getByRole('switch',{name:/비공개/}).check();
  await page.getByRole('button',{name:'저장',exact:true}).click();
  await expect(page.getByRole('link',{name:'산책 수정',exact:true})).toBeVisible();
  expect(state.writes[0].body).toEqual({name:'산책',color:'#C6DDB2',isPrivate:true});
  await page.getByRole('link',{name:'산책 수정',exact:true}).click();
  await expect(page.getByRole('radio',{name:'초록',exact:true})).toBeChecked();
  await expect(page.getByRole('switch',{name:/비공개/})).toBeChecked();
  await page.getByLabel('이름',{exact:true}).fill('저녁 산책');
  await page.getByRole('radio',{name:'핑크',exact:true}).check();
  await page.getByRole('switch',{name:/비공개/}).uncheck();
  await expect(page.getByText('비공개를 꺼도 자동으로 공개되지 않아요.',{exact:false})).toBeVisible();
  await page.screenshot({path:testInfo.outputPath('category-edit.png'),fullPage:true});
  await page.getByRole('button',{name:'저장',exact:true}).click();
  await expect(page.getByRole('link',{name:'저녁 산책 수정',exact:true})).toBeVisible();
  expect(state.writes[1].body).toEqual({name:'저녁 산책',color:'#F4BED2',isPrivate:false});
  await page.getByRole('link',{name:'저녁 산책 수정',exact:true}).click();
  await page.getByRole('button',{name:'카테고리 삭제',exact:true}).click();
  await expect(page.getByRole('dialog')).toContainText('기록과 사진도 함께 삭제');
  await expect(page.getByRole('dialog')).toContainText('복구할 수 없습니다');
  await page.getByRole('button',{name:'취소',exact:true}).click();
  expect(state.writes.filter(write=>write.method==='DELETE')).toHaveLength(0);
  await expect(page.getByRole('button',{name:'카테고리 삭제',exact:true})).toBeFocused();
  await page.getByRole('button',{name:'카테고리 삭제',exact:true}).click();
  await page.screenshot({path:testInfo.outputPath('category-delete.png')});
  await page.getByRole('dialog').getByRole('button',{name:'삭제',exact:true}).click();
  await expect(page.getByRole('heading',{name:'카테고리 관리',exact:true})).toBeVisible();
  await expect(page.getByRole('link',{name:'저녁 산책 수정',exact:true})).toHaveCount(0);
  expect(state.writes.filter(write=>write.method==='DELETE')).toHaveLength(1);
});

test('keyboard and button reorder save the entire list, cancel leaves order unchanged',async({page})=>{
  const state=await setup(page);
  await page.goto('/#/settings/categories');
  const handle=page.getByRole('button',{name:'아침기록 순서 변경',exact:true});
  await handle.focus();await page.keyboard.press('Enter');await page.keyboard.press('ArrowDown');
  await expect(page.locator('.managed-category-name')).toHaveText(['점심기록','아침기록','운동비공개']);
  await handle.press('Enter');
  await expect(page.getByText('카테고리 순서를 저장했어요.',{exact:true})).toHaveCount(1);
  expect(state.writes[0].body).toEqual({categoryIds:[2,1,3]});
  await page.getByRole('button',{name:'운동 순서 변경',exact:true}).click();
  await page.getByRole('button',{name:'위로 이동',exact:true}).click();
  await page.getByRole('button',{name:'취소',exact:true}).click();
  expect(state.writes).toHaveLength(1);
  await expect(page.locator('.managed-row').nth(2)).toHaveAttribute('data-category-id','3');
  await page.getByRole('button',{name:'운동 순서 변경',exact:true}).click();
  await page.getByRole('button',{name:'위로 이동',exact:true}).click();
  await page.getByRole('button',{name:'순서 저장',exact:true}).click();
  await expect.poll(()=>state.writes.length).toBe(2);
  expect(state.writes[1].body).toEqual({categoryIds:[2,3,1]});
});

test('drag handle saves the order and a failed reorder restores the original list',async({page})=>{
  const state=await setup(page,{failReorder:true});
  await page.goto('/#/settings/categories');
  const handle=page.getByRole('button',{name:'아침기록 순서 변경',exact:true});
  await expect(handle).toBeVisible();
  const start=(await handle.boundingBox())!;
  const target=(await page.locator('.managed-row').nth(2).boundingBox())!;
  await page.mouse.move(start.x+start.width/2,start.y+start.height/2);
  await page.mouse.down();await page.mouse.move(start.x+start.width/2,target.y+target.height/2,{steps:8});await page.mouse.up();
  await expect(page.getByRole('alert')).toContainText('이전 순서로 되돌렸습니다');
  expect(state.writes[0].body).toEqual({categoryIds:[2,3,1]});
  await expect(page.locator('.managed-row').nth(0)).toHaveAttribute('data-category-id','1');
  await page.getByRole('button',{name:'목록 새로고침',exact:true}).click();
  await expect(page.getByRole('link',{name:'아침기록 수정',exact:true})).toBeVisible();
});

test('empty list, save failure preserves form, expired session returns to login',async({page})=>{
  await setup(page,{empty:true,failSave:true});
  await page.goto('/#/settings/categories');
  await expect(page.getByRole('heading',{name:'첫 카테고리를 만들어보세요'})).toBeVisible();
  await page.getByRole('link',{name:'카테고리 추가',exact:true}).click();
  await page.getByLabel('이름',{exact:true}).fill('독서');
  await page.getByRole('button',{name:'저장',exact:true}).click();
  await expect(page.getByRole('alert')).toContainText('저장하지 못했어요');
  await expect(page.getByLabel('이름',{exact:true})).toHaveValue('독서');
  await page.route('**/api/categories',route=>route.fulfill({status:401,json:{}}));
  await page.getByRole('button',{name:'저장',exact:true}).click();
  await expect(page.getByText('세션이 만료됐어요. 다시 로그인해 주세요.')).toBeVisible();
});

test('touch drag saves the order and retains it after reload',async({page},testInfo)=>{
  test.skip(!testInfo.project.use.isMobile,'Touch interaction is checked in the mobile projects.');
  const state=await setup(page);
  await page.goto('/#/settings/categories');
  const handle=page.getByRole('button',{name:'아침기록 순서 변경',exact:true});
  await expect(handle).toBeVisible();
  const start=(await handle.boundingBox())!;
  const target=(await page.locator('.managed-row').nth(2).boundingBox())!;
  const x=start.x+start.width/2;const y=start.y+start.height/2;
  const end=target.y+target.height/2;
  const session=await page.context().newCDPSession(page);
  await session.send('Input.dispatchTouchEvent',{type:'touchStart',touchPoints:[{x,y,id:1}]});
  for(let step=1;step<=8;step++)await session.send('Input.dispatchTouchEvent',{type:'touchMove',touchPoints:[{x,y:y+(end-y)*step/8,id:1}]});
  await session.send('Input.dispatchTouchEvent',{type:'touchEnd',touchPoints:[]});
  await expect(page.getByText('카테고리 순서를 저장했어요.',{exact:true})).toHaveCount(1);
  expect(state.writes[0].body).toEqual({categoryIds:[2,3,1]});
  await page.reload();
  await expect(page.locator('.managed-row').nth(0)).toHaveAttribute('data-category-id','2');
  await expect(page.locator('.managed-row').nth(2)).toHaveAttribute('data-category-id','1');
  await session.detach();
});
