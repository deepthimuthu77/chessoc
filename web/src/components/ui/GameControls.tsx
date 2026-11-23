"use client";

import { useEffect, useState } from 'react';

interface GameControlsProps {
  active: boolean;
  canAbort: boolean;
  canOfferDraw: boolean;
  showDraw?: boolean;
  drawOffer: 'none' | 'incoming' | 'sent';
  requireConfirm?: boolean;
  onResign: () => void;
  onAbort: () => void;
  onOfferDraw: () => void;
  onRespondDraw: (accept: boolean) => void;
}

export function GameControls({
  active,
  canAbort,
  canOfferDraw,
  showDraw = true,
  drawOffer,
  requireConfirm = true,
  onResign,
  onAbort,
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
      <div className="surface flex w-full flex-col p-4">
        <span className="mb-3 block text-center text-xs font-bold uppercase tracking-wider text-accent-400">
          Opponent offers a draw
        </span>
        <div className="flex gap-3">
          <button
            type="button"
            onClick={() => onRespondDraw(true)}
            className="button-primary flex-1"
          >
            Accept
          </button>
          <button
            type="button"
            onClick={() => onRespondDraw(false)}
            className="button-secondary flex-1"
          >
            Decline
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="surface flex w-full gap-2 p-3">
      {canAbort ? (
        <button
          type="button"
          onClick={onAbort}
          className="flex-1 rounded-xl border border-rose-500/30 px-4 py-3 font-bold text-rose-400 transition-colors hover:bg-rose-500/10"
        >
          Abort
        </button>
      ) : (
        <button
          type="button"
          onClick={() => {
            if (!requireConfirm) {
              onResign();
            } else if (confirmResign) {
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
      )}
      {showDraw && <button
        type="button"
        disabled={!canOfferDraw}
        onClick={onOfferDraw}
        className="flex-1 rounded-xl border border-zinc-700 px-4 py-3 font-bold text-zinc-300 transition-colors hover:bg-zinc-800 disabled:cursor-default disabled:text-zinc-600 disabled:hover:bg-transparent"
      >
        {drawOffer === 'sent' ? 'Draw offered' : 'Offer Draw'}
      </button>}
    </div>
  );
}
