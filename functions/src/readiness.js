'use strict';

const crypto = require('crypto');
const { isImageName } = require('./library');

/** A folder is quiet this long after its newest object and its last upload or delete. */
const QUIET_MS = 30000;
/** A quiet build is scheduled this long after the event: by then most uploads are done. */
const QUIET_DELAY_S = 45;
/** A fast build (and a build of a folder whose pack just left another) runs this soon. */
const FAST_DELAY_S = 2;

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

/**
 * A folder the fast path may build: pack.json lists stickers that have all arrived, and it was written after every
 * image, as the export writes it last. Anything uploaded after pack.json means the upload isn't over.
 *
 * @param {object} manifest parsePackManifest's result
 * @param {{file: string, updatedMs: number}[]} entries the folder's objects
 */
function fastPathReady(manifest, entries) {
  if (!manifest.listed || manifest.errors.length > 0 || manifest.stickers.length === 0) return false;
  const manifestEntry = entries.find((e) => e.file === 'pack.json');
  return !!manifestEntry && entries.every((e) => !isImageName(e.file) || e.updatedMs <= manifestEntry.updatedMs);
}

/**
 * Whether a build must wait for the folder to settle: until 30 s after the later of its newest object and its
 * last upload or delete event (deletions leave no timestamp on the remaining files). A folder the fast path may
 * build needs no wait.
 */
function mustWait({ fastReady, newestObjectMs, lastEventMs, nowMs }) {
  if (fastReady) return false;
  return !isQuiet(Math.max(newestObjectMs, lastEventMs || 0), nowMs);
}

module.exports = { quietTaskId, fastTaskId, isQuiet, fastPathReady, mustWait, QUIET_MS, QUIET_DELAY_S, FAST_DELAY_S };
