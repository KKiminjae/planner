import { useEffect, useLayoutEffect, useRef, useState, type CSSProperties, type ReactNode } from 'react';
import { api, ApiError, type DailyCategory, type DailyRecord, type IntegratedDay } from './api';
import { categoryColor, monthGrid, seoulToday } from './calendar';

function useToday() {
  const [today, setToday] = useState(seoulToday());
  useEffect(() => {
    const update = () => setToday(seoulToday());
    const timer = window.setInterval(update, 30000);
    window.addEventListener('focus', update);
    return () => { window.clearInterval(timer); window.removeEventListener('focus', update); };
  }, []);
  return today;
}
function BackIcon() { return <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="m14 5-7 7 7 7"/></svg>; }
function Header({ title, href, backLabel, action }: { title: string; href: string; backLabel: string; action?: ReactNode }) {
  const heading = useRef<HTMLHeadingElement>(null);
  useLayoutEffect(() => { heading.current?.focus({preventScroll:true}); }, []);
  return <header className="collection-header"><a className="calendar-back" href={href} aria-label={backLabel}><BackIcon/></a><h1 ref={heading} tabIndex={-1}>{title}</h1><div className="collection-header-action">{action}</div></header>;
}
function monthValue(year: number, month: number) { return `${year}-${String(month).padStart(2,'0')}`; }
function calendarMonths(year: number) { return Array.from({length:24},(_,i) => ({ year: year-1+Math.floor(i/12), month: i%12+1 })); }
function allowedDate(date: string, today: string) {
  return /^\d{4}-\d{2}-\d{2}$/.test(date) && !Number.isNaN(Date.parse(date)) && new Date(`${date}T12:00:00Z`).toISOString().slice(0,10) === date && date >= `${Number(today.slice(0,4))-1}-01-01` && date <= today;
}
function IntegratedMonth({ year, month, today, onExpired, onChoose }: { year: number; month: number; today: string; onExpired: () => void; onChoose: () => void }) {
  const element = useRef<HTMLElement>(null);
  const [visible, setVisible] = useState(false);
  const [data, setData] = useState<IntegratedDay[] | null>(null);
  const [error, setError] = useState(false);
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    const observer = new IntersectionObserver(entries => { if (entries.some(entry => entry.isIntersecting)) { setVisible(true); observer.disconnect(); } }, {rootMargin:'200px'});
    if (element.current) observer.observe(element.current);
    return () => observer.disconnect();
  }, []);
  useEffect(() => {
    if (!visible) return;
    let active = true; setData(null); setError(false);
    api.integrated(year, month).then(result => { if (active) setData(result); }).catch(cause => {
      if (!active) return;
      if (cause instanceof ApiError && cause.status === 401) onExpired(); else setError(true);
    });
    return () => { active = false; };
  }, [visible, year, month, attempt, onExpired]);
  const byDate = new Map(data?.map(day => [day.date, day]));
  return <section ref={element} id={`integrated-${year}-${month}`} className="integrated-month" aria-label={`${year}년 ${month}월 통합 기록`}>
    <h2><button className="month-title" onClick={onChoose}>{year}년 {month}월</button></h2>
    <div className="full-weekdays" aria-hidden="true">{['월','화','수','목','금','토','일'].map(day => <span key={day}>{day}</span>)}</div>
    {error && <div className="month-error"><p role="alert">월간 기록을 불러오지 못했어요.</p><button className="primary" onClick={() => setAttempt(v => v+1)}>다시 시도</button></div>}
    {!data && !error && <p className="sr-only" role="status">{year}년 {month}월 기록을 불러오는 중이에요.</p>}
    <div className="integrated-grid">{monthGrid(year,month).map((day,i) => {
      if (!day) return <span className="integrated-day blank" key={i}/>;
      const date = `${monthValue(year,month)}-${String(day).padStart(2,'0')}`;
      const summary = byDate.get(date);
      const className = `integrated-day ${date === today ? 'is-today' : ''} ${date > today ? 'future' : ''} ${i%7 === 0 ? 'sunday' : i%7 === 6 ? 'saturday' : ''}`;
      const content = <><span>{day}</span>{summary && summary.completedCount > 0 && date <= today && <span className={`completion-badge ${summary.totalCount > 0 && summary.completedCount === summary.totalCount ? 'complete' : ''}`}>{summary.completedCount}/{summary.totalCount}</span>}</>;
      return date <= today ? <a key={i} className={className} href={`#/daily/${date}`} aria-label={`${date}${summary ? `, ${summary.completedCount}/${summary.totalCount} 카테고리 완료` : ', 일간 기록 보기'}`}>{content}</a> : <span key={i} className={className} aria-label={`${date}, 아직 오지 않은 날`}>{content}</span>;
    })}</div>
  </section>;
}
export function IntegratedCalendar({ onExpired, initialMonth }: { onExpired: () => void; initialMonth?: string }) {
  const today = useToday();
  const year = Number(today.slice(0,4));
  const validInitial = initialMonth && calendarMonths(year).some(item => monthValue(item.year,item.month) === initialMonth) ? initialMonth : today.slice(0,7);
  const [selected, setSelected] = useState(validInitial);
  const [picker, setPicker] = useState(false);
  function jump(value: string, day?: string) {
    const [y,m] = value.split('-').map(Number);
    const cell = day ? document.querySelector(`.integrated-day[aria-label^="${day},"]`) : null;
    (cell || document.getElementById(`integrated-${y}-${m}`))?.scrollIntoView({block:day ? 'center' : 'start'});
  }
  useLayoutEffect(() => { jump(validInitial); }, [validInitial]);
  return <main className="app integrated-page"><Header title="통합 캘린더" href="#/" backLabel="나의 기록으로 돌아가기" action={<button className="pink-button" onClick={() => { setSelected(today.slice(0,7)); jump(today.slice(0,7),today); }}>오늘</button>}/>
    <div className="month-jump"><button className="month-toggle" onClick={() => setPicker(!picker)} aria-expanded={picker}>월 선택⌄</button>{picker && <form onSubmit={event => { event.preventDefault(); jump(selected); setPicker(false); }}><label htmlFor="integrated-month-select">이동할 월</label><select id="integrated-month-select" value={selected} onChange={event => setSelected(event.target.value)}>{calendarMonths(year).map(item => <option key={monthValue(item.year,item.month)} value={monthValue(item.year,item.month)}>{item.year}년 {item.month}월</option>)}</select><button className="primary">이동</button></form>}</div>
    <div className="month-stream">{calendarMonths(year).map(item => <IntegratedMonth key={monthValue(item.year,item.month)} {...item} today={today} onExpired={onExpired} onChoose={() => { setSelected(monthValue(item.year,item.month)); setPicker(true); document.querySelector('.month-jump')?.scrollIntoView({block:'center'}); }}/>)}</div>
  </main>;
}
function RecordPhoto({ record, onExpired, grid }: { record: DailyRecord; onExpired: () => void; grid: boolean }) {
  const [url,setUrl] = useState('');
  const [failed,setFailed] = useState(false);
  useEffect(() => {
    let active = true;
    api.photoUrl(record.id).then(data => { if (active) setUrl(data.imageUrl); }).catch(cause => {
      if (!active) return;
      if (cause instanceof ApiError && cause.status === 401) onExpired(); else setFailed(true);
    });
    return () => { active = false; };
  }, [record.id,onExpired]);
  if (failed) return grid ? <span className="daily-photo-status">사진을 표시하지 못했어요</span> : <p className="daily-photo-status">사진을 불러오지 못했어요.</p>;
  return url ? <img src={url} alt={`${record.categoryName} 기록 사진`} onError={() => setFailed(true)} loading="lazy"/> : <span className="daily-photo-status" role="status">사진을 불러오는 중이에요.</span>;
}
function dayShift(date: string, amount: number) { const day = new Date(`${date}T12:00:00Z`); day.setUTCDate(day.getUTCDate()+amount); return day.toISOString().slice(0,10); }
export function DailyCollection({ date, feed, onExpired }: { date: string; feed: boolean; onExpired: () => void }) {
  const today = useToday();
  const [records,setRecords] = useState<DailyRecord[] | null>(null);
  const [categories,setCategories] = useState<DailyCategory[] | null>(null);
  const [error,setError] = useState('');
  const [attempt,setAttempt] = useState(0);
  const [picker,setPicker] = useState(false);
  const [selected,setSelected] = useState(date);
  const valid = allowedDate(date,today);
  useEffect(() => {
    if (!valid) return;
    let active = true; setRecords(null); setCategories(null); setError('');
    Promise.all([api.dailyRecords(date), api.dailyCategories(date)]).then(([data, items]) => {
      if (!active) return;
      setRecords([...data].sort((a,b) => a.recordTime.localeCompare(b.recordTime) || a.id-b.id));
      setCategories(items.filter(item => !item.isDeleted).sort((a,b) => a.displayOrder-b.displayOrder || a.id-b.id));
    }).catch(cause => {
      if (!active) return;
      if (cause instanceof ApiError && cause.status === 401) onExpired(); else setError('이날의 카테고리와 기록을 불러오지 못했어요.');
    });
    return () => { active = false; };
  }, [date,valid,onExpired,attempt]);
  const title = feed ? '일간 피드' : '모아보기';
  const backHref = feed ? `#/daily/${date}` : `#/calendar?month=${date.slice(0,7)}`;
  function navigate(value: string) { if (allowedDate(value,today)) window.location.hash = `#/daily/${value}${feed ? '/feed' : ''}`; }
  return <main className="app daily-page"><Header title={title} href={valid ? backHref : '#/calendar'} backLabel={feed ? '모아보기로 돌아가기' : '통합 캘린더로 돌아가기'} action={!feed && valid ? <a className="pink-button" href={`#/daily/${date}/feed`}>피드보기</a> : undefined}/>
    {!valid ? <section className="empty-state"><p role="alert">캘린더에서 최근 2년 이내의 날짜를 선택해 주세요.</p><a className="primary" href="#/calendar">통합 캘린더로 돌아가기</a></section> : <>
    <div className="daily-date-control">{feed && <button disabled={!allowedDate(dayShift(date,-1),today)} aria-label="이전 날짜" onClick={() => navigate(dayShift(date,-1))}>‹</button>}<button className="daily-date-toggle" aria-expanded={picker} onClick={() => setPicker(!picker)}><time dateTime={date}>{date.replaceAll('-','.')}</time>{!feed && '⌄'}</button>{feed && <button disabled={!allowedDate(dayShift(date,1),today)} aria-label="다음 날짜" onClick={() => navigate(dayShift(date,1))}>›</button>}</div>
    {picker && <form className="daily-date-picker" onSubmit={event => { event.preventDefault(); navigate(selected); setPicker(false); }}><label htmlFor="daily-date">이동할 날짜</label><input id="daily-date" type="date" min={`${Number(today.slice(0,4))-1}-01-01`} max={today} value={selected} onChange={event => setSelected(event.target.value)} required/><button className="primary">이동</button></form>}
    {error ? <section className="empty-state"><p role="alert">{error}</p><button className="primary" onClick={() => setAttempt(v => v+1)}>다시 시도</button></section> : !records || !categories ? <p className="muted" role="status">이날의 기록을 불러오는 중이에요.</p> : (feed ? !records.length : !categories.length) ? <section className="empty-state"><span className="empty-symbol" aria-hidden="true">✦</span><h2>{feed ? '이날의 기록이 없어요' : '이날의 카테고리가 없어요'}</h2><p>다른 날짜를 살펴보세요.</p><a className="primary" href={backHref}>캘린더로 돌아가기</a></section> : feed ? <div className="daily-feed" aria-label="시간순 일간 기록">{records.map(record => {
      const color = categoryColor(categories.find(item => item.id === record.categoryId)?.color || '#a996ca');
      return <a className={`daily-feed-card ${record.imageKey ? 'with-photo' : ''}`} key={record.id} href={`#/records/${record.id}?from=${encodeURIComponent(`/daily/${date}/feed`)}`} style={{'--category':color} as CSSProperties} aria-label={`${record.categoryName}, ${record.recordTime.slice(0,5)} 기록 보기`}>
        {record.imageKey && <div className="feed-photo"><RecordPhoto record={record} onExpired={onExpired} grid={false}/></div>}<div className="feed-content"><header><h2>{record.categoryName}</h2><time>{record.recordTime.slice(0,5)}</time></header><p>{record.memo}</p></div>
      </a>;
    })}</div> : <div className="daily-grid" aria-label="일간 기록 카드">{categories.map(category => {
      const record = records.find(item => item.categoryId === category.id);
      const style = {'--category':categoryColor(category.color)} as CSSProperties;
      if (!record) {
        const content = <><h2>{category.name}</h2><div className="daily-record-image empty"><span className="empty-record-symbol" aria-hidden="true">+</span><span className="empty-record-label">기록 없음</span></div></>;
        return <a className="daily-record-card" key={category.id} style={style} href={`#/categories/${category.id}/new?date=${date}&from=${encodeURIComponent(`/daily/${date}`)}`} aria-label={`${category.name}, 기록 없음, 기록 작성`}>{content}</a>;
      }
      return <a className="daily-record-card" key={category.id} href={`#/records/${record.id}?from=${encodeURIComponent(`/daily/${date}`)}`} style={style} aria-label={`${category.name}, ${record.recordTime.slice(0,5)} 기록 보기`}><h2>{category.name}</h2><div className={`daily-record-image ${record.imageKey ? 'has-photo' : ''}`}>{record.imageKey && <RecordPhoto record={record} onExpired={onExpired} grid={true}/>}<time>{record.recordTime.slice(0,5)}</time></div></a>;
    })}</div>}

    </>}
  </main>;
}
