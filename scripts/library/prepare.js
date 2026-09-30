#!/usr/bin/env node
/**
 * prepare.js — writes each pack folder's pack.json into a sticker export (a folder of pack folders, such as Claude
 * Design's WhatsApp export), from catalog/packs.json: name, lettering languages, tags (which also place the pack in
 * the app's categories), the pack's emoji, search phrases, order, the After Dark flag, and each sticker's emoji and
 * text where known. The folders are then ready to upload to library/ as they are.
 *
 * Usage: node scripts/library/prepare.js <exportDir> [--dry-run]
 *
 * A folder with no entry in catalog/packs.json is left alone and listed at the end: it would build with its folder
 * name, no tags and no category.
 */
'use strict';

const fs = require('fs');
const path = require('path');

const ROOT = path.resolve(__dirname, '..', '..');
const CATALOG = path.join(ROOT, 'catalog', 'packs.json');
const IMAGE = /\.(png|webp|gif)$/i;
const TRAY = /^tray\.png$/i;
const collator = new Intl.Collator('en', { numeric: true, sensitivity: 'base' });

/** pack.json for one folder: the catalog entry plus a listing of the folder's stickers in natural order. */
function packJson(entry, stickerFiles) {
  const out = { name: entry.name, lang: entry.lang };
  if (entry.adult) return { ...out, emojis: entry.emojis, adult: true };
  Object.assign(out, { tags: entry.tags, emojis: entry.emojis });
  if (entry.keywords && entry.keywords.length) out.keywords = entry.keywords;
  if (typeof entry.order === 'number') out.order = entry.order;
  if (entry.animate) out.animate = entry.animate;
  const known = entry.stickers || {};
  out.stickers = stickerFiles.map((file) => ({ file, ...(known[file] || {}) }));
  return out;
}

function main() {
  const exportDir = process.argv[2];
  if (!exportDir || exportDir.startsWith('--') || !fs.existsSync(exportDir)) {
    console.error('usage: prepare.js <exportDir> [--dry-run]');
    process.exit(2);
  }
  const dryRun = process.argv.includes('--dry-run');
  const { packs } = JSON.parse(fs.readFileSync(CATALOG, 'utf8'));
  const folders = fs
    .readdirSync(exportDir)
    .filter((f) => !f.startsWith('.') && !f.startsWith('_') && fs.statSync(path.join(exportDir, f)).isDirectory())
    .sort(collator.compare);

  const counts = { written: 0, unchanged: 0, parked: 0 };
  const unknown = [];
  const warnings = [];
  for (const folder of folders) {
    const entry = packs[folder];
    if (!entry) {
      unknown.push(folder);
      continue;
    }
    const dir = path.join(exportDir, folder);
    const images = fs.readdirSync(dir).filter((f) => IMAGE.test(f) && !f.startsWith('.'));
    const stickers = images.filter((f) => !TRAY.test(f)).sort(collator.compare);
    if (!entry.adult && (stickers.length < 3 || stickers.length > 30)) warnings.push(`${folder}: ${stickers.length} stickers (WhatsApp needs 3 to 30)`);
    if (!images.some((f) => TRAY.test(f))) warnings.push(`${folder}: no tray.png (the build makes one from the first sticker)`);
    const missing = Object.keys(entry.stickers || {}).filter((f) => !stickers.includes(f));
    if (missing.length) warnings.push(`${folder}: catalog/packs.json describes missing stickers ${missing.join(', ')}`);

    const text = `${JSON.stringify(packJson(entry, stickers), null, 2)}\n`;
    const target = path.join(dir, 'pack.json');
    if (entry.adult) counts.parked += 1;
    if (fs.existsSync(target) && fs.readFileSync(target, 'utf8') === text) {
      counts.unchanged += 1;
      continue;
    }
    if (!dryRun) fs.writeFileSync(target, text);
    counts.written += 1;
  }

  console.log(
    `${folders.length} folders: ${counts.written} pack.json ${dryRun ? 'to write' : 'written'}, ${counts.unchanged} unchanged` +
      (counts.parked ? `, ${counts.parked} After Dark (parked by the pipeline)` : '')
  );
  for (const w of warnings) console.log(`! ${w}`);
  if (unknown.length) console.log(`Not in catalog/packs.json, left alone: ${unknown.join(', ')}`);
  const absent = Object.keys(packs).filter((id) => !folders.includes(id));
  if (absent.length) console.log(`In catalog/packs.json but not in this export: ${absent.join(', ')}`);
}

if (require.main === module) main();

module.exports = { packJson };
