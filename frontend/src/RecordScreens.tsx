import { useEffect, useRef, useState, type ChangeEvent, type FormEvent, type ReactNode } from 'react';
import { api, ApiError, type DailyRecord } from './api';
import { seoulToday } from './calendar';

function Back({ href, disabled = false, label = '카테고리 캘린더로 돌아가기' }: { href: string; disabled?: boolean; label?: string }) {
  return <a className="calendar-back" href={disabled ? undefined : href} aria-disabled={disabled || undefined} aria-label={label}><svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="m14 5-7 7 7 7"/></svg></a>;
}
function Header({ title, href, action, busy, backLabel }: { title: string; href: string; action?: ReactNode; busy?: boolean; backLabel?: string }) {
  const heading = useRef<HTMLHeadingElement>(null);
  useEffect(() => { heading.current?.focus({ preventScroll: true }); }, []);
  return <header className="record-header"><Back href={href} disabled={busy} label={backLabel}/><h1 ref={heading} tabIndex={-1}>{title}</h1><div className="record-header-action">{action}</div></header>;
}
function message(cause: unknown, fallback: string) {
  if (!(cause instanceof ApiError)) return fallback;
  if (cause.code === 'DUPLICATE_DAILY_RECORD') return '이 날짜에는 이미 기록이 있어요. 캘린더에서 기존 기록을 확인해 주세요.';
  if (cause.code === 'RECORD_BEFORE_CATEGORY_CREATION') return '카테고리를 만들기 전 날짜에는 기록할 수 없어요.';
  if (cause.status === 403) return '저장 권한을 확인하지 못했어요. 새로고침 후 다시 로그인해 주세요.';
  return cause.status >= 500 ? fallback : cause.message;
}
function useRecord(id: number, onExpired: () => void) {
  const [record, setRecord] = useState<DailyRecord | null>(null);
  const [error, setError] = useState('');
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    let active = true; setRecord(null); setError('');
    api.record(id).then(data => { if (active) setRecord(data); }).catch(cause => {
      if (!active) return;
      if (cause instanceof ApiError && cause.status === 401) onExpired(); else setError(message(cause, '기록을 불러오지 못했어요. 다시 시도해 주세요.'));
    });
    return () => { active = false; };
  }, [id, attempt, onExpired]);
  return { record, error, retry: () => setAttempt(v => v+1) };
}
function usePhoto(record: DailyRecord | null, onExpired: () => void) {
  const [url, setUrl] = useState('');
  const [error, setError] = useState(false);
  useEffect(() => {
    let active = true; setUrl(''); setError(false);
    if (record?.imageKey) api.photoUrl(record.id).then(data => { if (active) setUrl(data.imageUrl); }).catch(cause => {
      if (!active) return;
      if (cause instanceof ApiError && cause.status === 401) onExpired(); else setError(true);
    });
    return () => { active = false; };
  }, [record?.id, record?.imageKey, onExpired]);
  return { url, error, failed: () => { setUrl(''); setError(true); } };
}
function RecordLoad({ title, error, retry }: { title: string; error: string; retry: () => void }) {
  return <main className="app"><Header title={title} href="#/" backLabel="나의 기록으로 돌아가기"/>{error ? <section className="empty-state"><p role="alert">{error}</p><button className="primary" onClick={retry}>다시 시도</button></section> : <p role="status">기록을 불러오는 중이에요.</p>}</main>;
}

export function NewRecord({ categoryId, date, onExpired, returnTo }: { categoryId: number; date: string; onExpired: () => void; returnTo?: string }) {
  const [categoryName, setCategoryName] = useState<string | null>(null);
  const [error, setError] = useState('');
  const [attempt, setAttempt] = useState(0);
  const validDate = /^\d{4}-\d{2}-\d{2}$/.test(date) && !Number.isNaN(Date.parse(date)) && new Date(`${date}T12:00:00Z`).toISOString().slice(0,10) === date && date <= seoulToday();
  useEffect(() => {
    if (!validDate) return;
    let active = true; setError('');
    api.categories().then(data => {
      if (!active) return;
      const category = data.find(item => item.id === categoryId);
      if (category) setCategoryName(category.name); else setError('카테고리를 찾을 수 없어요.');
    }).catch(cause => {
      if (!active) return;
      if (cause instanceof ApiError && cause.status === 401) onExpired(); else setError('카테고리를 불러오지 못했어요. 다시 시도해 주세요.');
    });
    return () => { active = false; };
  }, [categoryId, validDate, onExpired, attempt]);
  if (!validDate) return <main className="app"><Header title="기록 작성" href={`#/categories/${categoryId}`}/><p role="alert">기록할 수 있는 날짜를 캘린더에서 선택해 주세요.</p></main>;
  if (!categoryName) return <RecordLoad title="기록 작성" error={error} retry={() => setAttempt(v => v+1)}/>;
  return <RecordForm categoryId={categoryId} categoryName={categoryName} date={date} returnTo={returnTo} onExpired={onExpired}/>;
}
export function EditRecord({ id, onExpired, returnTo }: { id: number; onExpired: () => void; returnTo?: string }) {
  const { record, error, retry } = useRecord(id, onExpired);
  if (!record) return <RecordLoad title="기록 수정" error={error} retry={retry}/>;
  return <RecordForm categoryId={record.categoryId} categoryName={record.categoryName} date={record.recordDate} record={record} returnTo={returnTo} onExpired={onExpired}/>;
}
function RecordForm({ categoryId, categoryName, date, record, onExpired, returnTo }: { categoryId: number; categoryName: string; date: string; record?: DailyRecord; onExpired: () => void; returnTo?: string }) {
  const initialTime = new Intl.DateTimeFormat('en-GB', { timeZone: 'Asia/Seoul', hour: '2-digit', minute: '2-digit', hourCycle: 'h23' }).format(new Date());
  const [time, setTime] = useState(record?.recordTime.slice(0,5) || initialTime);
  const [memo, setMemo] = useState(record?.memo || '');
  const [file, setFile] = useState<File | null>(null);
  const [preview, setPreview] = useState('');
  const [removeImage, setRemoveImage] = useState(false);
  const [imageError, setImageError] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [phase, setPhase] = useState('');
  const uploadedKey = useRef<string | null>(null);
  const saving = useRef(false);
  const input = useRef<HTMLInputElement>(null);
  const photo = usePhoto(record || null, onExpired);
  useEffect(() => {
    if (!file) { setPreview(''); return; }
    const url = URL.createObjectURL(file); setPreview(url);
    return () => URL.revokeObjectURL(url);
  }, [file]);
  function choose(event: ChangeEvent<HTMLInputElement>) {
    const selected = event.target.files?.[0]; event.target.value = '';
    if (!selected) return;
    setImageError('');
    if (!['image/jpeg', 'image/png'].includes(selected.type)) { setImageError('JPEG 또는 PNG 사진을 선택해 주세요.'); return; }
    if (selected.size > 5 * 1024 * 1024) { setImageError('사진은 5MB 이하로 선택해 주세요.'); return; }
    setFile(selected); uploadedKey.current = null; setRemoveImage(false);
  }
  async function save(event: FormEvent) {
    event.preventDefault();
    if (saving.current) return;
    if (!memo.trim() || !/^\d{2}:\d{2}$/.test(time)) { setError('기록 시간과 메모를 입력해 주세요.'); return; }
    saving.current = true; setBusy(true); setError('');
    let uploading = false;
    try {
      let imageKey: string | null = null;
      if (file) {
        setPhase('사진 업로드 중…');
        if (!uploadedKey.current) { uploading = true; uploadedKey.current = (await api.uploadPhoto(file)).imageKey; uploading = false; }
        imageKey = uploadedKey.current;
      }
      setPhase('저장 중…');
      const payload = { recordTime: time, memo: memo.trim(), imageKey, ...(record ? { removeImage: !file && removeImage } : {}) };
      const saved = record ? await api.updateRecord(record.id, payload) : await api.createRecord({ ...payload, categoryId, recordDate: date });
      window.location.hash = `#/records/${saved.id}${returnTo ? `?from=${encodeURIComponent(returnTo)}` : ''}`;
    } catch (cause) {
      if (cause instanceof ApiError && cause.status === 401) onExpired();
      else setError(cause instanceof ApiError && cause.status === 404 && uploading ? '사진 저장 기능을 사용할 수 없어요. 사진을 제거하고 저장하거나 연결을 확인해 주세요.' : message(cause, '저장하지 못했어요. 입력 내용은 유지됩니다. 다시 시도해 주세요.'));
    } finally { saving.current = false; setBusy(false); setPhase(''); }
  }
  const src = preview || (!removeImage ? photo.url : '');
  const hasPhoto = !!file || (!!record?.imageKey && !removeImage);
  return <main className="app record-page"><form onSubmit={save}>
    <Header title={record ? '기록 수정' : '기록 작성'} href={record ? `#/records/${record.id}${returnTo ? `?from=${encodeURIComponent(returnTo)}` : ''}` : returnTo ? `#${returnTo}` : `#/categories/${categoryId}`} backLabel={record ? '상세 기록으로 돌아가기' : returnTo ? '모아보기로 돌아가기' : undefined} busy={busy} action={<button className="record-save" disabled={busy} type="submit">{busy ? phase || '저장 중…' : '저장'}</button>}/>
    <p className="record-date"><time dateTime={date}>{date.replaceAll('-', '.')}</time></p><p className="record-category">{categoryName}</p>
    <div className="photo-picker">{src ? <img src={src} alt="선택한 기록 사진" onError={() => { if (preview) { setImageError('사진 미리보기를 표시할 수 없어요. 다른 사진을 선택해 주세요.'); } else photo.failed(); }}/>: <svg width="56" height="56" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" aria-hidden="true"><rect x="3" y="3" width="18" height="18" rx="3"/><circle cx="16" cy="8" r="1.5"/><path d="m3 17 6-7 5 6 3-3 4 5"/></svg>}
    <div className="photo-controls"><button type="button" className="record-save" onClick={() => input.current?.click()} disabled={busy}>{hasPhoto ? '사진 변경' : '사진 선택'}</button>{hasPhoto && <button type="button" className="photo-remove" disabled={busy} onClick={() => { setFile(null); uploadedKey.current = null; setRemoveImage(true); setImageError(''); }}>사진 제거</button>}</div></div>
    <input ref={input} className="sr-only" type="file" accept="image/jpeg,image/png" onChange={choose} aria-label="기록 사진 선택" disabled={busy}/>
    {photo.error && !file && !removeImage && <p className="muted" role="status">기존 사진을 표시하지 못했어요. 변경하지 않으면 그대로 유지됩니다.</p>}
    {imageError && <p className="error" role="alert">{imageError}</p>}
    <label className="record-label" htmlFor="record-time">기록 시간</label><input className="record-input" id="record-time" type="time" value={time} onChange={event => setTime(event.target.value)} required disabled={busy}/>
    <label className="record-label" htmlFor="record-memo">메모</label><textarea className="record-input record-memo" id="record-memo" placeholder="오늘의 기록을 남겨보세요." value={memo} onChange={event => setMemo(event.target.value)} required disabled={busy}/>
    {error && <p className="error" role="alert">{error}</p>}{busy && <p className="sr-only" role="status">{phase}</p>}
  </form></main>;
}
export function RecordDetail({ id, onExpired, returnTo }: { id: number; onExpired: () => void; returnTo?: string }) {
  const { record, error, retry } = useRecord(id, onExpired);
  const photo = usePhoto(record, onExpired);
  const [menu, setMenu] = useState(false);
  const [confirm, setConfirm] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState('');
  const deletingRef = useRef(false);
  const dialog = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    if (!confirm) return;
    const prior = document.activeElement as HTMLElement | null;
    const modal = dialog.current; modal?.showModal();
    return () => { modal?.close(); prior?.focus({preventScroll:true}); };
  }, [confirm]);
  async function remove() {
    if (!record || deletingRef.current) return;
    deletingRef.current = true; setDeleting(true); setDeleteError('');
    try { await api.deleteRecord(record.id); window.location.hash = returnTo ? `#${returnTo}` : `#/categories/${record.categoryId}`; }
    catch (cause) { if (cause instanceof ApiError && cause.status === 401) onExpired(); else setDeleteError(message(cause, '삭제하지 못했어요. 다시 시도해 주세요.')); }
    finally { deletingRef.current = false; setDeleting(false); }
  }
  if (!record) return <RecordLoad title="상세 기록" error={error} retry={retry}/>;
  return <main className="app record-page"><Header title="상세 기록" href={returnTo ? `#${returnTo}` : `#/categories/${record.categoryId}`} backLabel={returnTo ? returnTo.endsWith('/feed') ? '일간 피드로 돌아가기' : '모아보기로 돌아가기' : undefined} busy={deleting} action={<button className="record-menu-button" aria-label="기록 메뉴" aria-expanded={menu} onClick={() => setMenu(!menu)}>⋮</button>}/>
    {menu && <div className="record-menu"><a href={`#/records/${id}/edit${returnTo ? `?from=${encodeURIComponent(returnTo)}` : ''}`}>수정</a><button onClick={() => { setConfirm(true); setMenu(false); }}>삭제</button></div>}
    <p className="record-category">{record.categoryName} · <time dateTime={record.recordDate}>{record.recordDate.replaceAll('-', '.')}</time></p><h2 className="detail-time">{record.recordTime.slice(0,5)}</h2>
    {record.imageKey && (photo.url ? <div className="detail-photo"><img src={photo.url} alt={`${record.categoryName} 기록 사진`} onError={photo.failed}/><span>{record.recordTime.slice(0,5)}</span></div> : <p className="muted" role="status">{photo.error ? '사진을 불러오지 못했어요.' : '사진을 불러오는 중이에요.'}</p>)}
    <section className="detail-memo"><h2>메모</h2><p>{record.memo}</p></section>
    {confirm && <dialog ref={dialog} className="delete-dialog" aria-labelledby="delete-title" onCancel={event => { if (deleting) event.preventDefault(); else setConfirm(false); }}><h2 id="delete-title">기록을 삭제할까요?</h2><p>이 기록과 연결된 사진을 삭제합니다.<br/>삭제한 기록은 복구할 수 없어요.</p>{deleteError && <p className="error" role="alert">{deleteError}</p>}<div><button autoFocus className="primary" disabled={deleting} onClick={() => setConfirm(false)}>취소</button><button className="delete-button" disabled={deleting} onClick={remove}>{deleting ? '삭제 중…' : '삭제'}</button></div></dialog>}
  </main>;
}
