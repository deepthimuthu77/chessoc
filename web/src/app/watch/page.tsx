"use client";

import { useCallback, useMemo, useRef, useState, type CSSProperties } from 'react';
import Link from 'next/link';
import { useAuth } from '@/hooks/use-auth';
import { TopNav } from '@/components/ui/TopNav';
import { useWatchFeed, type FeedStatus } from './use-watch-feed';
import { useWatchPins } from './use-watch-pins';
import { useWatchFocusTrap } from './use-watch-focus-trap';
import { WatchGameCard } from './WatchGameCard';

const VARIANTS = ['all', 'standard', 'crazyhouse', 'bughouse'] as const;
const SPEEDS = ['all', 'bullet', 'blitz', 'rapid', 'classical'] as const;
type VariantFilter = (typeof VARIANTS)[number];
type SpeedFilter = (typeof SPEEDS)[number];
const FEED_LABELS: Record<FeedStatus, string> = {
  loading: 'Connecting to live games…', live: 'Live updates connected', polling: 'Live updates via polling',
  reconnecting: 'Connection interrupted. Reconnecting…', error: 'Live games unavailable. Retrying…',
  paused: 'Live updates paused while this tab is hidden',
};

export default function WatchPage() {
  const { account, logout } = useAuth();
  const feed = useWatchFeed();
  const { pins, ready: pinsReady, togglePin, clearPins } = useWatchPins(feed.snapshot);
  const [variant, setVariant] = useState<VariantFilter>('all');
  const [speed, setSpeed] = useState<SpeedFilter>('all');
  const [expanded, setExpanded] = useState(false);
  const pinsPanel = useRef<HTMLElement>(null);
  const closeTiles = useCallback(() => setExpanded(false), []);
  useWatchFocusTrap(expanded, pinsPanel, closeTiles);
  const online = feed.status === 'live' || feed.status === 'polling';
  const filtered = useMemo(() => feed.games
    .filter((game) => variant === 'all' || game.variant === variant)
    .filter((game) => speed === 'all' || game.speed === speed)
    .sort((a, b) => b.spectators - a.spectators || b.moveCount - a.moveCount), [feed.games, speed, variant]);
  const pinnedIds = useMemo(() => new Set(pins.map((pin) => pin.id)), [pins]);
  const dense = pins.length > 2;

  return (
    <main className="min-h-screen bg-slate-950 text-white">
      <TopNav username={account?.username} accountId={account?.id} onLogout={account ? logout : undefined} />
      <div className="mx-auto max-w-[1600px] px-4 py-7 sm:px-6 lg:px-8">
        <header className="flex flex-wrap items-end justify-between gap-4 border-b border-zinc-800 pb-5">
          <div>
            <div className="flex items-center gap-3">
              <h1 className="text-2xl font-black">Live games</h1>
              <span aria-hidden="true" className={`h-2 w-2 rounded-full ${online ? 'bg-emerald-400' : 'bg-amber-400'}`} />
            </div>
            <p role="status" className="mt-1 text-sm text-zinc-400">{FEED_LABELS[feed.status]}</p>
            {online && <p className="mt-1 text-sm text-zinc-500">{feed.games.length} {feed.games.length === 1 ? 'match' : 'matches'} in progress</p>}
          </div>
          {online && <div className="text-right text-xs font-bold uppercase text-zinc-500">{filtered.length} shown</div>}
        </header>

        <section ref={pinsPanel} role={expanded ? 'dialog' : undefined} aria-modal={expanded ? true : undefined}
          aria-labelledby="watch-pins-title" tabIndex={-1}
          className={expanded ? 'fixed inset-0 z-50 overflow-y-auto overscroll-contain bg-slate-950 p-4 sm:p-6' : 'border-b border-zinc-800 py-6'}>
          <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
            <div>
              <h2 id="watch-pins-title" className="text-lg font-bold">Pinned games <span className="text-sm text-zinc-500">{pins.length}/4</span></h2>
              {!expanded && <p className="text-sm text-zinc-500">Pin up to four matches to watch together. Click a board to open its room.</p>}
              {expanded && <p role="status" className="text-xs text-zinc-400">{FEED_LABELS[feed.status]}</p>}
            </div>
            <div className="flex gap-2">
              {(pins.length > 0 || expanded) && <button type="button" onClick={() => setExpanded((value) => !value)}
                aria-pressed={expanded} className="rounded-md bg-blue-600 px-3 py-2 text-sm font-bold">{expanded ? 'Exit tiled view' : 'Expand tiles'}</button>}
              {pins.length > 0 && <button type="button" onClick={clearPins} className="rounded-md px-3 py-2 text-sm text-zinc-400 hover:bg-zinc-800">Clear pins</button>}
            </div>
          </div>
          {!pinsReady ? <p className="p-6 text-sm text-zinc-500">Restoring pinned games…</p> : pins.length === 0 ? (
            <p className="rounded-lg border border-dashed border-zinc-800 p-6 text-sm text-zinc-500">
              {expanded ? 'No pinned games. Exit tiled view to pin matches.' : 'Use Pin on any live match below to build your viewing window.'}
            </p>
          ) : (
            <div className={`grid items-start gap-4 ${pins.length > 1 ? 'md:grid-cols-2' : 'mx-auto max-w-5xl'}`}
              style={{ '--watch-board-size': expanded
                ? dense ? 'min(28rem, max(8rem, calc((100dvh - 22rem) / 2)))' : 'min(36rem, max(10rem, calc(100dvh - 20rem)))'
                : dense ? '20rem' : '28rem' } as CSSProperties}>
              {pins.map(({ id, game }) => game ? <WatchGameCard key={id} game={game} pinned tiled dense={dense}
                verified={online && feed.games.some((current) => current.gameId === id)} frozenAt={feed.frozenAt} onPin={togglePin} /> : (
                <article key={id} className="rounded-lg border border-zinc-800 bg-zinc-900 p-5">
                  <p className="mb-3 text-sm text-zinc-400">{online ? 'This match is no longer live. No saved board is available.'
                    : feed.status === 'loading' ? 'Loading pinned match…' : 'Pinned match unavailable until the feed reconnects.'}</p>
                  <div className="flex flex-wrap justify-between gap-3 text-sm">
                    <Link prefetch={false} href={`/room/${encodeURIComponent(id)}`} className="text-blue-400">Open room</Link>
                    <Link prefetch={false} href={`/review/${encodeURIComponent(id)}`} className="text-blue-400">Review game</Link>
                    <button type="button" onClick={() => togglePin(id)} aria-label={`Unpin match ${id}`} className="text-zinc-400">Unpin</button>
                  </div>
                </article>
              ))}
            </div>
          )}
        </section>

        {!expanded && <>
          <section className="grid gap-4 border-b border-zinc-800 py-5 lg:grid-cols-[auto_1fr] lg:items-center">
            <FilterGroup label="Variant" values={VARIANTS} selected={variant} onSelect={setVariant} />
            <FilterGroup label="Speed" values={SPEEDS} selected={speed} onSelect={setSpeed} />
          </section>
          {!online ? <div className="py-24 text-center text-sm text-zinc-400">{FEED_LABELS[feed.status]}
            {feed.status !== 'loading' && <p className="mt-2 text-zinc-500">Saved pins show their last known positions. Clocks are paused.</p>}
          </div> : filtered.length === 0 ? <div className="py-24 text-center">
            <p className="font-bold text-zinc-300">{feed.games.length === 0 ? 'No live games right now' : 'No matching live games'}</p>
            <p className="mt-1 text-sm text-zinc-500">{feed.games.length === 0 ? 'New matches will appear here automatically.' : 'Try another variant or speed.'}</p>
          </div> : <div className="grid gap-5 py-6 sm:grid-cols-2 xl:grid-cols-3">
            {filtered.map((game) => <WatchGameCard key={game.gameId} game={game} pinned={pinnedIds.has(game.gameId)}
              pinDisabled={!pinsReady || (pins.length >= 4 && !pinnedIds.has(game.gameId))} verified frozenAt={0} onPin={togglePin} />)}
          </div>}
        </>}
      </div>
    </main>
  );
}

function FilterGroup<T extends string>({ label, values, selected, onSelect }: {
  label: string; values: readonly T[]; selected: T; onSelect: (value: T) => void;
}) {
  return <div className="flex min-w-0 items-center gap-3">
    <span className="w-14 shrink-0 text-xs font-bold uppercase text-zinc-500">{label}</span>
    <div className="flex min-w-0 flex-wrap gap-1" role="group" aria-label={`${label} filter`}>
      {values.map((value) => <button key={value} type="button" aria-pressed={selected === value} onClick={() => onSelect(value)}
        className={`rounded-md px-3 py-2 text-xs font-bold capitalize transition-colors ${selected === value
          ? 'bg-blue-600 text-white' : 'text-zinc-400 hover:bg-zinc-900 hover:text-zinc-200'}`}>{value}</button>)}
    </div>
  </div>;
}
