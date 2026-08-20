'use client';

import { useCallback, useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { api, type Account } from '@/lib/api';
import { clearAuth, getAuth, setAuth } from '@/lib/auth';

// Hydrating auth from localStorage after mount legitimately sets state in an effect.
/* eslint-disable react-hooks/set-state-in-effect */

/** Reads the stored session, optionally guarding a page, and exposes login/register/logout. */
export function useAuth(required = false) {
  const router = useRouter();
  const [account, setAccount] = useState<Account | null>(null);
  const [token, setToken] = useState<string | null>(null);
  const [ready, setReady] = useState(false);

  useEffect(() => {
    const session = getAuth();
    if (session) {
      setAccount(session.account);
      setToken(session.token);
    } else if (required) {
      router.replace('/login');
    }
    setReady(true);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Keep presence fresh while a session is open (server marks online by last-seen TTL).
  useEffect(() => {
    if (!token) {
      return;
    }
    api.heartbeat(token).catch(() => {});
    const id = setInterval(() => api.heartbeat(token).catch(() => {}), 30_000);
    return () => clearInterval(id);
  }, [token]);

  const apply = useCallback((session: { token: string; account: Account }) => {
    setAuth(session);
    setAccount(session.account);
    setToken(session.token);
    router.push('/');
  }, [router]);

  const login = useCallback(
    async (email: string, password: string) => apply(await api.login(email, password)),
    [apply],
  );

  const register = useCallback(
    async (username: string, email: string, password: string) =>
      apply(await api.register(username, email, password)),
    [apply],
  );

  const logout = useCallback(() => {
    clearAuth();
    setAccount(null);
    setToken(null);
    router.push('/login');
  }, [router]);

  return { account, token, ready, login, register, logout };
}
