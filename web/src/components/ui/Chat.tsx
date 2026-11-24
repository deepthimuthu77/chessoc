"use client";

import { useEffect, useId, useRef, useState } from 'react';
import type { ChatMessage } from '@/lib/protocol';
import { CHAT_MAX_LENGTH, insertChatEmoji, limitChatText } from '@/lib/chat-input';
import { Icon } from './Icon';

const EMOJI = [
  ['😀', 'Grinning face'], ['😄', 'Smiling face'], ['😂', 'Joy'], ['🙂', 'Slight smile'],
  ['😉', 'Wink'], ['😎', 'Cool'], ['🤔', 'Thinking'], ['😅', 'Relieved'],
  ['😮', 'Surprised'], ['😢', 'Sad'], ['❤️', 'Heart'], ['👍', 'Thumbs up'],
  ['👏', 'Applause'], ['🙌', 'Celebration'], ['🤝', 'Handshake'], ['🙏', 'Thanks'],
  ['🔥', 'Fire'], ['🎉', 'Party'], ['💪', 'Strong'], ['✨', 'Sparkles'],
  ['🏆', 'Trophy'], ['🎯', 'Target'], ['♟️', 'Chess pawn'], ['💯', 'Hundred'],
] as const;

const LABEL: Record<string, string> = { white: 'White', black: 'Black', spectator: 'Spectator' };
const COLOR: Record<string, string> = {
  white: 'text-zinc-200',
  black: 'text-zinc-400',
  spectator: 'text-accent-400',
};

function renderChatText(text: string) {
  const urlRegex = /(https?:\/\/[^\s]+)/g;
  const parts = text.split(urlRegex);
  return parts.map((part, i) =>
    urlRegex.test(part) ? (
      <a
        key={i}
        href={part}
        target="_blank"
        rel="noopener noreferrer"
        className="text-accent-300 underline transition-colors hover:text-accent-200"
      >
        {part}
      </a>
    ) : (
      part
    )
  );
}

export function Chat({
  messages,
  onSend,
  disabled = false,
  failure,
  title = 'At the table',
  subtitle = 'Room chat',
  onClose,
}: {
  messages: ChatMessage[];
  onSend: (text: string) => boolean;
  disabled?: boolean;
  failure?: { message: string; text: string } | null;
  title?: string;
  subtitle?: string;
  onClose?: () => void;
}) {
  const [text, setText] = useState('');
  const [sendFailed, setSendFailed] = useState(false);
  const [emojiOpen, setEmojiOpen] = useState(false);
  const [lengthError, setLengthError] = useState(false);
  const scrollRef = useRef<HTMLDivElement>(null);
  const rootRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const emojiButtonRef = useRef<HTMLButtonElement>(null);
  const selectionRef = useRef({ start: 0, end: 0 });
  const id = useId();

  useEffect(() => {
    if (!emojiOpen || disabled) return;
    document.getElementById(`${id}-emoji`)?.querySelector<HTMLButtonElement>('button')?.focus();
    function outside(event: PointerEvent) {
      if (!rootRef.current?.contains(event.target as Node)) setEmojiOpen(false);
    }
    function escape(event: KeyboardEvent) {
      if (event.key === 'Escape') { setEmojiOpen(false); emojiButtonRef.current?.focus(); }
    }
    document.addEventListener('pointerdown', outside);
    document.addEventListener('keydown', escape);
    return () => {
      document.removeEventListener('pointerdown', outside);
      document.removeEventListener('keydown', escape);
    };
  }, [emojiOpen, disabled, id]);

  function chooseEmoji(emoji: string) {
    const next = insertChatEmoji(text, emoji, selectionRef.current.start, selectionRef.current.end);
    if (!next) { setLengthError(true); return; }
    setText(next.value);
    setSendFailed(false);
    setLengthError(false);
    setEmojiOpen(false);
    requestAnimationFrame(() => {
      inputRef.current?.focus();
      inputRef.current?.setSelectionRange(next.cursor, next.cursor);
    });
  }

  function toggleEmoji() {
    selectionRef.current = {
      start: inputRef.current?.selectionStart ?? text.length,
      end: inputRef.current?.selectionEnd ?? text.length,
    };
    setEmojiOpen(open => !open);
  }

  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
    }
  }, [messages]);

  function submit(e: React.FormEvent) {
    e.preventDefault();
    if (!disabled && text.trim()) {
      const sent = onSend(text);
      setSendFailed(!sent);
      if (sent) { setText(''); setEmojiOpen(false); setLengthError(false); selectionRef.current = { start: 0, end: 0 }; }
    }
  }

  return (
    <div ref={rootRef} className="surface relative flex min-h-80 w-full flex-col overflow-hidden">
      <div className="flex items-center justify-between border-b border-zinc-800 px-4 py-4">
        <div>
          <h3 className="text-xs font-medium text-zinc-200">{title}</h3>
          <span className="text-[10px] text-zinc-500">{subtitle}</span>
        </div>
        {onClose && (
          <button
            type="button"
            onClick={onClose}
            aria-label="Close chat"
            className="flex h-7 w-7 items-center justify-center rounded-lg text-zinc-400 hover:bg-zinc-800 hover:text-white"
          >
            ✕
          </button>
        )}
      </div>
      <div ref={scrollRef} role="log" aria-label="Room messages" aria-live="polite" aria-relevant="additions" className="max-h-48 min-h-32 flex-1 space-y-1 overflow-y-auto p-3">
        {messages.length === 0 ? (
          <div className="flex h-full items-center justify-center text-sm italic text-zinc-600">Say hello…</div>
        ) : (
          messages.map((m, i) => (
            <div key={i} className="text-sm">
              <span className={`font-bold ${COLOR[m.color] ?? ((m.color as string) === 'You' ? 'text-accent-400' : 'text-zinc-300')}`}>{LABEL[m.color] ?? m.color ?? '?'}:</span>{' '}
              <span className="whitespace-pre-wrap break-words text-zinc-300 [overflow-wrap:anywhere]">{renderChatText(m.text)}</span>
            </div>
          ))
        )}
      </div>
      {emojiOpen && !disabled && <div id={`${id}-emoji`} role="group" aria-label="Choose an emoji" className="frosted-panel absolute inset-x-2 bottom-[4.5rem] z-10 rounded-xl p-3">
        <p className="mb-2 text-xs text-zinc-400">A little expression</p>
        <div className="grid grid-cols-6 gap-1">
          {EMOJI.map(([emoji, label]) => <button key={label} type="button" aria-label={label} onClick={() => chooseEmoji(emoji)} className="flex min-h-10 items-center justify-center rounded-md text-lg hover:bg-zinc-800 focus-visible:outline focus-visible:outline-2 focus-visible:outline-accent-400">{emoji}</button>)}
        </div>
      </div>}
      <form onSubmit={submit} className="border-t border-zinc-800 p-2">
        <div className="flex items-center gap-1">
        <button ref={emojiButtonRef} type="button" aria-label="Add emoji" aria-expanded={emojiOpen && !disabled} aria-controls={emojiOpen && !disabled ? `${id}-emoji` : undefined} disabled={disabled} onClick={toggleEmoji} className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg text-zinc-400 hover:bg-zinc-800 hover:text-white disabled:opacity-40"><Icon name="smile" size={20} /></button>
        <input
          ref={inputRef}
          aria-label="Chat message"
          disabled={disabled}
          value={text}
          onSelect={(e) => { selectionRef.current = { start: e.currentTarget.selectionStart ?? text.length, end: e.currentTarget.selectionEnd ?? text.length }; }}
          onChange={(e) => { const next = limitChatText(e.target.value); setText(next); setLengthError(next !== e.target.value); setSendFailed(false); }}
          aria-describedby={sendFailed || lengthError || failure ? `${id}-feedback` : undefined}
          placeholder={disabled ? 'Reconnect to send messages' : 'Message…'}
          className="min-w-0 flex-1 rounded-lg border border-zinc-800 bg-zinc-950 px-3 py-2 text-sm text-white placeholder-zinc-600 outline-none focus:border-accent-500"
        />
        <button type="submit" aria-label="Send message" disabled={disabled || !text.trim()} className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-accent-400 text-slate-950 hover:bg-accent-300 disabled:cursor-not-allowed disabled:opacity-30"><Icon name="send" size={18} /></button>
        </div>
        <div className="mt-1 flex justify-between px-1 text-[10px] text-zinc-500"><span>Keep it friendly</span><span>{text.length}/{CHAT_MAX_LENGTH}</span></div>
        {(sendFailed || lengthError || failure) && <div id={`${id}-feedback`} role="alert" className="mt-1 text-xs text-amber-300">
          {sendFailed ? 'Message not sent. Reconnect and try again.' : lengthError ? 'Message limit reached. Make a little room for more.' : failure?.message}
          {failure?.text && !text && <button type="button" disabled={disabled} onClick={() => { setText(failure.text); inputRef.current?.focus(); }} className="ml-1 underline">Restore unsent message</button>}
        </div>}
      </form>
    </div>
  );
}
