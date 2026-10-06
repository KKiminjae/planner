import { useEffect, useRef, useState, type CSSProperties, type FormEvent } from 'react';
import { CategoryManager, CategoryEditor } from './CategorySettings';
import { IntegratedCalendar, DailyCollection } from './CollectionScreens';
import { NewRecord, EditRecord, RecordDetail } from './RecordScreens';
import CategoryCalendar from './CategoryCalendar';
import { api, ApiError, type Category, type MonthlyRecord } from './api';
import { categoryColor, daysInMonth, monthGrid, recordedDays, seoulToday } from './calendar';

function Icon({ name }: { name: 'settings' | 'arrow' | 'back' }) {
  return <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">{name === 'settings' ? <><path d="m9 3-.6 2.3-2 .9-2.1-.7-2 3.5 1.6 1.7v2.6L2.3 15l2 3.5 2.2-.7 2 .9L9 21h4l.6-2.3 2-.9 2.1.7 2-3.5-1.6-1.7v-2.6L19.7 9l-2-3.5-2.2.7-2-.9L13 3Z"/><circle cx="11" cy="12" r="3"/></> : <path d={name === 'back' ? 'm14 5-7 7 7 7' : 'm9 5 7 7-7 7'}/>}</svg>;
}

function Login({ onSuccess, expired }: { onSuccess: () => void; expired: boolean }) {
  const [username, setUsername] = useState('owner');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [waitUntil, setWaitUntil] = useState(0);
  const [now, setNow] = useState(Date.now());
  const remaining = Math.max(0, Math.ceil((waitUntil - now) / 1000));
  useEffect(() => {
    if (!waitUntil) return;
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, [waitUntil]);
  async function submit(event: FormEvent) {
    event.preventDefault();
    if (busy || remaining) return;
    setBusy(true); setError('');
    try { await api.login(username.trim(), password); setPassword(''); onSuccess(); }
    catch (cause) {
      if (cause instanceof ApiError && cause.status === 429) {
        setWaitUntil(Date.now() + cause.retryAfter * 1000); setNow(Date.now());
        setError('로그인 시도가 많아요. 잠시 기다린 뒤 다시 시도해 주세요.');
      } else setError(cause instanceof ApiError && cause.status === 401 ? '아이디와 비밀번호를 확인해 주세요.' : '로그인하지 못했어요. 연결을 확인하고 다시 시도해 주세요.');
    } finally { setBusy(false); }
  }
  return <main className="app login-page"><div className="login-intro"><span className="eyebrow">사진 기록 캘린더</span><h1>나의 기록</h1></div><form className="login-form" onSubmit={submit}><h2>로그인</h2>{expired && <p className="muted" role="status">세션이 만료됐어요. 다시 로그인해 주세요.</p>}<label htmlFor="username">아이디</label><input id="username" autoComplete="username" value={username} onChange={event => setUsername(event.target.value)} required disabled={busy}/><label htmlFor="password">비밀번호</label><input id="password" type="password" autoComplete="current-password" value={password} onChange={event => setPassword(event.target.value)} required disabled={busy}/>{error && <p className="error" role="alert">{error}</p>}<button className="primary" disabled={busy || remaining > 0}>{busy ? '로그인 중…' : remaining > 0 ? `${remaining}초 후 다시 시도` : '내 기록 보러 가기'}</button></form></main>;
}

function CategoryCard({ category, year, month, today, onExpired }: { category: Category; year: number; month: number; today: string; onExpired: () => void }) {
  const [records, setRecords] = useState<MonthlyRecord[] | null>(null);
  const [error, setError] = useState(false);
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    let active = true;
    setRecords(null); setError(false);
    api.records(category.id, year, month).then(result => { if (active) setRecords(result); }).catch(cause => {
      if (!active) return;
      if (cause instanceof ApiError && cause.status === 401) onExpired();
      else setError(true);
    });
    return () => { active = false; };
  }, [category.id, year, month, attempt, onExpired]);
  const days = recordedDays(records?.map(record => record.recordDate) || [], year, month);
  const style = { '--category': categoryColor(category.color) } as CSSProperties;
  if (error) return <section className="category-card" style={style}><div className="card-heading"><h2>{category.name}</h2></div><div className="card-error"><p role="alert">기록을 불러오지 못했어요.</p><button onClick={() => setAttempt(value => value + 1)}>다시 시도</button></div></section>;
  return <a className="category-card" style={style} href={`#/categories/${category.id}`} aria-label={`${category.name}, ${year}년 ${month}월${records ? ` ${days.size}일 기록` : ' 기록 불러오는 중'}`} aria-busy={!records}><div className="card-heading"><h2>{category.name}</h2><span>{records ? `${days.size}/${daysInMonth(year, month)}` : '…'}</span></div><div className="mini-calendar"><div className="weekdays" aria-hidden="true">{['M', 'T', 'W', 'T', 'F', 'S', 'S'].map((day, index) => <span key={index}>{day}</span>)}</div><div className={`calendar-grid ${records ? '' : 'loading-grid'}`} aria-hidden="true">{monthGrid(year, month).map((day, index) => {
    const date = day ? `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}` : '';
    return <span key={index} className={`calendar-cell ${day === null ? 'blank' : days.has(day) ? 'recorded' : date > today ? 'future' : 'missed'} ${date === today ? 'today' : ''}`}/>;
  })}</div></div></a>;
}

function Home({ onExpired }: { onExpired: () => void }) {
  const [today, setToday] = useState(seoulToday());
  const [categories, setCategories] = useState<Category[] | null>(null);
  const [failed, setFailed] = useState(false);
  const [attempt, setAttempt] = useState(0);
  const [year, month] = today.split('-').map(Number);
  useEffect(() => {
    const update = () => setToday(seoulToday());
    const timer = window.setInterval(update, 30000);
    window.addEventListener('focus', update);
    return () => { window.clearInterval(timer); window.removeEventListener('focus', update); };
  }, []);
  useEffect(() => {
    let active = true;
    setFailed(false); setCategories(null);
    api.categories().then(result => { if (active) setCategories([...result].sort((a, b) => a.displayOrder - b.displayOrder)); }).catch(cause => {
      if (!active) return;
      if (cause instanceof ApiError && cause.status === 401) onExpired();
      else setFailed(true);
    });
    return () => { active = false; };
  }, [attempt, onExpired]);
  return <main className="app"><header className="home-header"><h1>나의 기록</h1><a className="settings-button" href="#/settings/categories" aria-label="카테고리 관리"><Icon name="settings"/></a></header><a className="collection-button" href="#/calendar">모아보기<Icon name="arrow"/></a><div className="month-caption"><span>{year}년 {month}월</span></div>{failed ? <section className="empty-state"><h2>기록을 불러오지 못했어요</h2><p>연결을 확인하고 다시 시도해 주세요.</p><button className="primary" onClick={() => setAttempt(value => value + 1)}>다시 시도</button></section> : categories?.length === 0 ? <section className="empty-state"><span className="empty-symbol" aria-hidden="true">✦</span><h2>첫 기록을 기다리고 있어요</h2><p>카테고리를 만들면<br/>이곳에 나의 기록이 차곡차곡 쌓여요.</p><a className="primary" href="#/settings/categories">카테고리 관리<Icon name="arrow"/></a></section> : <div className="category-list" aria-label="카테고리별 이번 달 기록">{categories ? categories.map(category => <CategoryCard key={category.id} category={category} year={year} month={month} today={today} onExpired={onExpired}/>) : <>{[0, 1, 2, 3].map(index => <div className="skeleton-card" key={index}/>) }<span className="sr-only" role="status">카테고리를 불러오는 중이에요.</span></>}</div>}</main>;
}

function Upcoming({ route }: { route: string }) {
  const title = /^\/records\/\d+$/.test(route) ? '상세 기록' : /^\/categories\/\d+\/new\?date=/.test(route) ? '기록 작성' : route === '/calendar' ? '통합 캘린더' : route === '/settings/categories' ? '카테고리 관리' : /^\/categories\/\d+$/.test(route) ? '카테고리 캘린더' : '화면을 찾을 수 없어요';
  const heading = useRef<HTMLHeadingElement>(null);
  useEffect(() => { heading.current?.focus(); }, [route]);
  return <main className="app"><header className="sub-header"><a href="#/" className="settings-button" aria-label="나의 기록으로 돌아가기"><Icon name="back"/></a><h1 tabIndex={-1} ref={heading}>{title}</h1></header><section className="empty-state"><span className="empty-symbol" aria-hidden="true">✦</span><h2>이 화면은 준비 중이에요</h2><p>다음 구현 단계에서 연결할게요.</p><a href="#/" className="primary">나의 기록으로 돌아가기</a></section></main>;
}

export default function App() {
  const [session, setSession] = useState<'checking' | 'authenticated' | 'login' | 'error'>('checking');
  const [expired, setExpired] = useState(false);
  const [route, setRoute] = useState(window.location.hash.slice(1) || '/');
  const expire = useRef(() => { setExpired(true); setSession('login'); }).current;
  async function checkSession() {
    setSession('checking');
    try { await api.me(); setSession('authenticated'); }
    catch (cause) { setSession(cause instanceof ApiError && cause.status === 401 ? 'login' : 'error'); }
  }
  useEffect(() => { void checkSession(); }, []);
  useEffect(() => {
    const change = () => { setRoute(window.location.hash.slice(1) || '/'); window.scrollTo(0, 0); };
    window.addEventListener('hashchange', change);
    return () => window.removeEventListener('hashchange', change);
  }, []);
  if (session === 'checking') return <main className="app session-state" role="status"><div className="spinner"/>내 기록을 준비하고 있어요.</main>;
  if (session === 'error') return <main className="app"><section className="empty-state"><h1>연결하지 못했어요</h1><p>서버 연결을 확인하고 다시 시도해 주세요.</p><button className="primary" onClick={checkSession}>다시 시도</button></section></main>;
  if (session === 'login') return <Login expired={expired} onSuccess={() => { setExpired(false); setSession('authenticated'); }}/>;
  if (route === '/settings/categories') return <CategoryManager onExpired={expire}/>;
  if (route === '/settings/categories/new') return <CategoryEditor key={route} onExpired={expire}/>;
  const categoryEditRoute = /^\/settings\/categories\/(\d+)$/.exec(route);
  if (categoryEditRoute) return <CategoryEditor key={route} id={Number(categoryEditRoute[1])} onExpired={expire}/>;
  if (route === '/calendar' || route.startsWith('/calendar?')) return <IntegratedCalendar key={route} initialMonth={new URLSearchParams(route.split('?')[1] || '').get('month') || undefined} onExpired={expire}/>;
  const dailyRoute = /^\/daily\/(\d{4}-\d{2}-\d{2})(\/feed)?$/.exec(route);
  if (dailyRoute) return <DailyCollection key={route} date={dailyRoute[1]} feed={!!dailyRoute[2]} onExpired={expire}/>;
  const from = new URLSearchParams(route.split('?')[1] || '').get('from');
  const returnTo = from && /^\/daily\/\d{4}-\d{2}-\d{2}(\/feed)?$/.test(from) ? from : undefined;
  const newRoute = /^\/categories\/(\d+)\/new\?(.+)$/.exec(route);
  if (newRoute) return <NewRecord key={route} categoryId={Number(newRoute[1])} date={new URLSearchParams(newRoute[2]).get('date') || ''} returnTo={returnTo} onExpired={expire}/>;
  const editRoute = /^\/records\/(\d+)\/edit(?:\?.*)?$/.exec(route);
  if (editRoute) return <EditRecord key={route} id={Number(editRoute[1])} returnTo={returnTo} onExpired={expire}/>;
  const detailRoute = /^\/records\/(\d+)(?:\?.*)?$/.exec(route);
  if (detailRoute) return <RecordDetail key={route} id={Number(detailRoute[1])} returnTo={returnTo} onExpired={expire}/>;
  const categoryRoute = /^\/categories\/(\d+)$/.exec(route);
  if (categoryRoute) return <CategoryCalendar id={Number(categoryRoute[1])} onExpired={expire}/>;
  return route === '/' ? <Home onExpired={expire}/> : <Upcoming route={route}/>;
}
