"use client";

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { motion } from 'framer-motion';
import { api, type ActiveGame } from '@/lib/api';
import { useAuth } from '@/hooks/use-auth';
import { TopNav } from '@/components/ui/TopNav';

const TIME_CONTROLS = [
  { label: '1+0', minutes: 1, increment: 0, tag: 'Bullet' },
  { label: '3+2', minutes: 3, increment: 2, tag: 'Blitz' },
  { label: '5+0', minutes: 5, increment: 0, tag: 'Blitz' },
  { label: '10+5', minutes: 10, increment: 5, tag: 'Rapid' },
];

const VARIANTS = ['standard', 'crazyhouse'];

export default function HomePage() {
  const { account, token, ready, logout } = useAuth(true);
  const router = useRouter();
  const [tc, setTc] = useState(TIME_CONTROLS[2]);
  const [variant, setVariant] = useState('standard');
  const [rated, setRated] = useState(true);
  const [color, setColor] = useState<'white' | 'black'>('white');
  const [room, setRoom] = useState('');
  const [busy, setBusy] = useState(false);
  const [active, setActive] = useState<ActiveGame[]>([]);

  useEffect(() => {
    if (token) {
      api.gamesActive(token).then((r) => setActive(r.games)).catch(() => setActive([]));
    }
  }, [token]);

  if (!ready || !account || !token) {
    return <main className="min-h-screen bg-slate-950" />;
  }

  async function create(extra: { vsEngine: boolean; color?: 'white' | 'black'; rated?: boolean }) {
    setBusy(true);
    try {
      const { gameId } = await api.createGame(
        { variant, minutes: tc.minutes, increment: tc.increment, ...extra },
        token!,
      );
      router.push(`/room/${gameId}`);
    } catch {
      setBusy(false);
    }
  }

  return (
    <main className="min-h-screen bg-slate-950 text-white">
      <TopNav username={account.username} accountId={account.id} onLogout={logout} />

      <div className="mx-auto flex max-w-3xl flex-col gap-8 px-6 py-12">
        <motion.div initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }}>
          <h1 className="text-4xl font-black tracking-tighter">
            Welcome, <span className="text-blue-400">{account.username}</span>
          </h1>
          <p className="mt-1 text-zinc-500">Choose a time control, then pick an opponent.</p>
        </motion.div>

        {active.length > 0 && (
          <section className="rounded-3xl border border-zinc-800 bg-zinc-900 p-6 shadow-xl">
            <h2 className="mb-4 text-xs font-bold uppercase tracking-[0.2em] text-blue-400">Continue playing</h2>
            <div className="flex flex-col gap-2">
              {active.map((g) => (
                <button
                  key={g.gameId}
                  type="button"
                  onClick={() => router.push(`/room/${g.gameId}`)}
                  className="flex items-center justify-between rounded-xl border border-zinc-800 bg-zinc-950 px-4 py-3 text-left transition-colors hover:border-zinc-700"
                >
                  <span className="text-zinc-200">
                    vs {g.opponent} <span className="text-xs text-zinc-500">({g.color})</span>
                  </span>
                  <span className="text-sm font-bold text-blue-400">Resume</span>
                </button>
              ))}
            </div>
          </section>
        )}

        <section className="rounded-3xl border border-zinc-800 bg-zinc-900 p-8 shadow-xl">
          <h2 className="mb-5 text-xs font-bold uppercase tracking-[0.2em] text-blue-400">Time control</h2>
          <div className="mb-6 grid grid-cols-2 gap-3 sm:grid-cols-4">
            {TIME_CONTROLS.map((option) => (
              <button
                key={option.label}
                type="button"
                onClick={() => setTc(option)}
                className={`flex flex-col items-center rounded-2xl border px-4 py-4 transition-colors ${
                  tc.label === option.label
                    ? 'border-blue-500 bg-blue-500/10'
                    : 'border-zinc-800 bg-zinc-950 hover:border-zinc-700'
                }`}
              >
                <span className="text-2xl font-bold">{option.label}</span>
                <span className="mt-1 text-xs font-medium uppercase tracking-wider text-zinc-500">{option.tag}</span>
              </button>
            ))}
          </div>

          <div className="flex items-center gap-2">
            <span className="mr-2 text-xs font-bold uppercase tracking-wider text-zinc-500">Variant</span>
            {VARIANTS.map((v) => (
              <button
                key={v}
                type="button"
                onClick={() => setVariant(v)}
                className={`rounded-xl px-4 py-2 text-sm font-bold capitalize transition-colors ${
                  variant === v ? 'bg-zinc-800 text-blue-400' : 'text-zinc-400 hover:text-zinc-200'
                }`}
              >
                {v}
              </button>
            ))}
          </div>
        </section>

        <div className="grid gap-6 md:grid-cols-2">
          {/* vs Computer */}
          <section className="flex flex-col rounded-3xl border border-zinc-800 bg-zinc-900 p-8 shadow-xl">
            <h2 className="text-lg font-bold text-white">Play the Computer</h2>
            <p className="mt-1 mb-5 text-sm text-zinc-500">The engine plays the other side.</p>
            <div className="mb-5">
              <span className="mb-2 block text-xs font-bold uppercase tracking-wider text-zinc-500">Play as</span>
              <div className="flex gap-2">
                {(['white', 'black'] as const).map((c) => (
                  <button
                    key={c}
                    type="button"
                    onClick={() => setColor(c)}
                    className={`flex-1 rounded-xl border px-4 py-2 text-sm font-bold capitalize transition-colors ${
                      color === c ? 'border-blue-500 bg-blue-500/10 text-blue-400' : 'border-zinc-800 text-zinc-400 hover:border-zinc-700'
                    }`}
                  >
                    {c}
                  </button>
                ))}
              </div>
            </div>
            <button
              type="button"
              disabled={busy}
              onClick={() => create({ vsEngine: true, color })}
              className="mt-auto rounded-xl bg-gradient-to-b from-[#5c8aab] to-[#4b7399] px-6 py-3 font-bold text-white shadow-[0_0_20px_rgba(75,115,153,0.4)] transition-all hover:from-[#6a9bc2] hover:to-[#5785b0] disabled:opacity-60"
            >
              Play
            </button>
          </section>

          {/* vs Friend */}
          <section className="flex flex-col rounded-3xl border border-zinc-800 bg-zinc-900 p-8 shadow-xl">
            <h2 className="text-lg font-bold text-white">Play a Friend</h2>
            <p className="mt-1 mb-5 text-sm text-zinc-500">Create a game and share the link.</p>
            <div className="mb-5">
              <span className="mb-2 block text-xs font-bold uppercase tracking-wider text-zinc-500">Mode</span>
              <button
                type="button"
                onClick={() => setRated(!rated)}
                className={`w-full rounded-xl border px-4 py-2 text-sm font-bold transition-colors ${
                  rated ? 'border-blue-500/40 text-blue-400' : 'border-zinc-700 text-zinc-400'
                }`}
              >
                {rated ? 'Rated' : 'Casual'}
              </button>
            </div>
            <button
              type="button"
              disabled={busy}
              onClick={() => create({ vsEngine: false, rated })}
              className="mt-auto rounded-xl border border-zinc-700 px-6 py-3 font-bold text-zinc-100 transition-colors hover:bg-zinc-800 disabled:opacity-60"
            >
              Create game
            </button>
          </section>
        </div>

        <section className="rounded-3xl border border-zinc-800 bg-zinc-900 p-8 shadow-xl">
          <h2 className="mb-4 text-xs font-bold uppercase tracking-[0.2em] text-blue-400">Join a room</h2>
          <div className="flex gap-3">
            <input
              value={room}
              onChange={(e) => setRoom(e.target.value)}
              placeholder="room name or game link id"
              className="flex-1 rounded-xl border border-zinc-700 bg-zinc-950 px-4 py-3 text-white placeholder-zinc-600 outline-none focus:border-blue-500"
            />
            <button
              type="button"
              disabled={!room.trim()}
              onClick={() => router.push(`/room/${room.trim()}`)}
              className="rounded-xl border border-zinc-700 px-6 py-3 font-bold text-zinc-200 transition-colors hover:bg-zinc-800 disabled:opacity-50"
            >
              Join
            </button>
          </div>
        </section>
      </div>
    </main>
  );
}
