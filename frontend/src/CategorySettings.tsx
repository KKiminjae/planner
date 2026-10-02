import { useEffect, useLayoutEffect, useRef, useState, type FormEvent, type PointerEvent, type ReactNode } from 'react';
import { api, ApiError, type Category } from './api';
import { categoryColor } from './calendar';

const palette = [
  {name:'빨강',value:'#EDA9A9'}, {name:'주황',value:'#F2C39B'}, {name:'노랑',value:'#F5E5AB'},
  {name:'초록',value:'#C6DDB2'}, {name:'파랑',value:'#B8D8F3'}, {name:'남색',value:'#ADB9DC'},
  {name:'보라',value:'#D1BDF0'}, {name:'핑크',value:'#F4BED2'}, {name:'회색',value:'#DADDE5'},
  {name:'민트',value:'#BDE5D5'},
];
function Arrow({ back = false }: {back?:boolean}) { return <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d={back ? 'm14 5-7 7 7 7' : 'm9 5 7 7-7 7'}/></svg>; }
function Header({title,action,busy,home=false}: {title:string;action?:ReactNode;busy?:boolean;home?:boolean}) {
  const heading=useRef<HTMLHeadingElement>(null);
  useLayoutEffect(() => { heading.current?.focus({preventScroll:true}); },[]);
  return <header className="settings-header"><a className="calendar-back" href={busy ? undefined : home ? '#/' : '#/settings/categories'} aria-disabled={busy || undefined} aria-label={home ? '나의 기록으로 돌아가기' : '카테고리 관리로 돌아가기'}><Arrow back/></a><h1 ref={heading} tabIndex={-1}>{title}</h1><div className="settings-header-action">{action}</div></header>;
}
function failure(cause:unknown,fallback:string) {
  if (!(cause instanceof ApiError)) return fallback;
  if (cause.code === 'INVALID_CATEGORY_ORDER') return '목록이 변경됐어요. 목록을 새로고침한 뒤 다시 순서를 바꿔 주세요.';
  if (cause.status === 403) return '요청 권한을 확인하지 못했어요. 새로고침 후 다시 로그인해 주세요.';
  return cause.status >= 500 ? fallback : cause.message;
}
function changed(before:Category[],after:Category[]) { return before.some((category,index) => category.id !== after[index]?.id); }

export function CategoryManager({onExpired}: {onExpired:()=>void}) {
  const [categories,setCategories]=useState<Category[]|null>(null);
  const order=useRef<Category[]>([]);
  const [error,setError]=useState('');
  const [attempt,setAttempt]=useState(0);
  const [busy,setBusy]=useState(false);
  const saving=useRef(false);
  const [status,setStatus]=useState('');
  const [dragId,setDragId]=useState<number|null>(null);
  const drag=useRef<{id:number;original:Category[];startY:number;moved:boolean}|null>(null);
  const suppressClick=useRef(false);
  const [sortingId,setSortingId]=useState<number|null>(null);
  const sortingIdRef=useRef<number|null>(null);
  const snapshot=useRef<Category[]>([]);
  function update(items:Category[]) { order.current=items;setCategories(items); }
  useEffect(() => {
    let active=true;setCategories(null);setError('');setStatus('');
    api.categories().then(data => {if(active)update([...data].sort((a,b)=>a.displayOrder-b.displayOrder));}).catch(cause=>{
      if(!active)return;
      if(cause instanceof ApiError && cause.status===401)onExpired();else setError('카테고리를 불러오지 못했어요. 다시 시도해 주세요.');
    });
    return()=>{active=false;};
  },[attempt,onExpired]);
  function move(id:number,index:number) {
    const items=[...order.current];const from=items.findIndex(item=>item.id===id);
    if(from<0 || index<0 || index>=items.length || from===index)return;
    const [item]=items.splice(from,1);items.splice(index,0,item);update(items);
    setStatus(`${item.name}, ${index+1}번째로 이동했어요.`);
    if(sortingIdRef.current!==null)window.requestAnimationFrame(()=>document.querySelector<HTMLButtonElement>(`[data-category-id="${id}"] .drag-handle`)?.focus({preventScroll:true}));
  }
  async function saveOrder(original:Category[]) {
    if(saving.current || !changed(original,order.current))return;
    const next=[...order.current];saving.current=true;setBusy(true);setError('');setStatus('순서 저장 중…');
    try {await api.reorderCategories(next.map(item=>item.id));update(next.map((item,i)=>({...item,displayOrder:i+1})));setStatus('카테고리 순서를 저장했어요.');}
    catch(cause){update(original);if(cause instanceof ApiError && cause.status===401)onExpired();else {setError(failure(cause,'순서를 저장하지 못했어요. 이전 순서로 되돌렸습니다.'));setStatus('');}}
    finally{saving.current=false;setBusy(false);}
  }
  function beginSort(id:number) {
    if(saving.current || sortingIdRef.current!==null)return;
    snapshot.current=[...order.current];sortingIdRef.current=id;setSortingId(id);
    setStatus('위·아래 버튼이나 방향키로 이동하고 순서를 저장해 주세요.');
  }
  function cancelSort() {update(snapshot.current);sortingIdRef.current=null;setSortingId(null);setStatus('순서 변경을 취소했어요.');}
  function finishSort() {sortingIdRef.current=null;setSortingId(null);void saveOrder(snapshot.current);}
  function pointerDown(event:PointerEvent<HTMLButtonElement>,id:number) {
    if(event.button!==0 || saving.current || sortingIdRef.current!==null)return;
    suppressClick.current=false;drag.current={id,original:[...order.current],startY:event.clientY,moved:false};
    event.currentTarget.setPointerCapture(event.pointerId);
  }
  function pointerMove(event:PointerEvent<HTMLButtonElement>) {
    const state=drag.current;if(!state)return;
    if(Math.abs(event.clientY-state.startY)>6){state.moved=true;setDragId(state.id);}
    if(!state.moved)return;
    const target=document.elementFromPoint(event.clientX,event.clientY)?.closest<HTMLElement>('[data-category-id]');
    if(target){const index=order.current.findIndex(item=>item.id===Number(target.dataset.categoryId));move(state.id,index);}
    const edge=80;
    if(event.clientY<edge)window.scrollBy(0,-14);else if(event.clientY>window.innerHeight-edge)window.scrollBy(0,14);
  }
  function pointerEnd(event:PointerEvent<HTMLButtonElement>,cancelled=false) {
    const state=drag.current;if(!state)return;drag.current=null;setDragId(null);
    if(event.currentTarget.hasPointerCapture(event.pointerId))event.currentTarget.releasePointerCapture(event.pointerId);
    if(cancelled){update(state.original);setStatus('순서 변경을 취소했어요.');suppressClick.current=true;}
    else if(state.moved){suppressClick.current=true;void saveOrder(state.original);}
  }
  const sortingIndex=order.current.findIndex(item=>item.id===sortingId);
  return <main className="app settings-page"><Header title="카테고리 관리" busy={busy} home/>
    {!categories ? error ? <section className="empty-state"><p role="alert">{error}</p><button className="primary" onClick={()=>setAttempt(v=>v+1)}>다시 시도</button></section> : <p className="muted" role="status">카테고리를 불러오는 중이에요.</p> : <>
    <p id="order-instructions" className="sr-only">핸들을 드래그하거나 눌러서 순서를 바꿉니다. 키보드에서는 Enter로 시작하고 위·아래 방향키로 이동, Enter로 저장, Escape로 취소합니다.</p>
    {!categories.length ? <section className="empty-state"><h2>첫 카테고리를 만들어보세요</h2><p>기록하고 싶은 일상을 추가해보세요.</p></section> : <ol className="managed-categories" aria-label="카테고리 목록">{categories.map(item=><li key={item.id} data-category-id={item.id} className={`managed-row ${dragId===item.id || sortingId===item.id ? 'is-moving' : ''}`}>
      <button className="drag-handle" aria-label={`${item.name} 순서 변경`} aria-describedby="order-instructions" aria-pressed={sortingId===item.id} disabled={busy || (sortingId!==null && sortingId!==item.id)}
        onPointerDown={event=>pointerDown(event,item.id)} onPointerMove={pointerMove} onPointerUp={event=>pointerEnd(event)} onPointerCancel={event=>pointerEnd(event,true)}
        onClick={()=>{if(suppressClick.current){suppressClick.current=false;return;}beginSort(item.id);}}
        onKeyDown={event=>{if(event.key==='Enter' || event.key===' '){event.preventDefault();if(sortingIdRef.current===item.id)finishSort();else beginSort(item.id);}else if(sortingIdRef.current===item.id && (event.key==='ArrowUp' || event.key==='ArrowDown')){event.preventDefault();move(item.id,order.current.findIndex(row=>row.id===item.id)+(event.key==='ArrowUp'?-1:1));}else if(sortingIdRef.current===item.id && event.key==='Escape'){event.preventDefault();cancelSort();}}}>
        <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" aria-hidden="true"><path d="M5 6h14M5 12h14M5 18h14"/></svg>
      </button><a className="managed-category-link" href={busy || sortingId!==null ? undefined : `#/settings/categories/${item.id}`} aria-disabled={busy || sortingId!==null || undefined} aria-label={`${item.name} 수정`}><span className="category-dot" style={{background:categoryColor(item.color)}}/><span className="managed-category-name">{item.name}{item.isPrivate && <span className="private-indicator" aria-label="비공개">비공개</span>}</span><Arrow/></a>
    </li>)}</ol>}
    {sortingId!==null && <section className="order-controls" aria-label="순서 조정"><p>{categories.find(item=>item.id===sortingId)?.name} · {sortingIndex+1}번째</p><div><button disabled={sortingIndex<=0} onClick={()=>move(sortingId,sortingIndex-1)} aria-label="위로 이동">↑</button><button disabled={sortingIndex>=categories.length-1} onClick={()=>move(sortingId,sortingIndex+1)} aria-label="아래로 이동">↓</button><button onClick={cancelSort}>취소</button><button className="pink-button" onClick={finishSort}>순서 저장</button></div></section>}
    {error && <div className="settings-error"><p className="error" role="alert">{error}</p><button className="primary" disabled={busy} onClick={()=>{sortingIdRef.current=null;setSortingId(null);setAttempt(v=>v+1);}}>목록 새로고침</button></div>}
    <p className="sr-only" role="status">{status}</p>
    <a className="category-add" href={busy || sortingId!==null ? undefined : '#/settings/categories/new'} aria-disabled={busy || sortingId!==null || undefined}>카테고리 추가</a>
    </>}
  </main>;
}
export function CategoryEditor({id,onExpired}: {id?:number;onExpired:()=>void}) {
  const [category,setCategory]=useState<Category|null>(null);
  const [error,setError]=useState('');
  const [attempt,setAttempt]=useState(0);
  useEffect(()=>{
    if(id===undefined)return;
    let active=true;setError('');setCategory(null);
    api.categories().then(data=>{if(active){const item=data.find(row=>row.id===id);if(item)setCategory(item);else setError('카테고리를 찾을 수 없어요.');}}).catch(cause=>{
      if(!active)return;if(cause instanceof ApiError && cause.status===401)onExpired();else setError('카테고리를 불러오지 못했어요.');
    });return()=>{active=false;};
  },[id,onExpired,attempt]);
  if(id!==undefined && !category)return <main className="app settings-page"><Header title="카테고리 수정"/>{error ? <section className="empty-state"><p role="alert">{error}</p><button className="primary" onClick={()=>setAttempt(v=>v+1)}>다시 시도</button></section> : <p role="status">카테고리를 불러오는 중이에요.</p>}</main>;
  return <CategoryForm category={category || undefined} onExpired={onExpired}/>;
}
function CategoryForm({category,onExpired}: {category?:Category;onExpired:()=>void}) {
  const [name,setName]=useState(category?.name || '');
  const [color,setColor]=useState(categoryColor(category?.color || palette[0].value));
  const [isPrivate,setIsPrivate]=useState(category?.isPrivate || false);
  const [error,setError]=useState('');
  const [busy,setBusy]=useState(false);
  const saving=useRef(false);
  const [confirm,setConfirm]=useState(false);
  const dialog=useRef<HTMLDialogElement>(null);
  useEffect(()=>{
    if(!confirm)return;
    const prior=document.activeElement as HTMLElement|null;const modal=dialog.current;modal?.showModal();
    return()=>{modal?.close();prior?.focus({preventScroll:true});};
  },[confirm]);
  async function save(event:FormEvent) {
    event.preventDefault();if(saving.current)return;
    if(!name.trim()){setError('카테고리 이름을 입력해 주세요.');return;}
    saving.current=true;setBusy(true);setError('');
    try{const input={name:name.trim(),color,isPrivate};if(category)await api.updateCategory(category.id,input);else await api.createCategory(input);window.location.hash='#/settings/categories';}
    catch(cause){if(cause instanceof ApiError && cause.status===401)onExpired();else setError(failure(cause,'저장하지 못했어요. 입력 내용은 유지됩니다. 다시 시도해 주세요.'));}
    finally{saving.current=false;setBusy(false);}
  }
  async function remove() {
    if(!category || saving.current)return;saving.current=true;setBusy(true);setError('');
    try{await api.deleteCategory(category.id);window.location.hash='#/settings/categories';}
    catch(cause){if(cause instanceof ApiError && cause.status===401)onExpired();else setError(failure(cause,'삭제하지 못했어요. 다시 시도해 주세요.'));}
    finally{saving.current=false;setBusy(false);}
  }
  const hasExistingColor=category && !palette.some(item=>item.value.toLowerCase()===color.toLowerCase());
  return <main className="app settings-page"><form onSubmit={save}><Header title={category ? '카테고리 수정' : '카테고리 추가'} busy={busy} action={<button className="pink-button" type="submit" disabled={busy}>{busy ? '저장 중…' : '저장'}</button>}/>
    <label className="record-label" htmlFor="category-name">이름</label><input className="record-input category-name-input" id="category-name" value={name} onChange={event=>setName(event.target.value)} maxLength={50} required disabled={busy} placeholder="카테고리 이름" autoComplete="off"/>
    <fieldset className="category-colors" disabled={busy}><legend>색상</legend>{hasExistingColor && <p className="current-category-color"><span style={{background:color}}/>현재 색상</p>}<div>{palette.map(item=><label className="color-option" key={item.value}><input style={{background:item.value}} type="radio" name="category-color" value={item.value} checked={color.toLowerCase()===item.value.toLowerCase()} onChange={()=>setColor(item.value)} aria-label={item.name}/></label>)}</div></fieldset>
    <section className="privacy-setting"><label className="privacy-label" htmlFor="category-private"><span><strong>비공개</strong><small>{isPrivate ? '나만 볼 수 있어요.' : '자동으로 공개되지 않아요.'}</small></span><input id="category-private" type="checkbox" role="switch" checked={isPrivate} onChange={event=>setIsPrivate(event.target.checked)} disabled={busy}/></label><p>비공개로 설정하면 나만 조회할 수 있으며,<br/>향후 공유 대상에서 제외됩니다.<br/>비공개를 꺼도 자동으로 공개되지 않아요.</p></section>
    {!confirm && error && <p className="error" role="alert">{error}</p>}
    {category && <button className="category-delete" type="button" disabled={busy} onClick={()=>{setError('');setConfirm(true);}}>카테고리 삭제</button>}
  </form>
    {confirm && category && <dialog ref={dialog} className="delete-dialog category-delete-dialog" aria-labelledby="category-delete-title" onCancel={event=>{if(busy)event.preventDefault();else setConfirm(false);}}><h2 id="category-delete-title">‘{category.name}’ 카테고리를 삭제할까요?</h2><p>이 카테고리에 저장된 기록과 사진도 함께 삭제됩니다.<br/>삭제 후 복구할 수 없습니다.</p>{error && <p className="error" role="alert">{error}</p>}<div><button className="primary" disabled={busy} autoFocus onClick={()=>setConfirm(false)}>취소</button><button className="delete-button" disabled={busy} onClick={remove}>{busy ? '삭제 중…' : '삭제'}</button></div></dialog>}
  </main>;
}
