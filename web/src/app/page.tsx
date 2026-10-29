"use client";

import { useEffect, useRef, useState } from 'react';
import { useRouter } from 'next/navigation';
import { motion } from 'framer-motion';
import Link from 'next/link';
import { api, type ActiveGame, type OpenGame } from '@/lib/api';
import { useAuth } from '@/hooks/use-auth';
import { TopNav } from '@/components/ui/TopNav';
import { prefersReducedMotion } from '@/lib/settings';

const TIME_CONTROLS = [
  { label: '1+0', minutes: 1, increment: 0, tag: 'Bullet' },
  { label: '3+2', minutes: 3, increment: 2, tag: 'Blitz' },
  { label: '5+0', minutes: 5, increment: 0, tag: 'Blitz' },
  { label: '10+5', minutes: 10, increment: 5, tag: 'Rapid' },
];

const VARIANTS = ['standard', 'crazyhouse', 'bughouse'];

const BUGHOUSE_SEATS = [
  { code: 'aw', board: 'A', color: 'White', team: 'Blue' },
  { code: 'ab', board: 'A', color: 'Black', team: 'Red' },
  { code: 'bw', board: 'B', color: 'White', team: 'Red' },
  { code: 'bb', board: 'B', color: 'Black', team: 'Blue' },
] as const;

function defaultBughouseSeats(color: 'white' | 'black', count: number) {
  const order = color === 'white' ? ['aw', 'bb', 'ab', 'bw'] : ['ab', 'bw', 'aw', 'bb'];
  return order.slice(0, count);
}

export default function HomePage() {
  const { account, token, ready, isGuest, logout } = useAuth(true);
  const router = useRouter();
  const [tc, setTc] = useState(TIME_CONTROLS[2]);
  const [variant, setVariant] = useState('standard');
  const [rated, setRated] = useState(true);
  const [color, setColor] = useState<'white' | 'black'>('white');
  const [bughouseHumans, setBughouseHumans] = useState(1);
  const [bughouseSeats, setBughouseSeats] = useState<string[]>(['aw']);
  const [room, setRoom] = useState('');
  const [busy, setBusy] = useState(false);
  const [createError, setCreateError] = useState<string | null>(null);
  const [active, setActive] = useState<ActiveGame[]>([]);
  const [open, setOpen] = useState<OpenGame[]>([]);
  const [searching, setSearching] = useState(false);
  const seekPending = useRef(false);
  const [startingSearch, setStartingSearch] = useState(false);
  const [canceling, setCanceling] = useState(false);
  const [roomError, setRoomError] = useState<string | null>(null);
  const [aiLevel, setAiLevel] = useState<'easy' | 'medium' | 'hard'>('medium');
  const [customMinutes, setCustomMinutes] = useState('5');
  const [customIncrement, setCustomIncrement] = useState('0');
  const [listError, setListError] = useState<string | null>(null);
  const [activeError, setActiveError] = useState<string | null>(null);
  const [listRetry, setListRetry] = useState(0);

  useEffect(() => {
    let stopped = false;
    if (token) {
      api.gamesActive(token).then((r) => { if (!stopped) { setActive(r.games); setActiveError(null); } })
        .catch((error) => { if (!stopped) setActiveError(error.message); });
    }
    return () => { stopped = true; };
  }, [token, listRetry]);

  useEffect(() => {
    if (!token) {
      return;
    }
    let stopped = false;
    let timer: ReturnType<typeof setTimeout>;
    const load = async () => {
      try {
        const result = await api.gamesOpen(token);
        if (!stopped) { setOpen(result.games); setListError(null); }
      } catch (error) {
        if (!stopped) setListError(error instanceof Error ? error.message : 'Rooms unavailable');
      } finally { if (!stopped) timer = setTimeout(load, 5000); }
    };
    load();
    return () => { stopped = true; clearTimeout(timer); };
  }, [token, listRetry]);

  useEffect(() => {
    if (!searching || !token) {
      return;
    }
    let stopped = false;
    let timer: ReturnType<typeof setTimeout>;
    const poll = async () => {
      try {
        const r = await api.seekStatus(token);
        if (stopped) return;
        setCreateError(null);
        if (r.status === 'matched' && r.gameId) {
          router.push(`/room/${r.gameId}`);
          return;
        }
      } catch {
        if (!stopped) setCreateError('Match search connection interrupted. Retrying; cancel to leave the queue.');
      }
      if (!stopped) timer = setTimeout(poll, 1500);
    };
    timer = setTimeout(poll, 1500);
    return () => { stopped = true; clearTimeout(timer); };
  }, [searching, token, router]);

  if (!ready || !account || !token) {
    return <main className="min-h-screen bg-slate-950" />;
  }

  async function create(extra: {
    vsEngine: boolean;
    color?: 'white' | 'black';
    rated?: boolean;
    humanPlayers?: number;
    humanSeats?: string[];
  }) {
    setBusy(true);
    setCreateError(null);
    try {
      const { gameId } = await api.createGame(
        { variant, minutes: tc.minutes, increment: tc.increment, aiLevel, ...extra },
        token!,
      );
      router.push(`/room/${gameId}`);
    } catch (error) {
      setBusy(false);
      setCreateError(error instanceof Error ? error.message : 'Unable to create the game');
    }
  }

  async function quickMatch() {
    if (seekPending.current || searching) return;
    seekPending.current = true;
    setStartingSearch(true);
    setCreateError(null);
    setSearching(true);
    const r = await api.seek({ variant, minutes: tc.minutes, increment: tc.increment }, token!).catch((error) => { setCreateError(error.message); return null; });
    if (r?.status === 'matched' && r.gameId) {
      router.push(`/room/${r.gameId}`);
    } else if (!r) {
      setSearching(false);
    }
    seekPending.current = false;
    setStartingSearch(false);
  }

  async function cancelSearch() {
    if (seekPending.current) return;
    seekPending.current = true;
    setCanceling(true);
    try {
      const result = await api.seekCancel(token!);
      if (result.gameId) router.push(`/room/${result.gameId}`);
      else setSearching(false);
    } catch (error) {
      setCreateError(error instanceof Error ? error.message : 'Could not cancel. Please retry.');
    } finally { seekPending.current = false; setCanceling(false); }
  }

  function joinRoom() {
    try {
      const value = room.trim();
      const url = new URL(value.startsWith('/') || /^https?:\/\//i.test(value) ? value : `/room/${value}`, window.location.origin);
      const match = url.pathname.match(/^\/room\/([^/]+)\/?$/);
      if (!match || !/^[a-zA-Z0-9_-]+$/.test(match[1])) throw new Error('Enter a room ID or a full room invite link.');
      const seat = url.searchParams.get('seat');
      router.push(`/room/${match[1]}${seat ? `?seat=${encodeURIComponent(seat)}` : ''}`);
    } catch {
      setRoomError('Enter a valid room ID or a full room invite link.');
    }
  }

  function chooseVariant(next: string) {
    setVariant(next);
    if (next === 'bughouse') {
      setRated(false);
    }
  }

  function chooseBughouseCount(count: number) {
    setBughouseHumans(count);
    setBughouseSeats(defaultBughouseSeats(color, count));
  }

  function chooseBughouseColor(next: 'white' | 'black') {
    setColor(next);
    setBughouseSeats(defaultBughouseSeats(next, bughouseHumans));
  }

  function toggleBughouseSeat(code: string) {
    const creatorSeat = color === 'white' ? 'aw' : 'ab';
    if (code === creatorSeat) return;
    setBughouseSeats((selected) => {
      if (selected.includes(code)) return selected.filter((seat) => seat !== code);
      return selected.length < bughouseHumans ? [...selected, code] : selected;
    });
  }

  return (
    <main className="min-h-screen bg-slate-950 text-white">
      <TopNav username={account.username} accountId={account.id} onLogout={logout} />

      <div className="mx-auto flex max-w-3xl flex-col gap-8 px-6 py-12">
        <motion.div initial={prefersReducedMotion() ? false : { opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }}>
          <h1 className="text-4xl font-black tracking-tighter">
            Welcome, <span className="text-blue-400">{account.username}</span>
          </h1>
          <p className="mt-1 text-zinc-500">Choose a time control, then pick an opponent.</p>
        </motion.div>

        {isGuest && (
          <Link
            href="/upgrade"
            className="flex flex-col items-start gap-2 rounded-2xl border border-blue-500/30 bg-blue-500/10 px-6 py-4 transition-colors hover:bg-blue-500/15 sm:flex-row sm:items-center sm:justify-between"
          >
            <span className="text-sm text-zinc-200">
              You&apos;re playing as a guest. Create an account to save your games and ratings.
            </span>
            <span className="text-sm font-bold text-blue-400">Create account →</span>
          </Link>
        )}

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
                  <span className="flex min-w-0 flex-col gap-0.5 text-zinc-200">
                    <span className="truncate">vs {g.opponent}</span>
                    <span className="text-xs capitalize text-zinc-500">
                      {g.variant} · {g.minutes}+{g.increment} · {g.color}
                    </span>
                  </span>
                  <span className="text-sm font-bold text-blue-400">Resume</span>
                </button>
              ))}
            </div>
          </section>
        )}

        {(listError || activeError) && <p role="alert" className="text-sm text-amber-300">Game lists unavailable: {activeError || listError} <button type="button" className="underline" onClick={() => { setListError(null); setActiveError(null); setListRetry((value) => value + 1); }}>Retry</button></p>}

        <section className="rounded-3xl border border-zinc-800 bg-zinc-900 p-8 shadow-xl">
          <h2 className="mb-5 text-xs font-bold uppercase tracking-[0.2em] text-blue-400">Time control</h2>
          <div className="mb-6 grid grid-cols-2 gap-3 sm:grid-cols-4">
            {TIME_CONTROLS.map((option) => (
              <button
                key={option.label}
                type="button"
                onClick={() => setTc(option)}
                aria-pressed={tc.label === option.label}
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

          <form className="mb-6 flex flex-wrap items-end gap-3" onSubmit={(event) => {
            event.preventDefault();
            const minutes = Number(customMinutes);
            const increment = Number(customIncrement);
            const estimate = minutes * 60 + increment * 40;
            setTc({ label: `${minutes}+${increment}`, minutes, increment,
              tag: estimate < 180 ? 'Bullet' : estimate < 480 ? 'Blitz' : estimate <= 1500 ? 'Rapid' : 'Classical' });
          }}>
            <label className="text-xs text-zinc-400">Minutes
              <input type="number" min="1" max="180" step="1" required value={customMinutes} onChange={(event) => setCustomMinutes(event.target.value)} className="mt-1 block w-24 rounded border border-zinc-700 bg-zinc-950 p-2 text-sm text-white" />
            </label>
            <label className="text-xs text-zinc-400">Increment (seconds)
              <input type="number" min="0" max="180" step="1" required value={customIncrement} onChange={(event) => setCustomIncrement(event.target.value)} className="mt-1 block w-28 rounded border border-zinc-700 bg-zinc-950 p-2 text-sm text-white" />
            </label>
            <button type="submit" className="rounded border border-blue-500/40 px-3 py-2 text-sm text-blue-300">Use custom time</button>
            <p className="w-full text-xs text-zinc-400">Selected: {tc.label} · {tc.tag}</p>
          </form>

          <div className="grid grid-cols-3 gap-1 sm:flex sm:items-center sm:gap-2">
            <span className="col-span-3 mb-1 text-xs font-bold uppercase tracking-wider text-zinc-500 sm:mb-0 sm:mr-2">Variant</span>
            {VARIANTS.map((v) => (
              <button
                key={v}
                type="button"
                onClick={() => chooseVariant(v)}
                aria-pressed={variant === v}
                className={`min-w-0 rounded-xl px-2 py-2 text-sm font-bold capitalize transition-colors sm:px-4 ${
                  variant === v ? 'bg-zinc-800 text-blue-400' : 'text-zinc-400 hover:text-zinc-200'
                }`}
              >
                {v}
              </button>
            ))}
          </div>

          <label className="mt-5 block text-sm text-zinc-300">Computer strength
            <select value={aiLevel} onChange={(event) => setAiLevel(event.target.value as typeof aiLevel)} className="ml-3 rounded border border-zinc-700 bg-zinc-950 px-3 py-2">
              <option value="easy">Easy</option><option value="medium">Medium</option><option value="hard">Hard</option>
            </select>
            <span className="mt-2 block text-xs text-zinc-500">Applies to all computer seats. Levels adjust search effort, not a guaranteed rating.</span>
          </label>
        </section>

        {variant !== 'bughouse' && <section className="flex flex-col items-center rounded-3xl border border-blue-500/30 bg-gradient-to-b from-blue-500/10 to-transparent p-8 shadow-xl sm:flex-row sm:justify-between">
          <div className="mb-4 text-center sm:mb-0 sm:text-left">
            <h2 className="text-lg font-bold text-white">Quick match</h2>
            <p className="mt-1 text-sm text-zinc-400">
              {searching ? 'Searching for an opponent…' : `Get paired with someone at ${tc.label} ${variant}.`}
            </p>
          </div>
          {searching ? (
            <div className="flex items-center gap-4">
              <span className="h-5 w-5 animate-spin rounded-full border-2 border-blue-400 border-t-transparent" />
              <button
                type="button"
                onClick={cancelSearch}
                disabled={canceling || startingSearch}
                className="rounded-xl border border-zinc-700 px-6 py-3 font-bold text-zinc-200 transition-colors hover:bg-zinc-800"
              >
                {canceling ? 'Canceling…' : 'Cancel'}
              </button>
            </div>
          ) : (
            <button
              type="button"
              onClick={quickMatch}
              className="rounded-xl bg-blue-600 px-8 py-3 font-bold text-white shadow-[0_0_20px_rgba(37,99,235,0.4)] transition-all hover:bg-blue-500"
            >
              Find opponent
            </button>
          )}
        </section>}

        {variant === 'bughouse' && (
          <section className="border border-zinc-800 bg-zinc-900 p-8 shadow-xl">
            <div className="flex flex-col gap-7 sm:flex-row sm:items-end sm:justify-between">
              <div className="flex-1">
                <h2 className="text-lg font-bold text-white">Bughouse room</h2>
                <p className="mt-1 text-sm text-zinc-500">Choose how many seats are controlled by people.</p>
                <span className="mb-2 mt-6 block text-xs font-bold uppercase tracking-wider text-zinc-500">Human players</span>
                <div className="grid grid-cols-4 gap-2">
                  {[1, 2, 3, 4].map((count) => (
                    <button
                      key={count}
                      type="button"
                      onClick={() => chooseBughouseCount(count)}
                      className={`border px-3 py-2 text-sm font-bold ${
                        bughouseHumans === count
                          ? 'border-blue-500 bg-blue-500/10 text-blue-300'
                          : 'border-zinc-700 text-zinc-400 hover:border-zinc-500'
                      }`}
                    >
                      {count}H
                    </button>
                  ))}
                </div>
              </div>
              <div className="flex-1">
                <span className="mb-2 block text-xs font-bold uppercase tracking-wider text-zinc-500">Your color on Board A</span>
                <div className="grid grid-cols-2 gap-2">
                  {(['white', 'black'] as const).map((choice) => (
                    <button
                      key={choice}
                      type="button"
                      onClick={() => chooseBughouseColor(choice)}
                      className={`border px-4 py-2 text-sm font-bold capitalize ${
                        color === choice
                          ? 'border-blue-500 bg-blue-500/10 text-blue-300'
                          : 'border-zinc-700 text-zinc-400 hover:border-zinc-500'
                      }`}
                    >
                      {choice}
                    </button>
                  ))}
                </div>
              </div>
            </div>
            <div className="mt-7 border-t border-zinc-800 pt-6">
              <div className="mb-3 flex items-center justify-between gap-4">
                <span className="text-xs font-bold uppercase tracking-wider text-zinc-500">Human seats</span>
                <span className={`text-xs font-semibold ${
                  bughouseSeats.length === bughouseHumans ? 'text-emerald-400' : 'text-amber-400'
                }`}>
                  {bughouseSeats.length}/{bughouseHumans} selected
                </span>
              </div>
              <div className="grid gap-3 sm:grid-cols-2">
                {(['A', 'B'] as const).map((board) => (
                  <div key={board} className="border border-zinc-800 p-3">
                    <p className="mb-2 text-xs font-bold uppercase tracking-wider text-zinc-500">Board {board}</p>
                    <div className="grid grid-cols-2 gap-2">
                      {BUGHOUSE_SEATS.filter((seat) => seat.board === board).map((seat) => {
                        const selected = bughouseSeats.includes(seat.code);
                        const creator = seat.code === (color === 'white' ? 'aw' : 'ab');
                        return (
                          <button
                            key={seat.code}
                            type="button"
                            aria-pressed={selected}
                            onClick={() => toggleBughouseSeat(seat.code)}
                            className={`min-w-0 border px-3 py-3 text-left transition-colors ${
                              selected
                                ? 'border-blue-500 bg-blue-500/10 text-white'
                                : 'border-zinc-700 text-zinc-400 hover:border-zinc-500'
                            }`}
                          >
                            <span className="block text-sm font-bold">{seat.color}</span>
                            <span className={`mt-1 block text-xs ${seat.team === 'Blue' ? 'text-blue-400' : 'text-rose-400'}`}>
                              {seat.team} team{creator ? ' · You' : ''}
                            </span>
                          </button>
                        );
                      })}
                    </div>
                  </div>
                ))}
              </div>
              <p className="mt-3 text-xs text-zinc-500">Unselected seats are controlled by the engine.</p>
            </div>
            <button
              type="button"
              disabled={busy || bughouseSeats.length !== bughouseHumans}
              onClick={() => create({
                vsEngine: bughouseHumans < 4,
                rated: false,
                color,
                humanPlayers: bughouseHumans,
                humanSeats: bughouseSeats,
              })}
              className="mt-7 w-full bg-blue-600 px-6 py-3 font-bold text-white hover:bg-blue-500 disabled:opacity-60"
            >
              {busy
                ? 'Creating room...'
                : bughouseHumans === 1
                  ? 'Play with three bots'
                  : bughouseHumans === 4
                    ? 'Create four-player room'
                    : `Create ${bughouseHumans}H + ${4 - bughouseHumans}AI room`}
            </button>
          </section>
        )}

        {createError && (
          <p role="alert" className="border border-rose-500/30 bg-rose-500/10 px-4 py-3 text-sm text-rose-300">
            {createError}
          </p>
        )}

        {variant !== 'bughouse' && <div className="grid gap-6 md:grid-cols-2">
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
              {busy ? 'Creating game...' : 'Play'}
            </button>
          </section>

          {/* vs Friend */}
          <section className="flex flex-col rounded-3xl border border-zinc-800 bg-zinc-900 p-8 shadow-xl">
            <h2 className="text-lg font-bold text-white">{variant === 'bughouse' ? 'Create team room' : 'Play a Friend'}</h2>
            <p className="mt-1 mb-5 text-sm text-zinc-500">
              {variant === 'bughouse' ? 'Four seats, two boards, one shared room.' : 'Create a game and share the link.'}
            </p>
            <div className="mb-5">
              <span className="mb-2 block text-xs font-bold uppercase tracking-wider text-zinc-500">Mode</span>
              <button
                type="button"
                disabled={variant === 'bughouse'}
                onClick={() => setRated(!rated)}
                className={`w-full rounded-xl border px-4 py-2 text-sm font-bold transition-colors ${
                  rated ? 'border-blue-500/40 text-blue-400' : 'border-zinc-700 text-zinc-400'
                }`}
              >
                {variant === 'bughouse' ? 'Team casual' : rated ? 'Rated' : 'Casual'}
              </button>
            </div>
            <button
              type="button"
              disabled={busy}
              onClick={() => create({ vsEngine: false, rated: variant === 'bughouse' ? false : rated })}
              className="mt-auto rounded-xl border border-zinc-700 px-6 py-3 font-bold text-zinc-100 transition-colors hover:bg-zinc-800 disabled:opacity-60"
            >
              {busy ? 'Creating game...' : variant === 'bughouse' ? 'Create bughouse room' : 'Create game'}
            </button>
          </section>
        </div>}

        {open.length > 0 && (
          <section className="rounded-3xl border border-zinc-800 bg-zinc-900 p-6 shadow-xl">
            <h2 className="mb-4 text-xs font-bold uppercase tracking-[0.2em] text-blue-400">Open challenges</h2>
            <div className="flex flex-col gap-2">
              {open.map((g) => (
                <button
                  key={g.gameId}
                  type="button"
                  onClick={() => router.push(`/room/${g.gameId}`)}
                  className="flex items-center justify-between rounded-xl border border-zinc-800 bg-zinc-950 px-4 py-3 text-left transition-colors hover:border-blue-500/40"
                >
                  <span className="text-zinc-200">
                    {g.creator}
                    <span className="ml-2 text-xs capitalize text-zinc-500">
                      {g.minutes}+{g.increment} · {g.variant} · {g.rated ? 'rated' : 'casual'}
                      {g.variant === 'bughouse'
                        ? ` · ${g.humansJoined ?? 0}/${g.humanTarget ?? 4} humans`
                        : ''}
                    </span>
                  </span>
                  <span className="text-sm font-bold text-blue-400">Join</span>
                </button>
              ))}
            </div>
          </section>
        )}

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
              onClick={joinRoom}
              className="rounded-xl border border-zinc-700 px-6 py-3 font-bold text-zinc-200 transition-colors hover:bg-zinc-800 disabled:opacity-50"
            >
              Join
            </button>
          </div>
          {roomError && <p role="alert" className="mt-2 text-sm text-rose-400">{roomError}</p>}
        </section>
      </div>
    </main>
  );
}
