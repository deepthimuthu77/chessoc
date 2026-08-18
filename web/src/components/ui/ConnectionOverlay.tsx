"use client";

type Status = 'connecting' | 'connected' | 'reconnecting' | 'closed';

const COPY: Record<'reconnecting' | 'closed', { tag: string; title: string; body: string }> = {
  reconnecting: {
    tag: 'Reconnecting',
    title: 'Resuming your game',
    body: 'The board is frozen at its last confirmed state until the connection is restored.',
  },
  closed: {
    tag: 'Disconnected',
    title: 'Connection lost',
    body: 'The board is read-only. Reload to rejoin the game from the latest server state.',
  },
};

export function ConnectionOverlay({ status }: { status: Status }) {
  if (status !== 'reconnecting' && status !== 'closed') return null;
  const copy = COPY[status];

  return (
    <div className="absolute inset-0 flex items-center justify-center bg-slate-950/60 backdrop-blur-sm">
      <div className="rounded-2xl border border-zinc-700 bg-zinc-900 px-8 py-6 text-center shadow-2xl">
        <p className="text-xs font-bold uppercase tracking-[0.2em] text-blue-400">{copy.tag}</p>
        <h1 className="mt-2 text-2xl font-bold text-white">{copy.title}</h1>
        <p className="mt-2 max-w-sm text-sm text-zinc-400">{copy.body}</p>
      </div>
    </div>
  );
}
