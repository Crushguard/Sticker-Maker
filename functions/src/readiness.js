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

/**
 * Whether a build must wait for the folder to settle: until 30 s after the later of its newest object and its
 * last upload or delete event (deletions leave no timestamp on the remaining files). A pack.json listing a
 * complete set needs no wait.
 */
function mustWait({ fastReady, newestObjectMs, lastEventMs, nowMs }) {
  if (fastReady) return false;
  return !isQuiet(Math.max(newestObjectMs, lastEventMs || 0), nowMs);
}

module.exports = { quietTaskId, fastTaskId, isQuiet, fastPathReady, mustWait, QUIET_MS };
