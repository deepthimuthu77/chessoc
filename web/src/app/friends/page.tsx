"use client";

import { useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import { api, type Friend, type FriendCard } from '@/lib/api';
import { useAuth } from '@/hooks/use-auth';
import { TopNav } from '@/components/ui/TopNav';
import { PageHeading, JourneyNav } from '@/components/ui/PageHeading';
import { Icon } from '@/components/ui/Icon';

export default function FriendsPage() {
  const { account, token, ready, logout, handleAuthError, authError, retryAuth } = useAuth(true);
  const [attempt, setAttempt] = useState(0);
  const key = `${token}:${attempt}`;
  const [result, setResult] = useState<{ key: string; friends: Friend[]; incoming: FriendCard[]; outgoing: FriendCard[]; error?: string } | null>(null);
  const loading = result?.key !== key;
  const friends = loading ? [] : result.friends;
  const incoming = loading ? [] : result.incoming;
  const outgoing = loading ? [] : result.outgoing;
  const [username, setUsername] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [note, setNote] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const mutationPending = useRef(false);

  useEffect(() => {
    if (!token) return;
    let cancelled = false;
    Promise.all([api.friends(token), api.friendRequests(token)]).then(([list, requests]) => {
      if (!cancelled) setResult({ key, friends: list.friends, incoming: requests.incoming, outgoing: requests.outgoing });
    }).catch((error: unknown) => {
      if (!cancelled && !handleAuthError(error, token)) {
        setResult({ key, friends: [], incoming: [], outgoing: [], error: error instanceof Error ? error.message : 'Could not load friends and requests.' });
      }
    });
    return () => { cancelled = true; };
  }, [token, key, handleAuthError]);

  async function add(e: React.FormEvent) {
    e.preventDefault();
    if (!token || !username.trim() || mutationPending.current) return;
    mutationPending.current = true;
    setBusy(true);
    setError(null);
    setNote(null);
    try {
      const r = await api.addFriend(username.trim(), token);
      if (!r.ok || !['friends', 'already_friends', 'requested'].includes(r.status)) {
        throw new Error(r.status === 'none' ? 'You cannot add yourself as a friend.' : 'The friend request was not sent. Please retry.');
      }
      setUsername('');
      setNote(r.status === 'friends' ? 'You are now friends.'
        : r.status === 'already_friends' ? 'Already friends.'
        : 'Request sent.');
      setAttempt((n) => n + 1);
    } catch (err) {
      if (!handleAuthError(err, token)) setError(err instanceof Error ? err.message : 'Could not send request. Please retry.');
    } finally {
      mutationPending.current = false;
      setBusy(false);
    }
  }

  async function respond(id: string, accept: boolean) {
    if (!token || mutationPending.current) return;
    mutationPending.current = true;
    setBusy(true);
    setError(null);
    setNote(null);
    try {
      const response = await (accept ? api.acceptFriend(id, token) : api.declineFriend(id, token));
      if (!response.ok) {
        setAttempt((n) => n + 1);
        throw new Error('This request is no longer available. The request list is being refreshed.');
      }
      setNote(accept ? 'Friend request accepted.' : 'Friend request declined.');
      setAttempt((n) => n + 1);
    } catch (err) {
      if (!handleAuthError(err, token)) setError(err instanceof Error ? err.message : 'Could not update request. Please retry.');
    } finally {
      mutationPending.current = false;
      setBusy(false);
    }
  }

  if (!ready || !account || !token) {
    return <main className="min-h-screen bg-slate-950 p-6 text-zinc-300"><p role="status">{ready ? 'Redirecting to sign in…' : 'Loading session…'}</p></main>;
  }

  return (
    <main className="min-h-screen app-background text-white">
      <TopNav username={account.username} accountId={account.id} onLogout={logout} />

      <div id="main-content" tabIndex={-1} className="page-wrap page-wrap-narrow page-enter">
        <PageHeading eyebrow="PLAY TOGETHER" title="Friends" description="Add players by username, manage requests and invite friends to a game." />
        {authError && <p role="alert" className="mb-4 text-rose-400">{authError} <button type="button" onClick={() => void retryAuth()} className="underline">Retry session</button></p>}

        <form onSubmit={add} className="surface mb-5 flex flex-wrap gap-3 p-5"><label className="field-label w-full" htmlFor="friend-username">Add a friend by username</label>
          <input
            id="friend-username" aria-label="Friend’s username"
            disabled={busy}
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            placeholder="add by username"
            className="field min-w-0 flex-1"
          />
          <button
            type="submit"
            disabled={busy || !username.trim()}
            className="button-primary"
          >
            {busy ? 'Please wait…' : 'Send request'}
          </button>
        </form>
        {note && <p role="status" className="mb-4 text-sm font-medium text-accent-400">{note}</p>}
        {error && <p role="alert" className="mb-4 text-sm font-medium text-rose-400">{error} Retry the action to try again.</p>}
        {!loading && result?.error && <div role="alert" className="mb-4 text-rose-400"><p>Could not load friends and requests: {result.error}</p><button type="button" onClick={() => setAttempt((n) => n + 1)} className="mt-2 underline">Retry friends and requests</button></div>}
        {loading && <p role="status" className="my-4 text-zinc-400">Loading friends and requests…</p>}
        {!loading && !result?.error && incoming.length === 0 && outgoing.length === 0 && <p className="my-4 text-sm text-zinc-400">No pending friend requests.</p>}

        {incoming.length > 0 && (
          <section className="mb-6 mt-4">
            <h2 className="mb-3 text-xs font-bold uppercase tracking-wider text-zinc-500">Requests</h2>
            <div className="overflow-hidden rounded-2xl border border-zinc-800 bg-zinc-900">
              {incoming.map((r) => (
                <div key={r.id} className="flex items-center gap-3 border-b border-zinc-800/70 px-6 py-3 last:border-0">
                  <Link href={`/profile/${r.id}`} className="flex-1 font-medium text-zinc-100 hover:text-accent-400">
                    {r.username}
                  </Link>
                  <button
                    type="button"
                    disabled={busy}
                    aria-label={`Accept request from ${r.username}`}
                    onClick={() => respond(r.id, true)}
                    className="rounded-lg bg-accent-600 px-3 py-1.5 text-sm font-bold text-white transition-colors hover:bg-accent-500"
                  >
                    Accept
                  </button>
                  <button
                    type="button"
                    disabled={busy}
                    aria-label={`Decline request from ${r.username}`}
                    onClick={() => respond(r.id, false)}
                    className="rounded-lg border border-zinc-700 px-3 py-1.5 text-sm font-bold text-zinc-300 transition-colors hover:bg-zinc-800"
                  >
                    Decline
                  </button>
                </div>
              ))}
            </div>
          </section>
        )}

        {outgoing.length > 0 && (
          <section className="mb-6">
            <h2 className="mb-3 text-xs font-bold uppercase tracking-wider text-zinc-500">Pending</h2>
            <div className="overflow-hidden rounded-2xl border border-zinc-800 bg-zinc-900">
              {outgoing.map((r) => (
                <div key={r.id} className="flex items-center gap-3 border-b border-zinc-800/70 px-6 py-3 last:border-0">
                  <span className="flex-1 font-medium text-zinc-300">{r.username}</span>
                  <span className="text-xs uppercase tracking-wider text-zinc-500">requested</span>
                </div>
              ))}
            </div>
          </section>
        )}

        <div className="section-head"><h2>Your circle <span className="text-xs text-zinc-500">{friends.length}</span></h2><Link href="/?opponent=friend#create-game" className="text-link">Create a game<Icon name="arrow" size={14} /></Link></div>
        <div className="overflow-hidden rounded-2xl border border-zinc-800 bg-zinc-900">
          {loading || result?.error ? null : friends.length === 0 ? (
            <div className="empty-state"><span className="icon-well"><Icon name="people" /></span><h2>Good company changes the game.</h2><p>Add a friend by username above. Once you’re connected, create a room and share a board.</p><Link href="/?opponent=friend#create-game" className="button-secondary">Set up a game<Icon name="arrow" size={15} /></Link>
            </div>
          ) : (
            friends.map((f) => (
              <div key={f.id} className="flex items-center gap-3 border-b border-zinc-800/70 px-6 py-4 last:border-0">
                <span className={`h-2.5 w-2.5 rounded-full ${f.online ? 'bg-green-500' : 'bg-zinc-600'}`} />
                <Link href={`/profile/${f.id}`} className="flex-1 font-medium text-zinc-100 hover:text-accent-400">
                  {f.username}
                </Link>
                <span className="text-xs uppercase tracking-wider text-zinc-500">{f.online ? 'online' : 'offline'}</span>
              </div>
            ))
          )}
        </div>
        <JourneyNav />
      </div>
    </main>
  );
}
