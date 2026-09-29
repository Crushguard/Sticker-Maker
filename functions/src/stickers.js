'use strict';

const sharp = require('sharp');
const { WHATSAPP, QUALITY } = require('./config');

/** A sticker that cannot be made to WhatsApp's rules; the message goes into the pack's report. */
class StickerError extends Error {}

const SIZE = WHATSAPP.size;
const TRANSPARENT = { r: 0, g: 0, b: 0, alpha: 0 };
const FIT = { fit: 'contain', background: TRANSPARENT, kernel: 'lanczos3' };
const DEFAULT_DELAY_MS = 100;
const WIGGLE_DELAY_MS = 150;
const WIGGLE_POSES = [
  { angle: 0, dx: 0, dy: 0 },
  { angle: 1.8, dx: 0, dy: -5 },
  { angle: 0, dx: 0, dy: 2 },
  { angle: -1.8, dx: 0, dy: -5 },
];
const WIGGLE_MARGIN = 16;

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

/** Best quality that fits 100 KB: lossless, then near-lossless, then lossy from 95 down to the floor. */
async function encodeStatic(buf) {
  const { data, raw } = await fittedRaw(buf);
  const attempt = (options) => sharp(data, { raw }).webp({ effort: 6, ...options }).toBuffer();
  const lossless = await attempt({ lossless: true });
  if (lossless.length <= WHATSAPP.staticMaxBytes) return { buffer: lossless, lossless: true, quality: 100 };
  const near = await attempt({ nearLossless: true, quality: 60 });
  if (near.length <= WHATSAPP.staticMaxBytes) return { buffer: near, lossless: false, quality: 'near-lossless' };
  for (let q = QUALITY.staticStart; q >= QUALITY.staticFloor; q -= QUALITY.step) {
    const out = await attempt({ quality: q, alphaQuality: 100, smartSubsample: true });
    if (out.length <= WHATSAPP.staticMaxBytes) return { buffer: out, lossless: false, quality: q };
  }
  throw new StickerError(`can't fit 100 KB without going below quality ${QUALITY.staticFloor}; simplify the art`);
}

/** Animated WebP at the best quality from 90 down that fits 500 KB. */
async function encodeFrames(image, delays) {
  for (let q = QUALITY.animatedStart; q >= QUALITY.animatedFloor; q -= QUALITY.step) {
    const out = await image
      .clone()
      .webp({ quality: q, alphaQuality: 100, smartSubsample: true, effort: 6, delay: delays, loop: 0 })
      .toBuffer();
    if (out.length <= WHATSAPP.animatedMaxBytes) return { buffer: out, quality: q };
  }
  throw new StickerError(
    `can't fit 500 KB without going below quality ${QUALITY.animatedFloor}; use fewer or smaller frames`
  );
}

async function encodeAnimated(buf, p, notes) {
  let delays = p.delays.length === p.pages ? [...p.delays] : new Array(p.pages).fill(DEFAULT_DELAY_MS);
  if (delays.some((d) => d < WHATSAPP.minFrameMs)) {
    delays = delays.map((d) => (d < WHATSAPP.minFrameMs ? DEFAULT_DELAY_MS : d));
    notes.push(`frames shorter than ${WHATSAPP.minFrameMs} ms now last ${DEFAULT_DELAY_MS} ms`);
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

/**
 * One sticker to WhatsApp's rules. Ready art passes through byte for byte; everything else is fitted into
 * 512×512 on transparency and encoded at the best quality that fits.
 */
async function encodeSticker(buf, { wiggle: wiggleIt = false } = {}) {
  const p = await probe(buf);
  const notes = [];
  if (wiggleIt && p.pages === 1) {
    const buffer = await wiggle(buf);
    return { buffer, animated: true, passthrough: false, quality: null, lossless: false, notes };
  }
  if (isWhatsAppReady(p)) {
    return { buffer: buf, animated: p.pages > 1, passthrough: true, quality: null, lossless: null, notes };
  }
  const longer = Math.max(p.width, p.height);
  if (longer < SIZE) notes.push(`upscaled from ${longer} px; stickers look best from 512 px art`);
  if (p.pages > 1) {
    const { buffer, quality } = await encodeAnimated(buf, p, notes);
    return { buffer, animated: true, passthrough: false, quality, lossless: false, notes };
  }
  const { buffer, lossless, quality } = await encodeStatic(buf);
  return { buffer, animated: false, passthrough: false, quality, lossless, notes };
}

module.exports = { probe, isWhatsAppReady, encodeSticker, wiggle, firstFramePng, StickerError };
