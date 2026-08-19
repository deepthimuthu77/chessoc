"use client";

import { useEffect, useState } from 'react';
import { useAuth } from '@/hooks/use-auth';
import { TopNav } from '@/components/ui/TopNav';
import { getSettings, setSettings, type Settings } from '@/lib/settings';

/* eslint-disable react-hooks/set-state-in-effect */

const TOGGLES: { key: keyof Settings; label: string; help: string }[] = [
  { key: 'sounds', label: 'Move sounds', help: 'Play a tick when a move is made.' },
  { key: 'autoFlip', label: 'Auto-flip board', help: 'Orient the board to your colour.' },
  { key: 'autoQueen', label: 'Auto-queen', help: 'Promote straight to a queen without asking.' },
  { key: 'confirmResign', label: 'Confirm resign', help: 'Require a second click to resign.' },
];

export default function SettingsPage() {
  const { account, ready, logout } = useAuth(true);
  const [settings, setLocal] = useState<Settings | null>(null);

  useEffect(() => {
    setLocal(getSettings());
  }, []);

  if (!ready || !account || !settings) {
    return <main className="min-h-screen bg-slate-950" />;
  }

  const toggle = (key: keyof Settings) => {
    const next = { ...settings, [key]: !settings[key] };
    setLocal(next);
    setSettings(next);
  };

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
      </div>
    </main>
  );
}
