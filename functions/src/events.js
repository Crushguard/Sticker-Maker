'use strict';

const { parseLibraryPath, isImageName } = require('./library');
const { parsePackManifest } = require('./manifest');
const { quietTaskId, fastTaskId, fastPathReady } = require('./readiness');

const QUIET_DELAY_S = 45;
const FAST_DELAY_S = 2;

/**
 * What an upload or delete in the library does. Every pack-file event records its time, and schedules a quiet
 * build unless one is already pending for the pack; the upload that completes a listing pack.json also schedules
 * a build straight away. Storage, Firestore and the queue come in as deps so the decisions are testable.
 *
 * @param {string} objectName
 * @param {'finalized'|'deleted'} kind
 * @param {number} nowMs
 * @param {{listFolder: Function, readText: Function, enqueueBuild: Function, recordEvent: Function, syncCategories: Function}} deps
 */
async function handleLibraryEvent(objectName, kind, nowMs, deps) {
  const parsed = parseLibraryPath(objectName);
  if (parsed.kind === 'categories') {
    if (kind === 'finalized') await deps.syncCategories();
    return;
  }
  if (parsed.kind !== 'pack') return;

  const { category, folder, packId, prefix } = parsed;
  if (await deps.recordEvent(packId, { category, folder }, nowMs)) {
    await deps.enqueueBuild({ category, folder, quiet: true, delaySeconds: QUIET_DELAY_S, id: quietTaskId(packId, nowMs) });
  }
  if (kind !== 'finalized') return;

  const entries = await deps.listFolder(prefix);
  const manifestEntry = entries.find((e) => e.file === 'pack.json');
  if (!manifestEntry) return;
  const manifest = parsePackManifest(
    await deps.readText(manifestEntry),
    entries.map((e) => e.file).filter(isImageName),
    { folder, category, categoryIds: new Set(), defaultEmojis: [] }
  );
  if (!fastPathReady(manifest)) return;
  const listed = new Set(manifest.stickers.map((s) => s.file));
  const generations = entries
    .filter((e) => listed.has(e.file) || e.file === 'pack.json')
    .map((e) => `${e.file}#${e.generation}`)
    .sort();
  await deps.enqueueBuild({ category, folder, delaySeconds: FAST_DELAY_S, id: fastTaskId(packId, generations) });
}

module.exports = { handleLibraryEvent };
