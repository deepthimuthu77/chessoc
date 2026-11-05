import { memo, useCallback, useEffect, useMemo, useRef, useState } from 'react';
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
  checkSquare?: string | null;
  orientation?: 'white' | 'black';
  interactive?: boolean;
  onMoveIntent?: (from: string, to: string) => void;
  /** Crazyhouse: when a reserve piece is selected, its legal drop squares. */
  dropTargets?: string[];
  /** Crazyhouse: a click while a reserve piece is selected (drop or cancel — the parent decides). */
  onDropSquare?: (square: string) => void;
  /** Premove: allow queuing a move on the opponent's turn (own pieces only, no legality check). */
  allowPremove?: boolean;
  myColor?: 'white' | 'black' | null;
  premove?: { from: string; to: string } | null;
  pendingMove?: { from: string; to: string } | null;
  onPremove?: (from: string, to: string) => void;
}

const FILES = ['a', 'b', 'c', 'd', 'e', 'f', 'g', 'h'];
const EMPTY_MOVES: Record<string, string[]> = {};
const EMPTY_TARGETS: string[] = [];

const PIECE_NAMES: Record<string, string> = { p: 'pawn', n: 'knight', b: 'bishop', r: 'rook', q: 'queen', k: 'king' };

/** A spoken description of a square for screen readers. */
function describeSquare(square: string, char: string | undefined, state: string): string {
  const piece = char ? `${char === char.toUpperCase() ? 'white' : 'black'} ${PIECE_NAMES[char.toLowerCase()]}` : 'empty';
  return `${square}, ${piece}${state}`;
}

function squareAt(col: number, row: number, flip: boolean): string {
  const file = flip ? 7 - col : col;
  const rank = flip ? row + 1 : 8 - row;
  return `${FILES[file]}${rank}`;
}

function gridOf(square: string, flip: boolean): { col: number; row: number } {
  const f = FILES.indexOf(square[0]);
  const r = Number(square[1]);
  return flip ? { col: 7 - f, row: r - 1 } : { col: f, row: 8 - r };
}

export const Board = memo(function Board({
  pieces,
  legalMoves = EMPTY_MOVES,
  lastMove = null,
  checkSquare = null,
  orientation = 'white',
  interactive = false,
  onMoveIntent,
  dropTargets = EMPTY_TARGETS,
  onDropSquare,
  allowPremove = false,
  myColor = null,
  premove = null,
  pendingMove = null,
  onPremove,
}: BoardProps) {
  const [selectedSquare, setSelectedSquare] = useState<string | null>(null);
  const [premoveFrom, setPremoveFrom] = useState<string | null>(null);
  const boardRef = useRef<HTMLDivElement>(null);
  const flip = orientation === 'black';
  const displayedPieces = useMemo(() => {
    if (!pendingMove) return pieces;
    const moving = pieces.find((piece) => piece.square === pendingMove.from);
    if (!moving) return pieces;
    return pieces.filter((piece) => piece.square !== pendingMove.from && piece.square !== pendingMove.to)
      .concat({ ...moving, square: pendingMove.to });
  }, [pieces, pendingMove]);
  const dropMode = dropTargets.length > 0;
  const dropSet = new Set(dropMode ? dropTargets : []);
  // Premoves are active only while it isn't our turn (interactive) and there's no drop in progress.
  const premoveMode = allowPremove && !interactive && !dropMode;

  const occupied = useMemo(() => new Set(pieces.map((p) => p.square)), [pieces]);
  const pieceBySquare = useMemo(() => new Map(pieces.map((p) => [p.square, p.char])), [pieces]);
  const targetSet = useMemo(() => new Set(selectedSquare ? legalMoves[selectedSquare] ?? [] : []), [selectedSquare, legalMoves]);

  function isOwnPiece(square: string): boolean {
    const ch = pieceBySquare.get(square);
    if (!ch || !myColor) return false;
    return (ch === ch.toUpperCase()) === (myColor === 'white');
  }

  function handlePremoveClick(square: string) {
    if (premoveFrom === null) {
      if (isOwnPiece(square)) setPremoveFrom(square);
    } else {
      onPremove?.(premoveFrom, square);
      setPremoveFrom(null);
    }
  }

  // Drop the transient from-selection whenever premove mode ends (e.g. our turn arrives).
  useEffect(() => {
    if (!premoveMode) {
      setPremoveFrom(null);
    }
  }, [premoveMode]);

  // Unified activation for clicks and keyboard (Enter/Space on a focused square).
  function handleSquareActivate(square: string) {
    if (!interactive) return;
    if (selectedSquare && targetSet.has(square)) {
      attemptMove(selectedSquare, square);
    } else if (occupied.has(square)) {
      setSelectedSquare(square);
    } else {
      setSelectedSquare(null);
    }
  }

  const attemptMove = useCallback((fromSquare: string, toSquare: string) => {
    const legal = (legalMoves[fromSquare] ?? []).includes(toSquare);
    if (!interactive || !legal) {
      setSelectedSquare(null);
      return;
    }
    onMoveIntent?.(fromSquare, toSquare);
    setSelectedSquare(null);
  }, [interactive, legalMoves, onMoveIntent]);

  const handlePieceClick = useCallback((square: string, isDrag = false) => {
    if (!interactive) return;
    if (dropMode) {
      onDropSquare?.(square); // clicking a piece cancels the drop (parent clears selection)
      return;
    }
    if (!isDrag && selectedSquare && targetSet.has(square)) {
      attemptMove(selectedSquare, square);
    } else {
      setSelectedSquare(square);
    }
  }, [interactive, dropMode, onDropSquare, selectedSquare, targetSet, attemptMove]);

  const handleDrop = useCallback((fromSquare: string, point: { x: number; y: number }) => {
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
    attemptMove(fromSquare, squareAt(col, row, flip));
  }, [interactive, flip, attemptMove]);

  return (
    <div
      ref={boardRef}
      aria-label="Chess board"
      aria-busy={!!pendingMove}
      className="relative isolate grid aspect-square w-full max-w-2xl grid-cols-8 grid-rows-8 overflow-hidden rounded-md border-[6px] border-zinc-900 shadow-[0_0_50px_rgba(0,0,0,0.5)]"
    >
      {Array.from({ length: 64 }, (_, index) => {
        const row = Math.floor(index / 8);
        const col = index % 8;
        const square = squareAt(col, row, flip);
        const isDark = (row + col) % 2 === 1;
        const isSelected = !dropMode && selectedSquare === square;
        const isTarget = !dropMode && selectedSquare !== null && targetSet.has(square);
        const isCapture = isTarget && occupied.has(square);
        const isDrop = dropSet.has(square);
        const isLast = lastMove !== null && (square === lastMove.from || square === lastMove.to);
        const isCheck = checkSquare === square;
        const isPremove = premove !== null && (square === premove.from || square === premove.to);
        const isPremoveSel = premoveFrom === square;

        const state = isSelected ? ', selected'
          : isPremove || isPremoveSel ? ', premove'
          : isDrop ? ', drop square'
          : isCapture ? ', capture move'
          : isTarget ? ', legal move'
          : isCheck ? ', in check' : '';

        return (
          <button
            key={square}
            type="button"
            aria-label={describeSquare(square, pieceBySquare.get(square), state)}
            aria-pressed={isSelected || isPremoveSel}
            disabled={!interactive && !premoveMode}
            onClick={() =>
              dropMode ? onDropSquare?.(square)
                : premoveMode ? handlePremoveClick(square)
                : handleSquareActivate(square)}
            className={`relative h-full w-full ${isDark ? 'bg-[#4b7399]' : 'bg-[#eae9d2]'} disabled:cursor-default`}
          >
            {isLast && <span className="pointer-events-none absolute inset-0 bg-yellow-300/30" />}
            {(isPremove || isPremoveSel) && <span className="pointer-events-none absolute inset-0 bg-orange-400/40" />}
            {isSelected && <span className="pointer-events-none absolute inset-0 bg-yellow-300/40" />}
            {isDrop && (
              <span className="pointer-events-none absolute inset-0 flex items-center justify-center">
                <span className="h-1/3 w-1/3 rounded-full bg-emerald-400/60 ring-2 ring-emerald-300/50" />
              </span>
            )}
            {isCheck && (
              <span
                className="pointer-events-none absolute inset-0"
                style={{ background: 'radial-gradient(circle, rgba(220,38,38,0.75) 0%, rgba(220,38,38,0) 70%)' }}
              />
            )}
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
                {square[1]}
              </span>
            )}
            {row === 7 && (
              <span className={`absolute bottom-1 right-1 text-[10px] font-bold ${isDark ? 'text-zinc-300' : 'text-zinc-700'}`}>
                {square[0]}
              </span>
            )}
          </button>
        );
      })}

      <div className="pointer-events-none absolute inset-0">
        <div className="pointer-events-none relative h-full w-full">
          {displayedPieces.map(({ char, square }) => {
            const { col, row } = gridOf(square, flip);
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
});
