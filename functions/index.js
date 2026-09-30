'use strict';

/**
 * Love Stickers catalog pipeline (codebase "stickermaker"). Deployed as stickermaker-<name>.
 *
 * library/<Pack Name>/ in bucket play-console-f33dd-stickermaker (pack.json: tags, which place the pack in the
 * categories of library/_categories.json, and languages; library/_tags.json holds the tags' search words)
 *   → onLibraryUpload / onLibraryDelete schedule buildPack
 *   → buildPack publishes public/packs/<id>/v<n>-<hash8>/ + Firestore packs/<id> + the folder's _report.txt
 *   → publishCatalog writes public/catalog/v<k>.json.gz + catalog/meta, which the app listens to.
 */

const { setGlobalOptions } = require('firebase-functions/v2');
const { onObjectFinalized, onObjectDeleted } = require('firebase-functions/v2/storage');
const { onTaskDispatched } = require('firebase-functions/v2/tasks');
const { onDocumentWritten } = require('firebase-functions/v2/firestore');
const { onSchedule } = require('firebase-functions/v2/scheduler');
const { HttpsError } = require('firebase-functions/v2/https');
const { defineString } = require('firebase-functions/params');
const logger = require('firebase-functions/logger');
const { BUCKET, DATABASE, REGION, BUILD_RETRY } = require('./src/config');

const GA4_PROPERTY_ID = defineString('GA4_PROPERTY_ID', {
  default: '',
  description: 'Analytics property id for weekly pack popularity (empty: ranking uses pin, order and newest)',
});
const PUBLIC_URL_TEMPLATE = defineString('PUBLIC_URL_TEMPLATE', {
  default: '',
  description: 'URL template for public files, e.g. https://cdn.example.com/{rawPath} (empty: Firebase Storage)',
});

setGlobalOptions({ region: REGION, minInstances: 0 });

/**
 * Library uploads and deletes. Retried when they fail (a failed enqueue clears its pending flag first): recording an
 * event twice only restamps its time, and a task id or the pending flag keeps a build from being scheduled twice.
 */
async function onLibraryEvent(event, kind) {
  const { handleLibraryEvent } = require('./src/events');
  const { libraryDeps } = require('./src/deps');
  await handleLibraryEvent(event.data.name, kind, Date.now(), libraryDeps());
}

const onLibraryUpload = onObjectFinalized({ bucket: BUCKET, memory: '256MiB', maxInstances: 5, retry: true }, (event) =>
  onLibraryEvent(event, 'finalized')
);

const onLibraryDelete = onObjectDeleted({ bucket: BUCKET, memory: '256MiB', maxInstances: 3, retry: true }, (event) =>
  onLibraryEvent(event, 'deleted')
);

const buildPack = onTaskDispatched(
  {
    retryConfig: BUILD_RETRY,
    rateLimits: { maxConcurrentDispatches: 3 },
    memory: '2GiB',
    cpu: 2,
    // One build per instance: a build holds up to 30 × 10 MB of sources plus decoded frames.
    concurrency: 1,
    timeoutSeconds: 540,
    maxInstances: 3,
  },
  async (request) => {
    const { runBuildTask, NotQuietYet } = require('./src/buildTask');
    const { buildDeps } = require('./src/deps');
    try {
      const result = await runBuildTask(request.data, { retryCount: request.retryCount, taskId: request.id }, buildDeps());
      logger.info('build', result);
    } catch (err) {
      if (err instanceof NotQuietYet) {
        // 503: Cloud Tasks retries shortly; an expected wait, not an error in the logs.
        logger.info(err.message);
        throw new HttpsError('unavailable', err.message);
      }
      logger.error('build failed', { data: request.data, error: err.stack || String(err) });
      throw err;
    }
  }
);

const publishCatalog = onTaskDispatched(
  {
    retryConfig: { maxAttempts: 5, minBackoffSeconds: 10 },
    rateLimits: { maxConcurrentDispatches: 1 },
    memory: '512MiB',
    maxInstances: 1,
  },
  async () => {
    const { runPublish } = require('./src/publishTask');
    logger.info('publish', await runPublish({ urlOverride: PUBLIC_URL_TEMPLATE.value() }));
  }
);

/** Console edits of pin or hidden (or a deleted pack document) republish the catalog. */
const onPackEdited = onDocumentWritten(
  { document: 'packs/{packId}', database: DATABASE, memory: '256MiB', maxInstances: 2 },
  async (event) => {
    const before = event.data.before.exists ? event.data.before.data() : null;
    const after = event.data.after.exists ? event.data.after.data() : null;
    const deleted = before && !after;
    const editorial = before && after && (before.pin !== after.pin || before.hidden !== after.hidden);
    if (deleted || editorial) {
      const { enqueuePublish } = require('./src/queue');
      await enqueuePublish();
    }
  }
);

const weeklyStats = onSchedule(
  { schedule: 'every monday 04:00', timeZone: 'Etc/UTC', memory: '512MiB', maxInstances: 1, retryCount: 1 },
  async () => {
    const { runWeeklyStats } = require('./src/statsTask');
    logger.info('weekly stats', await runWeeklyStats({ propertyId: GA4_PROPERTY_ID.value() }));
  }
);

exports.stickermaker = { onLibraryUpload, onLibraryDelete, buildPack, publishCatalog, onPackEdited, weeklyStats };
