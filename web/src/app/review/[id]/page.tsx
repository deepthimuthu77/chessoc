"use client";

import { useEffect, useMemo, useState } from 'react';
import { useParams } from 'next/navigation';
import { api, type Ply } from '@/lib/api';
import { useAuth } from '@/hooks/use-auth';
import { TopNav } from '@/components/ui/TopNav';
import { Board } from '@/components/ui/Board';
import { fenToPieces } from '@/lib/view-model';

const START = 'rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1';

export default function ReviewPage() {
  const params = useParams<{ id: string }>();
  const { account, logout } = useAuth();
  const [plies, setPlies] = useState<Ply[]>([]);
  const [idx, setIdx] = useState(0);

  useEffect(() => {
    api.gameMoves(params.id)
      .then((r) => {
        setPlies(r.plies);
        setIdx(r.plies.length);
      })
      .catch(() => setPlies([]));
  }, [params.id]);

  const positions = useMemo(() => [START, ...plies.map((p) => p.fen)], [plies]);
  const clamped = Math.min(Math.max(idx, 0), positions.length - 1);
  const fen = positions[clamped] ?? START;
  const pieces = useMemo(() => fenToPieces(fen), [fen]);
  const lastMove =
    clamped > 0 && plies[clamped - 1]
      ? { from: plies[clamped - 1].uci.slice(0, 2), to: plies[clamped - 1].uci.slice(2, 4) }
      : null;

  return (
    <main className="min-h-screen bg-slate-950 text-white">
      <TopNav username={account?.username} accountId={account?.id} onLogout={account ? logout : undefined} />

      <div className="mx-auto flex max-w-6xl flex-col items-center gap-8 px-6 py-10 xl:flex-row xl:items-start xl:justify-center">
        <div className="w-full max-w-2xl">
          <Board pieces={pieces} lastMove={lastMove} interactive={false} />
          <div className="mt-4 flex items-center justify-center gap-2">
            <NavBtn onClick={() => setIdx(0)} label="⏮" />
            <NavBtn onClick={() => setIdx((i) => Math.max(0, i - 1))} label="◀" />
            <span className="min-w-[80px] text-center font-mono text-sm text-zinc-400">
              {clamped} / {plies.length}
            </span>
            <NavBtn onClick={() => setIdx((i) => Math.min(plies.length, i + 1))} label="▶" />
            <NavBtn onClick={() => setIdx(plies.length)} label="⏭" />
          </div>
        </div>

        <div className="w-full max-w-xs">
          <div className="flex h-96 flex-col overflow-hidden rounded-2xl border border-zinc-800 bg-zinc-900 shadow-xl">
            <div className="border-b border-zinc-800 bg-zinc-950 p-3">
              <h3 className="text-center text-sm font-bold uppercase tracking-widest text-zinc-400">Moves</h3>
            </div>
            <div className="flex-1 overflow-y-auto p-2">
              {plies.map((p, i) => (
                <button
                  key={i}
                  type="button"
                  onClick={() => setIdx(i + 1)}
                  className={`flex w-full items-center gap-3 rounded-md px-3 py-1.5 text-left text-sm ${
                    clamped === i + 1 ? 'bg-blue-500/15 text-blue-300' : 'text-zinc-300 hover:bg-zinc-800/50'
                  }`}
                >
                  <span className="w-6 font-mono text-xs text-zinc-500">{Math.floor(i / 2) + 1}{i % 2 === 0 ? '.' : ''}</span>
                  <span className="font-medium">{p.uci}</span>
                </button>
              ))}
              {plies.length === 0 && (
                <div className="flex h-full items-center justify-center text-sm italic text-zinc-600">No moves.</div>
              )}
            </div>
          </div>
        </div>
      </div>
    </main>
  );
}

function NavBtn({ onClick, label }: { onClick: () => void; label: string }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="rounded-lg border border-zinc-700 px-4 py-2 font-bold text-zinc-200 transition-colors hover:bg-zinc-800"
    >
      {label}
    </button>
  );
}
