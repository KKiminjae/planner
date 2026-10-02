import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render, screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import App from './App';

const category = { id: 1, name: '아침기록', color: '#9470e8', displayOrder: 1, isPrivate: false };
function response(body: unknown, status = 200, headers = {}) { return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json', ...headers } }); }
beforeEach(() => {
  window.location.hash = '#/'; vi.stubGlobal('scrollTo', vi.fn());
  vi.stubGlobal('IntersectionObserver', class { observe() {} disconnect() {} });
  Object.defineProperty(HTMLElement.prototype, 'scrollIntoView', { configurable: true, value: vi.fn() });
});
afterEach(() => { cleanup(); vi.unstubAllGlobals(); });

describe('main screen with API', () => {
  it('loads sorted categories, record counts and navigates back from a future screen', async () => {
    vi.stubGlobal('fetch', vi.fn(async (path: string) => {
      if (path.endsWith('/me')) return response({ ownerId: 1, username: 'owner' });
      if (path === '/api/categories') return response([{ ...category, id: 2, name: '운동', displayOrder: 2 }, category]);
      const query = new URL(path, 'http://localhost').searchParams;
      return response([{ id: 1, recordDate: `${query.get('year')}-${query.get('month')!.padStart(2, '0')}-01`, recordTime: '08:30', imageKey: null }]);
    }));
    render(<App/>);
    await screen.findByRole('link', { name: /아침기록.*1일 기록/ });
    const cards = screen.getAllByRole('link', { name: /일 기록/ });
    expect(cards[0].textContent).toContain('아침기록');
    fireEvent.click(screen.getByRole('link', { name: '모아보기' }));
    window.dispatchEvent(new HashChangeEvent('hashchange'));
    await screen.findByRole('heading', { name: '통합 캘린더' });
    await userEvent.click(screen.getByRole('link', { name: '나의 기록으로 돌아가기' }));
    window.dispatchEvent(new HashChangeEvent('hashchange'));
    await screen.findByRole('heading', { name: '나의 기록' });
  });
  it('shows an empty state instead of inventing categories', async () => {
    vi.stubGlobal('fetch', vi.fn(async (path: string) => response(path.endsWith('/me') ? { ownerId: 1 } : [])));
    render(<App/>);
    await screen.findByText('첫 기록을 기다리고 있어요');
  });
  it('lets one failed card retry while keeping successful cards visible', async () => {
    let attempts = 0;
    vi.stubGlobal('fetch', vi.fn(async (path: string) => {
      if (path.endsWith('/me')) return response({ ownerId: 1 });
      if (path === '/api/categories') return response([category, { ...category, id: 2, name: '운동' }]);
      if (path.includes('/1/records') && attempts++ === 0) return response({}, 500);
      return response([]);
    }));
    render(<App/>);
    await screen.findByRole('alert');
    expect(screen.getByRole('link', { name: /운동/ })).toBeTruthy();
    await userEvent.click(screen.getByRole('button', { name: '다시 시도' }));
    await screen.findByRole('link', { name: /아침기록.*0일 기록/ });
  });
  it('returns to login when record requests lose their session', async () => {
    vi.stubGlobal('fetch', vi.fn(async (path: string) => {
      if (path.endsWith('/me')) return response({ ownerId: 1 });
      if (path === '/api/categories') return response([category]);
      return response({}, 401);
    }));
    render(<App/>);
    await screen.findByText('세션이 만료됐어요. 다시 로그인해 주세요.');
  });
  it('uses form login with CSRF, handles wrong credentials and refreshes CSRF after success', async () => {
    let loggedIn = false;
    const fetchMock = vi.fn(async (path: string, init?: RequestInit) => {
      if (path.endsWith('/me')) return response({}, 401);
      if (path.endsWith('/csrf')) return response({ headerName: 'X-CSRF-TOKEN', token: 'test-token' });
      if (path.endsWith('/login')) {
        expect(init?.headers).toEqual({ 'Content-Type': 'application/x-www-form-urlencoded', 'X-CSRF-TOKEN': 'test-token' });
        loggedIn = String(init?.body).includes('password=correct');
        return response({}, loggedIn ? 200 : 401);
      }
      return response([]);
    });
    vi.stubGlobal('fetch', fetchMock);
    render(<App/>);
    const password = await screen.findByLabelText('비밀번호');
    await userEvent.type(password, 'wrong');
    await userEvent.click(screen.getByRole('button', { name: '내 기록 보러 가기' }));
    await screen.findByText('아이디와 비밀번호를 확인해 주세요.');
    await userEvent.clear(password);
    await userEvent.type(password, 'correct');
    await userEvent.click(screen.getByRole('button', { name: '내 기록 보러 가기' }));
    await screen.findByText('첫 기록을 기다리고 있어요');
    expect(fetchMock.mock.calls.filter(([path]) => path.endsWith('/csrf'))).toHaveLength(3);
  });
  it('disables login for the server-provided rate-limit delay', async () => {
    vi.stubGlobal('fetch', vi.fn(async (path: string) => {
      if (path.endsWith('/me')) return response({}, 401);
      if (path.endsWith('/csrf')) return response({ headerName: 'X-CSRF-TOKEN', token: 'test-token' });
      return response({}, 429, { 'Retry-After': '25' });
    }));
    render(<App/>);
    await userEvent.type(await screen.findByLabelText('비밀번호'), 'test');
    await userEvent.click(screen.getByRole('button', { name: '내 기록 보러 가기' }));
    await waitFor(() => expect((screen.getByRole('button', { name: /초 후 다시 시도/ }) as HTMLButtonElement).disabled).toBe(true));
  });
});
