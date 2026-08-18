// HTTPS request/response client: login, room join (seating), and the single-use live-connection
// ticket. Base URL comes from NEXT_PUBLIC_API_BASE.

const API_BASE = process.env.NEXT_PUBLIC_API_BASE ?? 'http://localhost:8080';

async function postJson<T>(path: string, body: unknown, token?: string): Promise<T> {
  const res = await fetch(`${API_BASE}${path}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify(body),
  });
  if (!res.ok) {
    throw new Error(`${path} failed: ${res.status}`);
  }
  return res.json() as Promise<T>;
}

export interface LoginResult {
  token: string;
  sessionId: string;
  playerId: string;
}

export interface JoinResult {
  gameId: string;
  color: 'white' | 'black' | 'spectator';
}

export const api = {
  login: (playerId: string) => postJson<LoginResult>('/login', { playerId }),
  join: (room: string, token: string) => postJson<JoinResult>('/join', { room }, token),
  ticket: (token: string) => postJson<{ ticket: string }>('/ticket', {}, token),
};
