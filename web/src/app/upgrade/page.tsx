"use client";

import { useRef, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/hooks/use-auth';
import { TopNav } from '@/components/ui/TopNav';
import { PageHeading, JourneyNav } from '@/components/ui/PageHeading';
import { Icon } from '@/components/ui/Icon';

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
    <main className="min-h-screen app-background text-white">
      <TopNav username={account.username} accountId={account.id} onLogout={logout} />

      <div id="main-content" tabIndex={-1} className="page-wrap page-wrap-narrow page-enter">
        <PageHeading eyebrow="A PLACE TO BELONG" title="Make yourself at home." description="Keep your guest progress, your games, and your place at the table." />
        {isGuest ? (
          <p className="mb-6 text-sm text-zinc-500">Keep the games and ratings from your guest session.</p>
        ) : (
          <p className="mb-6 text-sm text-amber-400">You already have a registered account.</p>
        )}

        <form onSubmit={submit} className="surface flex max-w-xl flex-col gap-4 p-6"><div className="mb-2 flex items-center gap-3"><span className="icon-well"><Icon name="knight" size={20} /></span><h2 className="text-sm font-medium">Your next chapter</h2></div>
          <label className="field-label" htmlFor="join-username">Username</label>
          <input
            id="join-username"
            aria-label="Username" required minLength={3} maxLength={20} pattern="[a-zA-Z0-9_]{3,20}" autoComplete="username" disabled={busy || !isGuest}
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            placeholder="Username"
            className="rounded-xl border border-zinc-700 bg-zinc-950 px-4 py-2.5 text-white placeholder-zinc-600 outline-none focus:border-accent-500"
          />
          <label className="field-label" htmlFor="join-email">Email</label>
          <input
            id="join-email"
            type="email"
            aria-label="Email" required autoComplete="email" disabled={busy || !isGuest}
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder="Email"
            className="rounded-xl border border-zinc-700 bg-zinc-950 px-4 py-2.5 text-white placeholder-zinc-600 outline-none focus:border-accent-500"
          />
          <label className="field-label" htmlFor="join-password">Password</label>
          <input
            id="join-password"
            type="password"
            aria-label="Password" required minLength={6} autoComplete="new-password" disabled={busy || !isGuest}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            placeholder="Password"
            className="rounded-xl border border-zinc-700 bg-zinc-950 px-4 py-2.5 text-white placeholder-zinc-600 outline-none focus:border-accent-500"
          />
          {error && <p role="alert" className="text-sm font-medium text-rose-400">{error}</p>}
          <button
            type="submit"
            disabled={busy || !isGuest}
            className="button-primary mt-2"
          >
            {busy ? 'Saving…' : 'Create account'}
          </button>
        </form>
        <JourneyNav />
      </div>
    </main>
  );
}
