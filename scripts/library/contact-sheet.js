#!/usr/bin/env node
/**
 * contact-sheet.js — one numbered image of a pack folder's stickers, for writing each sticker's text and emoji
 * into pack.json.
 *
 * Usage: node scripts/library/contact-sheet.js <packDir> <out.png> [--columns 6] [--tile 200]
 */
'use strict';

const fs = require('fs');
const path = require('path');
const sharp = require('sharp');

function arg(flag, fallback) {
  const i = process.argv.indexOf(flag);
  return i >= 0 && process.argv[i + 1] ? Number(process.argv[i + 1]) : fallback;
}

async function main() {
  const [dir, out] = process.argv.slice(2);
  if (!dir || !out) {
    console.error('usage: contact-sheet.js <packDir> <out.png> [--columns 6] [--tile 200]');
    process.exit(2);
  }
  const columns = arg('--columns', 6);
  const tile = arg('--tile', 200);
  const label = 28;
  const collator = new Intl.Collator('en', { numeric: true });
  const files = fs
    .readdirSync(dir)
    .filter((f) => /\.(png|webp|gif)$/i.test(f) && !/^tray\.png$/i.test(f))
    .sort(collator.compare);
  const rows = Math.ceil(files.length / columns);
  const cells = await Promise.all(
    files.map(async (file, i) => {
      const art = await sharp(path.join(dir, file))
        .resize(tile, tile, { fit: 'contain', background: { r: 0, g: 0, b: 0, alpha: 0 } })
        .png()
        .toBuffer();
      const caption = Buffer.from(
        `<svg xmlns="http://www.w3.org/2000/svg" width="${tile}" height="${label}">` +
          `<text x="${tile / 2}" y="20" font-family="Helvetica" font-size="18" text-anchor="middle" fill="#333">${file}</text></svg>`
      );
      const left = (i % columns) * tile;
      const top = Math.floor(i / columns) * (tile + label);
      return [
        { input: art, left, top },
        { input: caption, left, top: top + tile },
      ];
    })
  );
  await sharp({
    create: { width: columns * tile, height: rows * (tile + label), channels: 4, background: '#FFEEF0' },
  })
    .composite(cells.flat())
    .png()
    .toFile(out);
  console.log(`${out}: ${files.length} stickers`);
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
