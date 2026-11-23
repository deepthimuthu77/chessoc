"use client";

import { memo, useEffect, useRef, useState, useSyncExternalStore } from 'react';
import Link from 'next/link';
import { Icon } from '@/components/ui/Icon';
import { LiveBoardPreview } from '@/components/ui/LiveBoardPreview';
import { formatClock } from '@/lib/view-model';
import type { WatchBoard, WatchGame } from './use-watch-feed';

let clockNow = 0;
let clockTimer: ReturnType<typeof setInterval> | undefined;
const clockListeners = new Set<() => void>();
const getClockNow = () => clockNow;
const getPausedClock = () => 0;
const subscribePausedClock = () => () => {};

function subscribeClock(listener: () => void) {
  clockListeners.add(listener);
  if (!clockTimer) {
    clockNow = performance.now();
    clockTimer = setInterval(() => {
      clockNow = performance.now();
      for (const notify of clockListeners) notify();
    }, 250);
  }
  return () => {
    clockListeners.delete(listener);
    if (clockListeners.size === 0) {
      clearInterval(clockTimer);
      clockTimer = undefined;
    }
  };
}

const WatchClock = memo(function WatchClock({ base, receivedAt, running, frozenAt, label }: {
  base: number; receivedAt?: number; running: boolean; frozenAt: number; label: string;
}) {
  const now = useSyncExternalStore(running ? subscribeClock : subscribePausedClock,
    running ? getClockNow : getPausedClock, getPausedClock);
  const elapsed = receivedAt === undefined ? 0 : Math.max(0, (running ? now : frozenAt) - receivedAt);
  return <span aria-label={label} aria-live="off" className={`shrink-0 font-mono font-bold tabular-nums ${running ? 'text-emerald-300' : 'text-zinc-400'}`}>
    {formatClock(base - elapsed)}
  </span>;
});

export const WatchGameCard = memo(function WatchGameCard({
  game, pinned = false, tiled = false, dense = false, pinDisabled = false, verified, frozenAt, onPin,
}: {
  game: WatchGame; pinned?: boolean; tiled?: boolean; dense?: boolean; pinDisabled?: boolean;
  verified: boolean; frozenAt: number; onPin: (id: string, game?: WatchGame) => void;
}) {
  const ref = useRef<HTMLElement>(null);
  const [onScreen, setOnScreen] = useState(false);
  useEffect(() => {
    const element = ref.current;
    if (!element) return;
    if (typeof IntersectionObserver !== 'function') {
      setOnScreen(true);
      return;
    }
    const observer = new IntersectionObserver(([entry]) => setOnScreen(entry.isIntersecting), { rootMargin: '150px' });
    observer.observe(element);
    return () => observer.disconnect();
  }, []);

  const isBughouse = game.variant === 'bughouse';
  const ended = game.live === false;
  const running = verified && !ended;
  const roomUrl = `/room/${encodeURIComponent(game.gameId)}`;
  const reviewUrl = `/review/${encodeURIComponent(game.gameId)}`;
  const status = ended ? 'No longer live' : running ? 'Live' : 'Last known position';
  const result = [game.result, game.winner ? `Winner: ${game.winner}` : ''].filter(Boolean).join(' · ');
  return (
    <article ref={ref} style={{ contentVisibility: 'auto', containIntrinsicSize: 'auto 420px' }}
      className={`watch-card min-w-0 ${isBughouse && !tiled ? 'sm:col-span-2' : ''}`}>
      <div className={`flex flex-wrap items-center justify-between gap-2 border-b border-zinc-800 px-3 ${dense ? 'py-1' : 'py-3'}`}>
        <div className="flex min-w-0 flex-wrap items-center gap-2 text-xs">
          <span aria-hidden="true" className={`h-2 w-2 shrink-0 rounded-full ${running ? 'bg-accent-300' : 'bg-zinc-500'}`} />
          <span className="font-semibold uppercase text-zinc-200">{game.variant}</span>
          <span className="capitalize text-zinc-400">{game.speed}</span>
          <span className="text-zinc-400">{status}</span>
        </div>
        <div className="flex shrink-0 flex-wrap items-center gap-2 text-xs text-zinc-400">
          <span>{game.minutes}+{game.increment}</span>
          <span>{game.rated ? 'Rated' : 'Casual'}</span>
          {running && game.spectators > 0 && <span>{game.spectators} watching</span>}
          <button type="button" onClick={() => onPin(game.gameId, game)} disabled={pinDisabled} aria-pressed={pinned}
            aria-label={`${pinned ? 'Unpin' : 'Pin'} ${game.white} vs ${game.black}${isBughouse ? ', both boards' : ''}`}
            title={pinDisabled ? 'Unpin a match to make room (maximum four)' : undefined}
            className={`inline-flex min-h-9 items-center gap-1.5 rounded-lg border px-2.5 py-1 text-xs font-medium disabled:cursor-not-allowed disabled:opacity-40 ${pinned ? 'border-accent-400/40 bg-accent-400/10 text-accent-200' : 'border-zinc-700 text-zinc-300 hover:border-accent-400/40'}`}>
            <Icon name="pin" size={12} />{pinned ? 'Unpin' : pinDisabled ? '4/4 pinned' : 'Pin'}
          </button>
        </div>
      </div>
      <Link href={ended ? reviewUrl : roomUrl} prefetch={false}
        aria-label={`${ended ? 'Review' : 'Watch'} ${game.variant}: ${game.white} vs ${game.black}${isBughouse ? ', boards A and B' : ''}`}
        className={`grid gap-px bg-zinc-800 outline-offset-2 focus-visible:outline-2 focus-visible:outline-accent-400 ${isBughouse ? 'grid-cols-2' : 'grid-cols-1'}`}>
        {game.boards.map((board) => <LiveBoardPanel key={board.gameId} board={board} dense={dense} tiled={tiled}
          showPockets={game.variant === 'crazyhouse' || isBughouse} onScreen={onScreen}
          running={running} frozenAt={ended ? 0 : frozenAt} />)}
      </Link>
      {ended && <div className="flex flex-wrap items-center justify-between gap-2 border-t border-zinc-800 px-3 py-2 text-xs text-zinc-400">
        <span>{result || 'Last known position. Open review for the final game.'}</span>
        <div className="flex gap-3"><Link prefetch={false} href={roomUrl} className="text-accent-400">Open room</Link>
          <Link prefetch={false} href={reviewUrl} className="text-accent-400">Review game</Link></div>
      </div>}
    </article>
  );
});

const LiveBoardPanel = memo(function LiveBoardPanel({ board, dense, tiled, showPockets, onScreen, running, frozenAt }: {
  board: WatchBoard; dense: boolean; tiled: boolean; showPockets: boolean; onScreen: boolean; running: boolean; frozenAt: number;
}) {
  return (
    <section aria-label={board.label ? `Board ${board.label}` : 'Game board'} className={`min-w-0 bg-zinc-950 ${dense ? 'p-1' : 'p-3'}`}>
      <PlayerRow name={board.black} board={board} color="black" running={running && onScreen} frozenAt={frozenAt} compact={dense} />
      <div className={`mx-auto w-full ${dense ? 'my-1' : 'my-2'}`} style={{ maxWidth: tiled && showPockets ? 'calc(var(--watch-board-size, 28rem) + 3rem)' : 'var(--watch-board-size, 28rem)' }}>
        {onScreen ? <LiveBoardPreview fen={board.fen} lastMove={board.lastMove} moveCount={board.moveCount}
          label={board.label} showPockets={showPockets} sidePockets={tiled} /> : <div aria-hidden="true">
          {showPockets && <div className="h-7" />}
          <div className="aspect-square bg-zinc-900" />
          {showPockets && <div className="h-7" />}
        </div>}
      </div>
      <PlayerRow name={board.white} board={board} color="white" running={running && onScreen} frozenAt={frozenAt} compact={dense} />
    </section>
  );
});

const PlayerRow = memo(function PlayerRow({ name, board, color, running, frozenAt, compact }: {
  name: string; board: WatchBoard; color: 'white' | 'black'; running: boolean; frozenAt: number; compact: boolean;
}) {
  const active = board.sideToMove === color;
  return (
    <div className={`flex min-w-0 items-center justify-between gap-2 ${compact ? 'text-xs' : 'text-sm'}`}>
      <span className={`truncate font-bold ${active && running ? 'text-white' : 'text-zinc-400'}`} title={name}>{name}</span>
      <WatchClock base={color === 'white' ? board.whiteRemainingMs : board.blackRemainingMs} receivedAt={board.receivedAt}
        running={active && running} frozenAt={active ? frozenAt : 0} label={`${board.label ? `Board ${board.label}, ` : ''}${color} clock`} />
    </div>
  );
});
