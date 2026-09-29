'use strict';

const { LIBRARY_PREFIX, PUBLIC_PREFIX, publicPackBase } = require('./config');
const { slugify, parseLibraryPath, isImageName, REPORT_FILE } = require('./library');
const { parsePackManifest } = require('./manifest');
const { fastPathReady, mustWait } = require('./readiness');
const { buildPackFromFiles } = require('./build');
const { renderReport } = require('./report');
const { db, bucket, listFolder, readBuffer, readText, savePublic, saveText } = require('./firebase');
const { enqueuePublish } = require('./queue');

/** Thrown while files are still arriving: Cloud Tasks retries the build a little later. */
class NotQuietYet extends Error {}

const RECORD_KEYS_COMPARED = ['name', 'names', 'category', 'alsoIn', 'lang', 'tags', 'order', 'coverTiles'];
const same = (a, b) => JSON.stringify(a) === JSON.stringify(b);

async function categoriesById() {
  const snap = await db().collection('categories').get();
  return new Map(snap.docs.map((d) => [d.id, d.data()]));
}

async function folderHasPackFiles(prefix) {
  const entries = await listFolder(prefix);
  return entries.some((e) => parseLibraryPath(e.name).kind === 'pack');
}

/** Drops published versions older than the previous one: phones mid-download of v(n-1) still finish. */
async function deleteOldVersions(packId, version) {
  const [files] = await bucket().getFiles({ prefix: `${PUBLIC_PREFIX}packs/${packId}/` });
  await Promise.all(
    files
      .filter((f) => {
        const match = /\/v(\d+)\//.exec(f.name);
        return match && Number(match[1]) < version - 1;
      })
      .map((f) => f.delete({ ignoreNotFound: true }))
  );
}

/**
 * Builds the pack in library/<category>/<folder>/ and publishes it when it passes. Safe to run any number of
 * times: the same files give the same fingerprint and publish nothing new.
 */
async function runBuild({ category, folder }, { now = new Date() } = {}) {
  const packId = slugify(folder);
  const prefix = `${LIBRARY_PREFIX}${category}/${folder}/`;
  const reportPath = `${prefix}${REPORT_FILE}`;
  const packRef = db().collection('packs').doc(packId);
  const [liveSnap, entriesAll, eventsSnap] = await Promise.all([
    packRef.get(),
    listFolder(prefix),
    db().collection('builds').doc(packId).get(),
  ]);
  const lastEvent = eventsSnap.exists ? eventsSnap.data().lastEventAt : null;
  const lastEventMs = lastEvent && typeof lastEvent.toMillis === 'function' ? lastEvent.toMillis() : null;
  const live = liveSnap.exists ? liveSnap.data() : null;
  const entries = entriesAll.filter((e) => parseLibraryPath(e.name).kind === 'pack');

  if (entries.length === 0) {
    if (mustWait({ fastReady: false, newestObjectMs: 0, lastEventMs, nowMs: now.getTime() })) {
      throw new NotQuietYet(`${prefix} is still changing`);
    }
    if (live && live.source && live.source.folder === prefix && live.status !== 'removed') {
      await packRef.set({ status: 'removed', removedAt: now }, { merge: true });
      await enqueuePublish();
      return { outcome: 'removed', packId };
    }
    return { outcome: 'empty', packId };
  }

  const manifestEntry = entries.find((e) => e.file === 'pack.json');
  const manifestText = manifestEntry ? await readText(manifestEntry) : null;
  const imageNames = entries.map((e) => e.file).filter(isImageName);
  const quick = parsePackManifest(manifestText, imageNames, { folder, category, categoryIds: new Set(), defaultEmojis: [] });
  const newest = Math.max(...entries.map((e) => e.updatedMs));
  if (mustWait({ fastReady: fastPathReady(quick), newestObjectMs: newest, lastEventMs, nowMs: now.getTime() })) {
    throw new NotQuietYet(`${prefix} is still changing`);
  }

  const categories = await categoriesById();
  const categoryName = (id) => (categories.get(id) && categories.get(id).names && categories.get(id).names.en) || id;
  const liveVersion = live && live.status === 'live' ? live.version : null;

  if (live && live.source && live.source.folder !== prefix && live.status !== 'removed') {
    if (await folderHasPackFiles(live.source.folder)) {
      const errors = [`The id "${packId}" is already used by the folder ${live.source.folder}; rename this folder.`];
      await saveText(reportPath, renderReport({ ok: false, name: folder, liveVersion: null, errors, notes: [], at: now }));
      return { outcome: 'collision', packId };
    }
  }

  const files = await Promise.all(
    entries
      .filter((e) => e.file !== 'pack.json')
      .map(async (e) => ({ name: e.file, buffer: isImageName(e.file) ? await readBuffer(e) : Buffer.alloc(0) }))
  );
  const previous = live && live.version ? { version: live.version, contentHash: live.contentHash, animated: live.animated } : null;
  const result = await buildPackFromFiles({
    packId,
    category,
    folder,
    files,
    manifestText,
    categoryIds: new Set(categories.keys()),
    defaultEmojis: (categories.get(category) && categories.get(category).emojis) || [],
    live: previous,
  });

  if (!result.ok) {
    await packRef.set(
      {
        build: { status: 'failed', errors: result.errors, notes: result.notes, at: now },
        ...(live ? {} : { id: packId, status: 'failed', name: folder, category, source: { folder: prefix, files: entries.length } }),
      },
      { merge: true }
    );
    await saveText(
      reportPath,
      renderReport({ ok: false, name: (live && live.name) || quick.name, liveVersion, errors: result.errors, notes: result.notes, at: now })
    );
    return { outcome: 'failed', packId, errors: result.errors };
  }

  const recordChanged =
    !live ||
    live.status !== 'live' ||
    !live.source ||
    live.source.folder !== prefix ||
    RECORD_KEYS_COMPARED.some((k) => !same(live[k], result.record[k])) ||
    !same((live.stickers || []).map((s) => [s.emojis, s.text]), result.record.stickers.map((s) => [s.emojis, s.text]));

  if (!result.unchanged) {
    const base = publicPackBase(packId, result.version);
    await Promise.all([
      savePublic(`${base}cover-s.webp`, result.outputs.coverS, 'image/webp'),
      savePublic(`${base}cover-l.webp`, result.outputs.coverL, 'image/webp'),
      savePublic(`${base}pack.zip`, result.outputs.zip, 'application/zip'),
    ]);
  }
  if (!result.unchanged || recordChanged) {
    const doc = {
      ...result.record,
      id: packId,
      status: 'live',
      source: { folder: prefix, files: entries.length },
      build: { status: 'ok', errors: [], notes: result.notes, at: now },
      publishedAt: live && live.publishedAt ? live.publishedAt : now,
      updatedAt: result.unchanged && live && live.updatedAt ? live.updatedAt : now,
    };
    // mergeFields replaces these fields whole and leaves the console's pin/hidden and the weekly stats alone.
    await packRef.set(doc, { mergeFields: Object.keys(doc) });
    if (!result.unchanged) await deleteOldVersions(packId, result.version);
    await enqueuePublish();
  }

  await saveText(
    reportPath,
    renderReport({
      ok: true,
      unchanged: result.unchanged,
      name: result.record.name,
      version: result.version,
      count: result.count,
      animated: result.animated,
      category: categoryName(category),
      alsoIn: result.record.alsoIn.map(categoryName),
      liveVersion,
      errors: [],
      notes: result.notes,
      at: now,
    })
  );
  return { outcome: result.unchanged ? (recordChanged ? 'updated' : 'unchanged') : 'published', packId, version: result.version };
}

module.exports = { runBuild, NotQuietYet };
