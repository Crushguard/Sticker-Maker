'use strict';

const sharp = require('sharp');

/** A transparent square PNG with one filled circle, like a simple sticker. */
function shapePng({ size = 512, color = '#C23359', cx = 0.5 } = {}) {
  const r = Math.round(size * 0.23);
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}">` +
    `<circle cx="${Math.round(size * cx)}" cy="${size / 2}" r="${r}" fill="${color}" stroke="#fff" stroke-width="${Math.round(size / 40)}"/></svg>`;
  return sharp(Buffer.from(svg)).png().toBuffer();
}

/** An opaque gradient with seeded noise: bigger amp = harder to compress. */
function noisyPng(amp, seed = 42) {
  const w = 512;
  const h = 512;
  const buf = Buffer.alloc(w * h * 4);
  let s = seed;
  const rnd = () => {
    s = (s * 1103515245 + 12345) & 0x7fffffff;
    return s / 0x7fffffff;
  };
  for (let y = 0; y < h; y++) {
    for (let x = 0; x < w; x++) {
      const i = (y * w + x) * 4;
      const n = () => (rnd() - 0.5) * 2 * amp;
      buf[i] = Math.max(0, Math.min(255, x / 2 + n()));
      buf[i + 1] = Math.max(0, Math.min(255, y / 2 + n()));
      buf[i + 2] = Math.max(0, Math.min(255, 128 + n()));
      buf[i + 3] = 255;
    }
  }
  return sharp(buf, { raw: { width: w, height: h, channels: 4 } }).png().toBuffer();
}

/** An animated image from three moving circles. */
async function animated({ format = 'webp', delays = [100, 100, 100], size = 512 } = {}) {
  const frames = await Promise.all(
    [0.4, 0.5, 0.6].map((cx, i) => shapePng({ size, cx, color: ['#C23359', '#2E9E6B', '#3B6DD4'][i] }))
  );
  const joined = sharp(frames, { join: { animated: true } });
  return format === 'gif'
    ? joined.gif({ delay: delays, loop: 0 }).toBuffer()
    : joined.webp({ delay: delays, loop: 0, quality: 90 }).toBuffer();
}

/** An animated GIF of [count] frames, each [delay] ms. */
async function animatedGif(count, delay) {
  const colors = ['#C23359', '#2E9E6B', '#3B6DD4'];
  const frames = await Promise.all(
    Array.from({ length: count }, (_, i) => shapePng({ cx: 0.3 + (i % 5) * 0.1, color: colors[i % 3] }))
  );
  return sharp(frames, { join: { animated: true } }).gif({ delay: frames.map(() => delay), loop: 0 }).toBuffer();
}

async function meta(buf) {
  const m = await sharp(buf, { animated: true }).metadata();
  return { format: m.format, width: m.width, height: m.pageHeight || m.height, pages: m.pages || 1, delay: m.delay };
}

module.exports = { shapePng, noisyPng, animated, animatedGif, meta };
