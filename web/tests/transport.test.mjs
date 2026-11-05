import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
import ts from 'typescript';

function harness(freshTicket) {
  const timers = [];
  const intervals = [];
  let now = 0;
  const sockets = [];
  class Socket {
    static OPEN = 1;
    readyState = 0;
    sent = [];
    constructor() { sockets.push(this); }
    send(payload) { this.sent.push(payload); }
    close() { this.onclose?.(); }
  }
  const testModule = { exports: {} };
  const source = fs.readFileSync(new URL('../src/lib/transport.ts', import.meta.url), 'utf8');
  vm.runInNewContext(ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
  }).outputText, {
    module: testModule, exports: testModule.exports, process: { env: {} }, WebSocket: Socket,
    Date: { now: () => now },
    window: { location: { protocol: 'http:', host: 'localhost' } },
    setTimeout: callback => { timers.push(callback); return timers.length; }, clearTimeout() {},
    setInterval: callback => { intervals.push(callback); return intervals.length; }, clearInterval() {},
    require: () => ({ MessageType: {}, encode: () => '', decode: () => ({}) }),
  });
  const transport = new testModule.exports.LiveTransport({
    gameId: 'game', ticket: 'initial', freshTicket, lastVersion: () => 0,
    onEnvelope() {}, onStatus() {},
  });
  transport.start();
  return { transport, sockets, timers, intervals, advance: ms => { now += ms; } };
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
