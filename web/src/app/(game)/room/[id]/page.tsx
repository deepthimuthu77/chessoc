"use client";

import { useCallback, useEffect, useState, type ReactNode } from 'react';
import { useParams, useRouter, useSearchParams } from 'next/navigation';
import Link from 'next/link';
import { Board } from '@/components/ui/Board';
import { CapturedPieces } from '@/components/ui/CapturedPieces';
import { Chat } from '@/components/ui/Chat';
import { Hand } from '@/components/ui/Hand';
import { ConnectionOverlay } from '@/components/ui/ConnectionOverlay';
import { GameControls } from '@/components/ui/GameControls';
import { GameOverModal } from '@/components/ui/GameOverModal';
import { MoveHistory } from '@/components/ui/MoveHistory';
import { AccessiblePromotionPicker as PromotionPicker } from '@/components/ui/AccessiblePromotionPicker';
import { useGame } from '@/hooks/use-game';
import { ClockText } from '@/components/ui/ClockText';
import { Icon } from '@/components/ui/Icon';
import { FeatureLink } from '@/components/ui/PageHeading';

const NO_DROP_TARGETS: string[] = [];

export default function RoomPage() {
  const params = useParams<{ id: string }>();
  const searchParams = useSearchParams();
  return <Room key={`${params.id}:${searchParams.get('seat') ?? ''}`} roomId={params.id} preferredSeat={searchParams.get('seat')} />;
}

function Room({ roomId, preferredSeat }: { roomId: string; preferredSeat: string | null }) {
  const params = { id: roomId };
  const router = useRouter();
  const game = useGame(roomId, preferredSeat);
  const [selectedDrop, setSelectedDrop] = useState<string | null>(null);
  const [dismissedRoom, setDismissedRoom] = useState<string | null>(null);

  // The server creates the new game once the required players accept.
  useEffect(() => {
    if (game.rematchGameId) {
      router.push(`/room/${game.rematchGameId}`);
    }
  }, [game.rematchGameId, router]);

  const dropTargets = selectedDrop ? game.dropTargets[selectedDrop] ?? NO_DROP_TARGETS : NO_DROP_TARGETS;
  const { sendDrop } = game;

  const onDropSquare = useCallback((square: string) => {
    if (selectedDrop && dropTargets.includes(square)) {
      sendDrop(selectedDrop, square);
    }
    setSelectedDrop(null); // any click ends the drop selection
  }, [selectedDrop, dropTargets, sendDrop]);

  const lastPly = game.history[game.history.length - 1];
  const announcement = game.result
    ? game.result.kind === 'aborted'
      ? 'Game aborted with no result.'
      : `Game over. ${game.result.winner ? game.result.winner + ' wins' : 'draw'} by ${game.result.kind}.`
    : lastPly
      ? `Move ${game.history.length}: ${lastPly}${game.checkSquare ? ', check' : ''}.`
      : '';

  if (game.isBughouse) {
    return <BughouseRoom key={params.id} game={game} />;
  }

  return (
    <main className="room-shell min-h-screen px-4 py-5 md:px-8">
      <p className="sr-only" role="status" aria-live="polite">{announcement}</p>
      <div className="mx-auto max-w-[994px]">
        <RoomNavigation spectator={game.isSpectator} />
        <header className="mb-5 mt-7 flex flex-wrap items-center justify-between gap-4">
          <div><p className="eyebrow mb-2">{game.isSpectator ? 'THE LIVE GALLERY' : game.isVsEngine ? 'ENGINE PRACTICE' : 'AT THE BOARD'}</p><h1 className="font-serif text-3xl tracking-tight">{game.isCrazyhouse ? 'Crazyhouse' : 'Chess'}<span className="ml-3 text-sm font-normal text-zinc-500">{game.isSpectator ? 'Live room' : game.isVsEngine ? 'A worthy opponent awaits.' : 'A game between friends.'}</span></h1></div>
          <span role="status" className="live-pill"><span className="live-dot" />{game.result ? 'Game complete' : game.status !== 'connected' ? 'Connecting' : game.pendingMove ? 'Sending move' : game.interactive ? 'Your move' : game.seatsFilled < 2 ? 'Waiting for a player' : game.isSpectator ? 'Watching live' : 'Opponent’s move'}</span>
        </header>
        {game.result && <FinishedGame reviewId={game.reviewId} onShowResult={() => setDismissedRoom(null)} />}
        {!game.result && !game.isVsEngine && !game.isSpectator && game.seatsFilled < 2 && <div className="mb-5"><InviteLinks roomId={game.roomId} availableSeats={[]} /></div>}
        <div className="grid items-start gap-6 lg:grid-cols-[minmax(0,1fr)_290px] xl:gap-8">
          <section className="min-w-0" aria-label="Game board and players">
            <PlayerPanel label={game.isSpectator ? 'Black' : 'Opponent'} name={game.color === 'black' ? game.whiteName : game.blackName} clock={roomClock(game, game.color === 'black' ? 'white' : 'black')} captured={game.opponent.captured} score={game.opponent.score} />
            {game.isDropVariant && <div className="my-3"><Hand pieces={game.oppHand} /></div>}
            <div className="relative my-3 w-full">
              <Board pieces={game.pieces} pendingMove={game.pendingMove} legalMoves={game.legalMoves} lastMove={game.lastMove} checkSquare={game.checkSquare} orientation={game.orientation} interactive={game.interactive} onMoveIntent={game.sendMove} dropTargets={dropTargets} onDropSquare={onDropSquare} allowPremove={game.allowPremove} myColor={game.color} premove={game.premove} onPremove={game.setPremove} />
              {!game.connected && <div className="absolute inset-0 z-30 flex items-center justify-center bg-slate-950/65 p-5 backdrop-blur-sm"><div className="surface px-6 py-7 text-center"><span className="icon-well mx-auto mb-4"><Icon name="board" /></span><p className="eyebrow justify-center">Game unavailable</p><h2 className="mt-3 font-serif text-2xl">{game.error ? 'Unable to join this game' : 'Preparing your board'}</h2><p className="mt-3 max-w-sm text-sm leading-relaxed text-zinc-400">{game.error ?? 'Connecting to your game. You’ll be ready to play in a moment.'}</p>{game.error?.includes('session expired') && <Link href={`/login?reason=session-expired&next=${encodeURIComponent(`/room/${params.id}`)}`} className="text-link mt-4">Sign in to return</Link>}<Link href="/" className="button-secondary mt-5">Go to dashboard<Icon name="arrow" size={15} /></Link></div></div>}
              {game.connected && !game.result && (game.seatsFilled < 2 ? <WaitingOverlay joined={game.seatsFilled} target={2} /> : <ConnectionOverlay status={game.status} />)}
              <PromotionPicker open={!!game.promotion && !game.result} color={game.promotionColor} onPick={game.choosePromotion} onCancel={game.cancelPromotion} />
            </div>
            {game.isDropVariant && <div className="my-3"><Hand pieces={game.yourHand} selected={selectedDrop} interactive={game.interactive} onSelect={piece => setSelectedDrop(current => current === piece ? null : piece)} /></div>}
            <PlayerPanel label={game.isSpectator ? 'White' : 'You'} name={game.color === 'black' ? game.blackName : game.whiteName} clock={roomClock(game, game.color === 'black' ? 'black' : 'white')} captured={game.you.captured} score={game.you.score} highlighted={!game.isSpectator} />
            {game.premove && <p className="mt-3 text-center text-xs text-zinc-400">Premove queued · <button type="button" onClick={game.clearPremove} className="text-accent-300 underline">Cancel</button></p>}
          </section>
          <aside className="grid gap-4" aria-label="Game details">
            <MoveHistory history={game.history} />
            <GameControls active={game.active} canAbort={game.canAbort} canOfferDraw={game.canOfferDraw} showDraw={!game.isVsEngine} drawOffer={game.drawOffer} requireConfirm={game.confirmResign} onResign={game.resign} onAbort={game.abort} onOfferDraw={game.offerDraw} onRespondDraw={game.respondDraw} />
            {!game.isVsEngine && <Chat messages={game.chat} onSend={game.sendChat} failure={game.chatFailure} disabled={game.status !== 'connected'} />}
            <div className="surface p-3"><FeatureLink href="/watch" icon="watch" title="The live gallery" detail="See what’s happening across the club." /><FeatureLink href="/history" icon="history" title="Your game collection" detail="Every finished game, ready to revisit." /></div>
          </aside>
        </div>
      </div>
      <GameOverModal isOpen={!!game.result && dismissedRoom !== params.id} result={game.result} onRematch={game.canRematch ? game.rematch : undefined} rematchPending={game.rematchPending} error={game.error ?? (game.status !== 'connected' ? 'Connection lost. Return to the dashboard to start another game.' : undefined)} onDismiss={() => setDismissedRoom(params.id)} reviewHref={`/review/${encodeURIComponent(game.reviewId)}`} isSpectator={game.isSpectator} />
    </main>
  );
}

function BughouseRoom({ game }: { game: ReturnType<typeof useGame> }) {
  const router = useRouter();
  const [selectedDrop, setSelectedDrop] = useState<string | null>(null);
  const [dismissed, setDismissed] = useState(false);
  const [replaying, setReplaying] = useState(false);
  const [replayError, setReplayError] = useState<string | null>(null);
  const dropTargets = selectedDrop ? game.dropTargets[selectedDrop] ?? NO_DROP_TARGETS : NO_DROP_TARGETS;
  const { sendDrop } = game;
  const ready = game.seatsFilled === 4;
  const board = game.displayBoard;
  const partnerBoard = board === 'A' ? 'B' : 'A';
  const bottomColor = game.color === 'black' ? 'black' : 'white';
  const partnerBottomColor = game.isSpectator ? 'white' : bottomColor === 'white' ? 'black' : 'white';
  const topHand = game.orientation === bottomColor ? game.oppHand : game.yourHand;
  const bottomHand = game.orientation === bottomColor ? game.yourHand : game.oppHand;
  const partnerTopHand = game.partner.orientation === partnerBottomColor ? game.partner.oppHand : game.partner.yourHand;
  const partnerBottomHand = game.partner.orientation === partnerBottomColor ? game.partner.yourHand : game.partner.oppHand;
  const whiteTeam = board === 'A' ? 'Blue' : 'Red';
  const blackTeam = board === 'A' ? 'Red' : 'Blue';
  const winnerLabel = game.result?.winner ? `Team ${game.result.winner === 'white' ? whiteTeam : blackTeam}` : undefined;

  function playerName(color: 'white' | 'black', partner = false) {
    return partner ? (color === 'white' ? game.partner.whiteName : game.partner.blackName) : (color === 'white' ? game.whiteName : game.blackName);
  }

  async function replay() {
    if (game.isSpectator || replaying) return;
    setReplaying(true);
    setReplayError(null);
    try {
      const roomId = await game.replay();
      router.push(`/room/${encodeURIComponent(roomId)}`);
    } catch (error) {
      setReplayError(error instanceof Error ? error.message : 'Unable to create a new game. Please try again.');
      setReplaying(false);
    }
  }

  const onDropSquare = useCallback((square: string) => {
    if (selectedDrop && dropTargets.includes(square)) {
      sendDrop(selectedDrop, square);
    }
    setSelectedDrop(null);
  }, [selectedDrop, dropTargets, sendDrop]);

  return (
    <main className="room-shell min-h-screen px-4 py-5 text-white lg:px-8">
      <div className="mx-auto mb-4 max-w-[1680px]">
        <RoomNavigation spectator={game.isSpectator} />
        {game.result && <FinishedGame reviewId={game.reviewId} onShowResult={() => setDismissed(false)} />}
      </div>
      <header className="mx-auto mb-5 flex max-w-[1680px] flex-wrap items-center justify-between gap-3 border-b border-zinc-800 pb-4">
        <div>
          <div className="flex items-center gap-3">
            <h1 className="text-xl font-bold">Bughouse</h1>
            <span className={`rounded px-2 py-1 text-xs font-bold uppercase ${
              game.isSpectator ? 'bg-zinc-800 text-zinc-300' : game.team === 'blue' ? 'bg-sky-500/15 text-sky-300' : 'bg-rose-500/15 text-rose-300'
            }`}>
              {game.isSpectator ? 'Spectating' : `Team ${game.team}`}
            </span>
          </div>
          <p className="mt-1 text-sm text-zinc-500">
            {game.isSpectator ? 'Boards A and B' : `Board ${board}`} · {game.humansJoined}/{game.humanTarget} human seats
          </p>
          <p className="mt-1 text-xs text-zinc-400">Blue: A White + B Black · Red: A Black + B White</p>
        </div>
        <InviteLinks roomId={game.roomId} availableSeats={game.availableSeats} />
      </header>

      <div className="mx-auto grid max-w-[1680px] gap-5 xl:grid-cols-[minmax(0,1fr)_minmax(0,0.88fr)_18rem]">
        <section className="min-w-0">
          <h2 className="text-lg font-bold">Board {board}{!game.isSpectator && ' · Your board'}</h2>
          <BoardLabel title={`${game.orientation === 'white' ? 'Black' : 'White'} · Team ${game.orientation === 'white' ? blackTeam : whiteTeam}`} subtitle={playerName(game.orientation === 'white' ? 'black' : 'white')} clock={roomClock(game, game.orientation === 'white' ? 'black' : 'white')} />
          <Hand pieces={topHand} selected={selectedDrop} interactive={!game.isSpectator && game.orientation !== bottomColor && game.interactive && ready} onSelect={(piece) => setSelectedDrop((current) => current === piece ? null : piece)} />
          <div className="relative mx-auto mt-2 max-w-[720px]">
            <Board
              pieces={game.pieces}
              pendingMove={game.pendingMove}
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
            {!game.connected ? <UnavailableBoard error={game.error} /> : !game.result && !ready ? <WaitingOverlay joined={game.humansJoined} target={game.humanTarget} /> : !game.result && <ConnectionOverlay status={game.status} />}
          </div>
          <div className="mt-2">
            <Hand
              pieces={bottomHand}
              selected={selectedDrop}
              interactive={!game.isSpectator && game.orientation === bottomColor && game.interactive && ready}
              onSelect={(piece) => setSelectedDrop((current) => current === piece ? null : piece)}
            />
          </div>
          <BoardLabel title={`${game.orientation === 'white' ? 'White' : 'Black'} · Team ${game.orientation === 'white' ? whiteTeam : blackTeam}`} subtitle={playerName(game.orientation)} clock={roomClock(game, game.orientation)} active={!game.isSpectator} />
        </section>

        <section className="min-w-0 border-zinc-800 xl:border-l xl:pl-5">
          <h2 className="text-lg font-bold">Board {partnerBoard}{!game.isSpectator && ' · Partner board'}</h2>
          <BoardLabel title={`${game.partner.orientation === 'white' ? 'Black' : 'White'} · Team ${game.partner.orientation === 'white' ? whiteTeam : blackTeam}`} subtitle={playerName(game.partner.orientation === 'white' ? 'black' : 'white', true)} clock={roomClock(game, game.partner.orientation === 'white' ? 'black' : 'white', true)} />
          <Hand pieces={partnerTopHand} />
          <div className="relative mx-auto mt-2 max-w-[620px]">
            <Board
              pieces={game.partner.pieces}
              lastMove={game.partner.lastMove}
              checkSquare={game.partner.checkSquare}
              orientation={game.partner.orientation}
            />
            {!game.partner.connected ? <UnavailableBoard /> : !game.result && !ready && <WaitingOverlay joined={game.humansJoined} target={game.humanTarget} />}
            {game.partner.connected && !game.result && ready && <ConnectionOverlay status={game.partner.status} />}
          </div>
          <div className="mt-2"><Hand pieces={partnerBottomHand} /></div>
          <BoardLabel title={`${game.partner.orientation === 'white' ? 'White' : 'Black'} · Team ${game.partner.orientation === 'white' ? blackTeam : whiteTeam}`} subtitle={playerName(game.partner.orientation, true)} clock={roomClock(game, game.partner.orientation, true)} active={!game.isSpectator} />
        </section>

        <aside className="flex min-w-0 flex-col gap-4 border-zinc-800 xl:border-l xl:pl-5">
          <h2 className="text-sm font-bold text-zinc-400">Board {board} moves</h2>
          <MoveHistory history={game.history} />
          <GameControls
            active={game.active && ready}
            canAbort={game.canAbort}
            canOfferDraw={false}
            showDraw={false}
            drawOffer="none"
            requireConfirm={game.confirmResign}
            onResign={game.resign}
            onAbort={game.abort}
            onOfferDraw={game.offerDraw}
            onRespondDraw={game.respondDraw}
          />
          <Chat messages={game.chat} onSend={game.sendChat} failure={game.chatFailure} disabled={game.status !== 'connected'} />
        </aside>
      </div>

      <GameOverModal isOpen={!!game.result && !dismissed} result={game.result} onDismiss={() => setDismissed(true)} reviewHref={`/review/${encodeURIComponent(game.reviewId)}`} isSpectator={game.isSpectator} onRematch={game.isSpectator ? undefined : replay} rematchLabel="New game" rematchPending={replaying} error={replayError} winnerLabel={winnerLabel} />
      <PromotionPicker
        open={!!game.promotion && !game.result}
        color={game.promotionColor}
        onPick={game.choosePromotion}
        onCancel={game.cancelPromotion}
      />
    </main>
  );
}

function RoomNavigation({ spectator }: { spectator: boolean }) {
  return <nav aria-label="Room navigation" className="flex flex-wrap items-center justify-between gap-3 border-b border-zinc-800 pb-4">
    <Link href="/" className="wordmark" aria-label="Chessoc home"><Icon name="knight" size={26} />chessoc.</Link>
    <div className="flex flex-wrap items-center gap-1">{spectator && <Link href="/watch" className="button-quiet"><Icon name="watch" size={15} />Back to Watch</Link>}<Link href="/" className="button-secondary"><Icon name="board" size={15} />Go to dashboard</Link></div>
  </nav>;
}

function roomClock(game: ReturnType<typeof useGame>, color: 'white' | 'black', partner = false) {
  const source = partner ? game.partner : game;
  return <ClockText sample={source.clockSample} receivedAt={source.receivedAt} running={source.clocksRunning} color={color} />;
}

function FinishedGame({ reviewId, onShowResult }: { reviewId: string; onShowResult: () => void }) {
  return <div className="mb-5 flex flex-wrap items-center gap-4 rounded-lg border border-zinc-700 bg-zinc-900 px-4 py-3 text-sm text-zinc-300">
    <span>Game finished · Final position</span>
    <Link href={`/review/${encodeURIComponent(reviewId)}`} className="font-semibold text-accent-300 hover:underline">Review game</Link>
    <button type="button" onClick={onShowResult} className="font-semibold text-accent-300 hover:underline">Show result</button>
  </div>;
}

function UnavailableBoard({ error }: { error?: string | null }) {
  return <div role="status" className="absolute inset-0 z-30 flex flex-col items-center justify-center gap-4 bg-slate-950/75 p-6 text-center text-sm text-zinc-300">
    <p>{error ?? 'Connecting to board…'}</p>
    <Link href="/" className="font-semibold text-accent-300 hover:underline">Go to dashboard</Link>
  </div>;
}

const SEAT_LABELS: Record<string, string> = {
  aw: 'Board A White',
  ab: 'Board A Black',
  bw: 'Board B White',
  bb: 'Board B Black',
};

function InviteLinks({ roomId, availableSeats }: { roomId: string; availableSeats: string[] }) {
  const [copied, setCopied] = useState<string | null>(null);
  const [manualLink, setManualLink] = useState<string | null>(null);

  async function copySeat(seat?: string) {
    const suffix = seat ? `?seat=${seat}` : '';
    const link = `${window.location.origin}/room/${roomId}${suffix}`;
    try {
      if (!navigator.clipboard?.writeText) throw new Error('Clipboard unavailable');
      await navigator.clipboard.writeText(link);
      setManualLink(null);
      setCopied(seat ?? 'room');
      window.setTimeout(() => setCopied(null), 1500);
    } catch {
      setManualLink(link);
    }
  }

  return (
    <div className="flex flex-wrap justify-end gap-2">
      {availableSeats.map((seat) => (
        <button
          key={seat}
          type="button"
          onClick={() => copySeat(seat)}
          className="button-secondary"
        >
          {copied === seat ? 'Copied' : `Invite ${SEAT_LABELS[seat] ?? seat}`}
        </button>
      ))}
      <button
        type="button"
        onClick={() => copySeat()}
        className="button-secondary"
      >
        {copied === 'room' ? 'Copied' : availableSeats.length > 0 ? 'Copy open-seat link' : 'Copy room link'}
      </button>
      {manualLink && <label className="w-full max-w-lg text-left text-xs text-zinc-300">Copy this invite link manually
        <input readOnly value={manualLink} onFocus={(event) => event.target.select()} className="mt-2 block w-full rounded border border-zinc-600 bg-zinc-950 p-3 text-sm text-white" />
      </label>}
    </div>
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
  clock: ReactNode;
  active?: boolean;
}) {
  return (
    <div className="flex items-center justify-between py-3">
      <div>
        <span className={`text-xs font-bold uppercase ${active ? 'text-accent-400' : 'text-zinc-500'}`}>{title}</span>
        <p className="text-sm font-medium text-zinc-300">{subtitle}</p>
      </div>
      <span className="font-mono text-2xl text-zinc-200">{clock}</span>
    </div>
  );
}

function WaitingOverlay({ joined, target }: { joined: number; target: number }) {
  return (
    <div className="absolute inset-0 z-30 flex items-center justify-center bg-slate-950/75 backdrop-blur-sm">
      <div className="surface mx-5 px-6 py-6 text-center">
        <span className="icon-well mx-auto mb-4"><Icon name="people" /></span>
        <p className="font-serif text-2xl text-white">Waiting for players</p>
        <p className="mt-1 text-xs text-zinc-400">{joined}/{target} human seats filled</p>
        <Link href="/" className="button-secondary mt-4">Go to dashboard</Link>
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
  clock: ReactNode;
  captured: string[];
  score: number;
  highlighted?: boolean;
}) {
  return (
    <div
      className={`player-panel flex flex-row items-center justify-between gap-4 ${highlighted ? 'is-you' : ''}`}
    >
      <div className="flex min-w-0 flex-1 items-center justify-between gap-4">
        <div className="min-w-0">
          <span className={`mb-1 block text-xs font-bold uppercase tracking-wider ${highlighted ? 'text-accent-400' : 'text-zinc-500'}`}>
            {label}
          </span>
          <span className="block truncate text-sm font-medium text-zinc-200">{name}</span>
        </div>
        <div className="clock-value shrink-0 font-mono">{clock}</div>
      </div>
      <CapturedPieces pieces={captured} score={score} />
    </div>
  );
}
