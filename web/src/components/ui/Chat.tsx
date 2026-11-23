"use client";

import { useEffect, useRef, useState } from 'react';
import type { ChatMessage } from '@/lib/protocol';

const LABEL: Record<string, string> = { white: 'White', black: 'Black', spectator: 'Spectator' };
const COLOR: Record<string, string> = {
  white: 'text-zinc-200',
  black: 'text-zinc-400',
  spectator: 'text-accent-400',
};

export function Chat({ messages, onSend, disabled = false }: { messages: ChatMessage[]; onSend: (text: string) => void; disabled?: boolean }) {
  const [text, setText] = useState('');
  const scrollRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
    }
  }, [messages]);

  function submit(e: React.FormEvent) {
    e.preventDefault();
    if (!disabled && text.trim()) {
      onSend(text);
      setText('');
    }
  }

  return (
    <div className="surface flex h-56 w-full flex-col overflow-hidden">
      <div className="flex items-center justify-between border-b border-zinc-800 px-4 py-4">
        <h3 className="text-xs font-medium text-zinc-200">At the table</h3><span className="text-[10px] text-zinc-500">Room chat</span>
      </div>
      <div ref={scrollRef} className="flex-1 space-y-1 overflow-y-auto p-3">
        {messages.length === 0 ? (
          <div className="flex h-full items-center justify-center text-sm italic text-zinc-600">Say hello…</div>
        ) : (
          messages.map((m, i) => (
            <div key={i} className="text-sm">
              <span className={`font-bold ${COLOR[m.color] ?? 'text-zinc-400'}`}>{LABEL[m.color] ?? '?'}:</span>{' '}
              <span className="text-zinc-300">{m.text}</span>
            </div>
          ))
        )}
      </div>
      <form onSubmit={submit} className="border-t border-zinc-800 p-2">
        <input
          aria-label="Chat message"
          disabled={disabled}
          maxLength={500}
          value={text}
          onChange={(e) => setText(e.target.value)}
          placeholder={disabled ? 'Reconnect to send messages' : 'Message…'}
          className="w-full rounded-lg border border-zinc-800 bg-zinc-950 px-3 py-2 text-sm text-white placeholder-zinc-600 outline-none focus:border-accent-500"
        />
      </form>
    </div>
  );
}
