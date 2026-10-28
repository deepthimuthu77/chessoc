"use client";

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { api, type LeaderboardRow } from '@/lib/api';
import { useAuth } from '@/hooks/use-auth';
import { TopNav } from '@/components/ui/TopNav';

export default function LeaderboardPage() {
  const { account, token, logout, ready, handleAuthError, authError, retryAuth } = useAuth();
  const [scope, setScope] = useState<'global' | 'friends'>('global');
  const [variant, setVariant] = useState('standard');
  const [speed, setSpeed] = useState('blitz');
  const [attempt, setAttempt] = useState(0);
  const key = `${scope}:${variant}:${speed}:${scope === 'friends' ? token : ''}:${attempt}`;
  const [result, setResult] = useState<{ key: string; rows: LeaderboardRow[]; error?: string } | null>(null);
  const loading = !ready || result?.key !== key;
  const rows = loading ? [] : result.rows;

  useEffect(() => {
    if (!ready || (scope === 'friends' && !token)) return;
    let cancelled = false;
    const load = scope === 'friends' ? api.friendsLeaderboard(token!, variant, speed) : api.leaderboard(variant, speed);
    load
      .then((board) => { if (!cancelled) setResult({ key, rows: board.entries }); })
      .catch((error: unknown) => {
        if (cancelled || (scope === 'friends' && handleAuthError(error, token))) return;
        setResult({ key, rows: [], error: error instanceof Error ? error.message : 'Could not load leaderboard.' });
      });
    return () => { cancelled = true; };
  }, [scope, token, ready, variant, speed, key, handleAuthError]);

  return (
    <main className="min-h-screen bg-slate-950 text-white">
      <TopNav username={account?.username} accountId={account?.id} onLogout={account ? logout : undefined} />

      <div className="mx-auto max-w-3xl px-6 py-12">
        <div className="mb-6 flex items-center justify-between">
          <h1 className="text-3xl font-black tracking-tighter">Leaderboard</h1>
          <div className="flex gap-1 rounded-xl border border-zinc-800 bg-zinc-900 p-1">
            <Tab active={scope === 'global'} onClick={() => setScope('global')}>Global</Tab>
            {(token || scope === 'friends') && <Tab active={scope === 'friends'} onClick={() => setScope('friends')}>Friends</Tab>}
          </div>
        </div>
        {authError && <p role="alert" className="mb-4 text-rose-400">{authError} <button type="button" onClick={() => void retryAuth()} className="underline">Retry session</button></p>}
        <div className="mb-4 flex flex-wrap gap-4">
          <label className="flex flex-col gap-1 text-sm text-zinc-300">
            Variant
            <select value={variant} onChange={(e) => setVariant(e.target.value)} className="rounded-lg border border-zinc-700 bg-zinc-900 px-3 py-2">
              <option value="standard">Standard</option><option value="crazyhouse">Crazyhouse</option><option value="bughouse">Bughouse</option>
            </select>
          </label>
          <label className="flex flex-col gap-1 text-sm text-zinc-300">
            Speed
            <select value={speed} onChange={(e) => setSpeed(e.target.value)} className="rounded-lg border border-zinc-700 bg-zinc-900 px-3 py-2">
              <option value="bullet">Bullet</option><option value="blitz">Blitz</option><option value="rapid">Rapid</option><option value="classical">Classical</option>
            </select>
          </label>
        </div>
        <p className="mb-4 text-sm capitalize text-zinc-400">{scope} · {variant} · {speed}</p>

        <div className="overflow-hidden rounded-2xl border border-zinc-800 bg-zinc-900 shadow-xl">
          {ready && scope === 'friends' && !token ? (
            <Empty><Link href="/login" className="underline">Sign in to view the friends leaderboard.</Link></Empty>
          ) : loading ? (
            <Empty><span role="status">Loading leaderboard…</span></Empty>
          ) : result?.error ? (
            <Empty><div role="alert" className="text-rose-400"><p>Could not load leaderboard: {result.error}</p><button type="button" onClick={() => setAttempt((n) => n + 1)} className="mt-3 underline">Retry leaderboard</button></div></Empty>
          ) : rows.length === 0 ? (
            <Empty>{variant === 'bughouse' ? 'Bughouse games are casual and do not receive ratings.' : 'No rated players in this pool yet.'}</Empty>
          ) : (
            rows.map((row) => (
              <div
                key={row.accountId}
                className={`flex items-center gap-4 border-b border-zinc-800/70 px-6 py-3 last:border-0 ${
                  row.accountId === account?.id ? 'bg-blue-500/5' : ''
                }`}
              >
                <span className="w-8 font-mono text-sm text-zinc-500">{row.rank}</span>
                <Link href={`/profile/${row.accountId}`} className="flex-1 font-medium text-zinc-100 hover:text-blue-400">
                  {row.username}
                </Link>
                <span className="w-16 text-right text-xs text-zinc-500">{row.games} games</span>
                <span className="w-16 text-right font-bold text-blue-400">{row.rating}</span>
              </div>
            ))
          )}
        </div>
      </div>
    </main>
  );
}

function Tab({ active, onClick, children }: { active: boolean; onClick: () => void; children: React.ReactNode }) {
  return (
    <button
      type="button"
      aria-pressed={active}
      onClick={onClick}
      className={`rounded-lg px-4 py-1.5 text-sm font-bold transition-colors ${
        active ? 'bg-zinc-800 text-blue-400' : 'text-zinc-400 hover:text-zinc-200'
      }`}
    >
      {children}
    </button>
  );
}

function Empty({ children }: { children: React.ReactNode }) {
  return <div className="px-6 py-12 text-center text-sm italic text-zinc-600">{children}</div>;
}
