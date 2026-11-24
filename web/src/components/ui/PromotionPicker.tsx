"use client";

import { AnimatePresence, motion, useReducedMotion } from 'framer-motion';
import { prefersReducedMotion } from '@/lib/settings';

type Piece = 'q' | 'r' | 'b' | 'n';

// Sprite columns on Chess_Pieces_Sprite.svg (600% × 200%); white row y=0%, black row y=100%.
const SPRITE_X: Record<Piece, string> = { q: '20%', b: '40%', n: '60%', r: '80%' };
const ORDER: Piece[] = ['q', 'r', 'b', 'n'];

interface PromotionPickerProps {
  open: boolean;
  color: 'white' | 'black';
  onPick: (piece: Piece) => void;
  onCancel: () => void;
}

export function PromotionPicker({ open, color, onPick, onCancel }: PromotionPickerProps) {
  const systemReduced = useReducedMotion();
  const reduce = systemReduced || prefersReducedMotion();
  const y = color === 'white' ? '0%' : '100%';

  return (
    <AnimatePresence>
      {open && (
        <motion.div
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          initial={reduce ? false : { opacity: 0 }}
          transition={reduce ? { duration: 0 } : undefined}
          onClick={onCancel}
          className="absolute inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm"
        >
          <motion.div
            animate={{ scale: 1, y: 0 }}
            exit={reduce ? undefined : { scale: 0.98, y: 6 }}
            initial={reduce ? false : { scale: 0.98, y: 8 }}
            transition={reduce ? { duration: 0 } : undefined}
            onClick={(e) => e.stopPropagation()}
            className="frosted-panel mx-4 flex flex-col items-center rounded-2xl p-8"
          >
            <span className="mb-6 text-xs font-bold uppercase tracking-[0.2em] text-accent-400">
              Promote to
            </span>
            <div className="flex gap-3">
              {ORDER.map((piece) => (
                <button
                  key={piece}
                  type="button"
                  aria-label={`promote to ${piece}`}
                  onClick={() => onPick(piece)}
                  className="h-16 w-16 rounded-xl border border-zinc-700 bg-zinc-800 p-1 transition-colors hover:border-accent-500 hover:bg-zinc-700"
                >
                  <div
                    className="h-full w-full"
                    style={{
                      backgroundImage: 'url(/Chess_Pieces_Sprite.svg)',
                      backgroundSize: '600% 200%',
                      backgroundPosition: `${SPRITE_X[piece]} ${y}`,
                      backgroundRepeat: 'no-repeat',
                    }}
                  />
                </button>
              ))}
            </div>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}
