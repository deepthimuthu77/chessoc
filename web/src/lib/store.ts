// Pure reducer over the authoritative projection (§4). The projection is replaced wholesale by server
// messages and its version is monotonic; ephemeral UI concerns (pending action, error) live here too
// but never leak into the projection.

import {
  Capability,
  ChatMessage,
  Envelope,
  ErrorMessage,
  GameSnapshot,
  MessageType,
  RematchReady,
  ResultMessage,
  SnapshotMessage,
} from './protocol';
import type { ConnectionStatus } from './transport';

export interface GameState {
  connection: ConnectionStatus;
  projection: GameSnapshot | null;
  capabilities: Capability[];
  version: number;
  pendingIntentId: string | null;
  pendingMove: { from: string; to: string } | null;
  error: string | null;
  chat: ChatMessage[];
  chatFailure: { message: string; text: string } | null;
  rematchGameId: string | null;
}

export const initialState: GameState = {
  connection: 'connecting',
  projection: null,
  capabilities: [],
  version: -1,
  pendingIntentId: null,
  pendingMove: null,
  error: null,
  chat: [],
  chatFailure: null,
  rematchGameId: null,
};

export type Action =
  | { type: 'chat_sent' }
  | { type: 'status'; status: ConnectionStatus }
  | { type: 'intent'; intentId: string; move?: { from: string; to: string } }
  | { type: 'envelope'; env: Envelope };

function adopt(state: GameState, snapshot: GameSnapshot, capabilities: Capability[] | null): GameState {
  if (snapshot.version < state.version) {
    return state; // monotonic: never regress the projection
  }
  return {
    ...state,
    projection: snapshot,
    capabilities: capabilities ?? [],
    version: snapshot.version,
    pendingIntentId: null,
    pendingMove: null,
    error: null,
  };
}

export function reducer(state: GameState, action: Action): GameState {
  switch (action.type) {
    case 'chat_sent':
      return { ...state, chatFailure: null };
    case 'status':
      return { ...state, connection: action.status,
        ...(action.status !== 'connected' ? { pendingIntentId: null, pendingMove: null, capabilities: [] } : {}) };

    case 'intent':
      return { ...state, pendingIntentId: action.intentId, pendingMove: action.move ?? null, error: null };

    case 'envelope': {
      const { env } = action;
      switch (env.type) {
        case MessageType.Snapshot:
        case MessageType.ResyncOk: {
          const m = env.payload as SnapshotMessage;
          return adopt(state, m.snapshot, m.capabilities);
        }
        case MessageType.Result: {
          const m = env.payload as ResultMessage;
          if ((m.disposition === 'accepted' || m.disposition === 'stale') && m.snapshot) {
            return adopt(state, m.snapshot, m.capabilities);
          }
          if (m.disposition === 'rejected') {
            return { ...state, pendingIntentId: null, pendingMove: null, error: m.reason ?? 'move rejected' };
          }
          return { ...state, pendingIntentId: null, pendingMove: null };
        }
        case MessageType.Error: {
          const m = env.payload as ErrorMessage;
          if (m.code === 'chat_rate_limited') {
            return { ...state, chatFailure: { message: m.message, text: m.text ?? '' } };
          }
          return { ...state, pendingIntentId: null, pendingMove: null, error: m.message };
        }
        case MessageType.Chat: {
          const m = env.payload as ChatMessage;
          return { ...state, chat: [...state.chat.slice(-199), m] };
        }
        case MessageType.RematchReady: {
          const m = env.payload as RematchReady;
          return { ...state, rematchGameId: m.gameId };
        }
        default:
          return state;
      }
    }

    default:
      return state;
  }
}
