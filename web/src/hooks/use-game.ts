// Wires the transport to the store and exposes render-ready state plus the game actions. This is the
// only place the room UI touches the server. Authority stays server-side: the hook sends intents and
// renders whatever projection comes back.

'use client';

import { useCallback, useEffect, useMemo, useReducer, useRef, useState } from 'react';
import { api } from '@/lib/api';
import { getAuth } from '@/lib/auth';
import { Capability, MessageType, type Color } from '@/lib/protocol';
import { initialState, reducer } from '@/lib/store';
import { LiveTransport } from '@/lib/transport';
import {
  displayClocks,
  fenToPieces,
  findDrawOffer,
  findDrawResponse,
  findMove,
  findRematch,
  findResign,
  legalTargets,
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
  const [receivedAt, setReceivedAt] = useState(0);
  const [, forceTick] = useState(0);

  const transportRef = useRef<LiveTransport | null>(null);
  const versionRef = useRef<number>(0);

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

  const send = useCallback((cap: Capability | undefined) => {
    if (!cap || !transportRef.current) return;
    transportRef.current.send(MessageType.Intent, {
      intentId: `i-${Math.random().toString(36).slice(2)}`,
      capabilityId: cap.id,
      params: {},
      clientTimestamp: Date.now(),
    });
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
      if (choices.length > 1) {
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

  const resign = useCallback(() => send(findResign(state.capabilities)), [send, state.capabilities]);
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
  const targets = useMemo(
    () => (interactive ? legalTargets(state.capabilities) : new Set<string>()),
    [state.capabilities, interactive],
  );

  const clocks = snapshot
    ? displayClocks(snapshot.clockSample, receivedAt, !snapshot.result)
    : { white: '--:--', black: '--:--' };
  const other: Color = myColor === 'black' ? 'white' : 'black';

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

  return {
    status: state.connection,
    connected: !!snapshot,
    color: myColor,
    isSpectator: color === 'spectator',
    error: state.error,
    pieces,
    legalTargets: targets,
    interactive,
    history: snapshot?.moves ?? [],
    result: snapshot?.result ? toModalResult(snapshot.result) : undefined,
    you: { name: myColor ? capitalize(myColor) : 'Spectator', clock: myColor === 'black' ? clocks.black : clocks.white },
    opponent: { name: myColor ? capitalize(other) : 'Opponent', clock: myColor === 'black' ? clocks.white : clocks.black },
    // actions
    sendMove,
    resign,
    offerDraw,
    respondDraw,
    rematch,
    // control state
    active,
    canOfferDraw: active && has('offer_draw'),
    drawOffer,
    canRematch: has('rematch'),
    promotion,
    promotionColor: (myColor ?? 'white') as Color,
    choosePromotion,
    cancelPromotion: () => setPromotion(null),
  };
}
