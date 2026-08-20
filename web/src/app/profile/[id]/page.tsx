"use client";

import { useEffect, useState } from 'react';
import { useParams } from 'next/navigation';
import { api, type Profile, type RatingPoint } from '@/lib/api';
import { useAuth } from '@/hooks/use-auth';
import { TopNav } from '@/components/ui/TopNav';
import { RatingChart } from '@/components/ui/RatingChart';

export default function ProfilePage() {
  const params = useParams<{ id: string }>();
  const { account, token, logout } = useAuth();
  const [profile, setProfile] = useState<Profile | null>(null);
  const [added, setAdded] = useState(false);
  const [pool, setPool] = useState<{ variant: string; speed: string } | null>(null);
  const [history, setHistory] = useState<RatingPoint[]>([]);

  useEffect(() => {
    api.profile(params.id).then(setProfile).catch(() => setProfile(null));
  }, [params.id]);

  // Chart pool: the user's pick, else the pool with the most games. Derived, not stored.
  const topPool = profile && profile.pools.length > 0
    ? [...profile.pools].sort((a, b) => b.games - a.games)[0]
    : null;
  const selVariant = pool?.variant ?? topPool?.variant ?? null;
  const selSpeed = pool?.speed ?? topPool?.speed ?? null;

  useEffect(() => {
    if (selVariant && selSpeed) {
      api.ratingHistory(params.id, selVariant, selSpeed)
        .then((r) => setHistory(r.points))
        .catch(() => setHistory([]));
    }
  }, [params.id, selVariant, selSpeed]);

  const isSelf = account?.id === params.id;

  return (
    <main className="min-h-screen bg-slate-950 text-white">
      <TopNav username={account?.username} accountId={account?.id} onLogout={account ? logout : undefined} />

      <div className="mx-auto max-w-2xl px-6 py-12">
        {!profile ? (
          <p className="text-center text-zinc-600">Loading…</p>
        ) : (
          <>
            <div className="mb-8 flex items-center justify-between">
              <div>
                <h1 className="text-4xl font-black tracking-tighter">{profile.account.username}</h1>
                <p className="mt-1 text-zinc-500">Standard · Blitz rating</p>
              </div>
              <div className="text-right">
                <div className="text-5xl font-black text-blue-400">{profile.rating}</div>
              </div>
            </div>

            {token && !isSelf && (
              <button
                type="button"
                disabled={added}
                onClick={() => api.addFriend(profile.account.username, token).then(() => setAdded(true)).catch(() => {})}
                className="mb-8 rounded-xl bg-blue-600 px-6 py-2.5 font-bold text-white transition-colors hover:bg-blue-500 disabled:opacity-60"
              >
                {added ? 'Request sent' : 'Add friend'}
              </button>
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
                        onClick={() => setPool({ variant: p.variant, speed: p.speed })}
                        className={`flex w-full items-center justify-between border-b border-zinc-800/70 px-6 py-3 text-left transition-colors last:border-0 ${
                          on ? 'bg-blue-500/10' : 'hover:bg-zinc-800/40'
                        }`}
                      >
                        <span className="capitalize text-zinc-300">{p.variant} · {p.speed}</span>
                        <span className="flex-1 px-4 text-right text-xs text-zinc-500">{p.games} games</span>
                        <span className="w-16 text-right font-bold text-blue-400">{p.rating}</span>
                      </button>
                    );
                  })}
                </div>

                {selVariant && selSpeed && (
                  <div className="mt-4 rounded-2xl border border-zinc-800 bg-zinc-900 p-4">
                    <div className="mb-2 px-2 text-xs font-medium capitalize text-zinc-500">
                      {selVariant} · {selSpeed} history
                    </div>
                    <RatingChart points={history} />
                  </div>
                )}
              </div>
            )}
          </>
        )}
      </div>
    </main>
  );
}

function Stat({ label, value, accent }: { label: string; value: number; accent: string }) {
  return (
    <div className="flex flex-col items-center rounded-2xl border border-zinc-800 bg-zinc-900 p-6 shadow-xl">
      <span className={`text-3xl font-black ${accent}`}>{value}</span>
      <span className="mt-1 text-xs font-bold uppercase tracking-wider text-zinc-500">{label}</span>
    </div>
  );
}
