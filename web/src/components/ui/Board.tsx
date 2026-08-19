import { useMemo, useRef, useState } from 'react';
import { Piece } from './Piece';

export interface BoardPiece {
  char: string;
  square: string;
}

interface BoardProps {
  pieces: BoardPiece[];
  /** Legal destinations per source square (from the server's move capabilities). */
  legalMoves?: Record<string, string[]>;
  lastMove?: { from: string; to: string } | null;
  interactive?: boolean;
  onMoveIntent?: (from: string, to: string) => void;
}

const FILES = ['a', 'b', 'c', 'd', 'e', 'f', 'g', 'h'];

export function Board({
  pieces,
  legalMoves = {},
  lastMove = null,
  interactive = false,
  onMoveIntent,
}: BoardProps) {
  const [selectedSquare, setSelectedSquare] = useState<string | null>(null);
  const boardRef = useRef<HTMLDivElement>(null);

  const occupied = useMemo(() => new Set(pieces.map((p) => p.square)), [pieces]);
  const targets = selectedSquare ? legalMoves[selectedSquare] ?? [] : [];
  const targetSet = new Set(targets);

  function attemptMove(fromSquare: string, toSquare: string) {
    const legal = (legalMoves[fromSquare] ?? []).includes(toSquare);
    if (!interactive || !legal) {
      setSelectedSquare(null);
      return;
    }
    onMoveIntent?.(fromSquare, toSquare);
    setSelectedSquare(null);
  }

  // a click on a piece captures (if it sits on a legal target) or selects it; a drag always selects
  function handlePieceClick(square: string, isDrag = false) {
    if (!interactive) return;
    if (!isDrag && selectedSquare && targetSet.has(square)) {
      attemptMove(selectedSquare, square);
    } else {
      setSelectedSquare(square);
    }
  }

  function handleDrop(fromSquare: string, point: { x: number; y: number }) {
    if (!boardRef.current || !interactive) return;

    const rect = boardRef.current.getBoundingClientRect();
    const x = point.x - rect.left;
    const y = point.y - rect.top;

    if (x < 0 || x >= rect.width || y < 0 || y >= rect.height) {
      setSelectedSquare(null);
      return;
    }

    const col = Math.floor((x / rect.width) * 8);
    const row = Math.floor((y / rect.height) * 8);
    attemptMove(fromSquare, `${FILES[col]}${8 - row}`);
  }

  return (
    <div
      ref={boardRef}
      aria-label="Chess board"
      className="relative grid aspect-square w-full max-w-2xl grid-cols-8 grid-rows-8 overflow-hidden rounded-md border-[6px] border-zinc-900 shadow-[0_0_50px_rgba(0,0,0,0.5)]"
    >
      {Array.from({ length: 64 }, (_, index) => {
        const row = Math.floor(index / 8);
        const col = index % 8;
        const square = `${FILES[col]}${8 - row}`;
        const isDark = (row + col) % 2 === 1;
        const isSelected = selectedSquare === square;
        const isTarget = selectedSquare !== null && targetSet.has(square);
        const isCapture = isTarget && occupied.has(square);
        const isLast = lastMove !== null && (square === lastMove.from || square === lastMove.to);

        return (
          <button
            key={square}
            type="button"
            aria-label={square}
            disabled={!interactive}
            onClick={() => selectedSquare && attemptMove(selectedSquare, square)}
            className={`relative h-full w-full ${isDark ? 'bg-[#4b7399]' : 'bg-[#eae9d2]'} disabled:cursor-default`}
          >
            {isLast && <span className="pointer-events-none absolute inset-0 bg-yellow-300/30" />}
            {isSelected && <span className="pointer-events-none absolute inset-0 bg-yellow-300/40" />}
            {isTarget && !isCapture && (
              <span className="pointer-events-none absolute inset-0 flex items-center justify-center">
                <span className="h-1/3 w-1/3 rounded-full bg-black/25" />
              </span>
            )}
            {isCapture && (
              <span className="pointer-events-none absolute inset-[7%] rounded-full border-4 border-black/25 md:border-[6px]" />
            )}
            {col === 0 && (
              <span className={`absolute left-1 top-1 text-[10px] font-bold ${isDark ? 'text-zinc-300' : 'text-zinc-700'}`}>
                {8 - row}
              </span>
            )}
            {row === 7 && (
              <span className={`absolute bottom-1 right-1 text-[10px] font-bold ${isDark ? 'text-zinc-300' : 'text-zinc-700'}`}>
                {FILES[col]}
              </span>
            )}
          </button>
        );
      })}

      <div className="pointer-events-none absolute inset-0">
        <div className="pointer-events-auto relative h-full w-full">
          {pieces.map(({ char, square }) => {
            const col = FILES.indexOf(square[0]);
            const row = 8 - Number(square[1]);

            return (
              <Piece
                key={`${char}-${square}`}
                char={char}
                col={col}
                row={row}
                squareName={square}
                interactive={interactive}
                onPieceClick={handlePieceClick}
                onDrop={handleDrop}
              />
            );
          })}
        </div>
      </div>
    </div>
  );
}
