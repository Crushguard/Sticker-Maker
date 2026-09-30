'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const sharp = require('sharp');
const { probe, isWhatsAppReady, encodeSticker, wiggle, firstFramePng, StickerError } = require('../src/stickers');
const { shapePng, noisyPng, animated, animatedGif, meta } = require('./helpers');

test('probe reports per-frame size, pages and delays', async () => {
  const p = await probe(await animated({ delays: [100, 120, 140] }));
  assert.equal(p.format, 'webp');
  assert.equal(p.width, 512);
  assert.equal(p.height, 512);
  assert.equal(p.pages, 3);
  assert.deepEqual(p.delays, [100, 120, 140]);
  assert.ok(p.bytes > 0);
});

test('a WhatsApp-ready static WebP ships byte for byte', async () => {
  const ready = await sharp(await shapePng()).webp({ quality: 90 }).toBuffer();
  assert.ok(isWhatsAppReady(await probe(ready)));
  const out = await encodeSticker(ready, {});
  assert.equal(out.passthrough, true);
  assert.equal(out.animated, false);
  assert.ok(out.buffer.equals(ready));
});

test('a WhatsApp-ready animated WebP ships byte for byte', async () => {
  const ready = await animated();
  const out = await encodeSticker(ready, {});
  assert.equal(out.passthrough, true);
  assert.equal(out.animated, true);
  assert.ok(out.buffer.equals(ready));
});

/** A WebP's top-level chunks as [tag, bytes] pairs. */
function chunks(webp) {
  const out = [];
  for (let i = 12; i + 8 <= webp.length; ) {
    const size = webp.readUInt32LE(i + 4);
    const end = i + 8 + size + (size & 1);
    out.push([webp.toString('latin1', i, i + 4), webp.subarray(i, end)]);
    i = end;
  }
  return out;
}

/** The WebP with extra chunks appended and its RIFF size (and VP8X flags) updated, as metadata tools write them. */
function withChunks(webp, extra, flags = 0) {
  const parts = extra.map(([tag, body]) => {
    const head = Buffer.alloc(8);
    head.write(tag, 0, 'latin1');
    head.writeUInt32LE(body.length, 4);
    return Buffer.concat([head, body, Buffer.alloc(body.length & 1)]);
  });
  const out = Buffer.concat([webp, ...parts]);
  out.writeUInt32LE(out.length - 8, 4);
  if (flags) out[20] |= flags;
  return out;
}

test('metadata chunks (C2PA content credentials, EXIF, XMP, unknown) are dropped; the image ships byte for byte', async () => {
  const ready = await sharp(await shapePng()).webp({ quality: 90 }).toBuffer();
  assert.equal(chunks(ready)[0][0], 'VP8X', 'an extended WebP, as art with transparency is');
  const tagged = withChunks(ready, [['C2PA', Buffer.alloc(301, 7)], ['EXIF', Buffer.from('Exif..')], ['XMP ', Buffer.from('<x/>')], ['ABCD', Buffer.alloc(3)]], 0x08 | 0x04);
  const out = await encodeSticker(tagged, {});
  assert.equal(out.passthrough, true);
  assert.deepEqual(chunks(out.buffer).map(([tag]) => tag), chunks(ready).map(([tag]) => tag));
  assert.ok(out.buffer.equals(ready), 'the image chunks and the VP8X flags are back to the untagged file');
  assert.equal(out.buffer.readUInt32LE(4), out.buffer.length - 8);
});

test('an animated sticker loses its trailing C2PA chunk and keeps every frame', async () => {
  const ready = await animated();
  const out = await encodeSticker(withChunks(ready, [['C2PA', Buffer.alloc(5759, 1)]]), {});
  assert.equal(out.animated, true);
  assert.ok(out.buffer.equals(ready));
});

test('a PNG becomes a lossless 512 WebP when that fits', async () => {
  const out = await encodeSticker(await shapePng(), {});
  assert.equal(out.passthrough, false);
  assert.equal(out.lossless, true);
  const m = await meta(out.buffer);
  assert.deepEqual([m.format, m.width, m.height, m.pages], ['webp', 512, 512, 1]);
  assert.ok(out.buffer.length <= 102400);
});

test('art that cannot be lossless ships lossy at quality 95 when that fits', async () => {
  const out = await encodeSticker(await noisyPng(6), {});
  assert.equal(out.lossless, false);
  assert.equal(out.quality, 95);
  assert.ok(out.buffer.length <= 102400);
});

test('harder art is lossy at the best quality from 95 down that fits', async () => {
  const out = await encodeSticker(await noisyPng(24), {});
  assert.equal(typeof out.quality, 'number');
  assert.ok(out.quality >= 75 && out.quality <= 95, `quality ${out.quality}`);
  assert.ok(out.buffer.length <= 102400);
});

test('art that would need quality under 75 fails instead of shipping blurry', async () => {
  await assert.rejects(encodeSticker(await noisyPng(128), {}), (err) => {
    assert.ok(err instanceof StickerError);
    assert.match(err.message, /100 KB/);
    return true;
  });
});

test('small art is upscaled to 512 with a note', async () => {
  const out = await encodeSticker(await shapePng({ size: 300 }), {});
  const m = await meta(out.buffer);
  assert.equal(m.width, 512);
  assert.equal(m.height, 512);
  assert.ok(out.notes.some((n) => n.includes('upscaled from 300 px')));
});

test('a non-square image is fitted without cropping', async () => {
  const wide = await sharp(await shapePng()).resize(600, 300, { fit: 'fill' }).png().toBuffer();
  const out = await encodeSticker(wide, {});
  const m = await meta(out.buffer);
  assert.equal(m.width, 512);
  assert.equal(m.height, 512);
  const { data, info } = await sharp(out.buffer).ensureAlpha().raw().toBuffer({ resolveWithObject: true });
  const alphaAt = (x, y) => data[(y * info.width + x) * 4 + 3];
  assert.equal(alphaAt(256, 5), 0, 'letterbox is transparent');
});

test('a GIF with 0 ms frames becomes WebP with 100 ms frames and a note', async () => {
  const out = await encodeSticker(await animated({ format: 'gif', delays: [0, 0, 0] }), {});
  assert.equal(out.animated, true);
  const m = await meta(out.buffer);
  assert.equal(m.format, 'webp');
  assert.equal(m.pages, 3);
  assert.deepEqual(m.delay, [100, 100, 100]);
  assert.ok(out.notes.some((n) => n.includes('100 ms')));
  assert.ok(out.buffer.length <= 512000);
});

test('an animation longer than 10 seconds fails', async () => {
  await assert.rejects(encodeSticker(await animated({ format: 'gif', delays: [4000, 4000, 4000] }), {}), (err) => {
    assert.ok(err instanceof StickerError);
    assert.match(err.message, /10 s/);
    return true;
  });
});

test('wiggle makes a 4-frame 150 ms animation from static art', async () => {
  const buf = await wiggle(await sharp(await shapePng()).webp({ quality: 90 }).toBuffer());
  const m = await meta(buf);
  assert.deepEqual([m.format, m.width, m.height, m.pages], ['webp', 512, 512, 4]);
  assert.deepEqual(m.delay, [150, 150, 150, 150]);
  assert.ok(buf.length <= 512000);
});

test('encodeSticker with wiggle animates static art', async () => {
  const out = await encodeSticker(await sharp(await shapePng()).webp({ quality: 90 }).toBuffer(), { wiggle: true });
  assert.equal(out.animated, true);
  assert.equal(out.passthrough, false);
  assert.equal((await meta(out.buffer)).pages, 4);
});

test('firstFramePng is a 512 PNG of the first frame', async () => {
  const png = await firstFramePng(await animated());
  const m = await meta(png);
  assert.deepEqual([m.format, m.width, m.height, m.pages], ['png', 512, 512, 1]);
});

test('GIF frames of 10 ms or less play at 100 ms, as browsers and the WebP encoder treat them', async () => {
  const out = await encodeSticker(await animatedGif(4, 10), {});
  const m = await meta(out.buffer);
  assert.deepEqual(m.delay, [100, 100, 100, 100]);
  assert.ok(out.notes.some((n) => n.includes('100 ms')));
});

test('a GIF whose 10 ms frames really loop longer than 10 s fails instead of breaking in WhatsApp', async () => {
  await assert.rejects(encodeSticker(await animatedGif(120, 10), {}), (err) => {
    assert.ok(err instanceof StickerError);
    assert.match(err.message, /10 s/);
    return true;
  });
});
