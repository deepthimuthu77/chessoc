"use client";

import Link from 'next/link';
import { usePathname } from 'next/navigation';

const LINKS = [
  { href: '/', label: 'Play' },
  { href: '/leaderboard', label: 'Leaderboard' },
  { href: '/friends', label: 'Friends' },
];

export function TopNav({ username, accountId, onLogout }: { username?: string; accountId?: string; onLogout?: () => void }) {
  const path = usePathname();

  return (
    <nav className="flex w-full items-center justify-between border-b border-zinc-800 bg-zinc-900/60 px-6 py-4 backdrop-blur-xl">
      <Link href="/" className="text-xl font-black tracking-tighter bg-gradient-to-br from-white to-zinc-400 bg-clip-text text-transparent">
        Chessoc
      </Link>

      <div className="flex items-center gap-1">
        {LINKS.map((l) => (
          <Link
            key={l.href}
            href={l.href}
            className={`rounded-lg px-4 py-2 text-sm font-bold transition-colors ${
              path === l.href ? 'bg-zinc-800 text-blue-400' : 'text-zinc-400 hover:text-zinc-200'
            }`}
          >
            {l.label}
          </Link>
        ))}
      </div>

      <div className="flex items-center gap-4">
        {username && accountId && (
          <Link href={`/profile/${accountId}`} className="text-sm font-medium text-zinc-300 hover:text-white">
            {username}
          </Link>
        )}
        {onLogout && (
          <button
            type="button"
            onClick={onLogout}
            className="rounded-lg border border-zinc-700 px-3 py-1.5 text-sm font-bold text-zinc-400 transition-colors hover:bg-zinc-800"
          >
            Logout
          </button>
        )}
      </div>
    </nav>
  );
}
