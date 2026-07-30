import React from 'react';
import { motion, AnimatePresence } from 'framer-motion';

interface GameOverModalProps {
  isOpen: boolean;
  reason: 'checkmate' | 'stalemate' | 'draw' | 'threefold' | 'insufficient';
  winner: 'w' | 'b' | null;
  onRestart: () => void;
}

export function GameOverModal({ isOpen, reason, winner, onRestart }: GameOverModalProps) {
  return (
    <AnimatePresence>
      {isOpen && (
        <motion.div 
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          className="absolute inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm"
        >
          <motion.div 
            initial={{ scale: 0.9, y: 20 }}
            animate={{ scale: 1, y: 0 }}
            exit={{ scale: 0.9, y: 20 }}
            className="flex flex-col items-center bg-zinc-900 border border-zinc-700 p-8 rounded-3xl shadow-2xl max-w-sm w-full mx-4"
          >
            <h2 className="text-4xl font-black text-transparent bg-clip-text bg-gradient-to-br from-white to-zinc-400 mb-2">
              Game Over
            </h2>
            
            <p className="text-zinc-300 text-lg mb-8 text-center">
              {winner === 'w' && 'White wins by '}
              {winner === 'b' && 'Black wins by '}
              {!winner && 'Draw by '}
              <span className="font-bold text-blue-400 capitalize">{reason}</span>
            </p>

            <button 
              onClick={onRestart}
              className="px-8 py-3 w-full bg-blue-600 hover:bg-blue-500 text-white font-bold rounded-xl transition-colors shadow-[0_0_15px_rgba(37,99,235,0.4)]"
            >
              Play Again
            </button>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}
