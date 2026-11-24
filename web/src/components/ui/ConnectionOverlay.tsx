"use client";

import Link from 'next/link';
import { Icon } from './Icon';

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
    <div role="status" className="absolute inset-0 z-30 flex items-center justify-center bg-slate-950/60 p-4 backdrop-blur-sm">
      <div className="frosted-panel relative rounded-2xl px-6 py-6 text-center">
        <span className="icon-well mx-auto mb-4"><Icon name="link" size={21} /></span>
        <p className="eyebrow justify-center">{copy.tag}</p>
        <h2 className="mt-3 font-serif text-3xl tracking-tight text-white">{copy.title}</h2>
        <p className="mt-3 max-w-sm text-sm leading-relaxed text-zinc-400">{copy.body}</p>
        <Link href="/" className="button-secondary mt-5">Go to dashboard<Icon name="arrow" size={15} /></Link>
      </div>
    </div>
  );
}
