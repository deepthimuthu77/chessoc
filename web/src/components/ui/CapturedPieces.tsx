"use client";
import React from 'react';

const PIECE_POSITIONS: Record<string, { x: string; y: string }> = {
  Q: { x: '20%', y: '0%' },
  B: { x: '40%', y: '0%' },
  N: { x: '60%', y: '0%' },
  R: { x: '80%', y: '0%' },
  P: { x: '100%', y: '0%' },
  q: { x: '20%', y: '100%' },
  b: { x: '40%', y: '100%' },
  n: { x: '60%', y: '100%' },
  r: { x: '80%', y: '100%' },
  p: { x: '100%', y: '100%' },
};

export function CapturedPieces({ pieces, score }: { pieces: string[], score: number }) {
  // Sort pieces by value (Q, R, B, N, P)
  const order = ['Q', 'q', 'R', 'r', 'B', 'b', 'N', 'n', 'P', 'p'];
  const sorted = [...pieces].sort((a, b) => order.indexOf(a) - order.indexOf(b));

  return (
    <div className="flex items-center h-6 mt-2 overflow-hidden">
      <div className="flex -space-x-2">
        {sorted.map((char, i) => {
          const position = PIECE_POSITIONS[char];
          if (!position) return null;
          
          const isWhite = char === char.toUpperCase();
          const outlineFilter = isWhite
            ? 'drop-shadow(1px 0px 0px black) drop-shadow(-1px 0px 0px black) drop-shadow(0px 1px 0px black) drop-shadow(0px -1px 0px black)'
            : 'drop-shadow(1px 0px 0px white) drop-shadow(-1px 0px 0px white) drop-shadow(0px 1px 0px white) drop-shadow(0px -1px 0px white)';

          return (
            <div 
              key={i}
              className="w-6 h-6 shrink-0 relative z-10"
              style={{
                backgroundImage: 'url(/Chess_Pieces_Sprite.svg)',
                backgroundSize: '600% 200%',
                backgroundPosition: `${position.x} ${position.y}`,
                backgroundRepeat: 'no-repeat',
                filter: outlineFilter,
              }}
            />
          );
        })}
      </div>
      {score > 0 && (
        <span className="ml-3 text-xs font-bold text-zinc-400">+{score}</span>
      )}
    </div>
  );
}
