'use strict';

const { zipSync, strToU8 } = require('fflate');

// Zip timestamps are local-time fields: a date built from local components writes the same bytes in every
// time zone, so a pack's fingerprint depends on its content only.
const FIXED_MTIME = new Date(2000, 0, 1, 12, 0, 0);

/**
 * The pack as the app downloads it: contents.json (emoji and text per sticker), tray.png, then the stickers in
 * pack order. Stored, not deflated: WebP and PNG are already compressed.
 */
function makePackZip({ contents, tray, stickers }) {
  const entries = {
    'contents.json': strToU8(JSON.stringify(contents)),
    'tray.png': new Uint8Array(tray),
  };
  for (const { name, buffer } of stickers) entries[name] = new Uint8Array(buffer);
  return Buffer.from(zipSync(entries, { level: 0, mtime: FIXED_MTIME }));
}

module.exports = { makePackZip };
