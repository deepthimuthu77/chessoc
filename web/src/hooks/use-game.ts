// Wires the transport to the store and exposes render-ready state plus the game actions. This is the
// only place the room UI touches the server. Authority stays server-side: the hook sends intents and
// renders whatever projection comes back.

'use client';

import { useCallback, useEffect, useMemo, useReducer, useRef, useState } from 'react';
import { api } from '@/lib/api';
import { getAuth } from '@/lib/auth';
import { getSettings } from '@/lib/settings';
import { playMoveSound } from '@/lib/sound';
import { Capability, MessageType, type Color } from '@/lib/protocol';
import { initialState, reducer } from '@/lib/store';
import { LiveTransport } from '@/lib/transport';
import {
  displayClocks,
  dropTargetsByPiece,
  fenToPieces,
  findAbort,
  findDrawOffer,
  findDrawResponse,
  findMove,
  findRematch,
  findResign,
  lastMoveSquares,
  legalMovesBySource,
  materialScore,
  parseHand,
  promotionChoices,
  toModalResult,
} from '@/lib/view-model';

const capitalize = (s: string) => s.charAt(0).toUpperCase() + s.slice(1);

export interface PromotionPrompt {
  from: string;
  to: string;
}

export function useGame(room: string) {
  const [state, dispatch] = useReducer(reducer, initialState);
  const [color, setColor] = useState<Color | 'spectator' | null>(null);
  const [promotion, setPromotion] = useState<PromotionPrompt | null>(null);
  const [premove, setPremoveState] = useState<{ from: string; to: string } | null>(null);
  const [receivedAt, setReceivedAt] = useState(0);
  const [, forceTick] = useState(0);

  const transportRef = useRef<LiveTransport | null>(null);
  const versionRef = useRef<number>(0);
  const prevMovesRef = useRef(0);

  useEffect(() => {
    versionRef.current = Math.max(0, state.version);
  }, [state.version]);

  useEffect(() => {
    let cancelled = false;
    let transport: LiveTransport | null = null;

    (async () => {
      try {
        const token = getAuth()?.token ?? (await api.guest()).token;
        const seat = await api.join(room, token);
        if (cancelled) return;
        setColor(seat.color);
        const { ticket } = await api.ticket(token);
        if (cancelled) return;

        transport = new LiveTransport({
          gameId: seat.gameId,
          ticket,
          freshTicket: async () => (await api.ticket(token)).ticket,
          lastVersion: () => versionRef.current,
          onEnvelope: (env) => {
            setReceivedAt(Date.now());
            dispatch({ type: 'envelope', env });
          },
          onStatus: (status) => dispatch({ type: 'status', status }),
        });

        if (cancelled) {
          transport.close();
          return;
        }
        transportRef.current = transport;
        transport.start();
      } catch {
        if (!cancelled) dispatch({ type: 'status', status: 'closed' });
      }
    })();

    return () => {
      cancelled = true;
      transport?.close();
    };
  }, [room]);

  // drive the clock display between server samples
  useEffect(() => {
    const id = setInterval(() => forceTick((t) => t + 1), 250);
    return () => clearInterval(id);
  }, []);

  // play a tick whenever a new move lands
  useEffect(() => {
    const len = state.projection?.moves.length ?? 0;
    if (len > prevMovesRef.current && getSettings().sounds) {
      playMoveSound();
    }
    prevMovesRef.current = len;
  }, [state.projection]);

  const send = useCallback((cap: Capability | undefined) => {
    if (!cap || !transportRef.current) return;
    transportRef.current.send(MessageType.Intent, {
      intentId: `i-${Math.random().toString(36).slice(2)}`,
      capabilityId: cap.id,
      params: {},
      clientTimestamp: Date.now(),
    });
  }, []);

  const sendChat = useCallback((text: string) => {
    if (!transportRef.current || !text.trim()) return;
    transportRef.current.send(MessageType.Chat, { text: text.trim() });
  }, []);

  const commitMove = useCallback(
    (cap: Capability | undefined) => {
      if (!cap || state.pendingIntentId) return;
      const intentId = `i-${Math.random().toString(36).slice(2)}`;
      dispatch({ type: 'intent', intentId });
      transportRef.current?.send(MessageType.Intent, {
        intentId,
        capabilityId: cap.id,
        params: {},
        clientTimestamp: Date.now(),
      });
    },
    [state.pendingIntentId],
  );

  const sendMove = useCallback(
    (from: string, to: string) => {
      if (state.pendingIntentId) return;
      const choices = promotionChoices(state.capabilities, from, to);
      if (choices.length > 1 && !getSettings().autoQueen) {
        setPromotion({ from, to }); // let the player pick the piece
        return;
      }
      commitMove(findMove(state.capabilities, from, to));
    },
    [state.capabilities, state.pendingIntentId, commitMove],
  );

  const choosePromotion = useCallback(
    (piece: 'q' | 'r' | 'b' | 'n') => {
      if (!promotion) return;
      const uci = `${promotion.from}${promotion.to}${piece}`;
      commitMove(state.capabilities.find((c) => c.kind === 'move' && c.params.uci === uci));
      setPromotion(null);
    },
    [promotion, state.capabilities, commitMove],
  );

  // Crazyhouse: drop a reserve piece (uppercase letter) onto a square via a `P@e4` capability.
  const sendDrop = useCallback(
    (pieceUpper: string, to: string) => {
      if (state.pendingIntentId) return;
      commitMove(state.capabilities.find((c) => c.kind === 'move' && c.params.uci === `${pieceUpper}@${to}`));
    },
    [state.capabilities, state.pendingIntentId, commitMove],
  );

  const setPremove = useCallback((from: string, to: string) => {
    if (getSettings().premove) setPremoveState({ from, to });
  }, []);
  const clearPremove = useCallback(() => setPremoveState(null), []);

  const resign = useCallback(() => send(findResign(state.capabilities)), [send, state.capabilities]);
  const abort = useCallback(() => send(findAbort(state.capabilities)), [send, state.capabilities]);
  const offerDraw = useCallback(() => send(findDrawOffer(state.capabilities)), [send, state.capabilities]);
  const respondDraw = useCallback(
    (accept: boolean) => send(findDrawResponse(state.capabilities, accept)),
    [send, state.capabilities],
  );
  const rematch = useCallback(() => send(findRematch(state.capabilities)), [send, state.capabilities]);

  const snapshot = state.projection;
  const myColor = color === 'spectator' ? null : color;
  const isPlayer = myColor != null;
  const isMyTurn = !!snapshot && !snapshot.result && isPlayer && snapshot.sideToMove === myColor;
  const interactive = state.connection === 'connected' && isMyTurn && !state.pendingIntentId;

  const pieces = useMemo(() => (snapshot ? fenToPieces(snapshot.fen) : []), [snapshot]);
  const legalMoves = useMemo(
    () => (interactive ? legalMovesBySource(state.capabilities) : {}),
    [state.capabilities, interactive],
  );
  const lastMove = useMemo(() => (snapshot ? lastMoveSquares(snapshot.moves) : null), [snapshot]);
  const checkSquare = useMemo(() => {
    if (!snapshot || !snapshot.inCheck) return null;
    const kingChar = snapshot.sideToMove === 'white' ? 'K' : 'k';
    return pieces.find((p) => p.char === kingChar)?.square ?? null;
  }, [snapshot, pieces]);
  const orientation: 'white' | 'black' = getSettings().autoFlip && myColor === 'black' ? 'black' : 'white';

  const clocks = snapshot
    ? displayClocks(snapshot.clockSample, receivedAt, !snapshot.result)
    : { white: '--:--', black: '--:--' };
  const other: Color = myColor === 'black' ? 'white' : 'black';

  const iAmBlack = myColor === 'black';
  const youCaptured = iAmBlack ? snapshot?.capturedByBlack ?? [] : snapshot?.capturedByWhite ?? [];
  const oppCaptured = iAmBlack ? snapshot?.capturedByWhite ?? [] : snapshot?.capturedByBlack ?? [];
  const youMaterial = materialScore(youCaptured);
  const oppMaterial = materialScore(oppCaptured);

  const isCrazyhouse = !!snapshot && snapshot.fen.includes('[');
  const yourHand = snapshot && myColor ? parseHand(snapshot.fen, myColor) : [];
  const oppHand = snapshot && myColor ? parseHand(snapshot.fen, other) : [];
  const dropTargets = interactive ? dropTargetsByPiece(state.capabilities) : {};

  const isVsEngine = !!snapshot && (snapshot.whitePlayerId === 'engine' || snapshot.blackPlayerId === 'engine');
  const caps = state.capabilities;
  const has = (kind: Capability['kind']) => caps.some((c) => c.kind === kind);
  const active = state.connection === 'connected' && !!snapshot && !snapshot.result && isPlayer;
  const drawOffer: 'none' | 'incoming' | 'sent' = !active
    ? 'none'
    : has('respond_draw')
      ? 'incoming'
      : !has('offer_draw')
        ? 'sent'
        : 'none';

  // When our turn arrives, fire a queued premove if it is now legal; otherwise discard it.
  useEffect(() => {
    if (!premove || !interactive) return;
    const cap = findMove(state.capabilities, premove.from, premove.to);
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setPremoveState(null);
    if (cap) commitMove(cap);
  }, [premove, interactive, state.capabilities, commitMove]);

  const allowPremove = active && !isMyTurn && getSettings().premove;

  return {
    status: state.connection,
    connected: !!snapshot,
    color: myColor,
    isSpectator: color === 'spectator',
    isVsEngine,
    error: state.error,
    pieces,
    legalMoves,
    lastMove,
    checkSquare,
    orientation,
    interactive,
    history: snapshot?.moves ?? [],
    result: snapshot?.result ? toModalResult(snapshot.result) : undefined,
    you: {
      name: myColor ? capitalize(myColor) : 'Spectator',
      clock: myColor === 'black' ? clocks.black : clocks.white,
      captured: youCaptured,
      score: Math.max(0, youMaterial - oppMaterial),
    },
    opponent: {
      name: isVsEngine ? 'Computer' : myColor ? capitalize(other) : 'Opponent',
      clock: myColor === 'black' ? clocks.white : clocks.black,
      captured: oppCaptured,
      score: Math.max(0, oppMaterial - youMaterial),
    },
    // chat
    chat: state.chat,
    sendChat,
    // crazyhouse
    isCrazyhouse,
    yourHand,
    oppHand,
    dropTargets,
    sendDrop,
    // premove
    allowPremove,
    premove,
    setPremove,
    clearPremove,
    // actions
    sendMove,
    resign,
    abort,
    offerDraw,
    respondDraw,
    rematch,
    rematchGameId: state.rematchGameId,
    // control state
    active,
    canAbort: active && has('abort'),
    canOfferDraw: active && has('offer_draw'),
    drawOffer,
    confirmResign: getSettings().confirmResign,
    canRematch: has('rematch'),
    promotion,
    promotionColor: (myColor ?? 'white') as Color,
    choosePromotion,
    cancelPromotion: () => setPromotion(null),
  };
}
