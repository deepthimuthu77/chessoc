"use client";

import { PIECE_POSITIONS } from './Piece';

const ORDER = ['Q', 'R', 'B', 'N', 'P'];
const NAMES: Record<string, string> = { Q: 'queen', R: 'rook', B: 'bishop', N: 'knight', P: 'pawn' };

interface HandProps {
  /** Colour-cased reserve letters (e.g. ['P','P','n'] parsed from the FEN). */
  pieces: string[];
  /** Uppercase letter currently selected for dropping. */
  selected?: string | null;
  /** Only your own hand is interactive, and only on your turn. */
  interactive?: boolean;
  onSelect?: (pieceUpper: string) => void;
}

/** Crazyhouse reserve: piece types with counts; clicking one arms a drop. */
export function Hand({ pieces, selected = null, interactive = false, onSelect }: HandProps) {
  if (pieces.length === 0) {
    return null;
  }
  const counts: Record<string, number> = {};
  for (const ch of pieces) {
    counts[ch] = (counts[ch] ?? 0) + 1;
  }
  const entries = Object.entries(counts).sort(
    (a, b) => ORDER.indexOf(a[0].toUpperCase()) - ORDER.indexOf(b[0].toUpperCase()),
  );

  return (
    <div className="flex flex-wrap gap-1.5">
      {entries.map(([char, count]) => {
        const upper = char.toUpperCase();
        const pos = PIECE_POSITIONS[char];
        const on = selected === upper;
        return (
          <button
            key={char}
            type="button"
            disabled={!interactive}
            aria-label={`${interactive ? 'Drop ' : ''}${char === upper ? 'white' : 'black'} ${NAMES[upper]} (${count} in reserve)`}
            aria-pressed={on}
            onClick={() => onSelect?.(upper)}
            className={`relative h-10 w-10 rounded-lg border transition-colors disabled:cursor-default ${
              on ? 'border-accent-400 bg-accent-400/15' : 'border-zinc-600 bg-zinc-300 enabled:hover:border-accent-400'
            }`}
          >
            <span
              className="absolute inset-1 select-none"
              style={{
                backgroundImage: 'url(/Chess_Pieces_Sprite.svg)',
                backgroundSize: '600% 200%',
                backgroundPosition: `${pos.x} ${pos.y}`,
                backgroundRepeat: 'no-repeat',
              }}
            />
            {count > 1 && (
              <span className="absolute -bottom-1 -right-1 rounded-full bg-zinc-800 px-1 text-[10px] font-bold text-zinc-200">
                {count}
              </span>
            )}
          </button>
        );
      })}
    </div>
  );
}
