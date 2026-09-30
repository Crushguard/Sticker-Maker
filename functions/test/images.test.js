'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const sharp = require('sharp');
const { makeTray, makeCoverStrip } = require('../src/images');
const { shapePng, animated, meta } = require('./helpers');

test('the tray is a full-colour 96×96 PNG under 50 KB', async () => {
  const tray = await makeTray(await shapePng());
  const m = await meta(tray.buffer);
  assert.deepEqual([m.format, m.width, m.height], ['png', 96, 96]);
  assert.ok(tray.buffer.length <= 51200);
  assert.equal(tray.palette, false);
  const { channels } = await sharp(tray.buffer).metadata();
  assert.equal(channels, 4);
});

test('the tray comes from the first frame of animated art', async () => {
  const tray = await makeTray(await animated());
  const m = await meta(tray.buffer);
  assert.deepEqual([m.width, m.height, m.pages], [96, 96, 1]);
});

test('a cover strip puts one tile per sticker side by side', async () => {
  const frames = await Promise.all([0.3, 0.4, 0.5, 0.6, 0.7, 0.5].map((cx) => shapePng({ cx })));
  const large = await makeCoverStrip(frames, 192);
  const lm = await meta(large);
  assert.deepEqual([lm.format, lm.width, lm.height], ['webp', 192 * 6, 192]);
  const small = await makeCoverStrip(frames.slice(0, 3), 96);
  const sm = await meta(small);
  assert.deepEqual([sm.width, sm.height], [96 * 3, 96]);
});

test('a cover strip keeps transparency', async () => {
  const strip = await makeCoverStrip([await shapePng()], 96);
  const { data, info } = await sharp(strip).ensureAlpha().raw().toBuffer({ resolveWithObject: true });
  assert.equal(data[3], 0, 'top-left corner is transparent');
  const centre = ((info.height / 2) * info.width + info.width / 2) * 4 + 3;
  assert.ok(data[centre] > 200, 'the circle is opaque');
});

test('a cover strip needs at least one frame', async () => {
  await assert.rejects(makeCoverStrip([], 96));
});
