import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
import ts from 'typescript';

function harness(freshTicket) {
  const timers = [];
  const sockets = [];
  class Socket {
    static OPEN = 1;
    constructor() { sockets.push(this); }
    close() { this.onclose?.(); }
  }
  const testModule = { exports: {} };
  const source = fs.readFileSync(new URL('../src/lib/transport.ts', import.meta.url), 'utf8');
  vm.runInNewContext(ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
  }).outputText, {
    module: testModule, exports: testModule.exports, process: { env: {} }, WebSocket: Socket,
    window: { location: { protocol: 'http:', host: 'localhost' } },
    setTimeout: callback => { timers.push(callback); return timers.length; }, clearTimeout() {},
    setInterval() {}, clearInterval() {},
    require: () => ({ MessageType: {}, encode: () => '', decode: () => ({}) }),
  });
  const transport = new testModule.exports.LiveTransport({
    gameId: 'game', ticket: 'initial', freshTicket, lastVersion: () => 0,
    onEnvelope() {}, onStatus() {},
  });
  transport.start();
  return { transport, sockets, timers };
}

test('leaving a room while a reconnect ticket is loading never opens another socket', async () => {
  let resolve;
  const h = harness(() => new Promise(done => { resolve = done; }));
  h.sockets[0].onclose();
  const reconnect = h.timers[0]();
  h.transport.close();
  resolve('late-ticket');
  await reconnect;
  assert.equal(h.sockets.length, 1);
});

test('a failed reconnect request after leaving does not schedule more retries', async () => {
  let reject;
  const h = harness(() => new Promise((_, fail) => { reject = fail; }));
  h.sockets[0].onclose();
  const reconnect = h.timers[0]();
  h.transport.close();
  reject(new Error('offline'));
  await reconnect;
  assert.equal(h.timers.length, 1);
});
