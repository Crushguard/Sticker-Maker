'use strict';

const { parseLibraryPath, isImageName, folderKey } = require('./library');
const { parsePackManifest } = require('./manifest');
const { quietTaskId, fastTaskId, fastPathReady, QUIET_DELAY_S, FAST_DELAY_S } = require('./readiness');

/**
 * Makes sure a quiet build of a folder is scheduled. With `event`, it also records the event's time (the folder's
 * quiet period restarts). Nothing is enqueued while the folder already has a quiet build pending, one that hasn't
 * looked at the folder yet. A failed enqueue clears the flag again, so the retried event, or the next one,
 * schedules the build.
 *
 * @returns {Promise<boolean>} whether a build was enqueued
 */
async function scheduleQuietBuild(deps, { packId, key, category, folder, nowMs, delaySeconds = QUIET_DELAY_S, event = false }) {
  const meta = { category, folder };
  const claimed = event
    ? await deps.store.recordEvent(packId, key, meta, nowMs)
    : await deps.store.claimPending(packId, key, meta, nowMs);
  if (!claimed) return false;
  try {
    await deps.enqueueBuild({ category, folder, quiet: true, delaySeconds, id: quietTaskId(packId, nowMs) });
  } catch (err) {
    await deps.store.clearPending(packId, key);
    throw err;
  }
  return true;
}

/**
 * What an upload or delete in the library does. Every pack-file event records its time in its folder's entry and
 * schedules the folder's quiet build unless one is pending. The pack.json that completes a new pack also schedules
 * a build straight away. Storage, the store and the queue come in as deps so the decisions are testable.
 *
 * @param {string} objectName
 * @param {'finalized'|'deleted'} kind
 * @param {number} nowMs
 * @param {{listFolder: Function, readText: Function, enqueueBuild: Function, store: object, syncCategories: Function}} deps
 */
async function handleLibraryEvent(objectName, kind, nowMs, deps) {
  const parsed = parseLibraryPath(objectName);
  if (parsed.kind === 'categories') {
    if (kind === 'finalized') await deps.syncCategories();
    return;
  }
  if (parsed.kind !== 'pack') return;

  const { category, folder, packId, prefix, file } = parsed;
  await scheduleQuietBuild(deps, { packId, key: folderKey(prefix), category, folder, nowMs, event: true });

  // The export uploads pack.json last: when it lands on a complete listing, the pack can build without the quiet
  // wait. The build itself keeps that shortcut for packs that were never published.
  if (kind !== 'finalized' || file !== 'pack.json') return;
  const entries = await deps.listFolder(prefix);
  const manifestEntry = entries.find((e) => e.file === 'pack.json');
  if (!manifestEntry) return;
  const manifest = parsePackManifest(
    await deps.readText(manifestEntry),
    entries.map((e) => e.file).filter(isImageName),
    { folder, category, categoryIds: new Set(), defaultEmojis: [] }
  );
  if (!fastPathReady(manifest, entries)) return;
  const listed = new Set(manifest.stickers.map((s) => s.file));
  const generations = entries
    .filter((e) => listed.has(e.file) || e.file === 'pack.json')
    .map((e) => `${e.file}#${e.generation}`)
    .sort();
  await deps.enqueueBuild({ category, folder, delaySeconds: FAST_DELAY_S, id: fastTaskId(packId, generations) });
}

module.exports = { handleLibraryEvent, scheduleQuietBuild };
