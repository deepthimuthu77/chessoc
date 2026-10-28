"use client";

import { useEffect, useMemo, useState } from 'react';
import Link from 'next/link';
import { api, type LiveBoard, type LiveGame } from '@/lib/api';
import { useAuth } from '@/hooks/use-auth';
import { TopNav } from '@/components/ui/TopNav';
import { LiveBoardPreview } from '@/components/ui/LiveBoardPreview';
import { formatClock } from '@/lib/view-model';

const VARIANTS = ['all', 'standard', 'crazyhouse', 'bughouse'] as const;
const SPEEDS = ['all', 'bullet', 'blitz', 'rapid', 'classical'] as const;
const PINS_KEY = 'chessoc-watch-pins';

type VariantFilter = (typeof VARIANTS)[number];
type SpeedFilter = (typeof SPEEDS)[number];

export default function WatchPage() {
  const { account, logout } = useAuth();
  const [games, setGames] = useState<LiveGame[]>([]);
  const [variant, setVariant] = useState<VariantFilter>('all');
  const [speed, setSpeed] = useState<SpeedFilter>('all');
  const [loaded, setLoaded] = useState(false);
  const [feedOnline, setFeedOnline] = useState(true);
  const [pinnedIds, setPinnedIds] = useState<string[]>([]);
  const [pinsReady, setPinsReady] = useState(false);
  const [expanded, setExpanded] = useState(false);
  const [, tick] = useState(0);

  useEffect(() => {
    try {
      const saved: unknown = JSON.parse(localStorage.getItem(PINS_KEY) ?? '[]');
      if (Array.isArray(saved)) {
        // Restore the browser's saved viewing layout after hydration.
        // eslint-disable-next-line react-hooks/set-state-in-effect
        setPinnedIds([...new Set(saved.filter((id): id is string => typeof id === 'string'))].slice(0, 4));
      }
    } catch { /* Storage may be unavailable. */ }
    setPinsReady(true);
  }, []);

  useEffect(() => {
    if (!pinsReady) return;
    try { localStorage.setItem(PINS_KEY, JSON.stringify(pinnedIds)); } catch { /* Storage may be unavailable. */ }
  }, [pinnedIds, pinsReady]);

  function togglePin(id: string) {
    setPinnedIds((ids) => ids.includes(id) ? ids.filter((value) => value !== id)
      : ids.length < 4 ? [...ids, id] : ids);
  }

  useEffect(() => {
    if (!expanded) return;
    const onKey = (event: KeyboardEvent) => { if (event.key === 'Escape') setExpanded(false); };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [expanded]);

  useEffect(() => {
    let active = true;
    const load = async () => {
      try {
        const response = await api.gamesLive();
        if (!active) return;
        setGames(response.games);
        setFeedOnline(true);
      } catch {
        if (active) setFeedOnline(false);
      } finally {
        if (active) setLoaded(true);
      }
    };
    load();
    const poll = window.setInterval(load, 1500);
    return () => {
      active = false;
      window.clearInterval(poll);
    };
  }, []);

  useEffect(() => {
    const timer = window.setInterval(() => tick((value) => value + 1), 250);
    return () => window.clearInterval(timer);
  }, []);

  const filtered = useMemo(
    () => games
      .filter((game) => variant === 'all' || game.variant === variant)
      .filter((game) => speed === 'all' || game.speed === speed)
      .sort((a, b) => b.spectators - a.spectators || b.moveCount - a.moveCount),
    [games, speed, variant],
  );

  return (
    <main className="min-h-screen bg-slate-950 text-white">
      <TopNav username={account?.username} accountId={account?.id} onLogout={account ? logout : undefined} />

      <div className="mx-auto max-w-[1600px] px-4 py-7 sm:px-6 lg:px-8">
        <header className="flex flex-wrap items-end justify-between gap-4 border-b border-zinc-800 pb-5">
          <div>
            <div className="flex items-center gap-3">
              <h1 className="text-2xl font-black">Live games</h1>
              <span className={`h-2 w-2 rounded-full ${feedOnline ? 'bg-emerald-400' : 'bg-rose-400'}`} />
            </div>
            <p className="mt-1 text-sm text-zinc-500">
              {games.length} {games.length === 1 ? 'match' : 'matches'} in progress
            </p>
          </div>
          <div className="text-right text-xs font-bold uppercase text-zinc-500">
            {filtered.length} shown
          </div>
        </header>

        <section aria-label="Pinned games" className={expanded ? 'fixed inset-0 z-50 overflow-auto bg-slate-950 p-4' : 'border-b border-zinc-800 py-6'}>
          <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
            <div>
              <h2 className="text-lg font-bold">Pinned games <span className="text-sm text-zinc-500">{pinnedIds.length}/4</span></h2>
              {!expanded && <p className="text-sm text-zinc-500">Pin up to four matches to watch together. Click a board to open its room.</p>}
            </div>
            <div className="flex gap-2">
              {(pinnedIds.length > 0 || expanded) && <button type="button" onClick={() => setExpanded((value) => !value)} aria-pressed={expanded} className="rounded-md bg-blue-600 px-3 py-2 text-sm font-bold">{expanded ? 'Exit tiled view' : 'Expand tiles'}</button>}
              {pinnedIds.length > 0 && <button type="button" onClick={() => setPinnedIds([])} className="rounded-md px-3 py-2 text-sm text-zinc-400 hover:bg-zinc-800">Clear pins</button>}
            </div>
          </div>
          {pinnedIds.length === 0 ? <p className="rounded-lg border border-dashed border-zinc-800 p-6 text-sm text-zinc-500">Use Pin on any live match below to build your viewing window.</p> : (
            <div className={`grid items-start gap-4 ${pinnedIds.length > 1 ? 'md:grid-cols-2' : 'mx-auto max-w-3xl'}`}>
              {pinnedIds.map((id) => {
                const game = games.find((candidate) => candidate.gameId === id);
                return game ? <LiveGameCard key={id} game={game} pinned tiled expanded={expanded} dense={pinnedIds.length > 2} onPin={() => togglePin(id)} /> : (
                  <article key={id} className="rounded-lg border border-zinc-800 bg-zinc-900 p-5">
                    <p className="mb-3 text-sm text-zinc-400">{!loaded ? 'Loading pinned match...' : !feedOnline ? 'Reconnecting to live games...' : 'This match is no longer live.'}</p>
                    <div className="flex justify-between gap-3"><Link href={`/room/${id}`} className="text-blue-400">Open room</Link><button type="button" onClick={() => togglePin(id)} aria-label="Unpin ended match" className="text-zinc-400">Unpin</button></div>
                  </article>
                );
              })}
            </div>
          )}
        </section>

        <section className="grid gap-4 border-b border-zinc-800 py-5 lg:grid-cols-[auto_1fr] lg:items-center">
          <FilterGroup label="Variant" values={VARIANTS} selected={variant} onSelect={setVariant} />
          <FilterGroup label="Speed" values={SPEEDS} selected={speed} onSelect={setSpeed} />
        </section>

        {!loaded ? (
          <div className="py-24 text-center text-sm text-zinc-500">Loading live games...</div>
        ) : filtered.length === 0 ? (
          <div className="py-24 text-center">
            <p className="font-bold text-zinc-300">No matching live games</p>
            <p className="mt-1 text-sm text-zinc-600">Try another variant or speed.</p>
          </div>
        ) : (
          <div className="grid gap-5 py-6 sm:grid-cols-2 xl:grid-cols-3">
            {filtered.map((game) => <LiveGameCard key={game.gameId} game={game} pinned={pinnedIds.includes(game.gameId)} pinDisabled={pinnedIds.length >= 4 && !pinnedIds.includes(game.gameId)} onPin={() => togglePin(game.gameId)} />)}
          </div>
        )}
      </div>
    </main>
  );
}

function FilterGroup<T extends string>({
  label,
  values,
  selected,
  onSelect,
}: {
  label: string;
  values: readonly T[];
  selected: T;
  onSelect: (value: T) => void;
}) {
  return (
    <div className="flex min-w-0 items-center gap-3">
      <span className="w-14 shrink-0 text-xs font-bold uppercase text-zinc-500">{label}</span>
      <div className="flex min-w-0 flex-wrap gap-1" role="group" aria-label={`${label} filter`}>
        {values.map((value) => (
          <button
            key={value}
            type="button"
            aria-pressed={selected === value}
            onClick={() => onSelect(value)}
            className={`rounded-md px-3 py-2 text-xs font-bold capitalize transition-colors ${
              selected === value ? 'bg-blue-600 text-white' : 'text-zinc-400 hover:bg-zinc-900 hover:text-zinc-200'
            }`}
          >
            {value}
          </button>
        ))}
      </div>
    </div>
  );
}

function LiveGameCard({ game, pinned = false, tiled = false, expanded = false, dense = false, pinDisabled = false, onPin }: {
  game: LiveGame; pinned?: boolean; tiled?: boolean; expanded?: boolean; dense?: boolean; pinDisabled?: boolean; onPin: () => void;
}) {
  const isBughouse = game.variant === 'bughouse';
  return (
    <article
      className={`group overflow-hidden rounded-lg border border-zinc-800 bg-zinc-900 shadow-lg transition-colors hover:border-zinc-600 ${
        isBughouse && !tiled ? 'sm:col-span-2 xl:col-span-2' : ''
      }`}
    >
      <div className={`flex flex-wrap items-center justify-between gap-3 border-b border-zinc-800 px-4 ${dense ? 'py-2' : 'py-3'}`}>
        <div className="flex min-w-0 items-center gap-2">
          <span className="h-2 w-2 shrink-0 rounded-full bg-rose-400" />
          <span className="truncate text-xs font-black uppercase text-zinc-200">{game.variant}</span>
          <span className="text-xs capitalize text-zinc-500">{game.speed}</span>
        </div>
        <div className="flex shrink-0 items-center gap-3 text-xs text-zinc-500">
          <span>{game.minutes}+{game.increment}</span>
          <span>{game.rated ? 'Rated' : 'Casual'}</span>
          {game.spectators > 0 && <span>{game.spectators} watching</span>}
          <button type="button" onClick={onPin} disabled={pinDisabled} aria-pressed={pinned}
            aria-label={`${pinned ? 'Unpin' : 'Pin'} ${game.variant} ${game.minutes}+${game.increment} match`}
            title={pinDisabled ? 'Unpin a match to make room (maximum four)' : undefined}
            className="rounded border border-zinc-700 px-2 py-1 font-bold text-blue-400 hover:bg-zinc-800 disabled:cursor-not-allowed disabled:opacity-40">
            {pinned ? 'Unpin' : pinDisabled ? '4/4 pinned' : 'Pin'}
          </button>
        </div>
      </div>

      <Link href={`/room/${game.gameId}`} aria-label={`Watch ${game.variant} ${game.minutes}+${game.increment}: ${game.white} vs ${game.black}`} className={`grid gap-px bg-zinc-800 outline-offset-2 focus-visible:outline-2 focus-visible:outline-blue-400 ${isBughouse ? 'grid-cols-2' : 'grid-cols-1'}`}>
        {game.boards.map((board) => <LiveBoardPanel key={board.gameId} board={board} tiled={tiled} expanded={expanded} dense={dense} />)}
      </Link>
    </article>
  );
}

function LiveBoardPanel({ board, tiled, expanded, dense }: { board: LiveBoard; tiled?: boolean; expanded?: boolean; dense?: boolean }) {
  return (
    <section className={`min-w-0 bg-zinc-950 ${dense ? 'p-2' : 'p-3'}`}>
      <PlayerRow name={board.black} time={liveClock(board, 'black')} active={board.sideToMove === 'black'} compact={dense} />
      <div className={`${dense ? 'my-1' : 'my-2'} ${tiled ? expanded ? dense ? 'mx-auto max-w-[clamp(6rem,calc((100dvh-18rem)/2),28rem)]' : 'mx-auto max-w-[min(65dvh,40rem)]' : dense ? 'mx-auto max-w-[clamp(6rem,calc((100dvh-32rem)/2),18rem)]' : 'mx-auto max-w-[min(45dvh,28rem)]' : ''}`}>
        <LiveBoardPreview
          fen={board.fen}
          lastMove={board.lastMove}
          moveCount={board.moveCount}
          label={board.label}
        />
      </div>
      <PlayerRow name={board.white} time={liveClock(board, 'white')} active={board.sideToMove === 'white'} compact={dense} />
    </section>
  );
}

function PlayerRow({ name, time, active, compact }: { name: string; time: string; active: boolean; compact?: boolean }) {
  return (
    <div className={`flex min-w-0 items-center justify-between gap-2 ${compact ? 'text-xs' : 'text-sm'}`}>
      <span className={`truncate font-bold ${active ? 'text-white' : 'text-zinc-400'}`}>{name}</span>
      <span className={`font-mono font-bold ${active ? 'text-emerald-300' : 'text-zinc-500'}`}>{time}</span>
    </div>
  );
}

function liveClock(board: LiveBoard, color: 'white' | 'black') {
  const base = color === 'white' ? board.whiteRemainingMs : board.blackRemainingMs;
  const elapsed = board.sideToMove === color ? Date.now() - board.serverTimestamp : 0;
  return formatClock(base - elapsed);
}
