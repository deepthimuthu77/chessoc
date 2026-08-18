// Wire contract shared with the Java server (see doc/ARCHITECTURE.md §8). Types + envelope framing
// only — no game logic lives here.

export type ProtocolVersion = [number, number];
export const PROTOCOL_VERSION: ProtocolVersion = [1, 0];

export type Color = 'white' | 'black';

export const MessageType = {
  Intent: 'intent',
  Resync: 'resync',
  Ping: 'ping',
  Snapshot: 'snapshot',
  Capabilities: 'capabilities',
  Receipt: 'receipt',
  Result: 'result',
  Clock: 'clock',
  ResyncOk: 'resync_ok',
  Error: 'error',
  Pong: 'pong',
} as const;

export interface Envelope {
  v: ProtocolVersion;
  type: string;
  payload: unknown;
}

export interface Capability {
  id: string;
  sessionId: string;
  gameId: string;
  gameVersion: number;
  kind: 'move' | 'resign' | 'offer_draw' | 'respond_draw' | 'rematch';
  params: Record<string, string>;
  expiresAtEpochMs: number;
  signature: string;
}

export interface ClockSample {
  whiteRemainingMs: number;
  blackRemainingMs: number;
  activeSide: Color;
  serverTimestamp: number;
}

export interface GameResult {
  type: 'WIN' | 'DRAW' | 'ABORTED';
  winner: Color | null;
  reason: string;
}

export interface GameSnapshot {
  gameId: string;
  version: number;
  initialFen: string;
  fen: string;
  sideToMove: Color;
  moves: string[];
  clockSample: ClockSample;
  result: GameResult | null;
  whitePlayerId: string;
  blackPlayerId: string;
}

export interface SnapshotMessage {
  snapshot: GameSnapshot;
  capabilities: Capability[];
}

export interface ResultMessage {
  disposition: 'accepted' | 'rejected' | 'stale';
  intentId: string;
  reason: string | null;
  snapshot: GameSnapshot | null;
  capabilities: Capability[] | null;
}

export interface ErrorMessage {
  code: string;
  message: string;
}

export function encode(type: string, payload: unknown): string {
  return JSON.stringify({ v: PROTOCOL_VERSION, type, payload });
}

export function decode(text: string): Envelope {
  const env = JSON.parse(text) as Envelope;
  if (!Array.isArray(env.v) || env.v[0] !== PROTOCOL_VERSION[0]) {
    throw new Error(`incompatible protocol version: ${JSON.stringify(env.v)}`);
  }
  return env;
}
