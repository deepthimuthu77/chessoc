import { AnimatePresence, motion } from 'framer-motion';

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
}

export function GameOverModal({ isOpen, result, onRematch }: GameOverModalProps) {
  return (
    <AnimatePresence>
      {isOpen && (
        <motion.div
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          className="absolute inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm"
          initial={{ opacity: 0 }}
        >
          <motion.div
            animate={{ scale: 1, y: 0 }}
            className="mx-4 flex w-full max-w-sm flex-col items-center rounded-3xl border border-zinc-700 bg-zinc-900 p-8 shadow-2xl"
            exit={{ scale: 0.9, y: 20 }}
            initial={{ scale: 0.9, y: 20 }}
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

            {onRematch && (
              <button
                type="button"
                onClick={onRematch}
                className="w-full rounded-xl bg-blue-600 px-8 py-3 font-bold text-white shadow-[0_0_15px_rgba(37,99,235,0.4)] transition-colors hover:bg-blue-500"
              >
                Play Again
              </button>
            )}
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}