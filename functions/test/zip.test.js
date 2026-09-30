'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { unzipSync, strFromU8 } = require('fflate');
const { makePackZip } = require('../src/zip');

const input = () => ({
  contents: { id: 'sorry-wiggle', version: 3, stickers: [{ file: '01.webp', emojis: ['🥺'], text: 'Sorry' }] },
  tray: Buffer.from('tray-bytes'),
  stickers: [
    { name: '01.webp', buffer: Buffer.from('one') },
    { name: '02.webp', buffer: Buffer.from('two') },
  ],
});

test('the pack zip holds contents.json, the tray and the stickers, in that order', () => {
  const zip = makePackZip(input());
  const files = unzipSync(new Uint8Array(zip));
  assert.deepEqual(Object.keys(files), ['contents.json', 'tray.png', '01.webp', '02.webp']);
  assert.deepEqual(JSON.parse(strFromU8(files['contents.json'])).stickers[0].text, 'Sorry');
  assert.equal(strFromU8(files['tray.png']), 'tray-bytes');
  assert.equal(strFromU8(files['02.webp']), 'two');
});

test('entries are stored, not deflated (WebP and PNG are already compressed)', () => {
  const zip = makePackZip(input());
  // Local file header: compression method at offset 8, 0 = stored.
  assert.equal(zip.readUInt16LE(8), 0);
});

test('identical inputs give identical bytes', () => {
  assert.ok(makePackZip(input()).equals(makePackZip(input())));
});
