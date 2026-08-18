"use client";

import { useParams } from 'next/navigation';
import { Board } from '@/components/ui/Board';
import { CapturedPieces } from '@/components/ui/CapturedPieces';
import { ConnectionOverlay } from '@/components/ui/ConnectionOverlay';
import { GameControls } from '@/components/ui/GameControls';
import { GameOverModal } from '@/components/ui/GameOverModal';
import { MoveHistory } from '@/components/ui/MoveHistory';
import { PromotionPicker } from '@/components/ui/PromotionPicker';
import { useGame } from '@/hooks/use-game';

export default function RoomPage() {
  const params = useParams<{ id: string }>();
  const game = useGame(params.id);

  return (
    <main className="flex min-h-screen items-center justify-center overflow-hidden bg-slate-950 p-4 md:p-8">
      <div className="flex w-full max-w-7xl flex-col items-center justify-center gap-8 xl:flex-row xl:items-start xl:gap-12">
        <div className="flex w-full max-w-md flex-col space-y-6 xl:w-80">
          <PlayerPanel label="Opponent" name={game.opponent.name} clock={game.opponent.clock} />
          <MoveHistory history={game.history} />
          <PlayerPanel label="You" name={game.you.name} clock={game.you.clock} highlighted />
          <GameControls
            active={game.active}
            canOfferDraw={game.canOfferDraw}
            drawOffer={game.drawOffer}
            onResign={game.resign}
            onOfferDraw={game.offerDraw}
            onRespondDraw={game.respondDraw}
          />
        </div>

        <div className="relative flex w-full max-w-2xl flex-col items-center">
          <Board
            pieces={game.pieces}
            legalTargets={game.legalTargets}
            interactive={game.interactive}
            onMoveIntent={game.sendMove}
          />
          {!game.connected && (
            <div className="absolute inset-0 flex items-center justify-center bg-slate-950/60 backdrop-blur-sm">
              <div className="rounded-2xl border border-zinc-700 bg-zinc-900 px-8 py-6 text-center shadow-2xl">
                <p className="text-xs font-bold uppercase tracking-[0.2em] text-blue-400">Game unavailable</p>
                <h1 className="mt-2 text-2xl font-bold text-white">Waiting for the server transport</h1>
                <p className="mt-2 max-w-sm text-sm text-zinc-400">
                  The board remains read-only until an authenticated server projection and capabilities arrive.
                </p>
              </div>
            </div>
          )}
          {game.connected && <ConnectionOverlay status={game.status} />}
          <GameOverModal isOpen={!!game.result} result={game.result} />
          <PromotionPicker
            open={!!game.promotion}
            color={game.promotionColor}
            onPick={game.choosePromotion}
            onCancel={game.cancelPromotion}
          />
        </div>
      </div>
    </main>
  );
}

function PlayerPanel({
  label,
  name,
  clock,
  highlighted = false,
}: {
  label: string;
  name: string;
  clock: string;
  highlighted?: boolean;
}) {
  return (
    <div
      className={`flex flex-col rounded-2xl border bg-zinc-900 p-6 shadow-xl ${
        highlighted ? 'border-blue-500/30' : 'border-zinc-800'
      }`}
    >
      <div className="flex items-center justify-between">
        <div>
          <span className={`mb-1 block text-xs font-bold uppercase tracking-wider ${highlighted ? 'text-blue-400' : 'text-zinc-500'}`}>
            {label}
          </span>
          <span className="text-xl font-medium text-zinc-500">{name}</span>
        </div>
        <div className="font-mono text-3xl text-zinc-600">{clock}</div>
      </div>
      <CapturedPieces pieces={[]} score={0} />
    </div>
  );
}
