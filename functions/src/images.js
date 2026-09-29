'use strict';

const sharp = require('sharp');
const { WHATSAPP, QUALITY } = require('./config');

const TRANSPARENT = { r: 0, g: 0, b: 0, alpha: 0 };
const FIT = { fit: 'contain', background: TRANSPARENT, kernel: 'lanczos3' };

/**
 * WhatsApp's tray icon: 96×96 PNG from the first frame, full colour, reduced to a palette only if it would
 * otherwise exceed 50 KB.
 */
async function makeTray(buf) {
  const base = sharp(buf).ensureAlpha().resize(WHATSAPP.traySize, WHATSAPP.traySize, FIT);
  const full = await base.clone().png({ compressionLevel: 9, adaptiveFiltering: true }).toBuffer();
  if (full.length <= WHATSAPP.trayMaxBytes) return { buffer: full, palette: false };
  const reduced = await base.clone().png({ palette: true, quality: 100, compressionLevel: 9 }).toBuffer();
  return { buffer: reduced, palette: true };
}

/** The Home card's cover: the given frames as tile×tile squares, side by side, on transparency. */
async function makeCoverStrip(framesPng, tile) {
  if (!framesPng.length) throw new Error('a cover strip needs at least one frame');
  const tiles = await Promise.all(
    framesPng.map((frame) => sharp(frame).ensureAlpha().resize(tile, tile, FIT).png().toBuffer())
  );
  return sharp({
    create: { width: tile * tiles.length, height: tile, channels: 4, background: TRANSPARENT },
  })
    .composite(tiles.map((input, i) => ({ input, left: i * tile, top: 0 })))
    .webp({ quality: QUALITY.cover, alphaQuality: 100, smartSubsample: true, effort: 6 })
    .toBuffer();
}

module.exports = { makeTray, makeCoverStrip };
