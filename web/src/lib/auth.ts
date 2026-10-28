// Durable session storage. The token is the account's bearer credential for the API and the live
// connection (via a fresh ticket).

import { ApiError, type Session } from './api';

const KEY = 'chessoc-auth';
export const AUTH_CHANGED = 'chessoc-auth-changed';

export type StoredSession = Session & { expired?: boolean };

export function safeReturnTo(value: string | null): string {
  if (!value?.startsWith('/') || value.startsWith('//') || value.includes('\\')
    || [...value].some((char) => char.charCodeAt(0) <= 32 || char.charCodeAt(0) === 127)) return '/';
  try {
    const url = new URL(value, 'https://chessoc.invalid');
    if (url.origin !== 'https://chessoc.invalid' || url.pathname.replace(/\/+$/, '') === '/login') return '/';
    return `${url.pathname}${url.search}${url.hash}`;
  } catch {
    return '/';
  }
}

export function getLoginPath(returnTo: string, expired = false): string {
  const query = new URLSearchParams({ next: safeReturnTo(returnTo) });
  if (expired) query.set('reason', 'session-expired');
  return `/login?${query}`;
}

export function getAuth(): StoredSession | null {
  if (typeof window === 'undefined') {
    return null;
  }
  try {
    const raw = window.localStorage.getItem(KEY);
    const session = raw ? JSON.parse(raw) as StoredSession : null;
    return session && typeof session.token === 'string' && session.token.length > 0
      && typeof session.account?.id === 'string' && typeof session.account.username === 'string'
      ? session : null;
  } catch {
    return null;
  }
}

export function setAuth(session: Session): void {
  window.localStorage.setItem(KEY, JSON.stringify(session));
  window.dispatchEvent(new Event(AUTH_CHANGED));
}

export function clearAuth(): void {
  window.localStorage.removeItem(KEY);
  window.dispatchEvent(new Event(AUTH_CHANGED));
}

export function isSessionError(error: unknown): boolean {
  if (error instanceof ApiError) return error.status === 401;
  // Legacy errors carry only messages. Do not confuse credential errors with rejected sessions.
  return error instanceof Error && [
    'missing bearer token', 'token expired', 'malformed token', 'bad signature',
    'malformed payload', 'bad token encoding',
  ].includes(error.message);
}

export function expireAuth(token: string): void {
  const session = getAuth();
  if (!session || session.token !== token || session.expired) return;
  // Keep the rejected identity until explicit sign-in/logout; room entry must not invent a guest.
  window.localStorage.setItem(KEY, JSON.stringify({ ...session, expired: true }));
  window.dispatchEvent(new Event(AUTH_CHANGED));
}
