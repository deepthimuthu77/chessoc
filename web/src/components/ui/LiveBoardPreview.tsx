"use client";

import { memo, useMemo } from 'react';
import { PIECE_POSITIONS } from './Piece';
import { fenToPieces, lastMoveSquares, parseHand } from '@/lib/view-model';

const FILES = ['a', 'b', 'c', 'd', 'e', 'f', 'g', 'h'];

const PIECE_NAMES: Record<string, string> = { p: 'pawn', n: 'knight', b: 'bishop', r: 'rook', q: 'queen' };

export const LiveBoardPreview = memo(function LiveBoardPreview({
  fen,
  lastMove,
  moveCount,
  label,
  showPockets = false,
  sidePockets = false,
}: {
  fen: string;
  lastMove: string;
  moveCount: number;
  label: string;
  showPockets?: boolean;
  sidePockets?: boolean;
}) {
  const pieces = useMemo(() => fenToPieces(fen), [fen]);
  const lastMoveSquaresValue = useMemo(() => lastMoveSquares(lastMove ? [lastMove] : []), [lastMove]);
  const reserves = useMemo(() => ({ white: parseHand(fen, 'white'), black: parseHand(fen, 'black') }), [fen]);
  const pockets = showPockets || fen.split(' ')[0].includes('[');

  return (
    <div className={pockets && sidePockets ? 'relative w-full pr-12' : 'w-full'}>
      {pockets && <Pocket color="black" pieces={reserves.black} label={label} side={sidePockets} />}
    <div
      role="img"
      aria-label={`${label ? `Board ${label}, ` : ''}chess position after ${moveCount} moves`}
      className="relative grid aspect-square w-full grid-cols-8 overflow-hidden border border-zinc-800"
    >
      {Array.from({ length: 64 }, (_, index) => {
        const row = Math.floor(index / 8);
        const col = index % 8;
        const square = `${FILES[col]}${8 - row}`;
        const highlighted = lastMoveSquaresValue
          && (lastMoveSquaresValue.from === square || lastMoveSquaresValue.to === square);
        return (
          <div
            key={square}
            className={`relative aspect-square ${(row + col) % 2 ? 'board-dark' : 'board-light'}`}
          >
            {highlighted && <span className="absolute inset-0 board-highlight" />}
          </div>
        );
      })}
      <div className="pointer-events-none absolute inset-0">
        {pieces.map(({ char, square }) => {
          const position = PIECE_POSITIONS[char];
          if (!position) return null;
          const col = FILES.indexOf(square[0]);
          const row = 8 - Number(square[1]);
          return (
            <span
              key={`${char}-${square}`}
              className="absolute h-[12.5%] w-[12.5%] p-[2%] drop-shadow-md"
              style={{ left: `${col * 12.5}%`, top: `${row * 12.5}%` }}
            >
              <span
                className="block h-full w-full"
                style={{
                  backgroundImage: 'url(/Chess_Pieces_Sprite.svg)',
                  backgroundPosition: `${position.x} ${position.y}`,
                  backgroundRepeat: 'no-repeat',
                  backgroundSize: '600% 200%',
                }}
              />
            </span>
          );
        })}
      </div>
      {label && (
        <span className="absolute left-1.5 top-1.5 bg-zinc-950/80 px-1.5 py-0.5 text-[10px] font-bold text-zinc-200">
          {label}
        </span>
      )}
    </div>
      {pockets && <Pocket color="white" pieces={reserves.white} label={label} side={sidePockets} />}
    </div>
  );
});

const Pocket = memo(function Pocket({ color, pieces, label, side }: { color: 'white' | 'black'; pieces: string[]; label: string; side?: boolean }) {
  const counts = Object.entries(PIECE_NAMES).map(([piece, name]) => ({
    piece: color === 'white' ? piece.toUpperCase() : piece,
    name,
    count: pieces.filter((value) => value.toLowerCase() === piece).length,
  })).filter(({ count }) => count > 0);
  const description = counts.map(({ name, count }) => `${count} ${name}${count === 1 ? '' : 's'}`).join(', ') || 'empty';
  return (
    <div role="group" aria-label={`${label ? `Board ${label}, ` : ''}${color} pocket: ${description}`}
      className={`flex min-h-0 flex-nowrap gap-1 bg-zinc-900 px-1 py-0.5 text-[10px] text-zinc-300 [color-scheme:dark] [scrollbar-width:thin] ${side ? `absolute right-0 h-1/2 w-12 ${color === 'black' ? 'top-0' : 'bottom-0'} flex-col items-start overflow-x-hidden overflow-y-auto` : 'h-7 items-center overflow-x-auto'}`}>
      <span className="mr-1 shrink-0 capitalize">{color}{!side && ' pocket'}</span>
      {counts.length === 0 ? <span className="text-zinc-500">Empty</span> : counts.map(({ piece, name, count }) => (
        <span key={piece} title={`${count} ${color} ${name}${count === 1 ? '' : 's'}`} aria-hidden="true" className="flex shrink-0 items-center">
          <span className="inline-block h-5 w-5 rounded bg-zinc-300" style={{ backgroundImage: 'url(/Chess_Pieces_Sprite.svg)',
            backgroundPosition: `${PIECE_POSITIONS[piece].x} ${PIECE_POSITIONS[piece].y}`,
            backgroundRepeat: 'no-repeat', backgroundSize: '600% 200%' }} />
          <span>×{count}</span>
        </span>
      ))}
    </div>
  );
});
