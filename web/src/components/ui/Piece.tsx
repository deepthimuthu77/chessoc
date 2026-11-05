"use client";
import React from 'react';
import { motion, useMotionValue, useReducedMotion } from 'framer-motion';
import { prefersReducedMotion } from '@/lib/settings';

export const PIECE_POSITIONS: Record<string, { x: string; y: string }> = {
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
  interactive: boolean;
  onDrop: (fromSquare: string, point: { x: number; y: number }) => void;
  onPieceClick: (square: string, isDrag?: boolean) => void;
}

export const Piece = React.memo(function Piece({ char, col, row, squareName, interactive, onDrop, onPieceClick }: PieceProps) {
  const systemReducedMotion = useReducedMotion();
  const reduce = systemReducedMotion || prefersReducedMotion();
  const dragX = useMotionValue(0);
  const dragY = useMotionValue(0);
  const position = PIECE_POSITIONS[char];
  if (!position) return null;

  // 8 squares = 12.5% each
  const left = `${col * 12.5}%`;
  const top = `${row * 12.5}%`;

  return (
    <motion.div
      className={`absolute z-10 h-[12.5%] w-[12.5%] p-1 md:p-2 ${interactive ? 'pointer-events-auto cursor-grab active:cursor-grabbing' : 'pointer-events-none'}`}
      initial={false}
      style={{ x: dragX, y: dragY }}
      animate={{ left, top }}
      whileHover={interactive && !reduce ? { scale: 1.15 } : undefined}
      transition={reduce ? { duration: 0 } : { type: "spring", stiffness: 200, damping: 25 }}
      drag={interactive}
      dragMomentum={false}
      onClick={() => interactive && onPieceClick(squareName)}
      onDragStart={() => interactive && onPieceClick(squareName, true)}
      onDragEnd={(_, info) => {
        if (interactive) onDrop(squareName, { x: info.point.x, y: info.point.y });
        if (reduce) {
          dragX.jump(0);
          dragY.jump(0);
        }
      }}
      dragSnapToOrigin={!reduce}
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
});
