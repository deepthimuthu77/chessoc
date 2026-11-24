"use client";

import { useEffect, useState } from 'react';
import { useParams, useRouter } from 'next/navigation';
import { api, type DirectMessageDto, type Profile, type RatingPoint } from '@/lib/api';
import { useAuth } from '@/hooks/use-auth';
import { TopNav } from '@/components/ui/TopNav';
import { JourneyNav } from '@/components/ui/PageHeading';
import { Icon } from '@/components/ui/Icon';
import Link from 'next/link';
import { RatingChart } from '@/components/ui/RatingChart';
import { Chat } from '@/components/ui/Chat';
import type { ChatMessage } from '@/lib/protocol';

export default function ProfilePage() {
  const params = useParams<{ id: string }>();
  return <ProfileContent key={params.id} id={params.id} />;
}

function ProfileContent({ id }: { id: string }) {
  const router = useRouter();
  const { account, token, logout, handleAuthError, authError, retryAuth } = useAuth();
  const [attempt, setAttempt] = useState(0);
  const [result, setResult] = useState<{ attempt: number; profile?: Profile; error?: string } | null>(null);
  const loading = result?.attempt !== attempt;
  const profile = loading ? null : result.profile;
  const [friendship, setFriendship] = useState<'none' | 'friends' | 'requested' | 'incoming' | 'loading'>('loading');
  const [friendNote, setFriendNote] = useState<string | null>(null);
  const [friendError, setFriendError] = useState<string | null>(null);
  const [adding, setAdding] = useState(false);
  const [chatOpen, setChatOpen] = useState(false);
  const [challengeOpen, setChallengeOpen] = useState(false);
  const [dmMessages, setDmMessages] = useState<DirectMessageDto[]>([]);
  const [challengeVariant, setChallengeVariant] = useState('standard');
  const [challengeMinutes, setChallengeMinutes] = useState(5);
  const [challengeIncrement, setChallengeIncrement] = useState(0);
  const [challengeColor, setChallengeColor] = useState<'random' | 'white' | 'black'>('random');
  const [challengeRated, setChallengeRated] = useState(true);
  const [challenging, setChallenging] = useState(false);
  const [challengeError, setChallengeError] = useState<string | null>(null);
  const [pool, setPool] = useState<{ variant: string; speed: string } | null>(null);
  const [historyAttempt, setHistoryAttempt] = useState(0);
  const [historyResult, setHistoryResult] = useState<{ key: string; points: RatingPoint[]; error?: string } | null>(null);
  const [lastSeen, setLastSeen] = useState<number>(() => {
    if (typeof window === 'undefined') return 0;
    try {
      const stored = localStorage.getItem(`chessoc_seen_${id}`);
      return stored ? parseInt(stored, 10) : 0;
    } catch {
      return 0;
    }
  });

  useEffect(() => {
    let cancelled = false;
    api.profile(id).then((profile) => {
      if (!cancelled) setResult({ attempt, profile });
    }).catch((error: unknown) => {
      if (!cancelled) setResult({ attempt, error: error instanceof Error ? error.message : 'Could not load profile.' });
    });
    return () => { cancelled = true; };
  }, [id, attempt]);

  const isSelf = account?.id === id;

  useEffect(() => {
    if (!token || !id || isSelf || id.startsWith('guest-')) {
      setFriendship('none');
      return;
    }
    let cancelled = false;
    api.friendStatus(id, token)
      .then(res => {
        if (!cancelled) setFriendship(res.status);
      })
      .catch(() => {
        Promise.all([api.friends(token), api.friendRequests(token)])
          .then(([fRes, rRes]) => {
            if (cancelled) return;
            if (fRes.friends.some(f => f.id === id)) setFriendship('friends');
            else if (rRes.outgoing.some(r => r.id === id)) setFriendship('requested');
            else if (rRes.incoming.some(r => r.id === id)) setFriendship('incoming');
            else setFriendship('none');
          })
          .catch(() => {
            if (!cancelled) setFriendship('none');
          });
      });
    return () => { cancelled = true; };
  }, [id, token, isSelf]);

  useEffect(() => {
    if (!token || !id || isSelf || id.startsWith('guest-')) return;
    let cancelled = false;
    async function loadChat() {
      try {
        const res = await api.chatMessages(id, token!);
        if (!cancelled) setDmMessages(res.messages);
      } catch {}
    }
    loadChat();
    const interval = setInterval(loadChat, chatOpen ? 2000 : 3500);
    return () => { cancelled = true; clearInterval(interval); };
  }, [token, id, chatOpen, isSelf]);

  const unseenMessages = dmMessages.filter(m => m.fromId === id && m.timestamp > lastSeen);
  const unseenCount = unseenMessages.length;

  useEffect(() => {
    if (chatOpen && dmMessages.length > 0) {
      const latest = Math.max(...dmMessages.map(m => m.timestamp), Date.now());
      setLastSeen(latest);
      try {
        localStorage.setItem(`chessoc_seen_${id}`, String(latest));
      } catch {}
    }
  }, [chatOpen, dmMessages, id]);

  // Chart pool: the user's pick, else the pool with the most games. Derived, not stored.
  const topPool = profile && profile.pools.length > 0
    ? [...profile.pools].sort((a, b) => b.games - a.games)[0]
    : null;
  const selVariant = pool?.variant ?? topPool?.variant ?? null;
  const selSpeed = pool?.speed ?? topPool?.speed ?? null;
  const historyKey = `${id}:${selVariant}:${selSpeed}:${historyAttempt}`;
  const historyLoading = historyResult?.key !== historyKey;

  useEffect(() => {
    if (!selVariant || !selSpeed) return;
    let cancelled = false;
    api.ratingHistory(id, selVariant, selSpeed)
      .then((r) => { if (!cancelled) setHistoryResult({ key: historyKey, points: r.points }); })
      .catch((error: unknown) => {
        if (!cancelled) setHistoryResult({ key: historyKey, points: [], error: error instanceof Error ? error.message : 'Could not load rating history.' });
      });
    return () => { cancelled = true; };
  }, [id, selVariant, selSpeed, historyKey]);

  async function addFriend() {
    if (!profile || !token || adding) return;
    setAdding(true);
    setFriendError(null);
    try {
      const response = await api.addFriend(profile.account.username, token);
      if (!response.ok || !['friends', 'already_friends', 'requested'].includes(response.status)) {
        throw new Error('The friend request was not sent. Please retry.');
      }
      if (response.status === 'friends' || response.status === 'already_friends') {
        setFriendship('friends');
        setFriendNote(response.status === 'friends' ? 'You are now friends.' : 'Already friends.');
      } else {
        setFriendship('requested');
        setFriendNote('Request sent.');
      }
    } catch (error) {
      if (!handleAuthError(error, token)) setFriendError(error instanceof Error ? error.message : 'Could not add friend. Please retry.');
    } finally {
      setAdding(false);
    }
  }

  async function acceptFriend() {
    if (!token || adding) return;
    setAdding(true);
    setFriendError(null);
    try {
      await api.acceptFriend(id, token);
      setFriendship('friends');
      setFriendNote('You are now friends.');
    } catch (error) {
      if (!handleAuthError(error, token)) setFriendError(error instanceof Error ? error.message : 'Could not accept friend request.');
    } finally {
      setAdding(false);
    }
  }

  function sendDirectChat(text: string): boolean {
    if (!token || !id || !text.trim()) return false;
    api.chatSend(id, text.trim(), token)
      .then(res => {
        if (res.ok && res.message) {
          setDmMessages(prev => [...prev, res.message]);
        }
      })
      .catch(() => {});
    return true;
  }

  async function createChallenge() {
    if (!token || challenging) return;
    setChallenging(true);
    setChallengeError(null);
    try {
      const color = challengeColor === 'random' ? (Math.random() < 0.5 ? 'white' : 'black') : challengeColor;
      const res = await api.createGame({
        variant: challengeVariant,
        minutes: challengeMinutes,
        increment: challengeIncrement,
        color,
        rated: challengeVariant !== 'bughouse' && challengeRated,
        vsEngine: false,
      }, token);

      const link = `${window.location.origin}/room/${res.gameId}`;
      try {
        await api.chatSend(id, `⚔️ I challenged you to a ${challengeMinutes}+${challengeIncrement} ${challengeVariant} game! Join here: ${link}`, token);
      } catch {}

      router.push(`/room/${res.gameId}`);
    } catch (err) {
      setChallengeError(err instanceof Error ? err.message : 'Could not create challenge. Please try again.');
      setChallenging(false);
    }
  }

  const chatMessages: ChatMessage[] = dmMessages.map(dm => ({
    color: (dm.fromId === account?.id ? 'You' : (profile?.account.username ?? 'Friend')) as 'white',
    text: dm.text,
    serverTimestamp: dm.timestamp,
  }));

  return (
    <main className="min-h-screen app-background text-white">
      <TopNav username={account?.username} accountId={account?.id} onLogout={account ? logout : undefined} />

      <div id="main-content" tabIndex={-1} className="page-wrap page-wrap-narrow page-enter"><p className="eyebrow mb-5">THE PLAYER BEHIND THE MOVES</p>
        {authError && <p role="alert" className="mb-4 text-rose-400">{authError} <button type="button" onClick={() => void retryAuth()} className="underline">Retry session</button></p>}
        {loading ? (
          <p role="status" className="text-center text-zinc-400">Loading profile…</p>
        ) : result?.error ? (
          <div role="alert" className="text-center text-rose-400"><p>Could not load profile: {result.error}</p><button type="button" onClick={() => setAttempt((n) => n + 1)} className="mt-3 underline">Retry profile</button></div>
        ) : !profile ? null : (
          <>
            <div className="surface mb-7 flex flex-wrap items-center justify-between gap-5 p-7">
              <div>
                <h1 className="page-title break-all">{profile.account.username}</h1>
                <p className="mt-1 text-zinc-500">Standard · Blitz rating</p>
              </div>
              <div className="text-right">
                <div className="font-mono text-4xl font-medium text-accent-300">{profile.rating}</div>
              </div>
            </div>

            {token && !isSelf && !id.startsWith('guest-') && (
              <div className="mb-8 flex flex-wrap items-center gap-3">
                {friendship === 'friends' ? (
                  <>
                    <span className="inline-flex items-center gap-1.5 rounded-xl border border-emerald-500/30 bg-emerald-500/10 px-4 py-2.5 text-sm font-semibold text-emerald-400">
                      <Icon name="check" size={16} /> Friends
                    </span>
                    <button
                      type="button"
                      onClick={() => { setChallengeOpen(o => !o); if (!challengeOpen) setChatOpen(false); }}
                      className={`flex items-center gap-2 rounded-xl px-5 py-2.5 font-bold transition-colors ${
                        challengeOpen ? 'bg-accent-500 text-white' : 'bg-accent-600 text-white hover:bg-accent-500'
                      }`}
                    >
                      <Icon name="swords" size={18} />
                      {challengeOpen ? 'Close Challenge' : 'Challenge'}
                    </button>
                    <div className="relative">
                      {unseenCount > 0 && !chatOpen && (
                        <div
                          role="button"
                          tabIndex={0}
                          onClick={() => setChatOpen(true)}
                          onKeyDown={(e) => { if (e.key === 'Enter') setChatOpen(true); }}
                          aria-label={`${unseenCount} unread message${unseenCount > 1 ? 's' : ''} from ${profile.account.username}. Click to open chat.`}
                          className="absolute -top-14 left-0 z-30 flex cursor-pointer items-center gap-2 rounded-xl border border-accent-400/50 bg-zinc-900 px-3 py-1.5 text-xs text-white shadow-2xl transition-all hover:scale-105"
                        >
                          <span className="relative flex h-2 w-2">
                            <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-accent-400 opacity-75"></span>
                            <span className="relative inline-flex h-2 w-2 rounded-full bg-accent-500"></span>
                          </span>
                          <span className="font-semibold text-accent-300">{profile.account.username}:</span>
                          <span className="max-w-[140px] truncate text-zinc-300">
                            {unseenMessages[unseenMessages.length - 1]?.text}
                          </span>
                          <div className="absolute -bottom-1 left-6 h-2 w-2 rotate-45 border-b border-r border-accent-400/50 bg-zinc-900" />
                        </div>
                      )}
                      <button
                        type="button"
                        onClick={() => { setChatOpen(o => !o); if (!chatOpen) setChallengeOpen(false); }}
                        className={`relative flex items-center gap-2 rounded-xl border px-5 py-2.5 font-bold transition-colors ${
                          chatOpen
                            ? 'border-accent-500 bg-accent-500/20 text-accent-300'
                            : unseenCount > 0
                            ? 'border-accent-400 bg-accent-500/15 text-accent-200 hover:bg-accent-500/25'
                            : 'border-zinc-700 bg-zinc-900 text-zinc-200 hover:bg-zinc-800'
                        }`}
                      >
                        <Icon name="message" size={18} />
                        <span>{chatOpen ? 'Close Chat' : 'Chat'}</span>
                        {unseenCount > 0 && !chatOpen && (
                          <span className="flex h-5 min-w-5 items-center justify-center rounded-full bg-rose-500 px-1.5 text-[11px] font-extrabold text-white shadow-lg animate-pulse">
                            {unseenCount}
                          </span>
                        )}
                      </button>
                    </div>
                  </>
                ) : friendship === 'requested' ? (
                  <span className="inline-flex items-center gap-1.5 rounded-xl border border-zinc-700 bg-zinc-800/60 px-4 py-2.5 text-sm font-medium text-zinc-400">
                    <Icon name="clock" size={16} /> Request sent
                  </span>
                ) : friendship === 'incoming' ? (
                  <button
                    type="button"
                    disabled={adding}
                    onClick={acceptFriend}
                    className="rounded-xl bg-accent-600 px-6 py-2.5 font-bold text-white transition-colors hover:bg-accent-500 disabled:opacity-60"
                  >
                    {adding ? 'Accepting…' : 'Accept friend request'}
                  </button>
                ) : (
                  <button
                    type="button"
                    disabled={adding || !!friendNote}
                    onClick={addFriend}
                    className="rounded-xl bg-accent-600 px-6 py-2.5 font-bold text-white transition-colors hover:bg-accent-500 disabled:opacity-60"
                  >
                    {adding ? 'Sending…' : friendNote ?? 'Add friend'}
                  </button>
                )}
              </div>
            )}
            {friendNote && <p role="status" className="mb-4 text-accent-400">{friendNote}</p>}
            {friendError && <p role="alert" className="mb-4 text-rose-400">{friendError}</p>}

            {challengeOpen && (
              <div className="surface mb-8 rounded-2xl border border-accent-500/30 p-6">
                <div className="flex items-center justify-between mb-4">
                  <div>
                    <h2 className="text-base font-semibold text-white">Challenge {profile.account.username}</h2>
                    <p className="text-xs text-zinc-400">Choose game settings and invite them directly.</p>
                  </div>
                  <button
                    type="button"
                    onClick={() => setChallengeOpen(false)}
                    className="rounded-lg p-1.5 text-zinc-400 hover:bg-zinc-800 hover:text-white"
                    aria-label="Close challenge"
                  >
                    ✕
                  </button>
                </div>

                <div className="grid gap-5 sm:grid-cols-2">
                  <div>
                    <label className="field-label mb-2">Variant</label>
                    <div className="segmented" role="group">
                      {(['standard', 'crazyhouse', 'bughouse'] as const).map(v => (
                        <button
                          key={v}
                          type="button"
                          aria-pressed={challengeVariant === v}
                          onClick={() => setChallengeVariant(v)}
                          className="segment capitalize"
                        >
                          {v}
                        </button>
                      ))}
                    </div>
                  </div>

                  <div>
                    <label className="field-label mb-2">Your color</label>
                    <div className="segmented" role="group">
                      {(['random', 'white', 'black'] as const).map(c => (
                        <button
                          key={c}
                          type="button"
                          aria-pressed={challengeColor === c}
                          onClick={() => setChallengeColor(c)}
                          className="segment capitalize"
                        >
                          {c}
                        </button>
                      ))}
                    </div>
                  </div>

                  <div className="sm:col-span-2">
                    <label className="field-label mb-2">Time control</label>
                    <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
                      {[
                        { label: '3 min · Blitz', m: 3, inc: 0 },
                        { label: '5 min · Blitz', m: 5, inc: 0 },
                        { label: '10 min · Rapid', m: 10, inc: 0 },
                        { label: '1 min · Bullet', m: 1, inc: 0 },
                      ].map(tc => {
                        const selected = challengeMinutes === tc.m && challengeIncrement === tc.inc;
                        return (
                          <button
                            key={tc.label}
                            type="button"
                            onClick={() => { setChallengeMinutes(tc.m); setChallengeIncrement(tc.inc); }}
                            className={`rounded-xl border p-3 text-left transition-colors ${
                              selected ? 'border-accent-400 bg-accent-500/10 text-white' : 'border-zinc-800 bg-zinc-950 text-zinc-300 hover:bg-zinc-900'
                            }`}
                          >
                            <span className="block text-sm font-semibold">{tc.label}</span>
                          </button>
                        );
                      })}
                    </div>
                  </div>
                </div>

                {challengeError && <p role="alert" className="mt-3 text-sm text-rose-400">{challengeError}</p>}

                <div className="mt-6 flex justify-end gap-3">
                  <button
                    type="button"
                    disabled={challenging}
                    onClick={() => setChallengeOpen(false)}
                    className="rounded-xl border border-zinc-700 px-4 py-2 text-sm font-medium text-zinc-300 hover:bg-zinc-800"
                  >
                    Cancel
                  </button>
                  <button
                    type="button"
                    disabled={challenging}
                    onClick={createChallenge}
                    className="button-primary"
                  >
                    {challenging ? 'Starting challenge…' : 'Start challenge'}
                  </button>
                </div>
              </div>
            )}

            {chatOpen && (
              <div className="surface mb-8 overflow-hidden rounded-2xl border border-zinc-800">
                <Chat
                  messages={chatMessages}
                  onSend={sendDirectChat}
                  disabled={!token}
                  title={`Chat with ${profile.account.username}`}
                  subtitle="Direct conversation"
                  bubbles={true}
                  onClose={() => setChatOpen(false)}
                />
              </div>
            )}

            <div className="grid grid-cols-3 gap-4">
              <Stat label="Wins" value={profile.stats.wins} accent="text-green-400" />
              <Stat label="Draws" value={profile.stats.draws} accent="text-zinc-300" />
              <Stat label="Losses" value={profile.stats.losses} accent="text-rose-400" />
            </div>

            {profile.pools.length > 0 && (
              <div className="mt-8">
                <h2 className="mb-3 text-xs font-bold uppercase tracking-wider text-zinc-500">Ratings</h2>
                <div className="overflow-hidden rounded-2xl border border-zinc-800 bg-zinc-900">
                  {profile.pools.map((p, i) => {
                    const on = selVariant === p.variant && selSpeed === p.speed;
                    return (
                      <button
                        key={i}
                        type="button"
                        aria-pressed={on}
                        onClick={() => setPool({ variant: p.variant, speed: p.speed })}
                        className={`flex w-full items-center justify-between border-b border-zinc-800/70 px-6 py-3 text-left transition-colors last:border-0 ${
                          on ? 'bg-accent-500/10' : 'hover:bg-zinc-800/40'
                        }`}
                      >
                        <span className="capitalize text-zinc-300">{p.variant} · {p.speed}</span>
                        <span className="flex-1 px-4 text-right text-xs text-zinc-500">{p.games} games</span>
                        <span className="w-16 text-right font-bold text-accent-400">{p.rating}</span>
                      </button>
                    );
                  })}
                </div>

                {selVariant && selSpeed && (
                  <div className="mt-4 rounded-2xl border border-zinc-800 bg-zinc-900 p-4">
                    <div className="mb-2 px-2 text-xs font-medium capitalize text-zinc-500">
                      {selVariant} · {selSpeed} history
                    </div>
                    {historyLoading ? <p role="status" className="p-4 text-zinc-400">Loading rating history…</p>
                      : historyResult?.error ? <div role="alert" className="p-4 text-rose-400"><p>Could not load rating history: {historyResult.error}</p><button type="button" onClick={() => setHistoryAttempt((n) => n + 1)} className="mt-2 underline">Retry rating history</button></div>
                      : <RatingChart points={historyResult?.points ?? []} />}
                  </div>
                )}
              </div>
            )}
            {profile.pools.length === 0 && <div className="empty-state surface mt-7"><span className="icon-well"><Icon name="trophy" /></span><h2>A story still being written.</h2><p>Ratings and progress will appear here after rated games.</p><Link href="/?opponent=friend#create-game" className="button-secondary">Create a rated game<Icon name="arrow" size={15} /></Link></div>}
          </>
        )}
        <JourneyNav destinations={['play', 'leaderboard', 'friends']} />
      </div>
    </main>
  );
}

function Stat({ label, value, accent }: { label: string; value: number; accent: string }) {
  return (
    <div className="flex flex-col items-center rounded-2xl border border-zinc-800 bg-zinc-900 px-3 py-5 sm:p-6">
      <span className={`text-3xl font-semibold ${accent}`}>{value}</span>
      <span className="mt-1 text-xs font-bold uppercase tracking-wider text-zinc-500">{label}</span>
    </div>
  );
}
