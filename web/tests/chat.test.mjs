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
  }).outputText, { module: result, exports: result.exports, Intl, require: path => load(path.slice(2)) });
  return result.exports;
}
const { limitChatText, insertChatEmoji } = load('chat-input');
const { reducer, initialState } = load('store');

test('message limits retain whole flags, skin tones, and joined emoji', () => {
  for (const emoji of ['😀', '👍🏽', '👨‍👩‍👧‍👦', '🇮🇳']) {
    assert.equal(limitChatText('x'.repeat(499) + emoji), 'x'.repeat(499));
    assert.equal(limitChatText(emoji), emoji);
  }
  assert.equal(limitChatText('x'.repeat(498) + '😀tail'), 'x'.repeat(498) + '😀');
});

test('emoji insertion replaces selected text and preserves the draft when full', () => {
  const next = insertChatEmoji('Good match', '🤝', 5, 10);
  assert.equal(next.value, 'Good 🤝');
  assert.equal(next.cursor, 7);
  assert.equal(insertChatEmoji('x'.repeat(500), '😀', 500, 500), null);
});

test('a chat rejection preserves a pending board move and retains text for retry', () => {
  const pending = { ...initialState, pendingIntentId: 'move-one', pendingMove: { from: 'e2', to: 'e4' } };
  const rejected = reducer(pending, { type: 'envelope', env: { type: 'error', payload: {
    code: 'chat_rate_limited', message: 'Please wait', text: 'Good game 😀',
  } } });
  assert.equal(rejected.pendingIntentId, 'move-one');
  assert.equal(rejected.pendingMove, pending.pendingMove);
  assert.equal(rejected.error, null);
  assert.equal(rejected.chatFailure.text, 'Good game 😀');
  assert.equal(reducer(rejected, { type: 'chat_sent' }).chatFailure, null);
});

test('a long-running room retains the latest 200 chat messages', () => {
  let state = initialState;
  for (let i = 0; i < 250; i++) state = reducer(state, { type: 'envelope', env: { type: 'chat', payload: { text: String(i) } } });
  assert.equal(state.chat.length, 200);
  assert.equal(state.chat[0].text, '50');
  assert.equal(state.chat.at(-1).text, '249');
});
