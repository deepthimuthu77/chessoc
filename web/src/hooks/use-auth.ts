'use client';

import { useCallback, useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { api, type Account } from '@/lib/api';
import { AUTH_CHANGED, clearAuth, expireAuth, getAuth, getLoginPath, isSessionError, safeReturnTo, setAuth } from '@/lib/auth';

// Hydrating auth from localStorage after mount legitimately sets state in an effect.
/* eslint-disable react-hooks/set-state-in-effect */

/** Reads the stored session, optionally guarding a page, and exposes login/register/logout. */
export function useAuth(required = false) {
  const router = useRouter();
  const [account, setAccount] = useState<Account | null>(null);
  const [token, setToken] = useState<string | null>(null);
  const [ready, setReady] = useState(false);
  const [sessionExpired, setSessionExpired] = useState(false);
  const [authError, setAuthError] = useState<string | null>(null);

  useEffect(() => {
    const sync = () => {
      const session = getAuth();
      setAccount(session && !session.expired ? session.account : null);
      setToken(session && !session.expired ? session.token : null);
      setSessionExpired(!!session?.expired);
      setAuthError(null);
      setReady(true);
      if (session?.expired || (!session && required)) {
        if (window.location.pathname !== '/login') {
          router.replace(getLoginPath(
            `${window.location.pathname}${window.location.search}${window.location.hash}`,
            !!session?.expired,
          ));
        }
      }
    };
    const onStorage = (event: StorageEvent) => {
      if (event.key === 'chessoc-auth' || event.key === null) sync();
    };
    const onExpired = (event: Event) => {
      const failedToken = (event as CustomEvent<{ token?: unknown }>).detail?.token;
      if (typeof failedToken === 'string') expireAuth(failedToken);
    };
    sync();
    window.addEventListener(AUTH_CHANGED, sync);
    window.addEventListener('storage', onStorage);
    window.addEventListener('chessoc-session-expired', onExpired);
    return () => {
      window.removeEventListener(AUTH_CHANGED, sync);
      window.removeEventListener('storage', onStorage);
      window.removeEventListener('chessoc-session-expired', onExpired);
    };
  }, [required, router]);

  const handleAuthError = useCallback((error: unknown, failedToken = token) => {
    if (!failedToken || !isSessionError(error)) return false;
    expireAuth(failedToken);
    return true;
  }, [token]);

  const retryAuth = useCallback(async () => {
    if (!token) return;
    try {
      await api.heartbeat(token);
      if (getAuth()?.token === token) setAuthError(null);
    } catch (error) {
      if (getAuth()?.token !== token) return;
      if (!handleAuthError(error, token)) {
        setAuthError('Could not refresh your session. Check your connection and retry.');
      }
    }
  }, [token, handleAuthError]);

  // Keep presence fresh while a session is open (server marks online by last-seen TTL).
  useEffect(() => {
    if (!token) {
      return;
    }
    void retryAuth();
    const id = setInterval(() => void retryAuth(), 30_000);
    return () => clearInterval(id);
  }, [token, retryAuth]);

  const apply = useCallback((session: { token: string; account: Account }, previousToken: string | null) => {
    if ((getAuth()?.token ?? null) !== previousToken) {
      throw new Error('Your session changed in another tab. Please try again.');
    }
    setAuth(session);
    setAccount(session.account);
    setToken(session.token);
    const next = window.location.pathname === '/login'
      ? new URLSearchParams(window.location.search).get('next') : null;
    router.replace(safeReturnTo(next));
  }, [router]);

  const login = useCallback(
    async (email: string, password: string) => {
      const previousToken = getAuth()?.token ?? null;
      apply(await api.login(email, password), previousToken);
    },
    [apply],
  );

  const register = useCallback(
    async (username: string, email: string, password: string) => {
      const previousToken = getAuth()?.token ?? null;
      apply(await api.register(username, email, password), previousToken);
    },
    [apply],
  );

  const playAsGuest = useCallback(async () => {
    const session = getAuth();
    if (session && !session.expired && session.account.id.startsWith('guest-')) {
      await api.me(session.token);
      apply(session, session.token);
      return;
    }
    apply(await api.guest(), session?.token ?? null);
  }, [apply]);

  const upgrade = useCallback(
    async (username: string, email: string, password: string) => {
      const current = token ?? getAuth()?.token;
      if (!current) {
        throw new Error('no guest session');
      }
      try {
        apply(await api.upgrade(username, email, password, current), current);
      } catch (error) {
        handleAuthError(error, current);
        throw error;
      }
    },
    [apply, token, handleAuthError],
  );

  const isGuest = !!account && account.id.startsWith('guest-');

  const logout = useCallback(() => {
    if (getAuth()?.token !== token) return;
    clearAuth();
    setAccount(null);
    setToken(null);
    router.push('/login');
  }, [router, token]);

  return { account, token, ready, isGuest, sessionExpired, authError, retryAuth, handleAuthError,
    login, register, playAsGuest, upgrade, logout };
}
