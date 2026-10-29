// Live WSS transport: owns the socket lifecycle — connect, keepalive, bounded-backoff reconnect, and
// resync on resume. It never mutates rendered state; it decodes envelopes and hands them up.

import { decode, encode, Envelope, MessageType } from './protocol';

export type ConnectionStatus = 'connecting' | 'connected' | 'reconnecting' | 'closed';

const CONFIGURED_WS_BASE = process.env.NEXT_PUBLIC_WS_BASE;
const PING_INTERVAL_MS = 20_000;
const MAX_BACKOFF_MS = 30_000;
const CONNECT_TIMEOUT_MS = 15_000;
const IDLE_TIMEOUT_MS = 45_000;

export interface TransportOptions {
  gameId: string;
  ticket: string; // first-connect ticket
  freshTicket: () => Promise<string>; // single-use ticket minted per reconnect
  lastVersion: () => number; // for resync after a resume
  onEnvelope: (env: Envelope) => void;
  onStatus: (status: ConnectionStatus) => void;
}

export class LiveTransport {
  private socket: WebSocket | null = null;
  private closed = false;
  private attempt = 0;
  private ping?: ReturnType<typeof setInterval>;
  private reconnectTimer?: ReturnType<typeof setTimeout>;
  private connectTimer?: ReturnType<typeof setTimeout>;
  private lastInboundAt = 0;

  constructor(private readonly opts: TransportOptions) {}

  start(): void {
    this.open(this.opts.ticket, false);
  }

  send(type: string, payload: unknown): void {
    if (this.socket && this.socket.readyState === WebSocket.OPEN) {
      this.socket.send(encode(type, payload));
    }
  }

  close(): void {
    this.closed = true;
    if (this.reconnectTimer) clearTimeout(this.reconnectTimer);
    if (this.connectTimer) clearTimeout(this.connectTimer);
    this.stopPing();
    this.socket?.close();
    this.opts.onStatus('closed');
  }

  private open(ticket: string, isReconnect: boolean): void {
    if (this.closed) return;
    this.opts.onStatus(isReconnect ? 'reconnecting' : 'connecting');
    const query = `ticket=${encodeURIComponent(ticket)}&gameId=${encodeURIComponent(this.opts.gameId)}`;
    const wsBase = CONFIGURED_WS_BASE || `${window.location.protocol === 'https:' ? 'wss:' : 'ws:'}//${window.location.host}`;
    const socket = new WebSocket(`${wsBase}/game?${query}`);
    this.socket = socket;
    this.connectTimer = setTimeout(() => socket.close(), CONNECT_TIMEOUT_MS);

    socket.onopen = () => {
      if (this.closed || this.socket !== socket) { socket.close(); return; }
      if (this.connectTimer) clearTimeout(this.connectTimer);
      this.lastInboundAt = Date.now();
      this.attempt = 0;
      this.opts.onStatus('connected');
      this.startPing();
      if (isReconnect) {
        this.send(MessageType.Resync, {
          gameId: this.opts.gameId,
          lastKnownVersion: this.opts.lastVersion(),
        });
      }
    };

    socket.onmessage = (event) => {
      if (this.closed || this.socket !== socket) return;
      this.lastInboundAt = Date.now();
      let env: Envelope;
      try {
        env = decode(event.data as string);
      } catch {
        return; // drop malformed/bad-version frames
      }
      if (env.type === MessageType.Ping) {
        this.send(MessageType.Pong, {});
        return;
      }
      this.opts.onEnvelope(env);
    };

    socket.onerror = () => socket.close();

    socket.onclose = () => {
      if (this.socket !== socket) return;
      if (this.connectTimer) clearTimeout(this.connectTimer);
      this.stopPing();
      if (this.closed) {
        this.opts.onStatus('closed');
        return;
      }
      this.scheduleReconnect();
    };
  }

  private scheduleReconnect(): void {
    this.opts.onStatus('reconnecting');
    const backoff = Math.min(MAX_BACKOFF_MS, 500 * 2 ** this.attempt);
    const delay = backoff + Math.random() * 250;
    this.attempt += 1;
    this.reconnectTimer = setTimeout(async () => {
      if (this.closed) return;
      try {
        const ticket = await this.opts.freshTicket();
        if (this.closed) return;
        this.open(ticket, true);
      } catch {
        if (!this.closed) this.scheduleReconnect();
      }
    }, delay);
  }

  private startPing(): void {
    this.stopPing();
    this.ping = setInterval(() => {
      if (Date.now() - this.lastInboundAt > IDLE_TIMEOUT_MS) {
        this.socket?.close();
      } else {
        this.send(MessageType.Ping, {});
      }
    }, PING_INTERVAL_MS);
  }

  private stopPing(): void {
    if (this.ping) {
      clearInterval(this.ping);
      this.ping = undefined;
    }
  }
}
