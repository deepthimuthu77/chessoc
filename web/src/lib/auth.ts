// Durable session storage. The token is the account's bearer credential for the API and the live
// connection (via a fresh ticket).

import type { Session } from './api';

const KEY = 'chessoc-auth';

export function getAuth(): Session | null {
  if (typeof window === 'undefined') {
    return null;
  }
  try {
    const raw = window.localStorage.getItem(KEY);
    return raw ? (JSON.parse(raw) as Session) : null;
  } catch {
    return null;
  }
}

export function setAuth(session: Session): void {
  window.localStorage.setItem(KEY, JSON.stringify(session));
}

export function clearAuth(): void {
  window.localStorage.removeItem(KEY);
}
