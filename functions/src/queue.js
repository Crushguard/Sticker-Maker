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

/**
 * Quiet builds share one id per pack per 30 s: in production that task runs 45 s after the window opened, after
 * every event of its window. The Cloud Tasks emulator ignores delays and runs tasks at once, so there each request
 * gets its own id (builds are idempotent).
 */
function enqueueBuild({ category, folder, delaySeconds, id }) {
  const taskId =
    process.env.FUNCTIONS_EMULATOR === 'true' && id.startsWith('q-')
      ? `${id}-${Math.random().toString(36).slice(2, 10)}`
      : id;
  return enqueue(BUILD_FUNCTION, { category, folder }, { scheduleDelaySeconds: delaySeconds, id: taskId });
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
