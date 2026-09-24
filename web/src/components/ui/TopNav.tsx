"use client";

import Link from 'next/link';
import { usePathname } from 'next/navigation';

const LINKS = [
  { href: '/', label: 'Play' },
  { href: '/watch', label: 'Watch' },
  { href: '/leaderboard', label: 'Leaderboard' },
  { href: '/friends', label: 'Friends' },
  { href: '/history', label: 'History' },
  { href: '/settings', label: 'Settings' },
];

export function TopNav({ username, accountId, onLogout }: { username?: string; accountId?: string; onLogout?: () => void }) {
  const path = usePathname();

  return (
    <nav className="flex w-full flex-wrap items-center justify-between gap-3 border-b border-zinc-800 bg-zinc-900/60 px-4 py-3 backdrop-blur-xl sm:px-6 sm:py-4">
      <Link href="/" className="text-xl font-black tracking-tighter bg-gradient-to-br from-white to-zinc-400 bg-clip-text text-transparent">
        Chessoc
      </Link>

      <div className="order-3 grid w-full grid-cols-3 gap-1 md:order-none md:flex md:w-auto md:items-center">
        {LINKS.map((l) => (
          <Link
            key={l.href}
            href={l.href}
            className={`rounded-lg px-2 py-2 text-center text-sm font-bold transition-colors lg:px-4 ${
              path === l.href ? 'bg-zinc-800 text-blue-400' : 'text-zinc-400 hover:text-zinc-200'
            }`}
          >
            {l.label}
          </Link>
        ))}
      </div>

      <div className="flex min-w-0 items-center gap-3">
        {username && accountId && (
          <Link href={`/profile/${accountId}`} className="max-w-24 truncate text-sm font-medium text-zinc-300 hover:text-white">
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
