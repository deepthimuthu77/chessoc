'use client';

import { useId, useRef } from 'react';
import { AnimatePresence, motion, useReducedMotion } from 'framer-motion';
import Link from 'next/link';
import { prefersReducedMotion } from '@/lib/settings';
import { useDialogFocus } from './useDialogFocus';
import { Icon } from './Icon';

interface GameOverModalProps {
  isOpen: boolean;
  result:
    | {
        kind:
          | 'checkmate' | 'stalemate' | 'timeout' | 'resignation'
          | 'draw_agreed' | 'repetition' | 'insufficient' | 'aborted';
        winner?: 'white' | 'black';
      }
    | undefined;
  onRematch?: () => void;
  rematchPending?: boolean;
  onDismiss?: () => void;
  reviewHref?: string;
  isSpectator?: boolean;
  rematchLabel?: string;
  winnerLabel?: string;
  error?: string | null;
}

export function GameOverModal({ isOpen, result, onRematch, rematchPending = false, onDismiss, reviewHref, isSpectator = false, rematchLabel = 'Play again', winnerLabel, error }: GameOverModalProps) {
  const systemReducedMotion = useReducedMotion();
  const reduce = systemReducedMotion || prefersReducedMotion();
  const dialogRef = useRef<HTMLDivElement>(null);
  const titleId = useId();
  const descriptionId = useId();
  useDialogFocus(dialogRef, isOpen, onDismiss);
  return (
    <AnimatePresence>
      {isOpen && (
        <motion.div
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          className="fixed inset-0 z-50 flex items-center justify-center overflow-y-auto bg-black/60 p-4 backdrop-blur-sm"
          initial={{ opacity: 0 }}
          transition={reduce ? { duration: 0 } : undefined}
        >
          <motion.div
            ref={dialogRef}
            role="dialog"
            aria-modal="true"
            aria-labelledby={titleId}
            aria-describedby={descriptionId}
            tabIndex={-1}
            animate={{ scale: 1, y: 0 }}
            className="frosted-panel flex max-h-[calc(100dvh-2rem)] w-full max-w-sm flex-col items-center overflow-y-auto rounded-2xl p-8"
            exit={reduce ? undefined : { scale: 0.98, y: 6 }}
            initial={reduce ? false : { scale: 0.98, y: 8 }}
          >
            <span className="icon-well mb-5"><Icon name={result?.kind === 'aborted' ? 'board' : 'trophy'} size={21} /></span>
            <p className="eyebrow mb-3">A MOMENT OFF THE CLOCK</p>
            <h2 id={titleId} className="mb-3 font-serif text-4xl font-normal tracking-tight text-zinc-100">
              {result?.kind === 'aborted' ? 'Aborted' : 'Game Over'}
            </h2>

            <p id={descriptionId} className="mb-7 text-center text-sm leading-relaxed text-zinc-300">
              {result?.kind === 'aborted' ? (
                <span className="text-zinc-400">No result — the game was aborted.</span>
              ) : (
                <>
                  {result?.winner ? (winnerLabel ?? result.winner) + ' wins by ' : 'Draw by '}
                  <span className="font-bold text-accent-400 capitalize">{result?.kind.replaceAll('_', ' ') ?? 'draw'}</span>
                </>
              )}
            </p>

            <div className="flex w-full flex-col gap-3">
              {onRematch && !isSpectator && (
              <button
                type="button"
                disabled={rematchPending}
                onClick={onRematch}
                className="button-primary w-full"
              >
                {rematchPending ? (rematchLabel === 'New game' ? 'Creating game…' : 'Rematch requested') : rematchLabel}
              </button>
              )}
              {reviewHref && <Link href={reviewHref} className="button-secondary w-full"><Icon name="history" size={16} />Review game</Link>}
              {onDismiss && <button type="button" onClick={onDismiss} className="button-quiet w-full">Inspect final board</button>}
              {error && <p role="alert" className="text-sm text-rose-300">{error}</p>}
              {isSpectator && <Link href="/watch" className="button-secondary w-full"><Icon name="watch" size={16} />Back to Watch</Link>}
              <Link
                href="/"
                className="button-quiet w-full border-t border-zinc-800"
              >
                Go to dashboard
              </Link>
            </div>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}
