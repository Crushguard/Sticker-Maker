'use strict';

const crypto = require('crypto');
const { REGION } = require('./config');
const { functions } = require('./firebase');

const BUILD_FUNCTION = 'stickermaker-buildPack';
const PUBLISH_FUNCTION = 'stickermaker-publishCatalog';
const PUBLISH_DELAY_S = 2;
/** Publish requests in one window become one publish, run this long after the window closes. */
const PUBLISH_WINDOW_S = 20;
const PUBLISH_AFTER_WINDOW_S = 5;

/** Enqueues a task; a task with the same id already queued or just run is the expected duplicate, not an error. */
async function enqueue(functionName, data, options) {
  try {
    await functions().taskQueue(`locations/${REGION}/functions/${functionName}`).enqueue(data, options);
    return true;
  } catch (err) {
    if (String(err.code || '').endsWith('task-already-exists')) return false;
    throw err;
  }
}

/**
 * A build of library/<folder>/ (library/<category>/<folder>/ in the older layout); quiet builds clear the pack's
 * pending flag when they start.
 */
function enqueueBuild({ category = null, folder, quiet = false, delaySeconds, id }) {
  return enqueue(BUILD_FUNCTION, { category, folder, quiet }, { scheduleDelaySeconds: delaySeconds, id });
}

/**
 * Catalog publishes are coalesced: every request made in one 20 s window is the same task, which runs 5 s after the
 * window closes, so it sees every change requested in it. A bulk upload of 100 packs then publishes a few catalogs,
 * not 100 (every online phone downloads each one). An id that already ran stays blocked in Cloud Tasks, but no
 * request can name a window whose task has run: it would have to be made after the window closed.
 *
 * The Cloud Tasks emulator ignores schedule times and would run a window's task at its first request, so there
 * every request is its own task, as before. Publishes run one at a time (maxConcurrentDispatches 1) and skip
 * themselves when the catalog is unchanged.
 */
function publishTaskOptions(nowMs = Date.now(), emulated = process.env.FUNCTIONS_EMULATOR === 'true') {
  if (emulated) return { scheduleDelaySeconds: PUBLISH_DELAY_S };
  const windowMs = PUBLISH_WINDOW_S * 1000;
  const closesMs = (Math.floor(nowMs / windowMs) + 1) * windowMs;
  // A hashed prefix: Cloud Tasks advises against names that grow in sequence, like timestamps.
  const hash = crypto.createHash('sha1').update(String(closesMs)).digest('hex').slice(0, 8);
  return { id: `publish-${hash}-${closesMs}`, scheduleTime: new Date(closesMs + PUBLISH_AFTER_WINDOW_S * 1000) };
}

function enqueuePublish() {
  return enqueue(PUBLISH_FUNCTION, {}, publishTaskOptions());
}

module.exports = { enqueueBuild, enqueuePublish, publishTaskOptions, BUILD_FUNCTION, PUBLISH_FUNCTION };
