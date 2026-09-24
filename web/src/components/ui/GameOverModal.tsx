import { AnimatePresence, motion } from 'framer-motion';
import Link from 'next/link';
import { prefersReducedMotion } from '@/lib/settings';

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
}

export function GameOverModal({ isOpen, result, onRematch, rematchPending = false }: GameOverModalProps) {
  const reduce = prefersReducedMotion();
  return (
    <AnimatePresence>
      {isOpen && (
        <motion.div
          role="alertdialog"
          aria-modal="true"
          aria-label="Game over"
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm"
          initial={{ opacity: 0 }}
        >
          <motion.div
            animate={{ scale: 1, y: 0 }}
            className="mx-4 flex w-full max-w-sm flex-col items-center rounded-3xl border border-zinc-700 bg-zinc-900 p-8 shadow-2xl"
            exit={reduce ? undefined : { scale: 0.9, y: 20 }}
            initial={reduce ? false : { scale: 0.9, y: 20 }}
          >
            <span className="mb-2 text-4xl font-black text-transparent bg-clip-text bg-linear-to-br from-white to-zinc-400">
              {result?.kind === 'aborted' ? 'Aborted' : 'Game Over'}
            </span>

            <p className="mb-8 text-center text-lg text-zinc-300">
              {result?.kind === 'aborted' ? (
                <span className="text-zinc-400">No result — the game was aborted.</span>
              ) : (
                <>
                  {result?.winner ? result.winner + ' wins by ' : 'Draw by '}
                  <span className="font-bold text-blue-400 capitalize">{result?.kind ?? 'draw'}</span>
                </>
              )}
            </p>

            <div className="flex w-full flex-col gap-3">
              {onRematch && (
              <button
                type="button"
                disabled={rematchPending}
                onClick={onRematch}
                className="w-full rounded-xl bg-blue-600 px-8 py-3 font-bold text-white shadow-[0_0_15px_rgba(37,99,235,0.4)] transition-colors hover:bg-blue-500 disabled:cursor-wait disabled:bg-blue-800 disabled:text-blue-200"
              >
                {rematchPending ? 'Rematch requested' : 'Play Again'}
              </button>
              )}
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
