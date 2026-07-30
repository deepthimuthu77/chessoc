import React, { useState, useRef } from 'react';
import { Chess } from 'chess.js';
import { Piece } from './Piece';

export function Board({ game, onMove }: { game: Chess, onMove: (from: string, to: string) => void }) {
  const [selectedSquare, setSelectedSquare] = useState<string | null>(null);
  const boardRef = useRef<HTMLDivElement>(null);

  if (!game) return null;

  // Draw 64 background squares
  const squares = [];
  const files = ['a', 'b', 'c', 'd', 'e', 'f', 'g', 'h'];
  
  for (let row = 0; row < 8; row++) {
    for (let col = 0; col < 8; col++) {
      const isDark = (row + col) % 2 === 1;
      const squareName = files[col] + (8 - row);
      
      const isSelected = selectedSquare === squareName;
      let isLegalDestination = false;
      try {
        const legalMoves = selectedSquare ? game.moves({ square: selectedSquare as import('chess.js').Square, verbose: true }) : [];
        isLegalDestination = legalMoves.some((m: any) => m.to === squareName);
      } catch (e) {
        // Handle potential chess.js errors gracefully
      }

      squares.push(
        <div
          key={squareName}
          onClick={() => {
            if (selectedSquare) {
              attemptMove(selectedSquare, squareName);
            }
          }}
          className={`w-full h-full relative ${
            isDark ? 'bg-[#4b7399]' : 'bg-[#eae9d2]'
          } ${isSelected ? 'ring-4 ring-inset ring-blue-500 bg-blue-400/30' : ''}`}
        >
          {/* Highlight dot for legal moves */}
          {isLegalDestination && (
            <div className="absolute inset-0 flex items-center justify-center">
              <div className="w-6 h-6 md:w-8 md:h-8 bg-black/20 rounded-full"></div>
            </div>
          )}
          
          {/* Rank/File coordinates for visual polish */}
          {col === 0 && <span className={`absolute top-1 left-1 text-[10px] font-bold ${isDark ? 'text-zinc-300' : 'text-zinc-700'}`}>{8 - row}</span>}
          {row === 7 && <span className={`absolute bottom-1 right-1 text-[10px] font-bold ${isDark ? 'text-zinc-300' : 'text-zinc-700'}`}>{files[col]}</span>}
        </div>
      );
    }
  }

  // Draw pieces in an absolute overlay layer
  const pieces = [];
  const boardLayout = game.board(); // 8x8 array of pieces or null
  
  for (let row = 0; row < 8; row++) {
    for (let col = 0; col < 8; col++) {
      const pieceData = boardLayout[row][col];
      if (pieceData) {
        const squareName = files[col] + (8 - row);
        const char = pieceData.color === 'w' ? pieceData.type.toUpperCase() : pieceData.type.toLowerCase();
        
        pieces.push(
          <Piece 
            key={`${char}-${squareName}-${game.history().length}`} // Force smooth re-render tracking
            char={char} 
            col={col} 
            row={row} 
            squareName={squareName}
            onPieceClick={(sq) => setSelectedSquare(sq)}
            onDrop={handleDrop}
          />
        );
      }
    }
  }

  function handleDrop(fromSquare: string, point: { x: number; y: number }) {
    if (!boardRef.current) return;
    
    // Calculate which square the mouse dropped on
    const rect = boardRef.current.getBoundingClientRect();
    const x = point.x - rect.left;
    const y = point.y - rect.top;
    
    // Bounds check
    if (x < 0 || x > rect.width || y < 0 || y > rect.height) {
      setSelectedSquare(null);
      return;
    }
    
    const col = Math.floor((x / rect.width) * 8);
    const row = Math.floor((y / rect.height) * 8);
    const toSquare = files[col] + (8 - row);
    
    attemptMove(fromSquare, toSquare);
  }

  function attemptMove(fromSquare: string, toSquare: string) {
    if (fromSquare === toSquare) return;
    onMove(fromSquare, toSquare);
    setSelectedSquare(null);
  }

  return (
    <div 
      ref={boardRef}
      className="relative grid grid-cols-8 grid-rows-8 w-full max-w-2xl aspect-square shadow-[0_0_50px_rgba(0,0,0,0.5)] rounded-md overflow-hidden border-[6px] border-zinc-900"
    >
      {/* Background squares */}
      {squares}
      
      {/* Absolute Pieces Overlay */}
      <div className="absolute inset-0 pointer-events-none">
        {/* We enable pointer-events only on the pieces themselves inside Piece.tsx */}
        <div className="relative w-full h-full pointer-events-auto">
          {pieces}
        </div>
      </div>
    </div>
  );
}
