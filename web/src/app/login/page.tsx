"use client";

import { useRef, useState } from 'react';
import { motion } from 'framer-motion';
import { useAuth } from '@/hooks/use-auth';
import { prefersReducedMotion } from '@/lib/settings';

export default function LoginPage() {
  const { account, ready, sessionExpired, login, register, playAsGuest, authError, retryAuth } = useAuth();
  const [mode, setMode] = useState<'login' | 'register'>('login');
  const [username, setUsername] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const pending = useRef(false);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (pending.current) return;
    pending.current = true;
    setError(null);
    setBusy(true);
    try {
      if (mode === 'login') {
        await login(email.trim(), password);
      } else {
        await register(username.trim(), email.trim(), password);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not sign in. Please try again.');
      setBusy(false);
    } finally {
      pending.current = false;
    }
  }

  return (
    <main className="flex min-h-screen items-center justify-center bg-slate-950 p-4">
      <motion.div
        initial={prefersReducedMotion() ? false : { opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.6, ease: 'easeOut' }}
        className="w-full max-w-md rounded-3xl border border-slate-800/50 bg-slate-900/40 p-10 shadow-[0_0_60px_rgba(0,0,0,0.5)] backdrop-blur-xl"
      >
        <h1 className="mb-1 text-center text-4xl font-black tracking-tighter bg-gradient-to-br from-white to-zinc-400 bg-clip-text text-transparent">
          Chessoc
        </h1>
        <p className="mb-8 text-center text-sm text-zinc-500">
          {mode === 'login' ? 'Sign in to play' : 'Create your account'}
        </p>
        {sessionExpired && <p role="alert" className="mb-4 text-sm text-amber-300">Your session expired. Sign in again to continue with your account. Starting a new guest session creates a different identity.</p>}
        {authError && <p role="alert" className="mb-4 text-sm text-rose-400">{authError} <button type="button" onClick={() => void retryAuth()} className="underline">Retry session</button></p>}

        <form onSubmit={submit} aria-busy={busy} className="flex flex-col gap-4">
          {mode === 'register' && (
            <Field label="Username" value={username} onChange={setUsername} placeholder="magnus" autoComplete="username" minLength={3} maxLength={20} pattern="[a-zA-Z0-9_]{3,20}" disabled={busy} />
          )}
          <Field label="Email" value={email} onChange={setEmail} type="email" placeholder="you@example.com" autoComplete={mode === 'login' ? 'username' : 'email'} disabled={busy} />
          <Field label="Password" value={password} onChange={setPassword} type="password" placeholder="••••••••" autoComplete={mode === 'login' ? 'current-password' : 'new-password'} minLength={mode === 'register' ? 6 : undefined} disabled={busy} />

          {error && <p role="alert" className="text-sm font-medium text-rose-400">{error}</p>}

          <button
            type="submit"
            disabled={busy || !ready}
            className="mt-2 rounded-xl bg-gradient-to-b from-[#5c8aab] to-[#4b7399] px-8 py-3 font-bold text-white shadow-[0_0_20px_rgba(75,115,153,0.4)] transition-all hover:from-[#6a9bc2] hover:to-[#5785b0] disabled:opacity-60"
          >
            {busy ? 'Please wait…' : mode === 'login' ? 'Sign in' : 'Create account'}
          </button>
        </form>

        <button
          type="button"
          disabled={busy}
          onClick={() => {
            setMode(mode === 'login' ? 'register' : 'login');
            setError(null);
          }}
          className="mt-6 w-full text-center text-sm text-zinc-500 transition-colors hover:text-zinc-300"
        >
          {mode === 'login' ? 'No account? Register' : 'Have an account? Sign in'}
        </button>

        <div className="my-5 flex items-center gap-3 text-xs text-zinc-600">
          <span className="h-px flex-1 bg-zinc-800" /> or <span className="h-px flex-1 bg-zinc-800" />
        </div>
        <button
          type="button"
          disabled={busy || !ready}
          onClick={async () => {
            if (pending.current) return;
            pending.current = true;
            setError(null);
            setBusy(true);
            try {
              await playAsGuest();
            } catch (err) {
              setError(err instanceof Error ? err.message : 'Could not start guest session. Please try again.');
              setBusy(false);
            } finally {
              pending.current = false;
            }
          }}
          className="w-full rounded-xl border border-zinc-700 px-8 py-3 font-bold text-zinc-200 transition-colors hover:bg-zinc-800 disabled:opacity-60"
        >
          {busy ? 'Please wait…' : sessionExpired ? 'Start a new guest session' : account?.id.startsWith('guest-') ? 'Resume guest session' : account ? 'Switch to a guest session' : 'Continue as guest'}
        </button>
      </motion.div>
    </main>
  );
}

function Field({
  label,
  value,
  onChange,
  type = 'text',
  placeholder,
  autoComplete,
  minLength,
  maxLength,
  pattern,
  disabled,
}: {
  label: string;
  value: string;
  onChange: (v: string) => void;
  type?: string;
  placeholder?: string;
  autoComplete?: string;
  minLength?: number;
  maxLength?: number;
  pattern?: string;
  disabled?: boolean;
}) {
  return (
    <label className="flex flex-col gap-1.5">
      <span className="text-xs font-bold uppercase tracking-wider text-zinc-500">{label}</span>
      <input
        type={type}
        name={label.toLowerCase()}
        required
        autoComplete={autoComplete}
        minLength={minLength}
        maxLength={maxLength}
        pattern={pattern}
        disabled={disabled}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder}
        className="rounded-xl border border-zinc-700 bg-zinc-900 px-4 py-3 text-white placeholder-zinc-600 outline-none transition-colors focus:border-blue-500"
      />
    </label>
  );
}
