"use client";

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { api } from '@/lib/api';
import { useAuth } from '@/hooks/use-auth';
import { TopNav } from '@/components/ui/TopNav';
import { getSettings, setSettings, type Settings } from '@/lib/settings';

/* eslint-disable react-hooks/set-state-in-effect */

const TOGGLES: { key: keyof Settings; label: string; help: string }[] = [
  { key: 'sounds', label: 'Move sounds', help: 'Play a tick when a move is made.' },
  { key: 'autoFlip', label: 'Auto-flip board', help: 'Orient the board to your colour.' },
  { key: 'autoQueen', label: 'Auto-queen', help: 'Promote straight to a queen without asking.' },
  { key: 'confirmResign', label: 'Confirm resign', help: 'Require a second click to resign.' },
  { key: 'reducedMotion', label: 'Reduced motion', help: 'Turn off non-essential animations.' },
  { key: 'premove', label: 'Premove', help: 'Queue a move during your opponent’s turn.' },
];

export default function SettingsPage() {
  const { account, token, ready, isGuest, logout } = useAuth(true);
  const [settings, setLocal] = useState<Settings | null>(null);
  const [oldPw, setOldPw] = useState('');
  const [newPw, setNewPw] = useState('');
  const [pwMsg, setPwMsg] = useState<string | null>(null);
  const [delPw, setDelPw] = useState('');
  const [delMsg, setDelMsg] = useState<string | null>(null);

  useEffect(() => {
    setLocal(getSettings());
  }, []);

  if (!ready || !account || !settings || !token) {
    return <main className="min-h-screen bg-slate-950" />;
  }

  const toggle = (key: keyof Settings) => {
    const next = { ...settings, [key]: !settings[key] };
    setLocal(next);
    setSettings(next);
  };

  async function changePassword(e: React.FormEvent) {
    e.preventDefault();
    setPwMsg(null);
    try {
      await api.changePassword(oldPw, newPw, token!);
      setOldPw('');
      setNewPw('');
      setPwMsg('Password changed.');
    } catch (err) {
      setPwMsg((err as Error).message);
    }
  }

  async function deleteAccount(e: React.FormEvent) {
    e.preventDefault();
    setDelMsg(null);
    try {
      await api.deleteAccount(delPw, token!);
      logout();
    } catch (err) {
      setDelMsg((err as Error).message);
    }
  }

  return (
    <main className="min-h-screen bg-slate-950 text-white">
      <TopNav username={account.username} accountId={account.id} onLogout={logout} />

      <div className="mx-auto max-w-2xl px-6 py-12">
        <h1 className="mb-6 text-3xl font-black tracking-tighter">Settings</h1>

        <div className="overflow-hidden rounded-2xl border border-zinc-800 bg-zinc-900 shadow-xl">
          {TOGGLES.map((t) => (
            <div key={t.key} className="flex items-center justify-between border-b border-zinc-800/70 px-6 py-4 last:border-0">
              <div>
                <div className="font-medium text-zinc-100">{t.label}</div>
                <div className="text-xs text-zinc-500">{t.help}</div>
              </div>
              <button
                type="button"
                role="switch"
                aria-checked={settings[t.key]}
                onClick={() => toggle(t.key)}
                className={`relative h-6 w-11 rounded-full transition-colors ${settings[t.key] ? 'bg-blue-600' : 'bg-zinc-700'}`}
              >
                <span className={`absolute top-0.5 h-5 w-5 rounded-full bg-white transition-all ${settings[t.key] ? 'left-[22px]' : 'left-0.5'}`} />
              </button>
            </div>
          ))}
        </div>

        <h2 className="mb-3 mt-10 text-xs font-bold uppercase tracking-wider text-zinc-500">Account</h2>
        {isGuest ? (
          <div className="border border-blue-500/30 bg-blue-500/10 p-6">
            <p className="text-sm text-zinc-300">Create an account to keep your games and manage a password.</p>
            <Link href="/upgrade" className="mt-4 inline-block bg-blue-600 px-5 py-2 text-sm font-bold text-white hover:bg-blue-500">
              Create account
            </Link>
          </div>
        ) : <>
        <form onSubmit={changePassword} className="mb-4 flex flex-col gap-3 rounded-2xl border border-zinc-800 bg-zinc-900 p-6">
          <input
            type="password"
            value={oldPw}
            onChange={(e) => setOldPw(e.target.value)}
            placeholder="Current password"
            className="rounded-xl border border-zinc-700 bg-zinc-950 px-4 py-2.5 text-white placeholder-zinc-600 outline-none focus:border-blue-500"
          />
          <input
            type="password"
            value={newPw}
            onChange={(e) => setNewPw(e.target.value)}
            placeholder="New password"
            className="rounded-xl border border-zinc-700 bg-zinc-950 px-4 py-2.5 text-white placeholder-zinc-600 outline-none focus:border-blue-500"
          />
          <button type="submit" className="self-start rounded-xl bg-blue-600 px-5 py-2 font-bold text-white transition-colors hover:bg-blue-500">
            Change password
          </button>
          {pwMsg && <p className="text-sm text-zinc-400">{pwMsg}</p>}
        </form>

        <form onSubmit={deleteAccount} className="flex flex-col gap-3 rounded-2xl border border-rose-500/30 bg-zinc-900 p-6">
          <span className="text-sm font-bold text-rose-400">Delete account</span>
          <input
            type="password"
            value={delPw}
            onChange={(e) => setDelPw(e.target.value)}
            placeholder="Password to confirm"
            className="rounded-xl border border-zinc-700 bg-zinc-950 px-4 py-2.5 text-white placeholder-zinc-600 outline-none focus:border-rose-500"
          />
          <button type="submit" className="self-start rounded-xl bg-rose-600 px-5 py-2 font-bold text-white transition-colors hover:bg-rose-500">
            Delete my account
          </button>
          {delMsg && <p className="text-sm text-rose-400">{delMsg}</p>}
        </form>
        </>}
      </div>
    </main>
  );
}
