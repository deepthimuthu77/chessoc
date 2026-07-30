"use client";
import React from 'react';
import { motion } from 'framer-motion';

const PIECE_POSITIONS: Record<string, { x: string; y: string }> = {
  K: { x: '0%', y: '0%' },
  Q: { x: '20%', y: '0%' },
  B: { x: '40%', y: '0%' },
  N: { x: '60%', y: '0%' },
  R: { x: '80%', y: '0%' },
  P: { x: '100%', y: '0%' },
  k: { x: '0%', y: '100%' },
  q: { x: '20%', y: '100%' },
  b: { x: '40%', y: '100%' },
  n: { x: '60%', y: '100%' },
  r: { x: '80%', y: '100%' },
  p: { x: '100%', y: '100%' },
};

interface PieceProps {
  char: string;
  col: number;
  row: number;
  squareName: string;
  onDrop: (fromSquare: string, point: { x: number; y: number }) => void;
  onPieceClick: (square: string) => void;
}

export function Piece({ char, col, row, squareName, onDrop, onPieceClick }: PieceProps) {
  const position = PIECE_POSITIONS[char];
  if (!position) return null;

  // 8 squares = 12.5% each
  const left = `${col * 12.5}%`;
  const top = `${row * 12.5}%`;

  return (
    <motion.div
      className="absolute w-[12.5%] h-[12.5%] z-10 cursor-grab active:cursor-grabbing p-1 md:p-2"
      initial={false}
      animate={{ left, top }}
      whileHover={{ scale: 1.15 }}
      transition={{ type: "spring", stiffness: 200, damping: 25 }}
      drag
      dragMomentum={false}
      onDragStart={() => onPieceClick(squareName)}
      onDragEnd={(event, info) => {
        // Fire drop event with screen coordinates
        onDrop(squareName, { x: info.point.x, y: info.point.y });
      }}
      // Snap piece back to origin instantly if not dropped on a valid square. 
      // The state update in Board will override this if it's a valid move.
      dragSnapToOrigin
    >
      <div
        className="w-full h-full select-none drop-shadow-xl"
        style={{
          backgroundImage: 'url(/Chess_Pieces_Sprite.svg)',
          backgroundSize: '600% 200%',
          backgroundPosition: `${position.x} ${position.y}`,
          backgroundRepeat: 'no-repeat',
        }}
      />
    </motion.div>
  );
}
