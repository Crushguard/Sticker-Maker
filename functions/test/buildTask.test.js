'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { unzipSync, strFromU8 } = require('fflate');
const { World, CATEGORIES } = require('./fakes');
const { shapePng, animated } = require('./helpers');
const { runBuild, runBuildTask, NotQuietYet } = require('../src/buildTask');
const { buildPackFromFiles } = require('../src/build');
const { folderKey } = require('../src/library');
const { BUILD_RETRY } = require('../src/config');

const A = 'library/Love Notes/';
/** Another spelling of the same name: the same pack id. */
const B = 'library/love notes/';
const ID = 'love-notes';
const IN_A = { folder: 'Love Notes' };
const FILES = ['1.png', '2.png', '3.png', 'pack.json'];

const RED = ['#C23359', '#2E9E6B', '#3B6DD4'];
const BLUE = ['#1F4E9E', '#9E1F7A', '#E0A64B'];

const png = (color) => shapePng({ color });
const listingJson = (count, extra = {}) =>
  Buffer.from(JSON.stringify({ ...extra, stickers: Array.from({ length: count }, (_, i) => ({ file: `${i + 1}.png` })) }));

/** Uploads a folder the way the export does: one sticker every stepMs, pack.json (a listing) last. */
async function uploadFolder(world, prefix, colors, { listing = true, stepMs = 200 } = {}) {
  for (const [i, color] of colors.entries()) {
    await world.upload(`${prefix}${i + 1}.png`, await png(color));
    world.tick(stepMs);
  }
  if (listing) await world.upload(`${prefix}pack.json`, listingJson(colors.length));
}

/** The content hash a clean build of these colours gets (no category: every sticker gets ❤️). */
async function hashOf(colors) {
  const files = await Promise.all(colors.map(async (c, i) => ({ name: `${i + 1}.png`, buffer: await png(c) })));
  const result = await buildPackFromFiles({
    packId: ID,
    folder: 'Love Notes',
    files,
    manifestText: listingJson(colors.length).toString('utf8'),
    categories: CATEGORIES,
    live: null,
  });
  return result.contentHash;
}

const versionFolders = (world) => [...new Set(world.paths(`public/packs/${ID}/`).map((p) => p.split('/')[3]))].sort();
const outcomes = (runs) => runs.map((r) => [r.quiet ? 'quiet' : 'fast', r.outcome || r.error.constructor.name]);

async function until(condition) {
  while (!condition()) await new Promise((resolve) => setImmediate(resolve));
}

test('a new pack uploaded with pack.json last goes live from the fast build; its quiet build finds it unchanged', async () => {
  const world = new World();
  await uploadFolder(world, A, RED);
  await world.settle();

  const record = world.store.packs.get(ID);
  assert.equal(record.status, 'live');
  assert.equal(record.version, 1);
  assert.equal(record.contentHash, await hashOf(RED));
  assert.deepEqual(outcomes(world.runs), [['fast', 'published'], ['quiet', 'unchanged']]);
  const base = `public/packs/${ID}/v1-${record.contentHash.slice(0, 8)}/`;
  assert.deepEqual(world.paths(`public/packs/${ID}/`), [`${base}cover-l.webp`, `${base}cover-s.webp`, `${base}pack.zip`]);
  const contents = JSON.parse(strFromU8(unzipSync(new Uint8Array(world.objects.get(`${base}pack.zip`).buffer))['contents.json']));
  assert.equal(contents.version, 1);
  assert.match(world.text(`${A}_report.txt`), /^✅ Love Notes is live and unchanged: version 1/);
  assert.equal(world.publishes, 2);
});

test('re-uploading the stickers of a live pack publishes one complete new version, once the folder is quiet', async () => {
  const world = new World();
  await uploadFolder(world, A, RED);
  await world.settle();
  const before = world.runs.length;
  world.tick(60000);

  // A slow re-upload, one sticker every 10 s, pack.json untouched: every moment in between is a mix of old and new.
  for (const [i, color] of BLUE.entries()) {
    await world.upload(`${A}${i + 1}.png`, await png(color));
    world.tick(10000);
  }
  await world.settle();

  const runs = world.runs.slice(before);
  assert.deepEqual(runs.filter((r) => !r.quiet), [], 'an update schedules no fast build');
  assert.equal(runs.filter((r) => r.outcome === 'published').length, 1);
  const record = world.store.packs.get(ID);
  assert.equal(record.version, 2);
  assert.equal(record.contentHash, await hashOf(BLUE), 'version 2 holds all three new stickers');
});

test('re-exporting a live pack with pack.json last still waits: the fast path is for packs never published', async () => {
  const world = new World();
  await uploadFolder(world, A, RED);
  await world.settle();
  const before = world.runs.length;
  world.tick(60000);

  await uploadFolder(world, A, BLUE);
  await world.settle();

  assert.deepEqual(outcomes(world.runs.slice(before)), [['fast', 'deferred'], ['quiet', 'published']]);
  assert.equal(world.store.packs.get(ID).version, 2);
  assert.equal(world.store.packs.get(ID).contentHash, await hashOf(BLUE));
});

test('two builds of one pack never overlap: a second quiet build waits, a second fast build stops', async () => {
  const world = new World();
  await uploadFolder(world, A, RED);
  world.tick(60000);
  let release;
  const gate = new Promise((resolve) => {
    release = resolve;
  });
  let reads = 0;
  world.hooks.readBuffer = async () => {
    if (reads++ === 0) await gate;
  };

  const first = runBuild({ ...IN_A, quiet: true }, world.deps, { holder: 'task-1' });
  await until(() => reads > 0);
  await assert.rejects(runBuild({ ...IN_A, quiet: true }, world.deps, { holder: 'task-2' }), NotQuietYet);
  assert.equal((await runBuild(IN_A, world.deps, { holder: 'task-3' })).outcome, 'deferred');
  release();

  assert.equal((await first).outcome, 'published');
  assert.equal(world.store.builds.get(ID).lease, undefined, 'the lease is released');
  assert.equal(world.store.builds.get(ID).folders[folderKey(A)].pending, true, 'the waiting build stays scheduled');
});

test('a duplicate delivery of one task cannot mix two builds: each version folder holds one build, one record wins', async () => {
  const world = new World();
  await uploadFolder(world, A, RED);
  world.tick(60000);
  let release;
  const gate = new Promise((resolve) => {
    release = resolve;
  });
  let paused = false;
  world.hooks.savePublic = async () => {
    if (!paused) {
      paused = true;
      await gate;
    }
  };

  // Cloud Tasks delivers at least once: both copies carry the task id, so both hold the lease.
  const first = runBuild({ ...IN_A, quiet: true }, world.deps, { holder: 'dup' });
  await until(() => paused);
  for (const [i, color] of BLUE.entries()) world.put(`${A}${i + 1}.png`, await png(color));
  world.tick(60000);
  const second = await runBuild({ ...IN_A, quiet: true }, world.deps, { holder: 'dup' });
  assert.equal(second.outcome, 'published');
  release();

  await assert.rejects(first, NotQuietYet);
  const record = world.store.packs.get(ID);
  const [red, blue] = [await hashOf(RED), await hashOf(BLUE)];
  assert.equal(record.contentHash, blue);
  // The first copy had begun writing v1 when the second listed public/, so the second took the next number.
  assert.equal(record.version, 2);
  assert.deepEqual(versionFolders(world), [`v1-${red.slice(0, 8)}`, `v2-${blue.slice(0, 8)}`]);
});

test('each new version keeps the two before it in public/ and deletes older ones', async () => {
  const world = new World();
  const palettes = [RED, BLUE, ['#111111', '#222222', '#333333'], ['#444444', '#555555', '#666666']];
  for (const colors of palettes) {
    await uploadFolder(world, A, colors);
    await world.settle();
    world.tick(60000);
  }
  assert.equal(world.store.packs.get(ID).version, 4);
  assert.deepEqual(versionFolders(world).map((f) => f.split('-')[0]), ['v2', 'v3', 'v4']);
});

test('a pack.json deleted while the build reads the folder sends the build back to wait', async () => {
  const world = new World();
  await uploadFolder(world, A, RED);
  world.tick(60000);
  const readText = world.deps.readText;
  world.deps.readText = async (entry) => {
    world.objects.delete(entry.name);
    return readText(entry);
  };

  await assert.rejects(runBuild({ ...IN_A, quiet: true }, world.deps, { holder: 'q' }), NotQuietYet);
});

test('a first build that fails still records who the pack is, even over a record holding only stats', async () => {
  const world = new World();
  world.store.packs.set(ID, { stats: { adds: 7 } });
  await world.upload(`${A}1.png`, await png(RED[0]));
  await world.settle();

  const record = world.store.packs.get(ID);
  assert.equal(record.status, 'failed');
  assert.equal(record.source.folder, A);
  assert.equal(record.stats.adds, 7);
  assert.match(world.text(`${A}_report.txt`), /WhatsApp needs 3 to 30/);
});

test('a file replaced while the build reads the folder sends the build back to wait', async () => {
  const world = new World();
  await uploadFolder(world, A, RED);
  world.tick(60000);
  let replaced = false;
  world.hooks.readBuffer = async () => {
    if (!replaced) {
      replaced = true;
      world.put(`${A}3.png`, await png('#000000'));
    }
  };

  await assert.rejects(runBuild({ ...IN_A, quiet: true }, world.deps, { holder: 'q' }), NotQuietYet);
  assert.equal(world.store.packs.get(ID), undefined);
  assert.deepEqual(versionFolders(world), []);
});

test('a second folder with the same name reports a collision, and takes the id over once the first is deleted', async () => {
  const world = new World();
  await uploadFolder(world, A, RED);
  await world.settle();
  world.tick(60000);

  await uploadFolder(world, B, BLUE, { listing: false });
  await world.settle();
  assert.match(world.text(`${B}_report.txt`), /already used by the folder library\/Love Notes\/; rename this folder/);
  assert.equal(world.store.packs.get(ID).source.folder, A);
  assert.equal(world.store.packs.get(ID).version, 1);

  world.tick(60000);
  for (const file of FILES) await world.remove(`${A}${file}`);
  await world.settle();

  const record = world.store.packs.get(ID);
  assert.equal(record.status, 'live');
  assert.equal(record.source.folder, B);
  assert.equal(record.version, 2);
  assert.match(world.text(`${B}_report.txt`), /^✅ love notes is live: version 2/, 'named after its folder');
});

test("renaming a pack's folder (the same id) moves the pack without taking it down", async () => {
  const world = new World();
  await uploadFolder(world, A, RED);
  await world.settle();
  const published = world.store.packs.get(ID).publishedAt;
  // A record written before tags decided categories.
  Object.assign(world.store.packs.get(ID), { category: 'romantic', alsoIn: ['cute'], lang: 'en' });
  world.tick(60000);

  for (const file of FILES) await world.remove(`${A}${file}`);
  world.tick(5000);
  await uploadFolder(world, B, RED, { listing: false });
  // Named like the old folder, so the pack's content, and version, stay the same.
  await world.upload(`${B}pack.json`, listingJson(3, { name: 'Love Notes' }));
  await world.settle();

  const outcomes = world.runs.map((r) => r.outcome);
  assert.ok(outcomes.includes('moving'), outcomes.join(', '));
  assert.ok(!outcomes.includes('removed'), 'never taken down');
  const record = world.store.packs.get(ID);
  assert.equal(record.status, 'live');
  assert.equal(record.source.folder, B);
  assert.equal(record.version, 1, 'the same files keep their version');
  assert.equal(record.publishedAt.getTime(), published.getTime(), 'and their date');
  assert.deepEqual([record.category, record.alsoIn, record.lang], [undefined, undefined, undefined]);
});

test('an After Dark pack is parked: no build, nothing in public/, and a report that says why', async () => {
  const world = new World();
  for (const [i, color] of RED.entries()) await world.upload(`${A}${i + 1}.png`, await png(color));
  await world.upload(`${A}pack.json`, Buffer.from(JSON.stringify({ name: 'Night Notes', adult: true })));
  await world.settle();

  assert.equal(world.store.packs.get(ID), undefined);
  assert.deepEqual(world.paths('public/'), []);
  assert.match(world.text(`${A}_report.txt`), /^⏸ Night Notes is not published: After Dark \(18\+\) packs stay out/);
});

test('a live pack marked adult later is taken down', async () => {
  const world = new World();
  await uploadFolder(world, A, RED);
  await world.settle();
  world.tick(60000);

  await world.upload(`${A}pack.json`, Buffer.from(JSON.stringify({ adult: true })));
  await world.settle();
  assert.equal(world.store.packs.get(ID).status, 'removed');
  assert.deepEqual(world.paths(`public/packs/${ID}/`), [], 'nothing of it stays downloadable');
});

test('a pack whose public files were wiped gets them back from its next build, as the same version', async () => {
  const world = new World();
  await uploadFolder(world, A, RED);
  await world.settle();
  const before = world.paths(`public/packs/${ID}/`);
  for (const path of before) world.objects.delete(path);
  world.tick(60000);

  await world.upload(`${A}pack.json`, listingJson(3));
  await world.settle();

  assert.deepEqual(world.paths(`public/packs/${ID}/`), before);
  assert.equal(world.store.packs.get(ID).version, 1);
  assert.equal(world.runs.at(-1).outcome, 'restored');
  assert.match(world.text(`${A}_report.txt`), /written again/);
});

test("a removed pack can't come back as the other kind under its id: phones that added it keep it", async () => {
  const world = new World();
  const moving = await animated();
  for (const i of [1, 2, 3]) await world.upload(`${A}${i}.webp`, moving);
  await world.settle();
  assert.equal(world.store.packs.get(ID).animated, true);
  world.tick(60000);

  for (const i of [1, 2, 3]) await world.remove(`${A}${i}.webp`);
  await world.settle();
  world.tick(60000);
  await uploadFolder(world, A, RED, { listing: false });
  await world.settle();

  const record = world.store.packs.get(ID);
  assert.deepEqual([record.status, record.animated, record.build.status], ['removed', true, 'failed']);
  assert.match(world.text(`${A}_report.txt`), /This pack was published animated, and WhatsApp can't switch/);
});

test('deleting every file of a live pack takes it down once the deletions settle, and keeps its files', async () => {
  const world = new World();
  await uploadFolder(world, A, RED);
  await world.settle();
  const publishes = world.publishes;
  world.tick(60000);

  for (const file of FILES) await world.remove(`${A}${file}`);
  await world.settle();

  assert.equal(world.store.packs.get(ID).status, 'removed');
  assert.ok(world.publishes > publishes);
  assert.equal(versionFolders(world).length, 1, 'phones mid-download still finish');
});

test('a broken update fails with a report and leaves the live version; restoring the file clears the failure', async () => {
  const world = new World();
  await uploadFolder(world, A, RED);
  await world.settle();
  const live = structuredClone(world.store.packs.get(ID));
  const good = world.objects.get(`${A}2.png`).buffer;
  world.tick(60000);

  await world.upload(`${A}2.png`, good.subarray(0, 200));
  await world.settle();
  let record = world.store.packs.get(ID);
  assert.equal(record.status, 'live');
  assert.equal(record.version, live.version);
  assert.equal(record.contentHash, live.contentHash);
  assert.equal(record.build.status, 'failed');
  const report = world.text(`${A}_report.txt`);
  assert.match(report, /^❌ Love Notes was not published; version 1 stays live/);
  assert.match(report, /2\.png: can't read the image/);

  world.tick(60000);
  await world.upload(`${A}2.png`, good);
  await world.settle();
  record = world.store.packs.get(ID);
  assert.equal(record.build.status, 'ok');
  assert.equal(record.version, 1);
  assert.match(world.text(`${A}_report.txt`), /^✅ Love Notes is live and unchanged: version 1/);
});

test('a build that published its files but died before its record is finished by the retry, as the same version', async () => {
  const world = new World();
  world.fault('commitPack');
  await uploadFolder(world, A, RED);
  await world.settle();

  const record = world.store.packs.get(ID);
  assert.equal(record.version, 1);
  assert.deepEqual(versionFolders(world), [`v1-${record.contentHash.slice(0, 8)}`]);
});

test('a build that changed the pack but died before asking for a publish still gets the catalog published', async () => {
  const world = new World();
  world.fault('enqueuePublish');
  await uploadFolder(world, A, RED);
  await world.settle();

  assert.equal(world.store.packs.get(ID).version, 1);
  assert.ok(world.runs[0].error, 'the fast build died after writing the record');
  assert.equal(world.publishes, 1, 'the quiet build found the pack unchanged and still asked for a publish');
});

test('a removal that died before asking for a publish still gets the catalog published by its retry', async () => {
  const world = new World();
  await uploadFolder(world, A, RED);
  await world.settle();
  const publishes = world.publishes;
  world.tick(60000);

  world.fault('enqueuePublish');
  for (const file of FILES) await world.remove(`${A}${file}`);
  await world.settle();

  assert.equal(world.store.packs.get(ID).status, 'removed');
  assert.equal(world.publishes, publishes + 1);
});

test('after the pack record is deleted in the console, the next build takes a version above every published one', async () => {
  const world = new World();
  await uploadFolder(world, A, RED);
  await world.settle();
  world.tick(60000);
  await uploadFolder(world, A, BLUE);
  await world.settle();
  assert.equal(world.store.packs.get(ID).version, 2);

  world.store.packs.delete(ID);
  world.tick(60000);
  for (const [i, color] of RED.entries()) await world.upload(`${A}${i + 1}.png`, await png(color));
  await world.settle();

  const record = world.store.packs.get(ID);
  assert.equal(record.version, 3, 'version 1 already named these files once');
  assert.equal(record.contentHash, await hashOf(RED));
});

test('a folder that keeps changing for ten minutes still builds once it settles', async () => {
  const world = new World();
  const colors = [...RED, ...BLUE];
  for (let i = 0; i < 30; i++) {
    await world.upload(`${A}${(i % 3) + 1}.png`, await png(colors[i % colors.length]));
    await world.advance(20000);
  }
  await world.settle();

  assert.ok(world.runs.some((r) => r.outcome === 'rescheduled'), 'the wait outlived one task');
  assert.equal(world.store.packs.get(ID).status, 'live');
  assert.equal(world.store.packs.get(ID).version, 1);
});

test('on its last attempt a waiting quiet build hands the wait to a fresh task', async () => {
  const world = new World();
  await world.upload(`${A}1.png`, await png(RED[0]));
  const [task] = world.queue.splice(0);

  const result = await runBuildTask(task.task, { retryCount: BUILD_RETRY.maxAttempts - 1, taskId: task.id }, world.deps);
  assert.equal(result.outcome, 'rescheduled');
  assert.equal(world.queue.length, 1);
  assert.deepEqual(world.queue[0].task, { ...IN_A, quiet: true });
  assert.equal(world.store.builds.get(ID).folders[folderKey(A)].pending, true);
});

test('an unexpected error leaves the author a report on the last attempt, not before', async () => {
  const world = new World();
  await uploadFolder(world, A, RED);
  world.tick(60000);
  world.deps.listFolder = async () => {
    throw new Error('storage is down');
  };
  const task = { ...IN_A, quiet: true };

  await assert.rejects(runBuildTask(task, { retryCount: 0, taskId: 't' }, world.deps), /storage is down/);
  assert.equal(world.text(`${A}_report.txt`), null);

  await assert.rejects(runBuildTask(task, { retryCount: BUILD_RETRY.maxAttempts - 1, taskId: 't' }, world.deps), /storage is down/);
  const report = world.text(`${A}_report.txt`);
  assert.match(report, /^❌ Love Notes was not published/);
  assert.match(report, /storage is down/);
});
