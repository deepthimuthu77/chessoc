'use client';

import { useId, useRef } from 'react';
import { AnimatePresence, motion, useReducedMotion } from 'framer-motion';
import Link from 'next/link';
import { prefersReducedMotion } from '@/lib/settings';
import { useDialogFocus } from './useDialogFocus';

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
            className="flex max-h-[calc(100dvh-2rem)] w-full max-w-sm flex-col items-center overflow-y-auto rounded-3xl border border-zinc-700 bg-zinc-900 p-8 shadow-2xl"
            exit={reduce ? undefined : { scale: 0.9, y: 20 }}
            initial={reduce ? false : { scale: 0.9, y: 20 }}
          >
            <h2 id={titleId} className="mb-2 text-4xl font-black text-transparent bg-clip-text bg-linear-to-br from-white to-zinc-400">
              {result?.kind === 'aborted' ? 'Aborted' : 'Game Over'}
            </h2>

            <p id={descriptionId} className="mb-8 text-center text-lg text-zinc-300">
              {result?.kind === 'aborted' ? (
                <span className="text-zinc-400">No result — the game was aborted.</span>
              ) : (
                <>
                  {result?.winner ? (winnerLabel ?? result.winner) + ' wins by ' : 'Draw by '}
                  <span className="font-bold text-blue-400 capitalize">{result?.kind.replaceAll('_', ' ') ?? 'draw'}</span>
                </>
              )}
            </p>

            <div className="flex w-full flex-col gap-3">
              {onDismiss && <button type="button" onClick={onDismiss} className="w-full rounded-xl border border-zinc-600 px-5 py-3 font-bold text-white hover:bg-zinc-800">Inspect final board</button>}
              {reviewHref && <Link href={reviewHref} className="w-full rounded-xl border border-blue-500/50 px-5 py-3 text-center font-bold text-blue-300 hover:bg-blue-500/10">Review game</Link>}
              {onRematch && !isSpectator && (
              <button
                type="button"
                disabled={rematchPending}
                onClick={onRematch}
                className="w-full rounded-xl bg-blue-600 px-8 py-3 font-bold text-white shadow-[0_0_15px_rgba(37,99,235,0.4)] transition-colors hover:bg-blue-500 disabled:cursor-wait disabled:bg-blue-800 disabled:text-blue-200"
              >
                {rematchPending ? (rematchLabel === 'New game' ? 'Creating game…' : 'Rematch requested') : rematchLabel}
              </button>
              )}
              {error && <p role="alert" className="text-sm text-rose-300">{error}</p>}
              {isSpectator && <Link href="/watch" className="w-full rounded-xl border border-zinc-700 px-5 py-3 text-center font-bold text-zinc-300 hover:bg-zinc-800">Back to Watch</Link>}
              <Link
                href="/"
                className="w-full rounded-xl border border-zinc-700 px-8 py-3 text-center font-bold text-zinc-300 transition-colors hover:bg-zinc-800"
              >
                Back to lobby
              </Link>
            </div>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}
