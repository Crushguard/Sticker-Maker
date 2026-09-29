'use strict';

const crypto = require('crypto');

const QUIET_MS = 30000;

/** One quiet build per pack per 30 seconds, however many files land in that window. */
function quietTaskId(packId, nowMs) {
  return `q-${packId}-${Math.floor(nowMs / QUIET_MS)}`;
}

/** One fast build per exact set of uploaded objects. */
function fastTaskId(packId, generations) {
  const hash = crypto.createHash('sha1').update(generations.join(',')).digest('hex').slice(0, 16);
  return `f-${packId}-${hash}`;
}

function isQuiet(newestUpdatedMs, nowMs) {
  return nowMs - newestUpdatedMs >= QUIET_MS;
}

/** pack.json lists the stickers and every one of them has arrived. */
function fastPathReady(manifest) {
  return manifest.listed && manifest.errors.length === 0 && manifest.stickers.length > 0;
}

module.exports = { quietTaskId, fastTaskId, isQuiet, fastPathReady, QUIET_MS };
