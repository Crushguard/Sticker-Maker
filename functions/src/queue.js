'use strict';

const { REGION } = require('./config');
const { functions } = require('./firebase');

const BUILD_FUNCTION = 'stickermaker-buildPack';
const PUBLISH_FUNCTION = 'stickermaker-publishCatalog';
const PUBLISH_BUCKET_MS = 5000;
const PUBLISH_DELAY_S = 3;

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

function enqueueBuild({ category, folder, delaySeconds, id }) {
  return enqueue(BUILD_FUNCTION, { category, folder }, { scheduleDelaySeconds: delaySeconds, id });
}

/** At most one catalog publish per 5 seconds, a few seconds out so bursts of changes land in one catalog. */
function enqueuePublish(nowMs = Date.now()) {
  return enqueue(PUBLISH_FUNCTION, {}, {
    scheduleDelaySeconds: PUBLISH_DELAY_S,
    id: `p-${Math.floor(nowMs / PUBLISH_BUCKET_MS)}`,
  });
}

module.exports = { enqueueBuild, enqueuePublish, BUILD_FUNCTION, PUBLISH_FUNCTION };
