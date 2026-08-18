// Pure reducer over the authoritative projection (§4). The projection is replaced wholesale by server
// messages and its version is monotonic; ephemeral UI concerns (pending action, error) live here too
// but never leak into the projection.

import {
  Capability,
  Envelope,
  ErrorMessage,
  GameSnapshot,
  MessageType,
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
  error: string | null;
}

export const initialState: GameState = {
  connection: 'connecting',
  projection: null,
  capabilities: [],
  version: -1,
  pendingIntentId: null,
  error: null,
};

export type Action =
  | { type: 'status'; status: ConnectionStatus }
  | { type: 'intent'; intentId: string }
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
  };
}

export function reducer(state: GameState, action: Action): GameState {
  switch (action.type) {
    case 'status':
      return { ...state, connection: action.status };

    case 'intent':
      return { ...state, pendingIntentId: action.intentId, error: null };

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
            return { ...state, pendingIntentId: null, error: m.reason ?? 'move rejected' };
          }
          return { ...state, pendingIntentId: null };
        }
        case MessageType.Error: {
          const m = env.payload as ErrorMessage;
          return { ...state, error: m.message };
        }
        default:
          return state;
      }
    }

    default:
      return state;
  }
}
