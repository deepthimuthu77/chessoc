"use client";

import { useEffect, useState } from 'react';
import { useParams } from 'next/navigation';
import { api, type Profile, type RatingPoint } from '@/lib/api';
import { useAuth } from '@/hooks/use-auth';
import { TopNav } from '@/components/ui/TopNav';
import { JourneyNav } from '@/components/ui/PageHeading';
import { Icon } from '@/components/ui/Icon';
import Link from 'next/link';
import { RatingChart } from '@/components/ui/RatingChart';

export default function ProfilePage() {
  const params = useParams<{ id: string }>();
  return <ProfileContent key={params.id} id={params.id} />;
}

function ProfileContent({ id }: { id: string }) {
  const { account, token, logout, handleAuthError, authError, retryAuth } = useAuth();
  const [attempt, setAttempt] = useState(0);
  const [result, setResult] = useState<{ attempt: number; profile?: Profile; error?: string } | null>(null);
  const loading = result?.attempt !== attempt;
  const profile = loading ? null : result.profile;
  const [friendNote, setFriendNote] = useState<string | null>(null);
  const [friendError, setFriendError] = useState<string | null>(null);
  const [adding, setAdding] = useState(false);
  const [pool, setPool] = useState<{ variant: string; speed: string } | null>(null);
  const [historyAttempt, setHistoryAttempt] = useState(0);
  const [historyResult, setHistoryResult] = useState<{ key: string; points: RatingPoint[]; error?: string } | null>(null);

  useEffect(() => {
    let cancelled = false;
    api.profile(id).then((profile) => {
      if (!cancelled) setResult({ attempt, profile });
    }).catch((error: unknown) => {
      if (!cancelled) setResult({ attempt, error: error instanceof Error ? error.message : 'Could not load profile.' });
    });
    return () => { cancelled = true; };
  }, [id, attempt]);

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

  const isSelf = account?.id === id;

  async function addFriend() {
    if (!profile || !token || adding) return;
    setAdding(true);
    setFriendError(null);
    try {
      const response = await api.addFriend(profile.account.username, token);
      if (!response.ok || !['friends', 'already_friends', 'requested'].includes(response.status)) {
        throw new Error('The friend request was not sent. Please retry.');
      }
      setFriendNote(response.status === 'friends' ? 'You are now friends.'
        : response.status === 'already_friends' ? 'Already friends.' : 'Request sent.');
    } catch (error) {
      if (!handleAuthError(error, token)) setFriendError(error instanceof Error ? error.message : 'Could not add friend. Please retry.');
    } finally {
      setAdding(false);
    }
  }

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
              <button
                type="button"
                disabled={adding || !!friendNote}
                onClick={addFriend}
                className="mb-8 rounded-xl bg-accent-600 px-6 py-2.5 font-bold text-white transition-colors hover:bg-accent-500 disabled:opacity-60"
              >
                {adding ? 'Sending…' : friendNote ?? 'Add friend'}
              </button>
            )}
            {friendNote && <p role="status" className="mb-4 text-accent-400">{friendNote}</p>}
            {friendError && <p role="alert" className="mb-4 text-rose-400">{friendError} Use Add friend to retry.</p>}

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
