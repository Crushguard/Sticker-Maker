'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { quietTaskId, fastTaskId, isQuiet, fastPathReady, mustWait } = require('../src/readiness');
const { parsePackManifest } = require('../src/manifest');

test('quiet task ids are unique per request', () => {
  assert.match(quietTaskId('sorry-wiggle', 60000), /^q-sorry-wiggle-[0-9a-z]+-[0-9a-z]+$/);
  assert.notEqual(quietTaskId('sorry-wiggle', 60000), quietTaskId('sorry-wiggle', 60000));
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

const entries = (times) => Object.entries(times).map(([file, updatedMs]) => ({ file, updatedMs }));

test('the fast path needs a pack.json that lists stickers which all exist', () => {
  const listing = JSON.stringify({ stickers: [{ file: '1.png' }, { file: '2.png' }, { file: '3.png' }] });
  const folder = entries({ '1.png': 1, '2.png': 2, '3.png': 3, 'pack.json': 4 });
  const manifest = (text, images) => parsePackManifest(text, images, OPTS);
  assert.equal(fastPathReady(manifest(listing, ['1.png', '2.png', '3.png']), folder), true);
  assert.equal(fastPathReady(manifest(listing, ['1.png', '2.png']), folder), false);
  assert.equal(fastPathReady(manifest('{"name":"x"}', ['1.png', '2.png', '3.png']), folder), false);
  assert.equal(fastPathReady(manifest(null, ['1.png', '2.png', '3.png']), folder), false);
  assert.equal(fastPathReady(manifest('{oops', ['1.png']), folder), false);
  assert.equal(fastPathReady(manifest('{"stickers":[]}', ['1.png']), folder), false);
});

test('the fast path needs pack.json to be written after every image, as the export does', () => {
  const listing = JSON.stringify({ stickers: [{ file: '1.png' }, { file: '2.png' }, { file: '3.png' }] });
  const manifest = parsePackManifest(listing, ['1.png', '2.png', '3.png', 'tray.png'], OPTS);
  assert.equal(fastPathReady(manifest, entries({ '1.png': 1, '2.png': 2, '3.png': 3, 'tray.png': 3, 'pack.json': 3 })), true);
  assert.equal(fastPathReady(manifest, entries({ '1.png': 1, '2.png': 5, '3.png': 3, 'tray.png': 3, 'pack.json': 4 })), false);
  assert.equal(fastPathReady(manifest, entries({ '1.png': 1, '2.png': 2, '3.png': 3, 'tray.png': 9, 'pack.json': 4 })), false);
  assert.equal(fastPathReady(manifest, entries({ '1.png': 1, '2.png': 2, '3.png': 3, 'notes.txt': 9, 'pack.json': 4 })), true);
});

test('a build waits while files or deletions are recent, unless pack.json lists a complete set', () => {
  const now = 100000;
  assert.equal(mustWait({ fastReady: false, newestObjectMs: 10000, lastEventMs: 95000, nowMs: now }), true, 'a deletion 5 s ago');
  assert.equal(mustWait({ fastReady: false, newestObjectMs: 90000, lastEventMs: 0, nowMs: now }), true, 'an upload 10 s ago');
  assert.equal(mustWait({ fastReady: false, newestObjectMs: 10000, lastEventMs: 60000, nowMs: now }), false, 'quiet for 40 s');
  assert.equal(mustWait({ fastReady: true, newestObjectMs: 99000, lastEventMs: 99000, nowMs: now }), false, 'complete listing');
  assert.equal(mustWait({ fastReady: false, newestObjectMs: 10000, lastEventMs: null, nowMs: now }), false, 'no event record');
});
