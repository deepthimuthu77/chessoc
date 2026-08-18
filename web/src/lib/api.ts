// HTTP client for the product API: auth, play, and the social layer. Base URL from NEXT_PUBLIC_API_BASE.

const API_BASE = process.env.NEXT_PUBLIC_API_BASE ?? 'http://localhost:8080';

async function request<T>(path: string, method: 'GET' | 'POST', body?: unknown, token?: string): Promise<T> {
  const res = await fetch(`${API_BASE}${path}`, {
    method,
    headers: {
      ...(body ? { 'Content-Type': 'application/json' } : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  const json = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new Error((json as { error?: string }).error ?? `${path} failed (${res.status})`);
  }
  return json as T;
}

export interface Account {
  id: string;
  username: string;
  email?: string;
}

export interface Session {
  token: string;
  account: Account;
}

export interface JoinResult {
  gameId: string;
  color: 'white' | 'black' | 'spectator';
}

export interface Friend {
  id: string;
  username: string;
  online: boolean;
}

export interface LeaderboardRow {
  rank: number;
  accountId: string;
  username: string;
  rating: number;
  games: number;
}

export interface Board {
  variant: string;
  speed: string;
  entries: LeaderboardRow[];
}

export interface Profile {
  account: Account;
  rating: number;
  stats: { wins: number; losses: number; draws: number };
}

export interface CreateOptions {
  variant?: string;
  minutes?: number;
  increment?: number;
  rated?: boolean;
}

export const api = {
  register: (username: string, email: string, password: string) =>
    request<Session>('/auth/register', 'POST', { username, email, password }),
  login: (email: string, password: string) =>
    request<Session>('/auth/login', 'POST', { email, password }),
  guest: () => request<Session>('/login', 'POST', {}),
  me: (token: string) => request<{ account: Account }>('/me', 'GET', undefined, token),

  join: (room: string, token: string) => request<JoinResult>('/join', 'POST', { room }, token),
  ticket: (token: string) => request<{ ticket: string }>('/ticket', 'POST', {}, token),
  createGame: (opts: CreateOptions, token: string) =>
    request<{ gameId: string }>('/play/create', 'POST', opts, token),

  addFriend: (username: string, token: string) =>
    request<{ ok: boolean }>('/friends/add', 'POST', { username }, token),
  friends: (token: string) => request<{ friends: Friend[] }>('/friends', 'GET', undefined, token),

  leaderboard: (variant = 'standard', speed = 'blitz', limit = 50) =>
    request<Board>(`/leaderboard?variant=${variant}&speed=${speed}&limit=${limit}`, 'GET'),
  friendsLeaderboard: (token: string, variant = 'standard', speed = 'blitz') =>
    request<Board>(`/leaderboard/friends?variant=${variant}&speed=${speed}`, 'GET', undefined, token),

  profile: (id: string) => request<Profile>(`/profile?id=${encodeURIComponent(id)}`, 'GET'),
};
