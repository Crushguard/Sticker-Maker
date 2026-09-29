'use strict';

const { REGION } = require('./config');
const { functions } = require('./firebase');

const BUILD_FUNCTION = 'stickermaker-buildPack';
const PUBLISH_FUNCTION = 'stickermaker-publishCatalog';
const PUBLISH_DELAY_S = 2;

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

/** A build of library/<category>/<folder>/; quiet builds clear the pack's pending flag when they start. */
function enqueueBuild({ category, folder, quiet = false, delaySeconds, id }) {
  return enqueue(BUILD_FUNCTION, { category, folder, quiet }, { scheduleDelaySeconds: delaySeconds, id });
}

/**
 * Catalog publishes are never deduplicated: an id that already ran stays blocked in Cloud Tasks, which would drop a
 * later request whose changes the earlier run never saw. Publishes run one at a time (maxConcurrentDispatches 1)
 * and skip themselves when the catalog is unchanged, so extra requests cost a few reads.
 */
function publishTaskOptions() {
  return { scheduleDelaySeconds: PUBLISH_DELAY_S };
}

function enqueuePublish() {
  return enqueue(PUBLISH_FUNCTION, {}, publishTaskOptions());
}

module.exports = { enqueueBuild, enqueuePublish, publishTaskOptions, BUILD_FUNCTION, PUBLISH_FUNCTION };
