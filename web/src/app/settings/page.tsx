"use client";

import { useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import { api } from '@/lib/api';
import { useAuth } from '@/hooks/use-auth';
import { TopNav } from '@/components/ui/TopNav';
import { PageHeading, JourneyNav } from '@/components/ui/PageHeading';
import { Icon } from '@/components/ui/Icon';
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
  const { account, token, ready, isGuest, logout, handleAuthError, authError, retryAuth } = useAuth(true);
  const [settings, setLocal] = useState<Settings | null>(null);
  const [oldPw, setOldPw] = useState('');
  const [newPw, setNewPw] = useState('');
  const [pwMsg, setPwMsg] = useState<string | null>(null);
  const [delPw, setDelPw] = useState('');
  const [delMsg, setDelMsg] = useState<string | null>(null);
  const [pwError, setPwError] = useState(false);
  const [busy, setBusy] = useState<'password' | 'delete' | null>(null);
  const [deleteConfirmed, setDeleteConfirmed] = useState(false);
  const [settingsError, setSettingsError] = useState<string | null>(null);
  const mutationPending = useRef(false);

  useEffect(() => {
    setLocal(getSettings());
  }, []);

  if (!ready || !account || !settings || !token) {
    return <main className="min-h-screen bg-slate-950 p-6 text-zinc-300"><p role="status">{ready && !account ? 'Redirecting to sign in…' : 'Loading settings…'}</p></main>;
  }

  const toggle = (key: keyof Settings) => {
    const next = { ...settings, [key]: !settings[key] };
    try {
      setSettings(next);
      setLocal(next);
      setSettingsError(null);
    } catch {
      setSettingsError('Could not save preferences on this device. Allow browser storage, then try the switch again.');
    }
  };

  async function changePassword(e: React.FormEvent) {
    e.preventDefault();
    if (!token || mutationPending.current) return;
    mutationPending.current = true;
    setBusy('password');
    setPwMsg(null);
    setPwError(false);
    try {
      const response = await api.changePassword(oldPw, newPw, token);
      if (!response.ok) throw new Error('Password was not changed. Please retry.');
      setOldPw('');
      setNewPw('');
      setPwMsg('Password changed.');
    } catch (err) {
      if (!handleAuthError(err, token)) {
        setPwError(true);
        setPwMsg(err instanceof Error ? err.message : 'Could not change password. Please retry.');
      }
    } finally {
      mutationPending.current = false;
      setBusy(null);
    }
  }

  async function deleteAccount(e: React.FormEvent) {
    e.preventDefault();
    if (!token || !deleteConfirmed || mutationPending.current) return;
    mutationPending.current = true;
    setBusy('delete');
    setDelMsg(null);
    try {
      const response = await api.deleteAccount(delPw, token);
      if (!response.ok) throw new Error('Account was not deleted. Please retry.');
      logout();
    } catch (err) {
      if (!handleAuthError(err, token)) setDelMsg(err instanceof Error ? err.message : 'Could not delete account. Please retry.');
    } finally {
      mutationPending.current = false;
      setBusy(null);
    }
  }

  return (
    <main className="min-h-screen app-background text-white">
      <TopNav username={account.username} accountId={account.id} onLogout={logout} />

      <div id="main-content" tabIndex={-1} className="page-wrap page-wrap-narrow page-enter">
        <PageHeading eyebrow="YOUR PREFERENCES" title="Settings" description="Manage board behavior, sounds, motion and your account." />
        {authError && <p role="alert" className="mb-4 text-rose-400">{authError} <button type="button" onClick={() => void retryAuth()} className="underline">Retry session</button></p>}
        {settingsError && <p role="alert" className="mb-4 text-rose-400">{settingsError}</p>}

        <div className="surface overflow-hidden"><div className="flex items-center gap-3 border-b border-zinc-800 px-6 py-5"><span className="icon-well"><Icon name="settings" size={18} /></span><div><h2 className="text-sm font-medium">Your playing experience</h2><p className="mt-1 text-xs text-zinc-500">Preferences are saved on this device.</p></div></div>
          {TOGGLES.map((t) => (
            <div key={t.key} className="flex items-center justify-between gap-5 border-b border-zinc-800/70 px-6 py-4 last:border-0">
              <div>
                <div id={`${t.key}-label`} className="font-medium text-zinc-100">{t.label}</div>
                <div id={`${t.key}-help`} className="mt-1 text-xs leading-relaxed text-zinc-500">{t.help}</div>
              </div>
              <button
                type="button"
                role="switch"
                aria-labelledby={`${t.key}-label`}
                aria-describedby={`${t.key}-help`}
                aria-checked={settings[t.key]}
                onClick={() => toggle(t.key)}
                className="relative h-11 w-14 shrink-0 rounded-lg"
              >
                <span aria-hidden="true" className={`absolute inset-x-1.5 top-2.5 h-6 rounded-full transition-colors ${settings[t.key] ? 'bg-accent-600' : 'bg-zinc-700'}`}><span className={`absolute top-0.5 h-5 w-5 rounded-full bg-white transition-all ${settings[t.key] ? 'left-[22px]' : 'left-0.5'}`} /></span>
              </button>
            </div>
          ))}
        </div>

        <h2 className="mb-3 mt-10 text-xs font-bold uppercase tracking-wider text-zinc-500">Account</h2>
        {isGuest ? (
          <div className="surface p-6">
            <p className="text-sm text-zinc-300">Create an account to keep your games and manage a password.</p>
            <Link href="/upgrade" className="button-primary mt-4">
              Create account
            </Link>
          </div>
        ) : <>
        <form onSubmit={changePassword} className="mb-4 flex flex-col gap-3 rounded-2xl border border-zinc-800 bg-zinc-900 p-6">
          <label htmlFor="current-password" className="text-sm text-zinc-300">Current password</label>
          <input
            id="current-password"
            name="currentPassword"
            autoComplete="current-password"
            required
            disabled={!!busy}
            type="password"
            value={oldPw}
            onChange={(e) => setOldPw(e.target.value)}
            placeholder="Current password"
            className="rounded-xl border border-zinc-700 bg-zinc-950 px-4 py-2.5 text-white placeholder-zinc-600 outline-none focus:border-accent-500"
          />
          <label htmlFor="new-password" className="text-sm text-zinc-300">New password</label>
          <input
            id="new-password"
            name="newPassword"
            autoComplete="new-password"
            minLength={6}
            required
            disabled={!!busy}
            type="password"
            value={newPw}
            onChange={(e) => setNewPw(e.target.value)}
            placeholder="New password"
            className="rounded-xl border border-zinc-700 bg-zinc-950 px-4 py-2.5 text-white placeholder-zinc-600 outline-none focus:border-accent-500"
          />
          <button type="submit" disabled={!!busy || !oldPw || newPw.length < 6} className="self-start rounded-xl bg-accent-600 px-5 py-2 font-bold text-white transition-colors hover:bg-accent-500 disabled:opacity-50">
            {busy === 'password' ? 'Changing password…' : 'Change password'}
          </button>
          {pwMsg && <p role={pwError ? 'alert' : 'status'} className={pwError ? 'text-sm text-rose-400' : 'text-sm text-zinc-300'}>{pwMsg}</p>}
        </form>

        <form onSubmit={deleteAccount} className="flex flex-col gap-3 rounded-2xl border border-rose-500/30 bg-zinc-900 p-6">
          <span className="text-sm font-bold text-rose-400">Delete account</span>
          <p id="delete-warning" className="text-sm text-zinc-300">Deleting your account is permanent.</p>
          <label htmlFor="delete-password" className="text-sm text-zinc-300">Password to confirm deletion</label>
          <input
            id="delete-password"
            name="deletePassword"
            autoComplete="current-password"
            required
            disabled={!!busy}
            type="password"
            value={delPw}
            onChange={(e) => setDelPw(e.target.value)}
            placeholder="Password to confirm"
            className="rounded-xl border border-zinc-700 bg-zinc-950 px-4 py-2.5 text-white placeholder-zinc-600 outline-none focus:border-rose-500"
          />
          <label className="flex items-center gap-2 text-sm text-zinc-300">
            <input type="checkbox" required checked={deleteConfirmed} disabled={!!busy} onChange={(e) => setDeleteConfirmed(e.target.checked)} aria-describedby="delete-warning" />
            I understand and want to permanently delete my account.
          </label>
          <button type="submit" disabled={!!busy || !delPw || !deleteConfirmed} className="self-start rounded-xl bg-rose-600 px-5 py-2 font-bold text-white transition-colors hover:bg-rose-500 disabled:opacity-50">
            {busy === 'delete' ? 'Deleting account…' : 'Delete my account'}
          </button>
          {delMsg && <p role="alert" className="text-sm text-rose-400">{delMsg}</p>}
        </form>
        </>}
        <JourneyNav />
      </div>
    </main>
  );
}
