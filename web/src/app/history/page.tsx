"use client";

import { useEffect, useState } from 'react';
import { api, type GameHistoryRow } from '@/lib/api';
import { useAuth } from '@/hooks/use-auth';
import { TopNav } from '@/components/ui/TopNav';

const RESULT_STYLE: Record<string, string> = {
  win: 'text-green-400',
  loss: 'text-rose-400',
  draw: 'text-zinc-400',
};

export default function HistoryPage() {
  const { account, token, ready, logout } = useAuth(true);
  const [games, setGames] = useState<GameHistoryRow[]>([]);

  useEffect(() => {
    if (token) {
      api.gamesMine(token).then((r) => setGames(r.games)).catch(() => setGames([]));
    }
  }, [token]);

  if (!ready || !account) {
    return <main className="min-h-screen bg-slate-950" />;
  }

  return (
    <main className="min-h-screen bg-slate-950 text-white">
      <TopNav username={account.username} accountId={account.id} onLogout={logout} />

      <div className="mx-auto max-w-3xl px-6 py-12">
        <h1 className="mb-6 text-3xl font-black tracking-tighter">Game history</h1>

        <div className="overflow-hidden rounded-2xl border border-zinc-800 bg-zinc-900 shadow-xl">
          {games.length === 0 ? (
            <div className="px-6 py-12 text-center text-sm italic text-zinc-600">
              No finished games yet.
            </div>
          ) : (
            games.map((g) => (
              <div key={g.gameId} className="flex items-center gap-4 border-b border-zinc-800/70 px-6 py-3 last:border-0">
                <span className={`w-14 text-sm font-bold uppercase ${RESULT_STYLE[g.result]}`}>{g.result}</span>
                <div className="flex-1">
                  <span className="text-zinc-100">vs {g.opponent}</span>
                  <span className="ml-2 text-xs text-zinc-500">{g.reason}</span>
                </div>
                <span className="text-xs capitalize text-zinc-500">{g.variant}{g.rated ? ' · rated' : ''}</span>
                <span className="w-24 text-right text-xs text-zinc-600">
                  {new Date(g.endedAt).toLocaleDateString()}
                </span>
              </div>
            ))
          )}
        </div>
      </div>
    </main>
  );
}
