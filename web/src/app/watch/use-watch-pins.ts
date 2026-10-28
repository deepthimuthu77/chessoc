"use client";

import { useCallback, useEffect, useReducer } from 'react';
import { readGames, type WatchGame } from './use-watch-feed';

const PINS_KEY = 'chessoc-watch-pins';
export type WatchPin = { id: string; game?: WatchGame };
type PinState = { ready: boolean; pins: WatchPin[] };
type Action = { type: 'restore'; pins: WatchPin[] } | { type: 'snapshot'; games: WatchGame[] }
  | { type: 'toggle'; id: string; game?: WatchGame } | { type: 'clear' };

function reducer(state: PinState, action: Action): PinState {
  if (action.type === 'restore') return { ready: true, pins: action.pins };
  if (action.type === 'clear') return { ...state, pins: [] };
  if (action.type === 'toggle') {
    const exists = state.pins.some((pin) => pin.id === action.id);
    return { ...state, pins: exists ? state.pins.filter((pin) => pin.id !== action.id)
      : state.pins.length < 4 ? [...state.pins, { id: action.id, game: action.game }] : state.pins };
  }
  const games = new Map(action.games.map((game) => [game.gameId, game]));
  let changed = false;
  const pins = state.pins.map((pin) => {
    const game = games.get(pin.id);
    if (game === pin.game) return pin;
    if (game) {
      changed = true;
      return { id: pin.id, game };
    }
    // Only a successful authoritative snapshot can retire a cached live pin.
    if (pin.game && pin.game.live !== false) {
      changed = true;
      return { id: pin.id, game: { ...pin.game, live: false } };
    }
    return pin;
  });
  return changed ? { ...state, pins } : state;
}

function restorePins(): WatchPin[] {
  try {
    const value: unknown = JSON.parse(localStorage.getItem(PINS_KEY) ?? '[]');
    const saved = Array.isArray(value) ? value
      : value && typeof value === 'object' && 'pins' in value && Array.isArray(value.pins) ? value.pins : [];
    const pins: WatchPin[] = [];
    for (const item of saved) {
      const id = typeof item === 'string' ? item : item?.id;
      if (typeof id !== 'string' || !id || pins.some((pin) => pin.id === id)) continue;
      let game: WatchGame | undefined;
      if (item?.game) {
        try {
          const cached = readGames({ games: [item.game] })[0];
          game = { ...cached, boards: cached.boards.map((board) => ({ ...board, receivedAt: undefined })) };
        } catch { /* Keep the room link if an old cache is invalid. */ }
      }
      pins.push({ id, game: game?.gameId === id ? game : undefined });
      if (pins.length === 4) break;
    }
    return pins;
  } catch {
    return [];
  }
}

export function useWatchPins(snapshot: WatchGame[] | null) {
  const [state, dispatch] = useReducer(reducer, { ready: false, pins: [] });

  useEffect(() => { dispatch({ type: 'restore', pins: restorePins() }); }, []);
  useEffect(() => {
    if (state.ready && snapshot) dispatch({ type: 'snapshot', games: snapshot });
  }, [snapshot, state.ready]);
  useEffect(() => {
    if (!state.ready) return;
    const pins = state.pins.map((pin) => ({ ...pin, game: pin.game ? { ...pin.game,
      boards: pin.game.boards.map((board) => ({ ...board, receivedAt: undefined })),
    } : undefined }));
    try { localStorage.setItem(PINS_KEY, JSON.stringify({ version: 2, pins })); } catch { /* Storage may be unavailable. */ }
  }, [state.pins, state.ready]);

  const togglePin = useCallback((id: string, game?: WatchGame) => dispatch({ type: 'toggle', id, game }), []);
  const clearPins = useCallback(() => dispatch({ type: 'clear' }), []);
  return { ...state, togglePin, clearPins };
}
