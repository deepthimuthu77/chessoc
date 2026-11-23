import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
import ts from 'typescript';

const compiled = { exports: {} };
vm.runInNewContext(ts.transpileModule(fs.readFileSync(new URL('../src/lib/board-input.ts', import.meta.url), 'utf8'), {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
}).outputText, { module: compiled, exports: compiled.exports });
const { dropClientPoint, dropSquare } = compiled.exports;

test('a mouse drop after scrolling uses the square under the pointer, not page coordinates', () => {
  const event = { clientX: 460, clientY: 420, pageX: 460, pageY: 700 };
  assert.equal(dropSquare(dropClientPoint(event), { left: 100, top: 60, width: 640, height: 640 }, false), 'e4');
});

test('touch drops use the released touch and respect black orientation', () => {
  const event = { changedTouches: [{ clientX: 380, clientY: 340, pageX: 380, pageY: 620 }] };
  assert.equal(dropSquare(dropClientPoint(event), { left: 100, top: 60, width: 640, height: 640 }, true), 'e4');
  assert.equal(dropClientPoint({ changedTouches: [] }), null);
});

test('the playable board excludes its frame and rejects outside drops', () => {
  const board = { left: 105, top: 65, width: 640, height: 640 };
  assert.equal(dropSquare({ x: 105, y: 65 }, board, false), 'a8');
  assert.equal(dropSquare({ x: 744.9, y: 704.9 }, board, false), 'h1');
  for (const point of [{ x: 104, y: 200 }, { x: 745, y: 200 }, { x: 200, y: 64 }, { x: 200, y: 705 }]) {
    assert.equal(dropSquare(point, board, false), null);
  }
});
