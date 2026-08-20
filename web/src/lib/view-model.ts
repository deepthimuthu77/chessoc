// Render adapters: turn the server projection + capabilities into render-ready values. These are
// presentation transforms only (FEN → placement, capability → targets, clock formatting) — never
// game decisions. Helpers for capabilities without UI yet are here so the hook can surface them once
// their UI exists (see doc/FRONTEND_INTEGRATION.md).

import type { BoardPiece } from '@/components/ui/Board';
import type { Capability, ClockSample, Color, GameResult } from './protocol';

const FILES = ['a', 'b', 'c', 'd', 'e', 'f', 'g', 'h'];

export function fenToPieces(fen: string): BoardPiece[] {
  const board = fen.split(' ')[0].replace(/\[[^\]]*\]/, '').replace(/~/g, '');
  const pieces: BoardPiece[] = [];
  board.split('/').forEach((rank, r) => {
    let file = 0;
    for (const ch of rank) {
      if (ch >= '1' && ch <= '8') {
        file += Number(ch);
      } else {
        pieces.push({ char: ch, square: `${FILES[file]}${8 - r}` });
        file += 1;
      }
    }
  });
  return pieces;
}

const moveCaps = (caps: Capability[]) => caps.filter((c) => c.kind === 'move');

/** Legal destinations grouped by source square, so the board can scope highlights to one piece. */
export function legalMovesBySource(caps: Capability[]): Record<string, string[]> {
  const map: Record<string, string[]> = {};
  for (const c of moveCaps(caps)) {
    const uci = c.params.uci;
    if (uci.length >= 4 && uci[1] !== '@') {
      const from = uci.slice(0, 2);
      const to = uci.slice(2, 4);
      (map[from] ??= []).push(to);
    }
  }
  return map;
}

/** Source and destination of the most recent move, for highlighting. */
export function lastMoveSquares(moves: string[]): { from: string; to: string } | null {
  if (moves.length === 0) {
    return null;
  }
  const uci = moves[moves.length - 1];
  if (uci[1] === '@') {
    return { from: uci.slice(2, 4), to: uci.slice(2, 4) };
  }
  return { from: uci.slice(0, 2), to: uci.slice(2, 4) };
}

/** The exact move capability for from→to, preferring a queen promotion when several exist. */
export function findMove(caps: Capability[], from: string, to: string): Capability | undefined {
  const matches = moveCaps(caps).filter((c) => c.params.uci.startsWith(from + to));
  return (
    matches.find((c) => c.params.uci === `${from}${to}q`) ??
    matches.find((c) => c.params.uci === `${from}${to}`) ??
    matches[0]
  );
}

/** Promotion options (>1 means a picker is required — no UI yet). */
export function promotionChoices(caps: Capability[], from: string, to: string): Capability[] {
  return moveCaps(caps).filter((c) => c.params.uci.startsWith(from + to) && c.params.uci.length === 5);
}

export const findResign = (caps: Capability[]) => caps.find((c) => c.kind === 'resign');
export const findAbort = (caps: Capability[]) => caps.find((c) => c.kind === 'abort');
export const findDrawOffer = (caps: Capability[]) => caps.find((c) => c.kind === 'offer_draw');
export const findDrawResponse = (caps: Capability[], accept: boolean) =>
  caps.find((c) => c.kind === 'respond_draw' && c.params.accept === String(accept));
export const findRematch = (caps: Capability[]) => caps.find((c) => c.kind === 'rematch');

const PIECE_VALUE: Record<string, number> = { p: 1, n: 3, b: 3, r: 5, q: 9 };

/** Material worth of a captured-piece list (presentation transform over server-provided data). */
export function materialScore(pieces: string[]): number {
  return pieces.reduce((sum, p) => sum + (PIECE_VALUE[p.toLowerCase()] ?? 0), 0);
}

export function formatClock(ms: number): string {
  const total = Math.floor(Math.max(0, ms) / 1000);
  return `${Math.floor(total / 60)}:${(total % 60).toString().padStart(2, '0')}`;
}

/** Interpolate the active side's clock between server samples (display only, §6). */
export function displayClocks(sample: ClockSample, receivedAt: number, live: boolean): { white: string; black: string } {
  const elapsed = live ? Date.now() - receivedAt : 0;
  const white = sample.activeSide === 'white' ? sample.whiteRemainingMs - elapsed : sample.whiteRemainingMs;
  const black = sample.activeSide === 'black' ? sample.blackRemainingMs - elapsed : sample.blackRemainingMs;
  return { white: formatClock(white), black: formatClock(black) };
}

export type ModalKind =
  | 'checkmate'
  | 'stalemate'
  | 'timeout'
  | 'resignation'
  | 'draw_agreed'
  | 'repetition'
  | 'insufficient'
  | 'aborted';

const REASON_KIND: Record<string, ModalKind> = {
  checkmate: 'checkmate',
  stalemate: 'stalemate',
  resign: 'resignation',
  abort: 'aborted',
  agreement: 'draw_agreed',
  timeout: 'timeout',
  timeout_insufficient_material: 'insufficient',
  draw_threefold: 'repetition',
  draw_fifty_move: 'draw_agreed',
  draw_insufficient_material: 'insufficient',
};

export function toModalResult(result: GameResult): { kind: ModalKind; winner?: Color } {
  const kind = REASON_KIND[result.reason]
    ?? (result.type === 'ABORTED' ? 'aborted' : result.type === 'DRAW' ? 'draw_agreed' : 'checkmate');
  return { kind, winner: result.winner ?? undefined };
}
