"use client";
import React, { useState, useEffect } from 'react';
import { Chess } from 'chess.js';
import { Board } from '@/components/ui/Board';
import { MoveHistory } from '@/components/ui/MoveHistory';
import { CapturedPieces } from '@/components/ui/CapturedPieces';
import { GameOverModal } from '@/components/ui/GameOverModal';

const PIECE_VALUES: Record<string, number> = { p: 1, n: 3, b: 3, r: 5, q: 9, k: 0 };

export default function RoomPage() {
  const initialFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
  const [game, setGame] = useState<Chess | null>(null);
  const [whiteTime, setWhiteTime] = useState(300);
  const [blackTime, setBlackTime] = useState(300);

  useEffect(() => {
    setGame(new Chess(initialFen));
  }, []);

  useEffect(() => {
    if (!game || game.isGameOver()) return;
    const interval = setInterval(() => {
      if (game.turn() === 'w') {
        setWhiteTime(t => Math.max(0, t - 1));
      } else {
        setBlackTime(t => Math.max(0, t - 1));
      }
    }, 1000);
    return () => clearInterval(interval);
  }, [game]);

  if (!game) return null;

  const formatTime = (seconds: number) => {
    const m = Math.floor(seconds / 60);
    const s = seconds % 60;
    return `${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`;
  };

  const handleMove = (from: string, to: string) => {
    try {
      // Create a copy that preserves move history using PGN
      const newGame = new Chess();
      newGame.loadPgn(game.pgn());
      const move = newGame.move({ from, to, promotion: 'q' });
      if (move) {
        setGame(newGame);
      }
    } catch (e) {
      // Invalid move
    }
  };

  const handleRestart = () => {
    setGame(new Chess(initialFen));
    setWhiteTime(300);
    setBlackTime(300);
  };

  // Calculate captured pieces
  const currentPieces = { w: [] as string[], b: [] as string[] };
  const board = game.board();
  for (let row = 0; row < 8; row++) {
    for (let col = 0; col < 8; col++) {
      const p = board[row][col];
      if (p) currentPieces[p.color].push(p.type);
    }
  }

  const initialCounts = { p: 8, n: 2, b: 2, r: 2, q: 1 };
  const capturedByWhite: string[] = []; // Black pieces captured by White
  const capturedByBlack: string[] = []; // White pieces captured by Black

  let whiteScore = 0;
  let blackScore = 0;

  for (const [type, count] of Object.entries(initialCounts)) {
    const currentWhite = currentPieces.w.filter(t => t === type).length;
    const currentBlack = currentPieces.b.filter(t => t === type).length;
    
    // If black has less than initial, white captured them
    for (let i = 0; i < count - currentBlack; i++) {
      capturedByWhite.push(type);
      whiteScore += PIECE_VALUES[type];
    }
    // If white has less than initial, black captured them
    for (let i = 0; i < count - currentWhite; i++) {
      capturedByBlack.push(type.toUpperCase());
      blackScore += PIECE_VALUES[type];
    }
  }

  const whiteAdvantage = Math.max(0, whiteScore - blackScore);
  const blackAdvantage = Math.max(0, blackScore - whiteScore);

  // Game over state
  const isGameOver = game.isGameOver();
  let gameOverReason: any = 'draw';
  let winner = null;
  if (isGameOver) {
    if (game.isCheckmate()) {
      gameOverReason = 'checkmate';
      winner = game.turn() === 'w' ? 'b' : 'w';
    } else if (game.isStalemate()) {
      gameOverReason = 'stalemate';
    } else if (game.isThreefoldRepetition()) {
      gameOverReason = 'threefold';
    } else if (game.isInsufficientMaterial()) {
      gameOverReason = 'insufficient';
    }
  }

  return (
    <main className="flex min-h-screen bg-slate-950 items-center justify-center p-4 md:p-8 overflow-hidden">
      <div className="flex flex-col xl:flex-row items-center xl:items-start justify-center gap-8 xl:gap-12 w-full max-w-7xl">
        
        {/* Left Side: Game Info & Clocks */}
        <div className="flex flex-col space-y-6 w-full max-w-md xl:w-80">
          
          {/* Opponent Banner */}
          <div className="flex flex-col p-6 bg-zinc-900 rounded-2xl border border-zinc-800 shadow-xl">
            <div className="flex justify-between items-center mb-4">
              <div>
                <span className="text-xs text-zinc-500 font-bold uppercase tracking-wider block mb-1">Opponent</span>
                <span className="text-xl text-white font-medium">Vector-64</span>
              </div>
              <div className={`text-3xl font-mono ${game.turn() === 'b' ? 'text-white' : 'text-zinc-500'}`}>
                {formatTime(blackTime)}
              </div>
            </div>
            {/* Captured by Black */}
            <CapturedPieces pieces={capturedByBlack} score={blackAdvantage} />
          </div>

          {/* Move History */}
          <MoveHistory history={game.history()} />

          {/* User Banner (Active) */}
          <div className="flex flex-col p-6 bg-zinc-900 rounded-2xl border border-blue-500/30 shadow-[0_0_30px_rgba(59,130,246,0.1)]">
            <div className="flex justify-between items-center mb-4">
              <div>
                <span className="text-xs text-blue-400 font-bold uppercase tracking-wider block mb-1">You</span>
                <span className="text-xl text-white font-medium">Deepthi</span>
              </div>
              <div className={`text-3xl font-mono font-bold ${game.turn() === 'w' ? 'text-blue-400' : 'text-zinc-500'}`}>
                {formatTime(whiteTime)}
              </div>
            </div>
            {/* Captured by White */}
            <CapturedPieces pieces={capturedByWhite} score={whiteAdvantage} />
          </div>

        </div>

        {/* Right Side: The Board */}
        <div className="flex flex-col items-center w-full max-w-2xl relative">
          <Board game={game} onMove={handleMove} />
          <GameOverModal 
            isOpen={isGameOver} 
            reason={gameOverReason} 
            winner={winner} 
            onRestart={handleRestart} 
          />
        </div>

      </div>
    </main>
  );
}
