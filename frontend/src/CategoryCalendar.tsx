import { useEffect, useLayoutEffect, useRef, useState, type CSSProperties } from 'react';
import { api, ApiError, type Category, type MonthlyRecord } from './api';
import { categoryColor, daysInMonth, monthGrid, seoulToday } from './calendar';

const weekdays = ['일', '월', '화', '수', '목', '금', '토'];
function Photo({ record, onExpired }: { record: MonthlyRecord; onExpired: () => void }) {
  const [url, setUrl] = useState('');
  useEffect(() => {
    let active = true;
    api.photoUrl(record.id).then(result => { if (active) setUrl(result.imageUrl); }).catch(error => {
      if (active && error instanceof ApiError && error.status === 401) onExpired();
    });
    return () => { active = false; };
  }, [record.id, onExpired]);
  return url ? <img src={url} alt="" onError={() => setUrl('')} loading="lazy"/> : null;
}
function Month({ category, year, month, today, onExpired, onChoose }: { category: Category; year: number; month: number; today: string; onExpired: () => void; onChoose: () => void }) {
  const element = useRef<HTMLElement>(null);
  const [visible, setVisible] = useState(false);
  const [records, setRecords] = useState<MonthlyRecord[] | null>(null);
  const [error, setError] = useState(false);
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    const observer = new IntersectionObserver(entries => { if (entries.some(entry => entry.isIntersecting)) { setVisible(true); observer.disconnect(); } }, { rootMargin: '200px' });
    if (element.current) observer.observe(element.current);
    return () => observer.disconnect();
  }, []);
  useEffect(() => {
    if (!visible) return;
    let active = true;
    setRecords(null); setError(false);
    api.records(category.id, year, month).then(data => { if (active) setRecords(data); }).catch(cause => {
      if (!active) return;
      if (cause instanceof ApiError && cause.status === 401) onExpired(); else setError(true);
    });
    return () => { active = false; };
  }, [visible, category.id, year, month, attempt, onExpired]);
  const byDate = new Map(records?.map(record => [record.recordDate, record]));
  return <section ref={element} id={`month-${year}-${month}`} className="full-month" aria-label={`${year}년 ${month}월`}>
    <h2><button className="month-title" onClick={onChoose}>{year}년 {month}월⌄</button></h2>
    <div className="full-weekdays" aria-hidden="true">{weekdays.map(day => <span key={day}>{day}</span>)}</div>
    {error && <div className="month-error"><p role="alert">기록을 불러오지 못했어요.</p><button className="primary" onClick={() => setAttempt(v => v + 1)}>다시 시도</button></div>}
    {!records && !error && <span className="sr-only" role="status">{year}년 {month}월 기록을 불러오는 중이에요.</span>}
    <div className="full-grid">{monthGrid(year, month).map((day, index) => {
      if (!day) return <span key={index} className="full-day blank"/>;
      const date = `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
      const record = byDate.get(date);
      const content = <><span className="day-number">{day}</span>{record?.imageKey && visible && <Photo record={record} onExpired={onExpired}/>} {record && <span className="record-time">{record.recordTime.slice(0, 5)}</span>}</>;
      const className = `full-day ${record ? 'has-record' : ''} ${date === today ? 'is-today' : ''} ${date > today ? 'future' : ''}`;
      return records && !error && date <= today ? <a key={day} className={className} href={record ? `#/records/${record.id}` : `#/categories/${category.id}/new?date=${date}`} aria-label={`${date}, ${record ? `${record.recordTime.slice(0,5)} 기록 보기` : '기록 작성'}`}>{content}</a> : <span key={day} className={className} aria-label={`${date}${date > today ? ', 아직 오지 않은 날' : ', 조회 대기'}`}>{content}</span>;
    })}</div>
  </section>;
}
function Annual({ category, today, onExpired, onClose }: { category: Category; today: string; onExpired: () => void; onClose: () => void }) {
  const currentYear = Number(today.slice(0,4));
  const [year, setYear] = useState(currentYear);
  const [data, setData] = useState<Awaited<ReturnType<typeof api.annual>> | null>(null);
  const [error, setError] = useState(false);
  const [attempt, setAttempt] = useState(0);
  const dialog = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    const prior = document.activeElement as HTMLElement | null;
    const modal = dialog.current;
    modal?.showModal();
    const overflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => { modal?.close(); document.body.style.overflow = overflow; prior?.focus({preventScroll:true}); };
  }, []);
  useEffect(() => {
    let active = true; setData(null); setError(false);
    api.annual(category.id, year).then(result => { if (active) setData(result); }).catch(cause => {
      if (!active) return;
      if (cause instanceof ApiError && cause.status === 401) onExpired(); else setError(true);
    });
    return () => { active = false; };
  }, [category.id, year, onExpired, attempt]);
  const states = new Map(data?.map(cell => [`${cell.month}-${cell.day}`, cell.status]));
  return <dialog ref={dialog} className="annual-sheet" aria-labelledby="annual-title" onCancel={onClose} onClick={event => { if (event.target === event.currentTarget) { const box = event.currentTarget.getBoundingClientRect(); if (event.clientX < box.left || event.clientX > box.right || event.clientY < box.top || event.clientY > box.bottom) onClose(); } }}>
    <div className="sheet-content"><header className="annual-header"><div className="annual-year-control"><button onClick={() => setYear(year-1)} disabled={year <= currentYear-1} aria-label="이전 연도">‹</button><h2 id="annual-title">{year}년 연간 기록</h2><button onClick={() => setYear(year+1)} disabled={year >= currentYear} aria-label="다음 연도">›</button></div><button onClick={onClose} aria-label="연간 보기 닫기">×</button></header>
    {error ? <div className="empty-state"><p role="alert">연간 기록을 불러오지 못했어요.</p><button className="primary" onClick={() => setAttempt(v => v+1)}>다시 시도</button></div> : !data ? <p role="status">연간 기록을 불러오는 중이에요.</p> : <><div className="annual-grid"><span/>{Array.from({length:12}, (_,i) => <span key={`m${i}`}>{i+1}</span>)}{Array.from({length:31}, (_,i) => <div className="heat-row" key={i}><span>{i+1}</span>{Array.from({length:12}, (_,m) => { const absent = i+1 > daysInMonth(year,m+1); const status = states.get(`${m+1}-${i+1}`); return <span key={m} className={`heat-cell ${absent ? 'absent' : status === 'RECORDED' ? 'recorded' : `${year}-${String(m+1).padStart(2,'0')}-${String(i+1).padStart(2,'0')}` > today ? 'future' : 'missed'}`} aria-label={absent ? undefined : `${year}년 ${m+1}월 ${i+1}일 ${status === 'RECORDED' ? '기록함' : status === 'FUTURE' ? '아직 안 온 날' : '기록 안 함'}`}/>; })}</div>)}</div><p className="heat-legend"><span className="heat-cell recorded"/>기록함 <span className="heat-cell missed"/>기록 안 함 <span className="heat-cell future"/>아직 안 온 날</p></>}
    </div></dialog>;
}
export default function CategoryCalendar({ id, onExpired }: { id: number; onExpired: () => void }) {
  const [today, setToday] = useState(seoulToday());
  useEffect(() => {
    const update = () => setToday(seoulToday());
    const timer = window.setInterval(update, 30000);
    window.addEventListener('focus', update);
    return () => { window.clearInterval(timer); window.removeEventListener('focus', update); };
  }, []);
  const currentYear = Number(today.slice(0,4));
  const [categories, setCategories] = useState<Category[] | null>(null);
  const [error, setError] = useState(false);
  const [attempt, setAttempt] = useState(0);
  const [picker, setPicker] = useState(false);
  const [annual, setAnnual] = useState(false);
  const [selectedMonth, setSelectedMonth] = useState(today.slice(0,7));
  const title = useRef<HTMLHeadingElement>(null);
  const category = categories?.find(item => item.id === id);
  function jump(value: string, day?: string) {
    const [year, month] = value.split('-').map(Number);
    const target = day ? document.querySelector(`[aria-label^="${day},"]`) : null;
    (target || document.getElementById(`month-${year}-${month}`))?.scrollIntoView({ block: day ? 'center' : 'start' });
  }
  useEffect(() => {
    let active = true; setError(false); setCategories(null);
    api.categories().then(data => { if (active) setCategories(data.sort((a,b) => a.displayOrder-b.displayOrder)); }).catch(cause => {
      if (!active) return;
      if (cause instanceof ApiError && cause.status === 401) onExpired(); else setError(true);
    });
    return () => { active = false; };
  }, [onExpired, attempt]);
  useEffect(() => { setAnnual(false); setPicker(false); }, [id]);
  useLayoutEffect(() => { if (category) { title.current?.focus({preventScroll:true}); jump(selectedMonth); } }, [category?.id]);
  return <main className="app category-page" style={{'--category': categoryColor(category?.color || '#9872ea')} as CSSProperties}>
    <header className="calendar-header"><a className="calendar-back" href="#/" aria-label="나의 기록으로 돌아가기"><svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="m14 5-7 7 7 7"/></svg></a><h1 ref={title} tabIndex={-1}>{category?.name || '카테고리 캘린더'}</h1>{category && <div className="calendar-actions"><button onClick={() => setAnnual(true)}>연간 보기</button><button onClick={() => { setSelectedMonth(today.slice(0,7)); jump(today.slice(0,7), today); }}>오늘</button></div>}</header>
    {error ? <section className="empty-state"><p role="alert">카테고리를 불러오지 못했어요.</p><button className="primary" onClick={() => setAttempt(v => v+1)}>다시 시도</button></section> : !categories ? <p role="status">캘린더를 준비하고 있어요.</p> : !category ? <section className="empty-state"><h2>카테고리를 찾을 수 없어요</h2><a className="primary" href="#/">나의 기록으로 돌아가기</a></section> : <>
    <div className="month-jump"><button className="month-toggle" onClick={() => setPicker(!picker)} aria-expanded={picker}>월 선택⌄</button>{picker && <form onSubmit={event => { event.preventDefault(); jump(selectedMonth); setPicker(false); }}><label htmlFor="month-select">이동할 월</label><select id="month-select" value={selectedMonth} onChange={event => setSelectedMonth(event.target.value)}>{Array.from({length:24},(_,i) => { const year = currentYear-1+Math.floor(i/12); const month = i%12+1; const value = `${year}-${String(month).padStart(2,'0')}`; return <option key={value} value={value}>{year}년 {month}월</option>; })}</select><button className="primary">이동</button></form>}</div>
    <div className="month-stream">{Array.from({length:24},(_,i) => <Month key={`${id}-${i}`} category={category} year={currentYear-1+Math.floor(i/12)} month={i%12+1} today={today} onExpired={onExpired} onChoose={() => { setSelectedMonth(`${currentYear-1+Math.floor(i/12)}-${String(i%12+1).padStart(2,'0')}`); setPicker(true); document.querySelector('.month-jump')?.scrollIntoView({block:'center'}); }}/>)}</div>
    <nav className="category-tabs" aria-label="카테고리 전환">{categories.map(item => <a key={item.id} href={`#/categories/${item.id}`} aria-current={item.id === id ? 'page' : undefined} style={{'--category':categoryColor(item.color)} as CSSProperties}>{item.name}</a>)}</nav>
    {annual && <Annual category={category} today={today} onExpired={onExpired} onClose={() => setAnnual(false)}/>}
    </>}
  </main>;
}
