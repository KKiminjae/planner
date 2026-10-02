export interface Category { id: number; name: string; color: string; displayOrder: number; isPrivate: boolean }
export interface DailyCategory extends Category { isDeleted: boolean }
export interface CategoryInput { name: string; color: string; isPrivate: boolean }
export interface MonthlyRecord { id: number; recordDate: string; recordTime: string; imageKey: string | null }
export interface DailyRecord extends MonthlyRecord { categoryId: number; categoryName: string; memo: string }
export interface IntegratedDay { date: string; completedCount: number; totalCount: number }
export interface RecordInput { recordTime: string; memo: string; imageKey?: string | null; removeImage?: boolean }
interface Csrf { headerName: string; token: string }

export class ApiError extends Error {
  constructor(public status: number, public code: string, message: string, public retryAfter = 12) { super(message); }
}

export async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const response = await fetch(path, { ...init, credentials: 'same-origin', cache: 'no-store' });
  if (!response.ok) {
    const body = await response.json().catch(() => ({}));
    throw new ApiError(response.status, body.code || '', body.message || '잠시 후 다시 시도해 주세요.', Number(response.headers.get('Retry-After')) || 12);
  }
  return response.status === 204 ? undefined as T : response.json();
}

async function write<T>(path: string, method: string, body?: object | FormData): Promise<T> {
  const csrf = await request<Csrf>('/api/auth/csrf');
  const multipart = body instanceof FormData;
  return request<T>(path, { method, headers: { [csrf.headerName]: csrf.token, ...(!multipart && body ? { 'Content-Type': 'application/json' } : {}) }, body: body ? multipart ? body : JSON.stringify(body) : undefined });
}

export const api = {
  me: () => request<{ ownerId: number; username: string }>('/api/auth/me'),
  categories: () => request<Category[]>('/api/categories'),
  dailyCategories: (date: string) => request<DailyCategory[]>(`/api/categories/daily?date=${date}`),
  createCategory: (input: CategoryInput) => write<Category>('/api/categories', 'POST', input),
  updateCategory: (id: number, input: CategoryInput) => write<Category>(`/api/categories/${id}`, 'PATCH', input),
  reorderCategories: (categoryIds: number[]) => write<void>('/api/categories/reorder', 'PATCH', { categoryIds }),
  deleteCategory: (id: number) => write<void>(`/api/categories/${id}`, 'DELETE'),
  records: (id: number, year: number, month: number) => request<MonthlyRecord[]>(`/api/categories/${id}/records?year=${year}&month=${month}`),
  annual: (id: number, year: number) => request<{ month: number; day: number; status: 'RECORDED' | 'MISSED' | 'FUTURE' | 'NO_DATE' }[]>(`/api/categories/${id}/annual?year=${year}`),
  photoUrl: (id: number) => request<{ imageUrl: string; expiresAt: string }>(`/api/records/${id}/photo-url`),
  integrated: (year: number, month: number) => request<IntegratedDay[]>(`/api/calendar/integrated?year=${year}&month=${month}`),
  dailyRecords: (date: string) => request<DailyRecord[]>(`/api/records?date=${date}`),
  record: (id: number) => request<DailyRecord>(`/api/records/${id}`),
  createRecord: (input: RecordInput & { categoryId: number; recordDate: string }) => write<DailyRecord>('/api/records', 'POST', input),
  updateRecord: (id: number, input: RecordInput) => write<DailyRecord>(`/api/records/${id}`, 'PATCH', input),
  deleteRecord: (id: number) => write<void>(`/api/records/${id}`, 'DELETE'),
  uploadPhoto: (file: File) => { const body = new FormData(); body.append('file', file); return write<{ imageKey: string }>('/api/photos', 'POST', body); },
  async login(username: string, password: string) {
    const csrf = await request<Csrf>('/api/auth/csrf');
    await request('/api/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded', [csrf.headerName]: csrf.token }, body: new URLSearchParams({ username, password }) });
    await request<Csrf>('/api/auth/csrf');
  },
};
