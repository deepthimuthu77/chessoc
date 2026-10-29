"use client";

import { useEffect, useState } from 'react';
import { api, type LiveBoard, type LiveGame } from '@/lib/api';

export type WatchBoard = LiveBoard & { receivedAt?: number };
export type WatchGame = Omit<LiveGame, 'boards'> & { boards: WatchBoard[]; live?: boolean; result?: string; winner?: string };
export type FeedStatus = 'loading' | 'live' | 'polling' | 'reconnecting' | 'error' | 'paused';

const API_BASE = process.env.NEXT_PUBLIC_API_BASE ?? '/api';
const REQUEST_TIMEOUT = 15_000;
const POLL_DELAY = 5_000;

export function readGames(payload: unknown): WatchGame[] {
  if (!payload || typeof payload !== 'object' || !('games' in payload) || !Array.isArray(payload.games)) {
    throw new Error('Invalid live games snapshot');
  }
  for (const game of payload.games) {
    if (!game || typeof game.gameId !== 'string' || !game.gameId || !Array.isArray(game.boards) || game.boards.length === 0
      || typeof game.variant !== 'string' || typeof game.white !== 'string' || typeof game.black !== 'string'
      || typeof game.speed !== 'string' || typeof game.rated !== 'boolean'
      || ![game.minutes, game.increment, game.moveCount, game.spectators].every(Number.isFinite)
      || (game.live !== undefined && typeof game.live !== 'boolean')
      || (game.result != null && typeof game.result !== 'string')
      || (game.winner != null && typeof game.winner !== 'string')
      || !game.boards.every((board: LiveGame['boards'][number]) => board
        && typeof board.gameId === 'string' && typeof board.fen === 'string'
        && typeof board.label === 'string' && typeof board.lastMove === 'string' && Number.isFinite(board.moveCount)
        && (board.sideToMove === 'white' || board.sideToMove === 'black')
        && typeof board.white === 'string' && typeof board.black === 'string'
        && Number.isFinite(board.serverTimestamp)
        && Number.isFinite(board.whiteRemainingMs) && Number.isFinite(board.blackRemainingMs))) {
      throw new Error('Invalid live game');
    }
  }
  return payload.games;
}

type Feed = {
  games: WatchGame[];
  snapshot: WatchGame[] | null;
  status: FeedStatus;
  frozenAt: number;
};

export function useWatchFeed() {
  const [feed, setFeed] = useState<Feed>({ games: [], snapshot: null, status: 'loading', frozenAt: 0 });

  useEffect(() => {
    let disposed = false;
    let generation = 0;
    let source: EventSource | undefined;
    let request: AbortController | undefined;
    let retry: ReturnType<typeof setTimeout> | undefined;
    let streamDeadline: ReturnType<typeof setTimeout> | undefined;
    let streamFailures = 0;
    let failures = 0;
    let polling = typeof window.EventSource !== 'function'
      || window.location.hostname.endsWith('.trycloudflare.com');
    const previousGames = new Map<string, { json: string; game: WatchGame }>();
    const previousBoards = new Map<string, { json: string; board: WatchBoard }>();

    function stop() {
      generation++;
      source?.close();
      source = undefined;
      request?.abort();
      request = undefined;
      clearTimeout(retry);
      clearTimeout(streamDeadline);
    }

    function unavailable(status: FeedStatus) {
      setFeed((previous) => ({ ...previous, games: [], status, frozenAt: performance.now() }));
    }

    function accept(games: WatchGame[]) {
      failures = 0;
      const now = performance.now();
      const snapshot = games.map((game) => {
        const json = JSON.stringify(game);
        const previous = previousGames.get(game.gameId);
        if (previous?.json === json) return previous.game;
        const boards = game.boards.map((board) => {
          const json = JSON.stringify(board);
          const previous = previousBoards.get(board.gameId);
          if (previous?.json === json) return previous.board;
          const old = previous?.board;
          const sameSample = old && old.serverTimestamp === board.serverTimestamp
            && old.whiteRemainingMs === board.whiteRemainingMs && old.blackRemainingMs === board.blackRemainingMs
            && old.sideToMove === board.sideToMove;
          const next = { ...board, receivedAt: sameSample ? old.receivedAt : now };
          previousBoards.set(board.gameId, { json, board: next });
          return next;
        });
        const next = { ...game, boards };
        previousGames.set(game.gameId, { json, game: next });
        return next;
      });
      const ids = new Set(snapshot.map((game) => game.gameId));
      const boardIds = new Set(snapshot.flatMap((game) => game.boards.map((board) => board.gameId)));
      for (const id of previousGames.keys()) if (!ids.has(id)) previousGames.delete(id);
      for (const id of previousBoards.keys()) if (!boardIds.has(id)) previousBoards.delete(id);
      setFeed((previous) => {
        const status = polling ? 'polling' : 'live';
        if (previous.status === status && previous.snapshot?.length === snapshot.length
          && snapshot.every((game, index) => previous.snapshot?.[index] === game)) return previous;
        return { games: snapshot.filter((game) => game.live !== false), snapshot, status, frozenAt: 0 };
      });
    }

    function schedule() {
      const delay = Math.min(15_000, 1_500 * 2 ** Math.min(failures++, 4));
      retry = setTimeout(refresh, delay);
    }

    function connect() {
      const current = generation;
      const failed = () => {
        if (disposed || current !== generation) return;
        stop();
        unavailable('reconnecting');
        // An older server or an SSE-blocking proxy can still serve snapshots.
        if (++streamFailures >= 3) polling = true;
        schedule();
      };
      try {
        source = new EventSource(api.liveEventsUrl);
        streamDeadline = setTimeout(failed, REQUEST_TIMEOUT);
        source.addEventListener('games', (event) => {
          if (disposed || current !== generation) return;
          try {
            const games = readGames(JSON.parse((event as MessageEvent<string>).data));
            clearTimeout(streamDeadline);
            streamDeadline = setTimeout(failed, REQUEST_TIMEOUT);
            streamFailures = 0;
            accept(games);
          } catch {
            failed();
          }
        });
        source.onerror = failed;
      } catch {
        failed();
      }
    }

    async function refresh() {
      if (disposed || document.hidden) return;
      const current = generation;
      const controller = new AbortController();
      request = controller;
      const timeout = setTimeout(() => controller.abort(), REQUEST_TIMEOUT);
      try {
        // Watch owns cancellation so hidden tabs and retries cannot overlap requests.
        const response = await fetch(`${API_BASE}/games/live`, { signal: controller.signal, cache: 'no-store' });
        if (!response.ok) throw new Error(`Live games failed (${response.status})`);
        const games = readGames(await response.json());
        if (disposed || current !== generation) return;
        accept(games);
      } catch {
        if (disposed || current !== generation) return;
        unavailable('error');
      } finally {
        clearTimeout(timeout);
        if (request === controller) request = undefined;
      }
      if (disposed || current !== generation) return;
      if (polling) retry = setTimeout(refresh, POLL_DELAY);
      else connect();
    }

    function visibilityChanged() {
      stop();
      unavailable(document.hidden ? 'paused' : 'reconnecting');
      if (!document.hidden) void refresh();
    }

    document.addEventListener('visibilitychange', visibilityChanged);
    window.addEventListener('online', visibilityChanged);
    if (!document.hidden) void refresh();
    return () => {
      disposed = true;
      stop();
      document.removeEventListener('visibilitychange', visibilityChanged);
      window.removeEventListener('online', visibilityChanged);
    };
  }, []);

  return feed;
}
