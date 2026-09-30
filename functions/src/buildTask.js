'use strict';

const crypto = require('crypto');
const { PUBLIC_PREFIX, BUILD_RETRY, publicPackBase } = require('./config');
const { slugify, parseLibraryPath, isImageName, folderKey, libraryPrefix, REPORT_FILE } = require('./library');
const { parsePackManifest } = require('./manifest');
const { fastPathReady, mustWait, quietTaskId, QUIET_DELAY_S, FAST_DELAY_S } = require('./readiness');
const { buildPackFromFiles } = require('./build');
const { renderReport } = require('./report');
const { scheduleQuietBuild } = require('./events');

/** The folder is still changing, or another build of the pack is running: Cloud Tasks retries the build later. */
class NotQuietYet extends Error {}

const RECORD_KEYS_COMPARED = ['name', 'names', 'langs', 'tags', 'keywords', 'order', 'coverTiles'];
/** Versions kept in public/: phones mid-download, or on a catalog a publish or two behind, still finish. */
const KEEP_VERSIONS = 3;
/** Outcomes of a build that looked at its folder: each ends with a publish request. */
const LOOKED = new Set(['published', 'updated', 'restored', 'unchanged', 'failed', 'collision', 'removed', 'empty', 'parked']);
/** The files of a published version, in its public/packs/<id>/v<n>-<hash8>/ folder. */
const OUTPUT_FILES = [
  ['cover-s.webp', 'coverS', 'image/webp'],
  ['cover-l.webp', 'coverL', 'image/webp'],
  ['pack.zip', 'zip', 'application/zip'],
];

const same = (a, b) => JSON.stringify(a) === JSON.stringify(b);
const publicPrefix = (packId) => `${PUBLIC_PREFIX}packs/${packId}/`;
const snapshot = (entries) => entries.map((e) => `${e.file}#${e.generation}`).sort().join('\n');

async function packEntries(prefix, deps) {
  return (await deps.listFolder(prefix)).filter((e) => parseLibraryPath(e.name).kind === 'pack');
}

/** The version folder of a path under public/packs/<id>/: v<n>-<hash8>/ (v<n>/ before content hashes). */
function versionFolder(packId, path) {
  const match = /^v(\d+)(?:-([0-9a-f]{8}))?\//.exec(path.slice(publicPrefix(packId).length));
  return match ? { name: match[0], version: Number(match[1]), hash: match[2] || null } : null;
}

function versionFolders(packId, paths) {
  const folders = new Map();
  for (const folder of paths.map((p) => versionFolder(packId, p)).filter(Boolean)) folders.set(folder.name, folder);
  return [...folders.values()];
}

async function deleteOldVersions(packId, version, deps) {
  const paths = await deps.listPaths(publicPrefix(packId));
  const old = paths.filter((p) => {
    const folder = versionFolder(packId, p);
    return folder && folder.version <= version - KEEP_VERSIONS;
  });
  await Promise.all(old.map((p) => deps.deletePath(p)));
}

/** Other folders recorded for this pack id that still hold pack files: one of them may be the pack's new home. */
async function otherFoldersWithFiles(prefix, folders, deps) {
  const others = [];
  for (const [key, f] of Object.entries(folders)) {
    if (!f.folder || libraryPrefix(f.category, f.folder) === prefix) continue;
    if ((await packEntries(libraryPrefix(f.category, f.folder), deps)).length) {
      others.push({ key, category: f.category, folder: f.folder });
    }
  }
  return others;
}

/**
 * Builds the pack in library/<folder>/ (library/<category>/<folder>/ in the older layout) and publishes it when it
 * passes. Safe to run any number of times, in any order: one build per pack id runs at a time (a lease in
 * builds/<packId>), the record only moves on from the version the build started from, and every version's files sit
 * in a folder named after their content.
 *
 * Quiet builds, scheduled by library events, are the ones every change waits for: while the folder is still
 * changing they wait by retrying. Fast builds, scheduled by the pack.json that completes a new pack, only save that
 * wait: they stop whenever they can't build right away, because the folder's quiet build always follows.
 *
 * @param {{category?: string|null, folder: string, quiet?: boolean}} task
 * @param {object} deps the clock, the bucket, the store (packs/, builds/, categories/) and the queue: deps.js in the
 *   cloud, test/fakes.js in tests
 * @param {{holder?: string}} [options] who holds the pack's lease: the Cloud Tasks task id, so a retry of a task
 *   whose instance died mid-build takes its lease straight back
 */
async function runBuild({ category = null, folder, quiet = false }, deps, { holder = crypto.randomUUID() } = {}) {
  const now = deps.now();
  const prefix = libraryPrefix(category, folder);
  const job = { category, folder, quiet, now, nowMs: now.getTime(), prefix, packId: slugify(folder), key: folderKey(prefix) };
  const { store } = deps;
  // A quiet build clears its folder's pending flag before it looks at the folder: any event from here on schedules
  // another quiet build, so no change can slip between this listing and the next build.
  if (quiet) await store.clearPending(job.packId, job.key);
  job.wait = async (reason) => {
    if (!quiet) return { outcome: 'deferred', packId: job.packId, reason };
    // Waiting, this build is the folder's pending one again, unless an event scheduled a newer one meanwhile.
    const stillMine = await store.claimPending(job.packId, job.key, { category, folder }, job.nowMs);
    if (!stillMine) return { outcome: 'superseded', packId: job.packId, reason };
    throw new NotQuietYet(`${prefix}: ${reason}`);
  };

  if (!(await store.acquireLease(job.packId, holder, job.nowMs))) {
    return job.wait(`another build of ${job.packId} is running`);
  }
  let result;
  try {
    result = await buildLocked(job, deps);
  } finally {
    await store.releaseLease(job.packId, holder);
  }
  const { rebuild = [], ...outcome } = result;
  for (const other of rebuild) {
    await scheduleQuietBuild(deps, { packId: job.packId, ...other, nowMs: job.nowMs, delaySeconds: FAST_DELAY_S });
  }
  // Every build that looked at its folder asks for a publish, which skips a catalog identical to the live one. That
  // also covers a retry whose first attempt changed the pack but died before asking.
  if (LOOKED.has(outcome.outcome)) await deps.enqueuePublish();
  return outcome;
}

async function buildLocked(job, deps) {
  const { store } = deps;
  const { category, folder, quiet, now, nowMs, prefix, packId, key } = job;
  const reportPath = `${prefix}${REPORT_FILE}`;
  const [live, folders, entries] = await Promise.all([store.readPack(packId), store.readFolders(packId), packEntries(prefix, deps)]);
  const lastEventMs = folders[key] ? folders[key].lastEventMs : null;

  if (entries.length === 0) {
    if (!quiet) return job.wait('the folder is empty');
    if (mustWait({ fastReady: false, newestObjectMs: 0, lastEventMs, nowMs })) return job.wait('files are still being deleted');
    if (!(live && live.source && live.source.folder === prefix && live.status !== 'removed')) return { outcome: 'empty', packId };
    await store.mergePack(packId, { status: 'removed', removedAt: now });
    // The pack may live on in another folder with the same name (moved out of an older category folder, say): it
    // builds next.
    return { outcome: 'removed', packId, rebuild: await otherFoldersWithFiles(prefix, folders, deps) };
  }

  // A file replaced, added or deleted while the build reads the folder would mix two uploads. The event that changed
  // it has scheduled another quiet build, which sees the settled folder.
  const changed = async () => snapshot(await packEntries(prefix, deps)) !== snapshot(entries);
  const read = async (reader) => {
    try {
      return { value: await reader() };
    } catch (err) {
      if (await changed()) return { changed: true };
      throw err;
    }
  };
  const manifestEntry = entries.find((e) => e.file === 'pack.json');
  const manifestRead = await read(async () => (manifestEntry ? deps.readText(manifestEntry) : null));
  if (manifestRead.changed) return job.wait('files changed while the build read them');
  const manifestText = manifestRead.value;
  const imageNames = entries.map((e) => e.file).filter(isImageName);
  const quick = parsePackManifest(manifestText, imageNames, { folder });
  // Only a pack that was never published skips the quiet wait. An update always waits for its folder to settle, so
  // a half-replaced set of stickers never goes live, whatever order the files arrive in.
  const fastReady = !(live && live.version) && fastPathReady(quick, entries);
  if (!quiet && !fastReady) return job.wait('only a complete new pack builds without the quiet wait');
  const newest = Math.max(...entries.map((e) => e.updatedMs));
  if (mustWait({ fastReady, newestObjectMs: newest, lastEventMs, nowMs })) return job.wait('files are still arriving');

  const liveVersion = live && live.status === 'live' ? live.version : null;
  if (quick.adult) {
    // After Dark packs are never built for the Google Play catalog: nothing of them reaches public/.
    if (liveVersion && live.source && live.source.folder === prefix) await store.mergePack(packId, { status: 'removed', removedAt: now });
    await deps.saveText(reportPath, renderReport({ parked: true, name: quick.name, at: now }));
    return { outcome: 'parked', packId };
  }
  const categories = [...(await store.categories()).entries()].map(([id, c]) => ({ id, ...c }));
  const categoryName = (id) => {
    const c = categories.find((x) => x.id === id);
    return (c && c.names && c.names.en) || id;
  };

  if (live && live.source && live.source.folder !== prefix && live.status !== 'removed') {
    if ((await packEntries(live.source.folder, deps)).length) {
      const errors = [`The id "${packId}" is already used by the folder ${live.source.folder}; rename this folder.`];
      await deps.saveText(reportPath, renderReport({ ok: false, name: folder, liveVersion: null, errors, notes: [], at: now }));
      return { outcome: 'collision', packId };
    }
  }

  const downloads = await read(() =>
    Promise.all(
      entries
        .filter((e) => e.file !== 'pack.json')
        .map(async (e) => ({ name: e.file, buffer: isImageName(e.file) ? await deps.readBuffer(e) : Buffer.alloc(0) }))
    )
  );
  if (downloads.changed || (await changed())) return job.wait('files changed while the build read them');
  const files = downloads.value;
  const published = await deps.listPaths(publicPrefix(packId));

  // A removed pack that comes back is a new pack: its old kind (static or animated) no longer binds it, but its
  // version number still only goes up.
  const previous =
    live && live.version
      ? { version: live.version, contentHash: live.contentHash, animated: live.status === 'live' ? live.animated : null }
      : null;
  const result = await buildPackFromFiles({
    packId,
    folder,
    folderTags: category ? [category] : [],
    files,
    manifestText,
    categories,
    live: previous,
    published: versionFolders(packId, published),
  });

  if (!result.ok) {
    await store.mergePack(packId, {
      build: { status: 'failed', errors: result.errors, notes: result.notes, at: now },
      // A record with no source yet (none at all, or only stats) learns which pack and folder this is.
      ...(live && live.source ? {} : { id: packId, status: 'failed', name: folder, source: { folder: prefix, files: entries.length } }),
    });
    await deps.saveText(
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
    !(live.build && live.build.status === 'ok') ||
    RECORD_KEYS_COMPARED.some((k) => !same(live[k], result.record[k])) ||
    !same((live.stickers || []).map((s) => [s.emojis, s.text]), result.record.stickers.map((s) => [s.emojis, s.text]));

  const base = publicPackBase(packId, result.version, result.contentHash);
  // An unchanged pack whose files left public/ (a wiped folder) gets them back: same bytes, same path.
  const restore = result.unchanged && !OUTPUT_FILES.every(([name]) => published.includes(`${base}${name}`));
  if (restore) result.notes.push('The published files were missing from public/: written again.');
  if (!result.unchanged || restore) {
    await Promise.all(OUTPUT_FILES.map(([name, key, type]) => deps.savePublic(`${base}${name}`, result.outputs[key], type)));
  }
  if (!result.unchanged || recordChanged) {
    const doc = {
      ...result.record,
      id: packId,
      status: 'live',
      source: { folder: prefix, files: entries.length },
      build: { status: 'ok', errors: [], notes: result.notes, at: now },
      // A pack that comes back after its removal counts as new (Home's new-pack boost).
      publishedAt: liveVersion && live.publishedAt ? live.publishedAt : now,
      updatedAt: result.unchanged && live && live.updatedAt ? live.updatedAt : now,
    };
    // The record only moves on from what this build started from; mergeFields replaces these fields whole and leaves
    // the console's pin/hidden and the weekly stats alone.
    const expected = { version: live && live.version, contentHash: live && live.contentHash, status: live && live.status };
    if (!(await store.commitPack(packId, expected, doc))) return job.wait(`packs/${packId} changed during the build`);
    if (!result.unchanged) await deleteOldVersions(packId, result.version, deps);
  }

  await deps.saveText(
    reportPath,
    renderReport({
      ok: true,
      unchanged: result.unchanged,
      name: result.record.name,
      version: result.version,
      count: result.count,
      animated: result.animated,
      categories: result.categories.map(categoryName),
      liveVersion,
      errors: [],
      notes: result.notes,
      at: now,
    })
  );
  const outcome = !result.unchanged ? 'published' : recordChanged ? 'updated' : restore ? 'restored' : 'unchanged';
  return { outcome, packId, version: result.version };
}

/** The report of a build that kept failing for a reason the pipeline didn't expect. */
async function reportCrash({ category, folder }, err, deps) {
  const live = await deps.store.readPack(slugify(folder));
  const errors = [
    `The build failed ${BUILD_RETRY.maxAttempts} times with an unexpected error (${err.message}). ` +
      'Upload any file of the folder again to retry.',
  ];
  await deps.saveText(
    `${libraryPrefix(category, folder)}${REPORT_FILE}`,
    renderReport({
      ok: false,
      name: (live && live.name) || folder,
      liveVersion: live && live.status === 'live' ? live.version : null,
      errors,
      notes: [],
      at: deps.now(),
    })
  );
}

/**
 * One Cloud Tasks attempt of a build (index.js answers NotQuietYet with a 503, which Cloud Tasks retries with
 * backoff). On the last attempt a wait moves to a fresh task, so a long upload never runs out of retries, and an
 * unexpected error still leaves the author a report.
 *
 * @param {{category?: string|null, folder: string, quiet?: boolean}} task
 * @param {{retryCount?: number, taskId?: string}} attempt
 * @param {object} deps see runBuild
 */
async function runBuildTask(task, { retryCount = 0, taskId } = {}, deps) {
  const lastAttempt = retryCount >= BUILD_RETRY.maxAttempts - 1;
  try {
    return await runBuild(task, deps, taskId ? { holder: taskId } : {});
  } catch (err) {
    if (!lastAttempt) throw err;
    const packId = slugify(task.folder);
    if (err instanceof NotQuietYet) {
      const { category, folder } = task;
      try {
        await deps.enqueueBuild({ category, folder, quiet: true, delaySeconds: QUIET_DELAY_S, id: quietTaskId(packId, deps.now().getTime()) });
      } catch (enqueueErr) {
        await deps.store.clearPending(packId, folderKey(libraryPrefix(category, folder)));
        throw enqueueErr;
      }
      return { outcome: 'rescheduled', packId };
    }
    await reportCrash(task, err, deps).catch(() => {});
    throw err;
  }
}

module.exports = { runBuild, runBuildTask, NotQuietYet };
