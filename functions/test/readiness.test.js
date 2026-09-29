'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { quietTaskId, fastTaskId, isQuiet, fastPathReady } = require('../src/readiness');
const { parsePackManifest } = require('../src/manifest');

test('quiet task ids share a 30-second bucket per pack', () => {
  assert.equal(quietTaskId('sorry-wiggle', 60000), 'q-sorry-wiggle-2');
  assert.equal(quietTaskId('sorry-wiggle', 89999), 'q-sorry-wiggle-2');
  assert.equal(quietTaskId('sorry-wiggle', 90000), 'q-sorry-wiggle-3');
});

test('fast task ids depend on the exact object generations', () => {
  const a = fastTaskId('gm-gn', ['1', '2', '3']);
  assert.match(a, /^f-gm-gn-[0-9a-f]{16}$/);
  assert.equal(fastTaskId('gm-gn', ['1', '2', '3']), a);
  assert.notEqual(fastTaskId('gm-gn', ['1', '2', '4']), a);
});

test('task ids only use characters Cloud Tasks accepts', () => {
  assert.match(quietTaskId('pack-1a2b3c4d', Date.now()), /^[A-Za-z0-9_-]+$/);
});

test('a folder is quiet 30 seconds after its newest object', () => {
  assert.equal(isQuiet(1000, 30999), false);
  assert.equal(isQuiet(1000, 31000), true);
});

const OPTS = { folder: 'P', category: 'sorry', categoryIds: new Set(), defaultEmojis: [] };

test('the fast path needs a pack.json that lists stickers which all exist', () => {
  const listing = JSON.stringify({ stickers: [{ file: '1.png' }, { file: '2.png' }, { file: '3.png' }] });
  assert.equal(fastPathReady(parsePackManifest(listing, ['1.png', '2.png', '3.png'], OPTS)), true);
  assert.equal(fastPathReady(parsePackManifest(listing, ['1.png', '2.png'], OPTS)), false);
  assert.equal(fastPathReady(parsePackManifest('{"name":"x"}', ['1.png', '2.png', '3.png'], OPTS)), false);
  assert.equal(fastPathReady(parsePackManifest(null, ['1.png', '2.png', '3.png'], OPTS)), false);
  assert.equal(fastPathReady(parsePackManifest('{oops', ['1.png'], OPTS)), false);
  assert.equal(fastPathReady(parsePackManifest('{"stickers":[]}', ['1.png'], OPTS)), false);
});
