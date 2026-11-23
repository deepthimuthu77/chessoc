"use client";

import { Suspense, useEffect, useRef, useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import Link from 'next/link';
import { api, type ActiveGame, type OpenGame } from '@/lib/api';
import { useAuth } from '@/hooks/use-auth';
import { TopNav } from '@/components/ui/TopNav';
import { Icon, type IconName } from '@/components/ui/Icon';
import { FeatureLink } from '@/components/ui/PageHeading';

const TIME_CONTROLS = [
  { label: '1+0', minutes: 1, increment: 0, tag: 'Bullet' },
  { label: '3+2', minutes: 3, increment: 2, tag: 'Blitz' },
  { label: '5+0', minutes: 5, increment: 0, tag: 'Blitz' },
  { label: '10+5', minutes: 10, increment: 5, tag: 'Rapid' },
];

const VARIANTS: { id: string; title: string; description: string; icon: IconName }[] = [
  { id: 'standard', title: 'Chess', description: 'The timeless original.', icon: 'knight' },
  { id: 'crazyhouse', title: 'Crazyhouse', description: 'Every capture, a new possibility.', icon: 'sparkle' },
  { id: 'bughouse', title: 'Bughouse', description: 'Two boards. One team.', icon: 'layers' },
];

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
  return <Suspense fallback={<main className="min-h-screen app-background p-8"><p role="status" className="text-sm text-zinc-400">Preparing your table…</p></main>}><DashboardRoute /></Suspense>;
}

function DashboardRoute() {
  const query = useSearchParams();
  const mode = query.get('opponent');
  const variant = query.get('variant');
  return <Dashboard key={query.toString()} initialOpponent={mode === 'friend' || mode === 'match' ? mode : 'computer'} initialVariant={VARIANTS.some(option => option.id === variant) ? variant! : 'standard'} />;
}

function Dashboard({ initialOpponent, initialVariant }: { initialOpponent: 'computer' | 'friend' | 'match'; initialVariant: string }) {
  const { account, token, ready, isGuest, logout, authError, retryAuth } = useAuth(true);
  const router = useRouter();
  const [tc, setTc] = useState(TIME_CONTROLS[2]);
  const [variant, setVariant] = useState(initialVariant);
  const [opponent, setOpponent] = useState(initialOpponent);
  const [rated, setRated] = useState(initialVariant !== 'bughouse');
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
    return <main className="app-background flex min-h-screen flex-col items-center justify-center gap-5 p-6 text-center"><span className="icon-well"><Icon name="knight" /></span><h1 className="page-title">Preparing your table.</h1><p role={authError ? 'alert' : 'status'} className="max-w-sm text-sm leading-relaxed text-zinc-400">{authError ?? 'Getting your playing session ready…'}</p>{authError && <button type="button" className="button-secondary" onClick={() => void retryAuth()}>Try again</button>}<Link href="/watch" className="text-link">Visit the live gallery<Icon name="arrow" size={15} /></Link></main>;
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
    <main className="min-h-screen app-background text-white">
      <TopNav username={account.username} accountId={account.id} onLogout={logout} />
      <div id="main-content" tabIndex={-1} className="page-wrap page-enter">
        <header className="dashboard-hero">
          <div>
            <p className="eyebrow mb-5"><span className="live-dot" />Your table is ready</p>
            <h1 className="display-title">A good day for<br />a great game.</h1>
            <p className="page-description mt-4">Hello, {account.username}. Settle into a classic, change the rules,<br className="hidden sm:block" /> or bring a friend to the other side of the board.</p>
            <div className="mt-5 flex flex-wrap items-center gap-5"><span className="quiet-label">Three variants. Endless possibilities.</span><Link href="/watch" className="text-link">Explore live games<Icon name="arrow" size={15} /></Link></div>
          </div>
          <div className="hero-art" aria-hidden="true"><Icon name="knight" className="hero-piece" /><span className="hero-art-caption">The next move is yours</span></div>
        </header>

        <div className="dashboard-grid">
          <div className="min-w-0 space-y-5">
            {active.length > 0 && <section className="surface px-6 py-5" aria-label="Continue playing"><div className="section-head mb-1"><h2>Pick up where you left off</h2><span className="live-pill"><span className="live-dot" />{active.length} active</span></div>{active.map(game => <Link key={game.gameId} href={`/room/${game.gameId}`} className="room-item"><span className="icon-well"><Icon name="board" size={18} /></span><span className="min-w-0 flex-1"><span className="block truncate text-sm">vs {game.opponent}</span><span className="mt-1 block text-xs capitalize text-zinc-500">{game.variant} · {game.minutes}+{game.increment} · {game.color}</span></span><span className="text-link">Resume<Icon name="arrow" size={14} /></span></Link>)}</section>}
            {(listError || activeError) && <p role="alert" className="alert-box">Game lists unavailable: {activeError || listError} <button type="button" className="underline" onClick={() => { setListError(null); setActiveError(null); setListRetry(value => value + 1); }}>Retry</button></p>}

            <section id="create-game" className="surface setup-card" aria-label="Create a game">
              <fieldset disabled={busy || searching} className="min-w-0 border-0 p-0">
                <div className="section-head"><h2><span className="section-number">01</span>Choose your game</h2><span className="quiet-label">Make it your own</span></div>
                <div className="variant-grid" role="group" aria-label="Chess variant">{VARIANTS.map(option => <button key={option.id} type="button" aria-label={option.title} aria-pressed={variant === option.id} onClick={() => chooseVariant(option.id)} className="variant-option"><span className="variant-option-icon"><Icon name={option.icon} size={26} />{variant === option.id && <Icon name="check" size={15} />}</span><span className="variant-option-title">{option.title}</span><span className="variant-option-description">{option.description}</span></button>)}</div>

                <div className="setup-divider" />
                <div className="section-head"><h2><span className="section-number">02</span>Set the pace</h2><span className="quiet-label">{tc.label} · {tc.tag}</span></div>
                <div className="time-grid" role="group" aria-label="Time control">{TIME_CONTROLS.map(option => <button key={option.label} type="button" aria-pressed={tc.label === option.label} className="time-option" onClick={() => setTc(option)}><strong>{option.label}</strong><span>{option.tag}</span></button>)}</div>
                <details className="custom-time"><summary>A different rhythm? Set a custom time</summary><form onSubmit={event => {
                  event.preventDefault(); const minutes = Number(customMinutes); const increment = Number(customIncrement); const estimate = minutes * 60 + increment * 40;
                  setTc({ label: `${minutes}+${increment}`, minutes, increment, tag: estimate < 180 ? 'Bullet' : estimate < 480 ? 'Blitz' : estimate <= 1500 ? 'Rapid' : 'Classical' });
                }}><label className="field-label">Minutes<input className="field" type="number" min="1" max="180" step="1" required value={customMinutes} onChange={event => setCustomMinutes(event.target.value)} /></label><label className="field-label">Increment (seconds)<input className="field" type="number" min="0" max="180" step="1" required value={customIncrement} onChange={event => setCustomIncrement(event.target.value)} /></label><button type="submit" className="button-secondary">Apply time</button></form></details>

                <div className="setup-divider" />
                <div className="section-head"><h2><span className="section-number">03</span>{variant === 'bughouse' ? 'Build your table' : 'Choose your company'}</h2></div>
                {variant !== 'bughouse' ? <>
                  <div className="segmented" role="group" aria-label="Opponent type">{([{ id: 'computer', label: 'Computer', icon: 'computer' }, { id: 'friend', label: 'A friend', icon: 'people' }, { id: 'match', label: 'Quick match', icon: 'play' }] as const).map(mode => <button type="button" key={mode.id} aria-pressed={opponent === mode.id} onClick={() => setOpponent(mode.id)} className="segment"><Icon name={mode.icon} size={15} />{mode.label}</button>)}</div>
                  <div className="mt-5 min-h-24">
                    {opponent === 'computer' ? <div className="grid gap-5 sm:grid-cols-2"><div><span className="field-label mb-2">Your side</span><div className="segmented" role="group" aria-label="Your color">{(['white', 'black'] as const).map(choice => <button type="button" key={choice} aria-pressed={color === choice} onClick={() => setColor(choice)} className="segment capitalize"><span className={`h-2.5 w-2.5 rounded-full border ${choice === 'white' ? 'border-zinc-200 bg-zinc-200' : 'border-zinc-500 bg-zinc-950'}`} />{choice}</button>)}</div></div><label className="field-label">Computer strength<select className="field" value={aiLevel} onChange={event => setAiLevel(event.target.value as typeof aiLevel)}><option value="easy">Easy · Find your footing</option><option value="medium">Medium · A worthy opponent</option><option value="hard">Hard · Rise to the challenge</option></select></label><p className="col-span-full text-[11px] leading-relaxed text-zinc-500">Practice at your pace. Strength changes search effort; it is not a guaranteed rating.</p></div>
                    : opponent === 'friend' ? <div className="flex flex-wrap items-center justify-between gap-4"><p className="max-w-xs text-sm leading-relaxed text-zinc-400">A board for two. Create a room and share the invitation with your friend.</p><button type="button" aria-pressed={rated} onClick={() => setRated(!rated)} className="button-secondary"><Icon name={rated ? 'trophy' : 'people'} size={16} />{rated ? 'Rated game' : 'Casual game'}</button></div>
                    : <div className="flex items-start gap-3"><span className="icon-well"><Icon name="play" size={18} /></span><div><h3 className="text-sm font-medium">Meet your next opponent.</h3><p className="mt-1 text-xs leading-relaxed text-zinc-500">Join the queue for {tc.label} {variant === 'standard' ? 'chess' : variant}. Your game opens as soon as a player matches.</p></div></div>}
                  </div>
                </> : <>
                  <p className="mb-5 text-xs leading-relaxed text-zinc-400">Invite your team or let the engine fill in. You choose who sits where.</p>
                  <div className="grid gap-5 sm:grid-cols-2"><div><span className="field-label mb-2">Human players</span><div className="segmented" role="group" aria-label="Human players">{[1, 2, 3, 4].map(count => <button key={count} type="button" aria-pressed={bughouseHumans === count} onClick={() => chooseBughouseCount(count)} className="segment">{count}</button>)}</div></div><div><span className="field-label mb-2">Your color on Board A</span><div className="segmented" role="group" aria-label="Your bughouse color">{(['white', 'black'] as const).map(choice => <button type="button" key={choice} aria-pressed={color === choice} onClick={() => chooseBughouseColor(choice)} className="segment capitalize">{choice}</button>)}</div></div></div>
                  <div className="mt-5 flex justify-between text-xs"><span className="text-zinc-400">Choose the human seats</span><span className={bughouseSeats.length === bughouseHumans ? 'text-accent-300' : 'text-amber-300'}>{bughouseSeats.length}/{bughouseHumans} selected</span></div>
                  <div className="mt-3 grid gap-3 sm:grid-cols-2">{(['A', 'B'] as const).map(board => <div key={board} className="surface-inset p-3"><p className="eyebrow mb-3">Board {board}</p><div className="grid grid-cols-2 gap-2">{BUGHOUSE_SEATS.filter(seat => seat.board === board).map(seat => { const selected = bughouseSeats.includes(seat.code); const creator = seat.code === (color === 'white' ? 'aw' : 'ab'); return <button key={seat.code} type="button" aria-pressed={selected} aria-label={`Board ${seat.board} ${seat.color}, ${seat.team} team${creator ? ', your seat' : ''}`} onClick={() => toggleBughouseSeat(seat.code)} className="seat-option"><span className="block text-xs font-semibold">{seat.color}{creator && <span className="ml-1 text-accent-300">· You</span>}</span><span className={`mt-1 block text-[10px] ${seat.team === 'Blue' ? 'text-sky-300' : 'text-rose-300'}`}>{seat.team} team · {selected ? 'Human' : 'Computer'}</span></button>; })}</div></div>)}</div>
                  <div className="mt-4"><label className="field-label">Computer strength<select className="field" disabled={bughouseHumans === 4} value={aiLevel} onChange={event => setAiLevel(event.target.value as typeof aiLevel)}><option value="easy">Easy</option><option value="medium">Medium</option><option value="hard">Hard</option></select></label><p className="mt-2 text-[11px] text-zinc-500">{bughouseHumans === 4 ? 'A full table. Invite three friends after creating the room.' : `One level for all ${4 - bughouseHumans} computer seats. Remaining human seats join by invitation.`}</p></div>
                </>}
              </fieldset>
              <div className="mt-5 border-t border-zinc-800 pt-2"><div className="setup-summary" aria-label="Game summary"><span className="capitalize">{variant === 'standard' ? 'Chess' : variant}</span><span>{tc.label} {tc.tag}</span><span>{variant === 'bughouse' ? `${bughouseHumans} human · ${4 - bughouseHumans} AI` : opponent === 'computer' ? `vs Computer · ${color}` : opponent === 'friend' ? rated ? 'Friend · Rated' : 'Friend · Casual' : 'Matchmaking'}</span></div>
                {searching ? <div role="status" className="flex items-center justify-between gap-3"><span className="flex items-center gap-2 text-xs text-accent-300"><Icon name="clock" size={17} />{startingSearch ? 'Joining the queue…' : 'Finding your opponent…'}</span><button type="button" disabled={canceling || startingSearch} onClick={cancelSearch} className="button-secondary">{canceling ? 'Canceling…' : 'Cancel search'}</button></div> : <button type="button" className="button-primary w-full" disabled={busy || (variant === 'bughouse' && bughouseSeats.length !== bughouseHumans)} onClick={() => variant === 'bughouse' ? create({ vsEngine: bughouseHumans < 4, rated: false, color, humanPlayers: bughouseHumans, humanSeats: bughouseSeats }) : opponent === 'computer' ? create({ vsEngine: true, color }) : opponent === 'friend' ? create({ vsEngine: false, rated }) : quickMatch()}>{busy ? 'Preparing your board…' : variant === 'bughouse' ? bughouseHumans === 1 ? 'Play with three computers' : 'Create your team room' : opponent === 'computer' ? 'Play the computer' : opponent === 'friend' ? 'Create an invitation' : 'Find an opponent'}{!busy && <Icon name="arrow" size={17} />}</button>}
              </div>
              {createError && <p role="alert" className="alert-box mt-4">{createError}</p>}
            </section>

            <section className="surface px-6 py-5"><div className="section-head mb-3"><h2>Have an invitation?</h2><Icon name="link" size={17} className="text-zinc-500" /></div><form className="flex gap-2" onSubmit={event => { event.preventDefault(); joinRoom(); }}><input aria-label="Room ID or invitation link" className="field min-w-0 flex-1" value={room} onChange={event => { setRoom(event.target.value); setRoomError(null); }} placeholder="Paste a room link or ID" /><button className="button-secondary" type="submit" disabled={!room.trim()}>Join<Icon name="arrow" size={14} /></button></form>{roomError && <p role="alert" className="mt-3 text-xs text-rose-300">{roomError}</p>}</section>
          </div>

          <aside className="dashboard-aside space-y-5 max-[800px]:space-y-0">
            <section className="surface side-card"><div className="section-head mb-2"><h2>Beyond your board</h2><Icon name="sparkle" size={16} className="text-accent-300" /></div><p className="mb-2 text-xs leading-relaxed text-zinc-500">There&apos;s more to the game than playing.</p><FeatureLink href="/watch" icon="watch" title="The live gallery" detail="Follow every move. Pin four matches." /><FeatureLink href="/history" icon="history" title="Your game collection" detail="Replay, reflect, find the better move." /><FeatureLink href="/friends" icon="people" title="Your circle" detail="Good games start with good company." /><FeatureLink href="/leaderboard" icon="trophy" title="The club rankings" detail="Find your place. Raise your game." /></section>
            <section className="surface side-card"><p className="eyebrow mb-3">A different kind of chess</p><h2 className="font-serif text-2xl leading-tight tracking-tight">Two boards.<br />One shared ambition.</h2><p className="mt-3 text-xs leading-relaxed text-zinc-500">In bughouse, your captures become your partner&apos;s possibilities. Bring friends, add computers, and find your rhythm as a team.</p><button type="button" className="text-link mt-4" onClick={() => { chooseVariant('bughouse'); document.getElementById('create-game')?.scrollIntoView({ behavior: 'auto', block: 'start' }); }}>Set up bughouse<Icon name="arrow" size={15} /></button></section>
            {isGuest && <section className="side-card rounded-2xl border border-[#d7bb8c35] bg-[#d7bb8c05]"><p className="eyebrow mb-3">Make yourself at home</p><h2 className="text-sm font-medium">Your games deserve a home.</h2><p className="mt-2 text-xs leading-relaxed text-zinc-500">Keep your guest progress and build your own collection. Create an account when you&apos;re ready.</p><Link href="/upgrade" className="text-link mt-3">Create your account<Icon name="arrow" size={15} /></Link></section>}
          </aside>
        </div>

        {open.length > 0 && <section className="surface mt-7 px-6 py-5"><div className="section-head"><h2>A seat at the table</h2><span className="live-pill"><span className="live-dot" />Open invitations</span></div><div className="grid gap-x-8 md:grid-cols-2">{open.map(game => <Link key={game.gameId} href={`/room/${game.gameId}`} className="room-item"><span className="avatar">{game.creator.slice(0, 2).toUpperCase()}</span><span className="min-w-0 flex-1"><span className="block truncate text-sm">{game.creator}</span><span className="mt-1 block text-[11px] capitalize text-zinc-500">{game.variant} · {game.minutes}+{game.increment} · {game.rated ? 'Rated' : 'Casual'}{game.variant === 'bughouse' ? ` · ${game.humansJoined ?? 0}/${game.humanTarget ?? 4} humans` : ''}</span></span><span className="text-link">Join<Icon name="arrow" size={14} /></span></Link>)}</div></section>}
        <footer className="screen-footer"><span>Chessoc · A place for the game.</span><div className="flex gap-5"><Link href="/settings" className="hover:text-zinc-200">Make it yours</Link><Link href="/watch" className="hover:text-zinc-200">Watch live</Link></div></footer>
      </div>
    </main>
  );
}
