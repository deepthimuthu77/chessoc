import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
import ts from 'typescript';

function harness(freshTicket, online = true) {
  const timers = [];
  const intervals = [];
  let now = 0;
  const sockets = [];
  const listeners = new Map();
  const statuses = [];
  const delays = [];
  class Socket {
    static OPEN = 1;
    readyState = 0;
    sent = [];
    constructor() { sockets.push(this); }
    send(payload) { this.sent.push(payload); }
    close() { this.readyState = 3; this.onclose?.(); }
  }
  const testModule = { exports: {} };
  const source = fs.readFileSync(new URL('../src/lib/transport.ts', import.meta.url), 'utf8');
  vm.runInNewContext(ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
  }).outputText, {
    module: testModule, exports: testModule.exports, process: { env: {} }, WebSocket: Socket,
    Date: { now: () => now },
    window: { location: { protocol: 'http:', host: 'localhost' }, navigator: { onLine: online },
      addEventListener: (name, callback) => listeners.set(name, callback),
      removeEventListener: name => listeners.delete(name) },
    setTimeout: (callback, delay) => { timers.push(callback); delays.push(delay); return timers.length; }, clearTimeout() {},
    setInterval: callback => { intervals.push(callback); return intervals.length; }, clearInterval() {},
    require: () => ({ MessageType: { Resync: 'resync' }, encode: (type, payload) => JSON.stringify({ type, payload }), decode: () => ({}) }),
  });
  const transport = new testModule.exports.LiveTransport({
    gameId: 'game', ticket: 'initial', freshTicket, lastVersion: () => 0,
    onEnvelope() {}, onStatus: status => statuses.push(status),
  });
  transport.start();
  return { transport, sockets, timers, intervals, statuses, delays, listeners,
    event: name => listeners.get(name)?.(), advance: ms => { now += ms; } };
}

test('leaving a room while a reconnect ticket is loading never opens another socket', async () => {
  let resolve;
  const h = harness(() => new Promise(done => { resolve = done; }));
  h.sockets[0].onclose();
  const reconnect = h.timers.at(-1)();
  h.transport.close();
  resolve('late-ticket');
  await reconnect;
  assert.equal(h.sockets.length, 1);
});

test('send reports whether an action was sent rather than silently losing a disconnected request', () => {
  const h = harness(async () => 'next');
  assert.equal(h.transport.send('intent', {}), false);
  h.sockets[0].readyState = 1;
  assert.equal(h.transport.send('intent', {}), true);
  assert.equal(h.sockets[0].sent.length, 1);
  h.sockets[0].readyState = 3;
  assert.equal(h.transport.send('intent', {}), false);
  assert.equal(h.sockets[0].sent.length, 1);
});

test('a failed reconnect request after leaving does not schedule more retries', async () => {
  let reject;
  const h = harness(() => new Promise((_, fail) => { reject = fail; }));
  h.sockets[0].onclose();
  const reconnect = h.timers.at(-1)();
  h.transport.close();
  reject(new Error('offline'));
  await reconnect;
  assert.equal(h.timers.length, 2);
});

test('a stalled handshake closes and schedules a fresh-ticket reconnect', () => {
  const h = harness(async () => 'next');
  h.timers[0]();
  assert.equal(h.timers.length, 2);
});

test('an apparently open connection with no inbound traffic reconnects', () => {
  const h = harness(async () => 'next');
  h.sockets[0].onopen();
  h.advance(60_000);
  h.intervals[0]();
  assert.equal(h.timers.length, 2);
});

test('network loss disables sends immediately and recovery gets a fresh ticket and resyncs', async () => {
  let requests = 0;
  const h = harness(async () => { requests += 1; return 'fresh'; });
  h.sockets[0].readyState = 1;
  h.sockets[0].onopen();
  h.event('offline');
  assert.equal(h.statuses.at(-1), 'reconnecting');
  assert.equal(h.transport.send('chat', { text: 'draft' }), false);
  assert.equal(h.sockets[0].readyState, 3);
  assert.equal(requests, 0);
  h.event('online');
  assert.equal(h.delays.at(-1), 0);
  await h.timers.at(-1)();
  assert.equal(requests, 1);
  h.sockets[1].readyState = 1;
  h.sockets[1].onopen();
  assert.equal(h.statuses.at(-1), 'connected');
  assert.equal(JSON.parse(h.sockets[1].sent[0]).type, 'resync');
  h.transport.close();
  h.event('online');
  assert.equal(h.listeners.size, 0);
  assert.equal(h.sockets.length, 2);
});

test('an old ticket response cannot open a duplicate socket after the network changes', async () => {
  const pending = [];
  const h = harness(() => new Promise(resolve => pending.push(resolve)));
  h.sockets[0].close();
  const oldRequest = h.timers.at(-1)();
  h.event('offline');
  h.event('online');
  const newRequest = h.timers.at(-1)();
  pending[1]('current');
  await newRequest;
  pending[0]('stale');
  await oldRequest;
  assert.equal(h.sockets.length, 2);
});

test('opening a room offline waits for network recovery before connecting', async () => {
  const h = harness(async () => 'fresh', false);
  assert.equal(h.sockets.length, 0);
  assert.equal(h.statuses.at(-1), 'reconnecting');
  h.event('online');
  await h.timers.at(-1)();
  assert.equal(h.sockets.length, 1);
});
