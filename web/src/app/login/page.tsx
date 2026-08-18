"use client";

import { useState } from 'react';
import { motion } from 'framer-motion';
import { useAuth } from '@/hooks/use-auth';

export default function LoginPage() {
  const { login, register } = useAuth();
  const [mode, setMode] = useState<'login' | 'register'>('login');
  const [username, setUsername] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try {
      if (mode === 'login') {
        await login(email, password);
      } else {
        await register(username, email, password);
      }
    } catch (err) {
      setError((err as Error).message);
      setBusy(false);
    }
  }

  return (
    <main className="flex min-h-screen items-center justify-center bg-slate-950 p-4">
      <motion.div
        initial={{ opacity: 0, y: 20 }}
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

        <form onSubmit={submit} className="flex flex-col gap-4">
          {mode === 'register' && (
            <Field label="Username" value={username} onChange={setUsername} placeholder="magnus" />
          )}
          <Field label="Email" value={email} onChange={setEmail} type="email" placeholder="you@example.com" />
          <Field label="Password" value={password} onChange={setPassword} type="password" placeholder="••••••••" />

          {error && <p className="text-sm font-medium text-rose-400">{error}</p>}

          <button
            type="submit"
            disabled={busy}
            className="mt-2 rounded-xl bg-gradient-to-b from-[#5c8aab] to-[#4b7399] px-8 py-3 font-bold text-white shadow-[0_0_20px_rgba(75,115,153,0.4)] transition-all hover:from-[#6a9bc2] hover:to-[#5785b0] disabled:opacity-60"
          >
            {busy ? 'Please wait…' : mode === 'login' ? 'Sign in' : 'Create account'}
          </button>
        </form>

        <button
          type="button"
          onClick={() => {
            setMode(mode === 'login' ? 'register' : 'login');
            setError(null);
          }}
          className="mt-6 w-full text-center text-sm text-zinc-500 transition-colors hover:text-zinc-300"
        >
          {mode === 'login' ? 'No account? Register' : 'Have an account? Sign in'}
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
}: {
  label: string;
  value: string;
  onChange: (v: string) => void;
  type?: string;
  placeholder?: string;
}) {
  return (
    <label className="flex flex-col gap-1.5">
      <span className="text-xs font-bold uppercase tracking-wider text-zinc-500">{label}</span>
      <input
        type={type}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder}
        className="rounded-xl border border-zinc-700 bg-zinc-900 px-4 py-3 text-white placeholder-zinc-600 outline-none transition-colors focus:border-blue-500"
      />
    </label>
  );
}
