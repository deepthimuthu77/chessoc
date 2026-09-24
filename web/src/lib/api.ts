// HTTP client for the product API: auth, play, and the social layer. Base URL from NEXT_PUBLIC_API_BASE.

const API_BASE = process.env.NEXT_PUBLIC_API_BASE ?? '/api';

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
  roomId: string;
  gameId: string;
  partnerGameId: string;
  board: 'A' | 'B' | 'spectator';
  color: 'white' | 'black' | 'spectator';
  partnerColor: 'white' | 'black' | 'spectator';
  team: 'blue' | 'red' | 'spectator' | '';
  seat: 'aw' | 'ab' | 'bw' | 'bb' | 'spectator' | 'white' | 'black';
  seatsFilled: number;
  humansJoined: number;
  humanTarget: number;
  variant: 'standard' | 'crazyhouse' | 'bughouse';
  humanSeats: string[];
  availableSeats: string[];
}

export interface Friend {
  id: string;
  username: string;
  online: boolean;
}

export interface FriendCard {
  id: string;
  username: string;
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

export interface PoolRating {
  variant: string;
  speed: string;
  rating: number;
  games: number;
}

export interface RatingPoint {
  at: number;
  rating: number;
}

export interface RatingHistory {
  variant: string;
  speed: string;
  points: RatingPoint[];
}

export interface Profile {
  account: Account;
  rating: number;
  stats: { wins: number; losses: number; draws: number };
  pools: PoolRating[];
}

export interface LiveGame {
  gameId: string;
  white: string;
  black: string;
  variant: string;
  spectators: number;
}

export interface OpenGame {
  gameId: string;
  creator: string;
  variant: string;
  rated: boolean;
  minutes: number;
  increment: number;
  seatsFilled?: number;
  humansJoined?: number;
  humanTarget?: number;
}

export interface GameHistoryRow {
  gameId: string;
  opponent: string;
  result: 'win' | 'loss' | 'draw';
  reason: string;
  rated: boolean;
  variant: string;
  endedAt: number;
}

export interface ActiveGame {
  gameId: string;
  color: string;
  opponent: string;
}

export interface Ply {
  version: number;
  uci: string;
  fen: string;
}

export interface Analysis {
  bestMove: string;
  score: { type: 'cp' | 'mate'; value: number };
  depth: number;
  pv: string[];
}

export interface CreateOptions {
  variant?: string;
  minutes?: number;
  increment?: number;
  rated?: boolean;
  vsEngine?: boolean;
  color?: 'white' | 'black';
  humanPlayers?: number;
  humanSeats?: string[];
}

export interface SeekOptions {
  variant?: string;
  minutes?: number;
  increment?: number;
}

export interface SeekResult {
  status: 'waiting' | 'matched';
  gameId?: string;
}

export const api = {
  register: (username: string, email: string, password: string) =>
    request<Session>('/auth/register', 'POST', { username, email, password }),
  login: (email: string, password: string) =>
    request<Session>('/auth/login', 'POST', { email, password }),
  guest: () => request<Session>('/login', 'POST', {}),
  upgrade: (username: string, email: string, password: string, token: string) =>
    request<Session>('/auth/upgrade', 'POST', { username, email, password }, token),
  me: (token: string) => request<{ account: Account }>('/me', 'GET', undefined, token),
  heartbeat: (token: string) => request<{ ok: boolean }>('/heartbeat', 'POST', {}, token),

  join: (room: string, token: string, seat?: string) =>
    request<JoinResult>('/join', 'POST', { room, ...(seat ? { seat } : {}) }, token),
  ticket: (token: string) => request<{ ticket: string }>('/ticket', 'POST', {}, token),
  createGame: (opts: CreateOptions, token: string) =>
    request<{ gameId: string }>('/play/create', 'POST', opts, token),

  seek: (opts: SeekOptions, token: string) =>
    request<SeekResult>('/seek', 'POST', opts, token),
  seekStatus: (token: string) => request<SeekResult>('/seek/status', 'GET', undefined, token),
  seekCancel: (token: string) => request<{ ok: boolean }>('/seek/cancel', 'POST', {}, token),

  addFriend: (username: string, token: string) =>
    request<{ ok: boolean; status: string }>('/friends/add', 'POST', { username }, token),
  friends: (token: string) => request<{ friends: Friend[] }>('/friends', 'GET', undefined, token),
  friendRequests: (token: string) =>
    request<{ incoming: FriendCard[]; outgoing: FriendCard[] }>('/friends/requests', 'GET', undefined, token),
  acceptFriend: (id: string, token: string) =>
    request<{ ok: boolean }>('/friends/accept', 'POST', { id }, token),
  declineFriend: (id: string, token: string) =>
    request<{ ok: boolean }>('/friends/decline', 'POST', { id }, token),

  leaderboard: (variant = 'standard', speed = 'blitz', limit = 50) =>
    request<Board>(`/leaderboard?variant=${variant}&speed=${speed}&limit=${limit}`, 'GET'),
  friendsLeaderboard: (token: string, variant = 'standard', speed = 'blitz') =>
    request<Board>(`/leaderboard/friends?variant=${variant}&speed=${speed}`, 'GET', undefined, token),

  profile: (id: string) => request<Profile>(`/profile?id=${encodeURIComponent(id)}`, 'GET'),
  ratingHistory: (id: string, variant = 'standard', speed = 'blitz') =>
    request<RatingHistory>(
      `/ratings/history?id=${encodeURIComponent(id)}&variant=${variant}&speed=${speed}`, 'GET'),
  gamesMine: (token: string) => request<{ games: GameHistoryRow[] }>('/games/mine', 'GET', undefined, token),
  gamesActive: (token: string) => request<{ games: ActiveGame[] }>('/games/active', 'GET', undefined, token),
  gameMoves: (id: string) =>
    request<{ gameId: string; variant: string; plies: Ply[] }>(
      `/game/moves?id=${encodeURIComponent(id)}`, 'GET'),
  analyze: (fen: string, variant = 'standard') =>
    request<Analysis>(`/analyze?fen=${encodeURIComponent(fen)}&variant=${variant}`, 'GET'),
  gamesLive: () => request<{ games: LiveGame[] }>('/games/live', 'GET'),
  gamesOpen: (token: string) => request<{ games: OpenGame[] }>('/games/open', 'GET', undefined, token),
  changePassword: (oldPassword: string, newPassword: string, token: string) =>
    request<{ ok: boolean }>('/account/password', 'POST', { oldPassword, newPassword }, token),
  deleteAccount: (password: string, token: string) =>
    request<{ ok: boolean }>('/account/delete', 'POST', { password }, token),
};
