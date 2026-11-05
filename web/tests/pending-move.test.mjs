import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
import ts from 'typescript';

function load(name) {
  const result = { exports: {} };
  const source = fs.readFileSync(new URL(`../src/lib/${name}.ts`, import.meta.url), 'utf8');
  vm.runInNewContext(ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
  }).outputText, { module: result, exports: result.exports, require: path => load(path.slice(2)) });
  return result.exports;
}

const { reducer, initialState } = load('store');
const projection = { version: 4, fen: 'server position', moves: [] };
const original = { ...initialState, connection: 'connected', projection, version: 4 };
const move = { from: 'e2', to: 'e4' };
const waiting = () => reducer(original, { type: 'intent', intentId: 'one', move });

test('disconnect invalidates rematch capabilities until a fresh server snapshot arrives', () => {
  const capability = { id: 'old', kind: 'rematch' };
  const disconnected = reducer({ ...original, capabilities: [capability] }, { type: 'status', status: 'reconnecting' });
  assert.equal(disconnected.capabilities.length, 0);
  const open = reducer(disconnected, { type: 'status', status: 'connected' });
  assert.equal(open.capabilities.length, 0);
  const refreshed = reducer(open, { type: 'envelope', env: {
    type: 'resync_ok', payload: { snapshot: projection, capabilities: [{ ...capability, id: 'fresh' }] },
  } });
  assert.equal(refreshed.capabilities[0].id, 'fresh');
});

test('a pending move keeps its visual destination without changing the authoritative position', () => {
  const state = waiting();
  assert.equal(state.projection, projection);
  assert.equal(state.version, 4);
  assert.equal(state.pendingMove, move);
});

test('acceptance replaces the visual preview with the authoritative snapshot', () => {
  const next = { ...projection, version: 5, fen: 'confirmed position' };
  const state = reducer(waiting(), { type: 'envelope', env: {
    type: 'result', payload: { disposition: 'accepted', snapshot: next, capabilities: [] },
  } });
  assert.equal(state.projection, next);
  assert.equal(state.pendingMove, null);
});

for (const action of [
  { type: 'status', status: 'reconnecting' },
  { type: 'envelope', env: { type: 'result', payload: { disposition: 'rejected', reason: 'stale capability' } } },
  { type: 'envelope', env: { type: 'error', payload: { message: 'invalid intent' } } },
]) {
  test(`${action.status ?? action.env.type} rolls the visual preview back without changing game state`, () => {
    const state = reducer(waiting(), action);
    assert.equal(state.projection, projection);
    assert.equal(state.pendingMove, null);
    assert.equal(state.pendingIntentId, null);
  });
}
