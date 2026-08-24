"use client";

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { api, type LiveGame } from '@/lib/api';
import { useAuth } from '@/hooks/use-auth';
import { TopNav } from '@/components/ui/TopNav';

export default function WatchPage() {
  const { account, logout } = useAuth();
  const [games, setGames] = useState<LiveGame[]>([]);

  useEffect(() => {
    const load = () => api.gamesLive().then((r) => setGames(r.games)).catch(() => setGames([]));
    load();
    const t = setInterval(load, 5000);
    return () => clearInterval(t);
  }, []);

  return (
    <main className="min-h-screen bg-slate-950 text-white">
      <TopNav username={account?.username} accountId={account?.id} onLogout={account ? logout : undefined} />

      <div className="mx-auto max-w-3xl px-6 py-12">
        <h1 className="mb-6 text-3xl font-black tracking-tighter">Live games</h1>

        <div className="overflow-hidden rounded-2xl border border-zinc-800 bg-zinc-900 shadow-xl">
          {games.length === 0 ? (
            <div className="px-6 py-12 text-center text-sm italic text-zinc-600">
              No games in progress.
            </div>
          ) : (
            games.map((g) => (
              <Link
                key={g.gameId}
                href={`/room/${g.gameId}`}
                className="flex items-center gap-4 border-b border-zinc-800/70 px-6 py-4 transition-colors last:border-0 hover:bg-zinc-800/40"
              >
                <span className="flex-1 font-medium text-zinc-100">
                  {g.white} <span className="text-zinc-600">vs</span> {g.black}
                </span>
                {g.spectators > 0 && (
                  <span className="text-xs text-zinc-500">
                    {g.spectators} watching
                  </span>
                )}
                <span className="text-xs capitalize text-zinc-500">{g.variant}</span>
                <span className="rounded-lg bg-rose-500/10 px-2.5 py-1 text-xs font-bold uppercase tracking-wider text-rose-400">
                  Live
                </span>
              </Link>
            ))
          )}
        </div>
      </div>
    </main>
  );
}
