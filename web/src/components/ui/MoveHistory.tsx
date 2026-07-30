import React, { useEffect, useRef } from 'react';

export function MoveHistory({ history }: { history: string[] }) {
  const scrollRef = useRef<HTMLDivElement>(null);

  // Auto-scroll to bottom when new moves are added
  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
    }
  }, [history]);

  // Group moves into pairs (White, Black)
  const movePairs = [];
  for (let i = 0; i < history.length; i += 2) {
    movePairs.push({
      turn: Math.floor(i / 2) + 1,
      white: history[i],
      black: history[i + 1] || '',
    });
  }

  return (
    <div className="flex flex-col w-full h-64 bg-zinc-900 rounded-2xl border border-zinc-800 shadow-xl overflow-hidden">
      <div className="p-3 bg-zinc-950 border-b border-zinc-800">
        <h3 className="text-sm font-bold text-zinc-400 uppercase tracking-widest text-center">Move History</h3>
      </div>
      
      <div ref={scrollRef} className="flex-1 overflow-y-auto p-2 space-y-1">
        {movePairs.map((pair, index) => (
          <div 
            key={index} 
            className={`flex items-center text-sm px-4 py-2 rounded-md ${index % 2 === 0 ? 'bg-zinc-800/40' : 'bg-transparent'}`}
          >
            <div className="w-8 text-zinc-500 font-mono text-xs">{pair.turn}.</div>
            <div className="flex-1 text-zinc-200 font-medium">{pair.white}</div>
            <div className="flex-1 text-zinc-200 font-medium">{pair.black}</div>
          </div>
        ))}
        {history.length === 0 && (
          <div className="flex items-center justify-center h-full text-zinc-600 text-sm italic">
            Waiting for first move...
          </div>
        )}
      </div>
    </div>
  );
}
