'use strict';

/**
 * Love Stickers catalog pipeline (codebase "stickermaker"). Deployed as stickermaker-<name>.
 *
 * library/<category>/<Pack Name>/ in bucket play-console-f33dd-stickermaker
 *   → onLibraryUpload / onLibraryDelete schedule buildPack
 *   → buildPack publishes public/packs/<id>/v<n>/ + Firestore packs/<id> + the folder's _report.txt
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
const { BUCKET, DATABASE, REGION } = require('./src/config');

const GA4_PROPERTY_ID = defineString('GA4_PROPERTY_ID', {
  default: '',
  description: 'Analytics property id for weekly pack popularity (empty: ranking uses pin, order and newest)',
});
const PUBLIC_URL_TEMPLATE = defineString('PUBLIC_URL_TEMPLATE', {
  default: '',
  description: 'URL template for public files, e.g. https://cdn.example.com/{rawPath} (empty: Firebase Storage)',
});

setGlobalOptions({ region: REGION, minInstances: 0 });

const QUIET_PENDING_STALE_MS = 10 * 60 * 1000;

function libraryDeps() {
  const { FieldValue } = require('firebase-admin/firestore');
  const { db, listFolder, readText } = require('./src/firebase');
  const { enqueueBuild } = require('./src/queue');
  const { syncCategories } = require('./src/categoriesTask');
  // builds/<packId>: lastEventAt lets a build wait out deletions too (they leave no timestamp on remaining
  // files); quietPending keeps one quiet build in flight per pack. A flag older than 10 minutes (a task that
  // ran out of retries) no longer blocks.
  const recordEvent = (packId, { category, folder }) =>
    db().runTransaction(async (tx) => {
      const ref = db().collection('builds').doc(packId);
      const snap = await tx.get(ref);
      const data = snap.exists ? snap.data() : {};
      const since = data.quietPendingSince && data.quietPendingSince.toMillis ? data.quietPendingSince.toMillis() : 0;
      const schedule = !data.quietPending || Date.now() - since > QUIET_PENDING_STALE_MS;
      tx.set(
        ref,
        {
          category,
          folder,
          lastEventAt: FieldValue.serverTimestamp(),
          ...(schedule ? { quietPending: true, quietPendingSince: FieldValue.serverTimestamp() } : {}),
        },
        { merge: true }
      );
      return schedule;
    });
  return { listFolder, readText, enqueueBuild, recordEvent, syncCategories };
}

async function onLibraryEvent(event, kind) {
  const { handleLibraryEvent } = require('./src/events');
  await handleLibraryEvent(event.data.name, kind, Date.now(), libraryDeps());
}

const onLibraryUpload = onObjectFinalized({ bucket: BUCKET, memory: '256MiB', maxInstances: 5 }, (event) =>
  onLibraryEvent(event, 'finalized')
);

const onLibraryDelete = onObjectDeleted({ bucket: BUCKET, memory: '256MiB', maxInstances: 3 }, (event) =>
  onLibraryEvent(event, 'deleted')
);

const buildPack = onTaskDispatched(
  {
    retryConfig: { maxAttempts: 8, minBackoffSeconds: 30, maxBackoffSeconds: 60 },
    rateLimits: { maxConcurrentDispatches: 3 },
    memory: '2GiB',
    cpu: 2,
    timeoutSeconds: 540,
    maxInstances: 3,
  },
  async (request) => {
    const { runBuild, NotQuietYet } = require('./src/buildTask');
    try {
      const result = await runBuild(request.data);
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
