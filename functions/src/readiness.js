'use strict';

const crypto = require('crypto');

const QUIET_MS = 30000;

/**
 * A quiet build's task id: unique per request. Debouncing is done by the builds/<packId> record (one pending
 * quiet build per pack), never by task id, because an id that already ran stays blocked and would swallow a
 * later event.
 */
function quietTaskId(packId, nowMs) {
  return `q-${packId}-${nowMs.toString(36)}-${crypto.randomBytes(4).toString('hex')}`;
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
