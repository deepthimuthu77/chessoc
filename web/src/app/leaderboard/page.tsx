"use client";

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { api, type LeaderboardRow } from '@/lib/api';
import { useAuth } from '@/hooks/use-auth';
import { TopNav } from '@/components/ui/TopNav';

/* eslint-disable react-hooks/set-state-in-effect */

export default function LeaderboardPage() {
  const { account, token, logout } = useAuth();
  const [scope, setScope] = useState<'global' | 'friends'>('global');
  const [rows, setRows] = useState<LeaderboardRow[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    setLoading(true);
    const load = scope === 'friends' && token ? api.friendsLeaderboard(token) : api.leaderboard();
    load
      .then((board) => setRows(board.entries))
      .catch(() => setRows([]))
      .finally(() => setLoading(false));
  }, [scope, token]);

  return (
    <main className="min-h-screen bg-slate-950 text-white">
      <TopNav username={account?.username} accountId={account?.id} onLogout={account ? logout : undefined} />

      <div className="mx-auto max-w-3xl px-6 py-12">
        <div className="mb-6 flex items-center justify-between">
          <h1 className="text-3xl font-black tracking-tighter">Leaderboard</h1>
          <div className="flex gap-1 rounded-xl border border-zinc-800 bg-zinc-900 p-1">
            <Tab active={scope === 'global'} onClick={() => setScope('global')}>Global</Tab>
            {token && <Tab active={scope === 'friends'} onClick={() => setScope('friends')}>Friends</Tab>}
          </div>
        </div>
        <p className="mb-4 text-xs font-bold uppercase tracking-wider text-zinc-600">Standard · Blitz</p>

        <div className="overflow-hidden rounded-2xl border border-zinc-800 bg-zinc-900 shadow-xl">
          {loading ? (
            <Empty>Loading…</Empty>
          ) : rows.length === 0 ? (
            <Empty>No rated games yet — play a rated game to appear here.</Empty>
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
