"use client";

import { useRef, useState } from 'react';
import { motion } from 'framer-motion';
import Link from 'next/link';
import { Icon } from '@/components/ui/Icon';
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
    <main className="app-background flex min-h-screen items-center justify-center p-5 sm:p-10"><div className="auth-grid">
      <aside className="auth-story"><Link href="/" className="wordmark"><Icon name="knight" size={28} />chessoc.</Link><div className="auth-intro"><p className="eyebrow mb-4">FOR THE LOVE OF THE GAME</p><h2 className="display-title">An open seat.<br />A world of possibilities.</h2><p className="page-description mt-5">The timeless game, with a few new ways to fall in love with it.</p></div><div className="auth-details space-y-5"><div className="flex gap-3"><Icon name="layers" className="text-accent-300" /><p className="text-xs leading-relaxed text-zinc-400">Chess, crazyhouse, and bughouse.<br /><span className="text-zinc-500">Your friends, your team, or the computer.</span></p></div><div className="flex gap-3"><Icon name="watch" className="text-accent-300" /><p className="text-xs leading-relaxed text-zinc-400">A live gallery, made personal.<br /><span className="text-zinc-500">Pin four games. Follow every move.</span></p></div><Link href="/watch" className="text-link">Look around the gallery<Icon name="arrow" size={15} /></Link></div></aside>
      <motion.div
        initial={prefersReducedMotion() ? false : { opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.25, ease: 'easeOut' }}
        className="auth-form min-w-0"
      >
        <p className="eyebrow mb-4">{mode === 'login' ? 'WELCOME BACK' : 'YOUR NEXT CHAPTER'}</p><h1 className="page-title">{mode === 'login' ? 'Take your seat.' : 'Make it your game.'}</h1><p className="page-description mb-7">{mode === 'login' ? 'Sign in, settle in, and make your next move.' : 'A name, an account, and a place to call your own.'}</p>
        {sessionExpired && <p role="alert" className="mb-4 text-sm text-amber-300">Your session expired. Sign in again to continue with your account. Starting a new guest session creates a different identity.</p>}
        {authError && <p role="alert" className="mb-4 text-sm text-rose-400">{authError} <button type="button" onClick={() => void retryAuth()} className="underline">Retry session</button></p>}

        <form onSubmit={submit} aria-busy={busy} className="flex flex-col gap-4">
          {mode === 'register' && (
            <Field label="Username" value={username} onChange={setUsername} placeholder="Choose your player name" autoComplete="username" minLength={3} maxLength={20} pattern="[a-zA-Z0-9_]{3,20}" disabled={busy} />
          )}
          <Field label="Email" value={email} onChange={setEmail} type="email" placeholder="you@example.com" autoComplete={mode === 'login' ? 'username' : 'email'} disabled={busy} />
          <Field label="Password" value={password} onChange={setPassword} type="password" placeholder="••••••••" autoComplete={mode === 'login' ? 'current-password' : 'new-password'} minLength={mode === 'register' ? 6 : undefined} disabled={busy} />

          {error && <p role="alert" className="text-sm font-medium text-rose-400">{error}</p>}

          <button
            type="submit"
            disabled={busy || !ready}
            className="button-primary mt-2"
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
          className="button-secondary w-full"
        >
          {busy ? 'Please wait…' : sessionExpired ? 'Start a new guest session' : account?.id.startsWith('guest-') ? 'Resume guest session' : account ? 'Switch to a guest session' : 'Continue as guest'}
        </button>
      </motion.div>
    </div></main>
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
      <span className="text-xs font-medium text-zinc-400">{label}</span>
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
        className="field"
      />
    </label>
  );
}
