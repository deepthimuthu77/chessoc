"use client";

import { useEffect, useState } from 'react';
import { useParams, useRouter } from 'next/navigation';
import { Board } from '@/components/ui/Board';
import { CapturedPieces } from '@/components/ui/CapturedPieces';
import { Chat } from '@/components/ui/Chat';
import { Hand } from '@/components/ui/Hand';
import { ConnectionOverlay } from '@/components/ui/ConnectionOverlay';
import { GameControls } from '@/components/ui/GameControls';
import { GameOverModal } from '@/components/ui/GameOverModal';
import { MoveHistory } from '@/components/ui/MoveHistory';
import { PromotionPicker } from '@/components/ui/PromotionPicker';
import { useGame } from '@/hooks/use-game';

export default function RoomPage() {
  const params = useParams<{ id: string }>();
  const router = useRouter();
  const game = useGame(params.id);
  const [selectedDrop, setSelectedDrop] = useState<string | null>(null);

  // both players accepted a rematch — the server created the new game
  useEffect(() => {
    if (game.rematchGameId) {
      router.push(`/room/${game.rematchGameId}`);
    }
  }, [game.rematchGameId, router]);

  const dropTargets = selectedDrop ? game.dropTargets[selectedDrop] ?? [] : [];

  function onDropSquare(square: string) {
    if (selectedDrop && dropTargets.includes(square)) {
      game.sendDrop(selectedDrop, square);
    }
    setSelectedDrop(null); // any click ends the drop selection
  }

  const lastPly = game.history[game.history.length - 1];
  const announcement = game.result
    ? `Game over. ${game.result.winner ? game.result.winner + ' wins' : 'draw'} by ${game.result.kind}.`
    : lastPly
      ? `Move ${game.history.length}: ${lastPly}${game.checkSquare ? ', check' : ''}.`
      : '';

  if (game.isBughouse) {
    return <BughouseRoom game={game} />;
  }

  return (
    <main className="flex min-h-screen items-center justify-center overflow-hidden bg-slate-950 p-4 md:p-8">
      <p className="sr-only" role="status" aria-live="polite">{announcement}</p>
      <div className="flex w-full max-w-7xl flex-col items-center justify-center gap-8 xl:flex-row xl:items-start xl:gap-12">
        <div className="flex w-full max-w-md flex-col space-y-6 xl:w-80">
          <PlayerPanel label="Opponent" name={game.opponent.name} clock={game.opponent.clock} captured={game.opponent.captured} score={game.opponent.score} />
          {game.isDropVariant && <Hand pieces={game.oppHand} />}
          <MoveHistory history={game.history} />
          {game.isDropVariant && (
            <Hand
              pieces={game.yourHand}
              selected={selectedDrop}
              interactive={game.interactive}
              onSelect={(p) => setSelectedDrop((cur) => (cur === p ? null : p))}
            />
          )}
          <PlayerPanel label="You" name={game.you.name} clock={game.you.clock} captured={game.you.captured} score={game.you.score} highlighted />
          <GameControls
            active={game.active}
            canAbort={game.canAbort}
            canOfferDraw={game.canOfferDraw}
            drawOffer={game.drawOffer}
            requireConfirm={game.confirmResign}
            onResign={game.resign}
            onAbort={game.abort}
            onOfferDraw={game.offerDraw}
            onRespondDraw={game.respondDraw}
          />
          {!game.isVsEngine && <Chat messages={game.chat} onSend={game.sendChat} />}
        </div>

        <div className="relative flex w-full max-w-2xl flex-col items-center">
          <Board
            pieces={game.pieces}
            legalMoves={game.legalMoves}
            lastMove={game.lastMove}
            checkSquare={game.checkSquare}
            orientation={game.orientation}
            interactive={game.interactive}
            onMoveIntent={game.sendMove}
            dropTargets={dropTargets}
            onDropSquare={onDropSquare}
            allowPremove={game.allowPremove}
            myColor={game.color}
            premove={game.premove}
            onPremove={game.setPremove}
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
          <GameOverModal
            isOpen={!!game.result}
            result={game.result}
            onRematch={game.canRematch ? game.rematch : undefined}
          />
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

function BughouseRoom({ game }: { game: ReturnType<typeof useGame> }) {
  const [selectedDrop, setSelectedDrop] = useState<string | null>(null);
  const dropTargets = selectedDrop ? game.dropTargets[selectedDrop] ?? [] : [];
  const ready = game.seatsFilled === 4;

  function onDropSquare(square: string) {
    if (selectedDrop && dropTargets.includes(square)) {
      game.sendDrop(selectedDrop, square);
    }
    setSelectedDrop(null);
  }

  return (
    <main className="min-h-screen bg-slate-950 px-4 py-5 text-white lg:px-8">
      <header className="mx-auto mb-5 flex max-w-[1680px] flex-wrap items-center justify-between gap-3 border-b border-zinc-800 pb-4">
        <div>
          <div className="flex items-center gap-3">
            <h1 className="text-xl font-bold">Bughouse</h1>
            <span className={`rounded px-2 py-1 text-xs font-bold uppercase ${
              game.team === 'blue' ? 'bg-blue-500/15 text-blue-300' : 'bg-rose-500/15 text-rose-300'
            }`}>
              Team {game.team}
            </span>
          </div>
          <p className="mt-1 text-sm text-zinc-500">
            Board {game.board} · {game.humansJoined}/{game.humanTarget} human seats
          </p>
        </div>
        <button
          type="button"
          onClick={() => navigator.clipboard.writeText(window.location.href)}
          className="rounded border border-zinc-700 px-4 py-2 text-sm font-semibold text-zinc-200 hover:bg-zinc-900"
        >
          Copy room link
        </button>
      </header>

      <div className="mx-auto grid max-w-[1680px] gap-5 xl:grid-cols-[minmax(0,1fr)_minmax(0,0.88fr)_18rem]">
        <section className="min-w-0">
          <BoardLabel title={`Board ${game.board}`} subtitle="Your board" clock={game.opponent.clock} />
          <Hand pieces={game.oppHand} />
          <div className="relative mx-auto mt-2 max-w-[720px]">
            <Board
              pieces={game.pieces}
              legalMoves={game.legalMoves}
              lastMove={game.lastMove}
              checkSquare={game.checkSquare}
              orientation={game.orientation}
              interactive={game.interactive && ready}
              onMoveIntent={game.sendMove}
              dropTargets={dropTargets}
              onDropSquare={onDropSquare}
              allowPremove={game.allowPremove && ready}
              myColor={game.color}
              premove={game.premove}
              onPremove={game.setPremove}
            />
            {!ready && <WaitingOverlay joined={game.humansJoined} target={game.humanTarget} />}
          </div>
          <div className="mt-2">
            <Hand
              pieces={game.yourHand}
              selected={selectedDrop}
              interactive={game.interactive && ready}
              onSelect={(piece) => setSelectedDrop((current) => current === piece ? null : piece)}
            />
          </div>
          <BoardLabel title="You" subtitle={game.you.name} clock={game.you.clock} active />
        </section>

        <section className="min-w-0 border-zinc-800 xl:border-l xl:pl-5">
          <BoardLabel title={game.board === 'A' ? 'Board B' : 'Board A'} subtitle="Partner board" clock={game.partner.opponent.clock} />
          <Hand pieces={game.partner.oppHand} />
          <div className="relative mx-auto mt-2 max-w-[620px]">
            <Board
              pieces={game.partner.pieces}
              lastMove={game.partner.lastMove}
              checkSquare={game.partner.checkSquare}
              orientation={game.partner.orientation}
            />
            {!ready && <WaitingOverlay joined={game.humansJoined} target={game.humanTarget} />}
          </div>
          <div className="mt-2"><Hand pieces={game.partner.yourHand} /></div>
          <BoardLabel title="Partner" subtitle={game.partner.you.name} clock={game.partner.you.clock} active />
        </section>

        <aside className="flex min-w-0 flex-col gap-4 border-zinc-800 xl:border-l xl:pl-5">
          <MoveHistory history={game.history} />
          <GameControls
            active={game.active && ready}
            canAbort={game.canAbort}
            canOfferDraw={false}
            drawOffer="none"
            requireConfirm={game.confirmResign}
            onResign={game.resign}
            onAbort={game.abort}
            onOfferDraw={game.offerDraw}
            onRespondDraw={game.respondDraw}
          />
          <Chat messages={game.chat} onSend={game.sendChat} />
        </aside>
      </div>

      <GameOverModal isOpen={!!game.result} result={game.result} />
      <PromotionPicker
        open={!!game.promotion}
        color={game.promotionColor}
        onPick={game.choosePromotion}
        onCancel={game.cancelPromotion}
      />
    </main>
  );
}

function BoardLabel({
  title,
  subtitle,
  clock,
  active = false,
}: {
  title: string;
  subtitle: string;
  clock: string;
  active?: boolean;
}) {
  return (
    <div className="flex items-center justify-between py-3">
      <div>
        <span className={`text-xs font-bold uppercase ${active ? 'text-blue-400' : 'text-zinc-500'}`}>{title}</span>
        <p className="text-sm font-medium text-zinc-300">{subtitle}</p>
      </div>
      <span className="font-mono text-2xl text-zinc-200">{clock}</span>
    </div>
  );
}

function WaitingOverlay({ joined, target }: { joined: number; target: number }) {
  return (
    <div className="absolute inset-0 flex items-center justify-center bg-slate-950/75 backdrop-blur-sm">
      <div className="border border-zinc-700 bg-zinc-950 px-6 py-4 text-center shadow-2xl">
        <p className="text-sm font-bold text-white">Waiting for players</p>
        <p className="mt-1 text-xs text-zinc-400">{joined}/{target} human seats filled</p>
      </div>
    </div>
  );
}

function PlayerPanel({
  label,
  name,
  clock,
  captured,
  score,
  highlighted = false,
}: {
  label: string;
  name: string;
  clock: string;
  captured: string[];
  score: number;
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
      <CapturedPieces pieces={captured} score={score} />
    </div>
  );
}
