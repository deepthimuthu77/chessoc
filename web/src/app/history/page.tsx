"use client";

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { api, type GameHistoryRow } from '@/lib/api';
import { useAuth } from '@/hooks/use-auth';
import { TopNav } from '@/components/ui/TopNav';
import { PageHeading, JourneyNav } from '@/components/ui/PageHeading';
import { Icon } from '@/components/ui/Icon';

const RESULT_STYLE: Record<string, string> = {
  win: 'text-green-400',
  loss: 'text-rose-400',
  draw: 'text-zinc-400',
};

export default function HistoryPage() {
  const { account, token, ready, logout, handleAuthError, authError, retryAuth } = useAuth(true);
  const [attempt, setAttempt] = useState(0);
  const key = `${token}:${attempt}`;
  const [result, setResult] = useState<{ key: string; games: GameHistoryRow[]; error?: string } | null>(null);
  const loading = result?.key !== key;
  const games = loading ? [] : result.games;

  useEffect(() => {
    if (!token) return;
    let cancelled = false;
    api.gamesMine(token).then((r) => {
      if (!cancelled) setResult({ key, games: r.games });
    }).catch((error: unknown) => {
      if (!cancelled && !handleAuthError(error, token)) {
        setResult({ key, games: [], error: error instanceof Error ? error.message : 'Could not load game history.' });
      }
    });
    return () => { cancelled = true; };
  }, [token, key, handleAuthError]);

  if (!ready || !account || !token) {
    return <main className="min-h-screen bg-slate-950 p-6 text-zinc-300"><p role="status">{ready ? 'Redirecting to sign in…' : 'Loading session…'}</p></main>;
  }

  return (
    <main className="min-h-screen app-background text-white">
      <TopNav username={account.username} accountId={account.id} onLogout={logout} />

      <div id="main-content" tabIndex={-1} className="page-wrap page-wrap-narrow page-enter">
        <PageHeading eyebrow="YOUR GAMES" title="Game history" description="Review your finished games, replay moves and analyze positions." />
        {authError && <p role="alert" className="mb-4 text-rose-400">{authError} <button type="button" onClick={() => void retryAuth()} className="underline">Retry session</button></p>}

        <div className="overflow-hidden rounded-2xl border border-zinc-800 bg-zinc-900">
          {loading ? (
            <p role="status" className="px-6 py-12 text-center text-zinc-400">Loading game history…</p>
          ) : result?.error ? (
            <div role="alert" className="px-6 py-12 text-center text-rose-400">
              <p>Could not load game history: {result.error}</p>
              <button type="button" onClick={() => setAttempt((n) => n + 1)} className="mt-3 underline">Retry history</button>
            </div>
          ) : games.length === 0 ? (
            <div className="empty-state"><span className="icon-well"><Icon name="history" /></span><h2>Your collection starts here.</h2><p>Finish a game and return to replay every move, flip the board, and explore positions with the engine.</p><Link href="/" className="button-primary">Play your first game<Icon name="arrow" size={15} /></Link>
            </div>
          ) : (
            games.map((g) => (
              <Link
                key={g.gameId}
                href={`/review/${g.gameId}`}
                className="flex flex-wrap items-center gap-3 border-b border-zinc-800/70 px-6 py-3 transition-colors last:border-0 hover:bg-zinc-800/40"
              >
                <span className={`w-14 text-sm font-bold uppercase ${RESULT_STYLE[g.result]}`}>{g.result}</span>
                <div className="min-w-24 flex-1">
                  <span className="text-zinc-100">vs {g.opponent}</span>
                  <span className="ml-2 text-xs text-zinc-500">{g.reason}</span>
                </div>
                <span className="text-xs capitalize text-zinc-500">{g.variant}{g.rated ? ' · rated' : ''}</span>
                <span className="text-right text-xs text-zinc-500">
                  {new Date(g.endedAt).toLocaleDateString()}
                </span>
              </Link>
            ))
          )}
        </div>
        <JourneyNav destinations={['play', 'friends', 'watch']} />
      </div>
    </main>
  );
}
