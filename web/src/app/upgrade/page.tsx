"use client";

import { useRef, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/hooks/use-auth';
import { TopNav } from '@/components/ui/TopNav';

export default function UpgradePage() {
  const { account, ready, isGuest, upgrade, logout } = useAuth(true);
  const router = useRouter();
  const [username, setUsername] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const pending = useRef(false);

  if (!ready || !account) {
    return <main className="min-h-screen bg-slate-950" />;
  }

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (pending.current || !isGuest) return;
    pending.current = true;
    setError(null);
    setBusy(true);
    try {
      await upgrade(username.trim(), email.trim(), password);
      router.push('/');
    } catch (err) {
      setError((err as Error).message);
      setBusy(false);
    } finally { pending.current = false; }
  }

  return (
    <main className="min-h-screen bg-slate-950 text-white">
      <TopNav username={account.username} accountId={account.id} onLogout={logout} />

      <div className="mx-auto max-w-md px-6 py-12">
        <h1 className="mb-2 text-3xl font-black tracking-tighter">Create your account</h1>
        {isGuest ? (
          <p className="mb-6 text-sm text-zinc-500">Keep the games and ratings from your guest session.</p>
        ) : (
          <p className="mb-6 text-sm text-amber-400">You already have a registered account.</p>
        )}

        <form onSubmit={submit} className="flex flex-col gap-4 rounded-2xl border border-zinc-800 bg-zinc-900 p-6">
          <input
            aria-label="Username" required minLength={3} maxLength={20} pattern="[a-zA-Z0-9_]{3,20}" autoComplete="username" disabled={busy || !isGuest}
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            placeholder="Username"
            className="rounded-xl border border-zinc-700 bg-zinc-950 px-4 py-2.5 text-white placeholder-zinc-600 outline-none focus:border-blue-500"
          />
          <input
            type="email"
            aria-label="Email" required autoComplete="email" disabled={busy || !isGuest}
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder="Email"
            className="rounded-xl border border-zinc-700 bg-zinc-950 px-4 py-2.5 text-white placeholder-zinc-600 outline-none focus:border-blue-500"
          />
          <input
            type="password"
            aria-label="Password" required minLength={6} autoComplete="new-password" disabled={busy || !isGuest}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            placeholder="Password"
            className="rounded-xl border border-zinc-700 bg-zinc-950 px-4 py-2.5 text-white placeholder-zinc-600 outline-none focus:border-blue-500"
          />
          {error && <p role="alert" className="text-sm font-medium text-rose-400">{error}</p>}
          <button
            type="submit"
            disabled={busy || !isGuest}
            className="rounded-xl bg-blue-600 px-6 py-2.5 font-bold text-white transition-colors hover:bg-blue-500 disabled:opacity-60"
          >
            {busy ? 'Saving…' : 'Create account'}
          </button>
        </form>
      </div>
    </main>
  );
}
