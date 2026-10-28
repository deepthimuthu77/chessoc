"use client";

import { useEffect, useMemo, useState } from 'react';
import { useParams } from 'next/navigation';
import Link from 'next/link';
import { api, type Analysis, type GameReview, type ReviewBoard } from '@/lib/api';
import { useAuth } from '@/hooks/use-auth';
import { TopNav } from '@/components/ui/TopNav';
import { Board } from '@/components/ui/Board';
import { Hand } from '@/components/ui/Hand';
import { fenToPieces, lastMoveSquares, parseHand } from '@/lib/view-model';

export default function ReviewPage() {
  const params = useParams<{ id: string }>();
  const { account, logout } = useAuth();
  const [loaded, setLoaded] = useState<{ id: string; data?: GameReview; error?: string } | null>(null);
  const [retry, setRetry] = useState(0);

  useEffect(() => {
    let cancelled = false;
    api.gameMoves(params.id).then((data) => {
      if (!cancelled) setLoaded({ id: params.id, data });
    }).catch((error: unknown) => {
      if (!cancelled) setLoaded({ id: params.id, error: error instanceof Error ? error.message : 'Unable to load this game.' });
    });
    return () => { cancelled = true; };
  }, [params.id, retry]);

  const current = loaded?.id === params.id ? loaded : null;
  const review = current?.data;
  const boards = review ? (review.boards.length ? review.boards : [{ gameId: review.gameId, label: '', initialFen: review.initialFen, plies: review.plies }]) : [];
  const bughouse = review?.variant === 'bughouse';

  return (
    <main className="min-h-screen bg-slate-950 text-white">
      <TopNav username={account?.username} accountId={account?.id} onLogout={account ? logout : undefined} />
      <div className="mx-auto max-w-[1500px] px-4 py-6 md:px-8">
        <nav aria-label="Review navigation" className="mb-5 flex flex-wrap gap-5 text-sm font-semibold text-blue-300">
          <Link href="/watch" className="hover:underline">Back to Watch</Link>
          <Link href="/" className="hover:underline">Back to lobby</Link>
          {review && <Link href={`/room/${encodeURIComponent(review.roomId || review.gameId)}`} className="hover:underline">Back to room</Link>}
        </nav>
        <h1 className="text-2xl font-bold capitalize">{review?.variant ?? 'Game'} review</h1>
        {!current && <p role="status" className="mt-6 text-zinc-400">Loading game…</p>}
        {current?.error && <p role="alert" className="mt-6 rounded-lg border border-rose-500/30 p-4 text-rose-300">{current.error} <button type="button" onClick={() => { setLoaded(null); setRetry((value) => value + 1); }} className="ml-3 underline">Retry</button></p>}
        {bughouse && <p className="mt-2 text-sm text-zinc-400">Each board has its own move controls. Pockets show the reserves recorded at that board’s selected move.</p>}
        {bughouse && boards.length < 2 && <p role="status" className="mt-3 text-amber-300">The other board’s history is unavailable.</p>}
        <div className={`mt-6 grid items-start gap-8 ${bughouse ? 'xl:grid-cols-2' : 'mx-auto max-w-3xl'}`}>
          {boards.map((board) => <BoardReview key={`${params.id}:${board.gameId}`} board={board} variant={review!.variant} />)}
        </div>
      </div>
    </main>
  );
}

function BoardReview({ board, variant }: { board: ReviewBoard; variant: string }) {
  const [idx, setIdx] = useState(board.plies.length);
  const [analysis, setAnalysis] = useState<{ fen: string; data: Analysis } | null>(null);
  const [analyzing, setAnalyzing] = useState(false);
  const [analysisError, setAnalysisError] = useState<string | null>(null);
  const [orientation, setOrientation] = useState<'white' | 'black'>('white');
  const positions = useMemo(() => [board.initialFen, ...board.plies.map((ply) => ply.fen)], [board]);
  const clamped = Math.min(Math.max(idx, 0), board.plies.length);
  const fen = positions[clamped];
  const pieces = useMemo(() => fen ? fenToPieces(fen) : [], [fen]);
  const lastMove = useMemo(() => clamped > 0 ? lastMoveSquares([board.plies[clamped - 1].uci]) : null, [board.plies, clamped]);
  const dropVariant = variant === 'crazyhouse' || variant === 'bughouse';
  const topColor = orientation === 'white' ? 'black' : 'white';
  const title = board.label ? (/^board\b/i.test(board.label) ? board.label : `Board ${board.label}`) : 'Board';
  const current = analysis?.fen === fen ? analysis.data : null;
  const evalText = (() => {
    if (!current) return null;
    const score = current.score.value * (fen.split(' ')[1] === 'b' ? -1 : 1);
    if (current.score.type === 'mate') return `M${Math.abs(score)}${score < 0 ? ' (Black)' : ' (White)'}`;
    return `${score >= 0 ? '+' : ''}${(score / 100).toFixed(2)}`;
  })();

  async function analyze() {
    setAnalyzing(true);
    setAnalysisError(null);
    try {
      const data = await api.analyze(fen, variant);
      setAnalysis({ fen, data });
    } catch (error) {
      setAnalysisError(error instanceof Error ? error.message : 'Position analysis is unavailable.');
    } finally {
      setAnalyzing(false);
    }
  }

  return (
    <section aria-label={`${title} review`} className="min-w-0">
      <div className="mb-3 flex items-center justify-between gap-3">
        <h2 className="text-lg font-bold">{title}</h2>
        <button type="button" onClick={() => setOrientation((value) => value === 'white' ? 'black' : 'white')} className="rounded border border-zinc-700 px-3 py-2 text-sm text-zinc-300 hover:bg-zinc-800">Flip board</button>
      </div>
      {dropVariant && fen && <ReviewPocket fen={fen} color={topColor} />}
      {fen ? <Board pieces={pieces} lastMove={lastMove} orientation={orientation} interactive={false} /> : <p role="alert" className="py-8 text-amber-300">The starting position is unavailable.</p>}
      {dropVariant && fen && <ReviewPocket fen={fen} color={orientation} />}
      <div role="group" aria-label={`${title} move controls`} className="mt-4 flex flex-wrap items-center justify-center gap-2">
        <NavBtn onClick={() => setIdx(0)} label="⏮" name="Starting position" disabled={clamped === 0} />
        <NavBtn onClick={() => setIdx((value) => Math.max(0, value - 1))} label="◀" name="Previous move" disabled={clamped === 0} />
        <span role="status" className="min-w-24 text-center font-mono text-sm text-zinc-400">{clamped} / {board.plies.length}</span>
        <NavBtn onClick={() => setIdx((value) => Math.min(board.plies.length, value + 1))} label="▶" name="Next move" disabled={clamped === board.plies.length} />
        <NavBtn onClick={() => setIdx(board.plies.length)} label="⏭" name="Final position" disabled={clamped === board.plies.length} />
      </div>
      {fen && <div className="mt-4 flex flex-wrap items-center justify-center gap-4 rounded-2xl border border-zinc-800 bg-zinc-900 px-5 py-3">
        <button type="button" onClick={analyze} disabled={analyzing} className="rounded-lg bg-blue-600 px-4 py-2 text-sm font-bold text-white hover:bg-blue-500 disabled:opacity-60">{analyzing ? 'Analyzing…' : 'Analyze position'}</button>
        {current && <div className="flex flex-wrap items-center gap-3 text-sm"><span className="font-mono text-lg font-bold text-blue-400">{evalText}</span><span className="text-zinc-400">best <span className="font-mono text-zinc-300">{current.bestMove}</span> · depth {current.depth}</span></div>}
        {analysisError && <p role="alert" className="w-full text-center text-sm text-rose-300">{analysisError}</p>}
        {variant === 'bughouse' && <p className="w-full text-center text-xs text-zinc-400">Evaluates this board and its recorded reserve only; partner-board tactics are not included.</p>}
      </div>}
      <div className="mt-5 overflow-hidden rounded-xl border border-zinc-800 bg-zinc-900">
        <h3 className="border-b border-zinc-800 px-4 py-3 text-sm font-bold text-zinc-400">{title} moves</h3>
        <div className="flex max-h-56 flex-wrap gap-1 overflow-y-auto p-3">
          {board.plies.map((ply, index) => <button key={`${ply.version}:${index}`} type="button" onClick={() => setIdx(index + 1)} aria-current={clamped === index + 1 ? 'step' : undefined} aria-label={`Move ${index + 1}: ${ply.uci}`} className={`rounded px-3 py-2 text-sm ${clamped === index + 1 ? 'bg-blue-500/15 text-blue-300' : 'text-zinc-300 hover:bg-zinc-800'}`}><span className="mr-2 font-mono text-xs text-zinc-500">{index + 1}.</span>{ply.uci}</button>)}
          {!board.plies.length && <p className="p-3 text-sm text-zinc-500">No moves were played.</p>}
        </div>
      </div>
    </section>
  );
}

function ReviewPocket({ fen, color }: { fen: string; color: 'white' | 'black' }) {
  const pieces = useMemo(() => parseHand(fen, color), [fen, color]);
  return <div role="group" aria-label={`${color} pocket`} className="my-3 rounded-lg border border-zinc-800 px-3 py-2">
    <p className="mb-2 text-xs font-semibold capitalize text-zinc-400">{color} pocket</p>
    {pieces.length ? <Hand pieces={pieces} /> : <p className="text-xs text-zinc-500">Empty</p>}
  </div>;
}

function NavBtn({ onClick, label, name, disabled }: { onClick: () => void; label: string; name: string; disabled: boolean }) {
  return <button type="button" onClick={onClick} aria-label={name} title={name} disabled={disabled} className="rounded-lg border border-zinc-700 px-3 py-2 font-bold text-zinc-200 hover:bg-zinc-800 disabled:cursor-default disabled:opacity-35">{label}</button>;
}
