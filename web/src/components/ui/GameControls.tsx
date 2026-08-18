"use client";

import { useEffect, useState } from 'react';

interface GameControlsProps {
  active: boolean;
  canOfferDraw: boolean;
  drawOffer: 'none' | 'incoming' | 'sent';
  onResign: () => void;
  onOfferDraw: () => void;
  onRespondDraw: (accept: boolean) => void;
}

export function GameControls({
  active,
  canOfferDraw,
  drawOffer,
  onResign,
  onOfferDraw,
  onRespondDraw,
}: GameControlsProps) {
  const [confirmResign, setConfirmResign] = useState(false);

  useEffect(() => {
    if (!confirmResign) return;
    const id = setTimeout(() => setConfirmResign(false), 3000);
    return () => clearTimeout(id);
  }, [confirmResign]);

  if (!active) return null;

  if (drawOffer === 'incoming') {
    return (
      <div className="flex w-full flex-col rounded-2xl border border-blue-500/30 bg-zinc-900 p-4 shadow-xl">
        <span className="mb-3 block text-center text-xs font-bold uppercase tracking-wider text-blue-400">
          Opponent offers a draw
        </span>
        <div className="flex gap-3">
          <button
            type="button"
            onClick={() => onRespondDraw(true)}
            className="flex-1 rounded-xl bg-blue-600 px-4 py-3 font-bold text-white shadow-[0_0_15px_rgba(37,99,235,0.4)] transition-colors hover:bg-blue-500"
          >
            Accept
          </button>
          <button
            type="button"
            onClick={() => onRespondDraw(false)}
            className="flex-1 rounded-xl border border-zinc-700 px-4 py-3 font-bold text-zinc-300 transition-colors hover:bg-zinc-800"
          >
            Decline
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="flex w-full gap-3 rounded-2xl border border-zinc-800 bg-zinc-900 p-4 shadow-xl">
      <button
        type="button"
        onClick={() => {
          if (confirmResign) {
            onResign();
            setConfirmResign(false);
          } else {
            setConfirmResign(true);
          }
        }}
        className={
          confirmResign
            ? 'flex-1 rounded-xl bg-rose-600 px-4 py-3 font-bold text-white transition-colors hover:bg-rose-500'
            : 'flex-1 rounded-xl border border-rose-500/30 px-4 py-3 font-bold text-rose-400 transition-colors hover:bg-rose-500/10'
        }
      >
        {confirmResign ? 'Confirm resign?' : 'Resign'}
      </button>
      <button
        type="button"
        disabled={!canOfferDraw}
        onClick={onOfferDraw}
        className="flex-1 rounded-xl border border-zinc-700 px-4 py-3 font-bold text-zinc-300 transition-colors hover:bg-zinc-800 disabled:cursor-default disabled:text-zinc-600 disabled:hover:bg-transparent"
      >
        {drawOffer === 'sent' ? 'Draw offered' : 'Offer Draw'}
      </button>
    </div>
  );
}
