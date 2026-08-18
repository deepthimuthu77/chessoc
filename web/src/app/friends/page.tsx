"use client";

import { useCallback, useEffect, useState } from 'react';
import Link from 'next/link';
import { api, type Friend } from '@/lib/api';
import { useAuth } from '@/hooks/use-auth';
import { TopNav } from '@/components/ui/TopNav';

export default function FriendsPage() {
  const { account, token, ready, logout } = useAuth(true);
  const [friends, setFriends] = useState<Friend[]>([]);
  const [username, setUsername] = useState('');
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(() => {
    if (token) {
      api.friends(token).then((r) => setFriends(r.friends)).catch(() => setFriends([]));
    }
  }, [token]);

  useEffect(() => {
    refresh();
  }, [refresh]);

  async function add(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    try {
      await api.addFriend(username.trim(), token!);
      setUsername('');
      refresh();
    } catch (err) {
      setError((err as Error).message);
    }
  }

  if (!ready || !account) {
    return <main className="min-h-screen bg-slate-950" />;
  }

  return (
    <main className="min-h-screen bg-slate-950 text-white">
      <TopNav username={account.username} accountId={account.id} onLogout={logout} />

      <div className="mx-auto max-w-2xl px-6 py-12">
        <h1 className="mb-6 text-3xl font-black tracking-tighter">Friends</h1>

        <form onSubmit={add} className="mb-8 flex gap-3">
          <input
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            placeholder="add by username"
            className="flex-1 rounded-xl border border-zinc-700 bg-zinc-900 px-4 py-3 text-white placeholder-zinc-600 outline-none focus:border-blue-500"
          />
          <button
            type="submit"
            disabled={!username.trim()}
            className="rounded-xl bg-blue-600 px-6 py-3 font-bold text-white transition-colors hover:bg-blue-500 disabled:opacity-50"
          >
            Add
          </button>
        </form>
        {error && <p className="mb-4 text-sm font-medium text-rose-400">{error}</p>}

        <div className="overflow-hidden rounded-2xl border border-zinc-800 bg-zinc-900 shadow-xl">
          {friends.length === 0 ? (
            <div className="px-6 py-12 text-center text-sm italic text-zinc-600">
              No friends yet — add someone by their username.
            </div>
          ) : (
            friends.map((f) => (
              <div key={f.id} className="flex items-center gap-3 border-b border-zinc-800/70 px-6 py-4 last:border-0">
                <span className={`h-2.5 w-2.5 rounded-full ${f.online ? 'bg-green-500' : 'bg-zinc-600'}`} />
                <Link href={`/profile/${f.id}`} className="flex-1 font-medium text-zinc-100 hover:text-blue-400">
                  {f.username}
                </Link>
                <span className="text-xs uppercase tracking-wider text-zinc-500">{f.online ? 'online' : 'offline'}</span>
              </div>
            ))
          )}
        </div>
      </div>
    </main>
  );
}
