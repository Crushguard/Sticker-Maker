'use strict';

const sharp = require('sharp');
const { WHATSAPP, QUALITY } = require('./config');

/** A sticker that cannot be made to WhatsApp's rules; the message goes into the pack's report. */
class StickerError extends Error {}

const SIZE = WHATSAPP.size;
const TRANSPARENT = { r: 0, g: 0, b: 0, alpha: 0 };
const FIT = { fit: 'contain', background: TRANSPARENT, kernel: 'lanczos3' };
const DEFAULT_DELAY_MS = 100;
/** Browsers and libwebp's encoder play GIF delays of 10 ms or less as 100 ms. */
const FASTEST_REAL_DELAY_MS = 10;
const WIGGLE_DELAY_MS = 150;
const WIGGLE_POSES = [
  { angle: 0, dx: 0, dy: 0 },
  { angle: 1.8, dx: 0, dy: -5 },
  { angle: 0, dx: 0, dy: 2 },
  { angle: -1.8, dx: 0, dy: -5 },
];
const WIGGLE_MARGIN = 16;
/** The chunks that make up a WebP's image; any other chunk is metadata. */
const IMAGE_CHUNKS = new Set(['VP8X', 'ICCP', 'ANIM', 'ANMF', 'ALPH', 'VP8 ', 'VP8L']);
/** VP8X flag bits announcing EXIF and XMP chunks. */
const VP8X_EXIF_XMP = 0x08 | 0x04;

const sum = (list) => list.reduce((a, b) => a + b, 0);

async function probe(buf) {
  let m;
  try {
    m = await sharp(buf, { animated: true }).metadata();
  } catch (err) {
    throw new StickerError(`can't read the image (${err.message})`);
  }
  const pages = m.pages || 1;
  return {
    format: m.format,
    width: m.width,
    height: m.pageHeight || m.height,
    pages,
    delays: pages > 1 && Array.isArray(m.delay) ? m.delay : [],
    loop: m.loop || 0,
    bytes: buf.length,
  };
}

/**
 * The WebP without its metadata chunks: C2PA content credentials, EXIF, XMP and anything unknown. WhatsApp silently
 * refuses stickers carrying a C2PA chunk (AI image and video tools write one): its add sheet shows no stickers and
 * the pack never arrives. The image chunks stay byte for byte.
 */
function withoutMetadata(buf) {
  if (buf.length < 20 || buf.toString('latin1', 0, 4) !== 'RIFF' || buf.toString('latin1', 8, 12) !== 'WEBP') return buf;
  const kept = [];
  let dropped = false;
  for (let i = 12; i + 8 <= buf.length; ) {
    const size = buf.readUInt32LE(i + 4);
    const end = Math.min(buf.length, i + 8 + size + (size & 1));
    if (IMAGE_CHUNKS.has(buf.toString('latin1', i, i + 4))) kept.push(buf.subarray(i, end));
    else dropped = true;
    i = end;
  }
  if (!dropped) return buf;
  const out = Buffer.concat([buf.subarray(0, 12), ...kept]);
  out.writeUInt32LE(out.length - 8, 4);
  if (out.toString('latin1', 12, 16) === 'VP8X') out[20] &= ~VP8X_EXIF_XMP;
  return out;
}

/** Already exactly what WhatsApp takes: shipped untouched, so no quality is lost. */
function isWhatsAppReady(p) {
  if (p.format !== 'webp' || p.width !== SIZE || p.height !== SIZE) return false;
  if (p.pages === 1) return p.bytes <= WHATSAPP.staticMaxBytes;
  return (
    p.bytes <= WHATSAPP.animatedMaxBytes &&
    p.delays.length === p.pages &&
    p.delays.every((d) => d >= WHATSAPP.minFrameMs) &&
    sum(p.delays) <= WHATSAPP.maxLoopMs
  );
}

async function fittedRaw(buf) {
  const { data, info } = await sharp(buf).ensureAlpha().resize(SIZE, SIZE, FIT).raw().toBuffer({ resolveWithObject: true });
  return { data, raw: { width: info.width, height: info.height, channels: info.channels } };
}

/**
 * Best quality that fits 100 KB: lossless, else lossy from 95 down to the floor. Lossy uses effort 4: effort 6
 * with a lossless alpha plane takes ~100x longer for ~2% fewer bytes.
 */
async function encodeStatic(buf) {
  const { data, raw } = await fittedRaw(buf);
  const lossless = await sharp(data, { raw }).webp({ lossless: true, effort: 6 }).toBuffer();
  if (lossless.length <= WHATSAPP.staticMaxBytes) return { buffer: lossless, lossless: true, quality: 100 };
  for (let q = QUALITY.staticStart; q >= QUALITY.staticFloor; q -= QUALITY.step) {
    const out = await sharp(data, { raw })
      .webp({ quality: q, alphaQuality: 100, smartSubsample: true, effort: 4 })
      .toBuffer();
    if (out.length <= WHATSAPP.staticMaxBytes) return { buffer: out, lossless: false, quality: q };
  }
  throw new StickerError(`can't fit 100 KB without going below quality ${QUALITY.staticFloor}; simplify the art`);
}

/** Animated WebP at the best quality from 90 down that fits 500 KB (effort 6 is ~100x slower for ~3%). */
async function encodeFrames(image, delays) {
  for (let q = QUALITY.animatedStart; q >= QUALITY.animatedFloor; q -= QUALITY.step) {
    const out = await image
      .clone()
      .webp({ quality: q, alphaQuality: 100, smartSubsample: true, effort: 4, delay: delays, loop: 0 })
      .toBuffer();
    if (out.length <= WHATSAPP.animatedMaxBytes) return { buffer: out, quality: q };
  }
  throw new StickerError(
    `can't fit 500 KB without going below quality ${QUALITY.animatedFloor}; use fewer or smaller frames`
  );
}

async function encodeAnimated(buf, p, notes) {
  let delays = p.delays.length === p.pages ? [...p.delays] : new Array(p.pages).fill(DEFAULT_DELAY_MS);
  if (delays.some((d) => d <= FASTEST_REAL_DELAY_MS)) {
    delays = delays.map((d) => (d <= FASTEST_REAL_DELAY_MS ? DEFAULT_DELAY_MS : d));
    notes.push(`frames of ${FASTEST_REAL_DELAY_MS} ms or less play at ${DEFAULT_DELAY_MS} ms, as in browsers`);
  }
  const total = sum(delays);
  if (total > WHATSAPP.maxLoopMs) {
    throw new StickerError(`the animation lasts ${(total / 1000).toFixed(1)} s; WhatsApp allows 10 s`);
  }
  const image = sharp(buf, { animated: true }).resize(SIZE, SIZE, FIT);
  return encodeFrames(image, delays);
}

async function firstFramePng(buf) {
  return sharp(buf).ensureAlpha().resize(SIZE, SIZE, FIT).png().toBuffer();
}

async function wiggleFrame(basePng, { angle, dx, dy }) {
  if (angle === 0 && dx === 0 && dy === 0) return basePng;
  const m = WIGGLE_MARGIN;
  const rotated = await sharp(basePng)
    .rotate(angle, { background: TRANSPARENT })
    .extend({ top: m, bottom: m, left: m, right: m, background: TRANSPARENT })
    .raw()
    .toBuffer({ resolveWithObject: true });
  const { width, height, channels } = rotated.info;
  const left = Math.round((width - SIZE) / 2 - dx);
  const top = Math.round((height - SIZE) / 2 - dy);
  return sharp(rotated.data, { raw: { width, height, channels } })
    .extract({ left, top, width: SIZE, height: SIZE })
    .png()
    .toBuffer();
}

/** The launch packs' "animated" look: a gentle 4-frame wiggle of static art; frame 1 is the art itself. */
async function wiggle(buf) {
  const base = await firstFramePng(buf);
  const frames = [];
  for (const pose of WIGGLE_POSES) frames.push(await wiggleFrame(base, pose));
  const image = sharp(frames, { join: { animated: true } });
  const { buffer } = await encodeFrames(image, WIGGLE_POSES.map(() => WIGGLE_DELAY_MS));
  return buffer;
}

/** The last word on a re-encoded sticker: it must meet WhatsApp's rules as written, whatever the encoder did. */
async function assertWhatsAppReady(buffer) {
  const p = await probe(buffer);
  if (isWhatsAppReady(p)) return;
  const loopMs = sum(p.delays);
  if (p.pages > 1 && loopMs > WHATSAPP.maxLoopMs) {
    throw new StickerError(`the animation lasts ${(loopMs / 1000).toFixed(1)} s; WhatsApp allows 10 s`);
  }
  throw new StickerError(`the encoded sticker breaks WhatsApp's rules (${p.width}×${p.height}, ${p.bytes} bytes)`);
}

/**
 * One sticker to WhatsApp's rules. Ready art passes through with its image byte for byte; everything else is fitted
 * into 512×512 on transparency and encoded at the best quality that fits. Metadata chunks never ship.
 */
async function encodeSticker(buf, options = {}) {
  const out = await encode(buf, options);
  return { ...out, buffer: withoutMetadata(out.buffer) };
}

async function encode(buf, { wiggle: wiggleIt = false } = {}) {
  const p = await probe(buf);
  const notes = [];
  if (wiggleIt && p.pages === 1) {
    const buffer = await wiggle(buf);
    await assertWhatsAppReady(buffer);
    return { buffer, animated: true, passthrough: false, quality: null, lossless: false, notes };
  }
  if (isWhatsAppReady(p)) {
    return { buffer: buf, animated: p.pages > 1, passthrough: true, quality: null, lossless: null, notes };
  }
  const longer = Math.max(p.width, p.height);
  if (longer < SIZE) notes.push(`upscaled from ${longer} px; stickers look best from 512 px art`);
  if (p.pages > 1) {
    const { buffer, quality } = await encodeAnimated(buf, p, notes);
    await assertWhatsAppReady(buffer);
    return { buffer, animated: true, passthrough: false, quality, lossless: false, notes };
  }
  const { buffer, lossless, quality } = await encodeStatic(buf);
  await assertWhatsAppReady(buffer);
  return { buffer, animated: false, passthrough: false, quality, lossless, notes };
}

module.exports = { probe, isWhatsAppReady, encodeSticker, withoutMetadata, wiggle, firstFramePng, StickerError };
