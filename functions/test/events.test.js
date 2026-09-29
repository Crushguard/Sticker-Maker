'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { World } = require('./fakes');
const { handleLibraryEvent } = require('../src/events');
const { folderKey } = require('../src/library');

const PACK = 'library/sorry/Sorry Wiggle/';
const ID = 'sorry-wiggle';
const png = Buffer.from('png');
const listing = Buffer.from(JSON.stringify({ stickers: [{ file: '01.png' }, { file: '02.png' }, { file: '03.png' }] }));

const builds = (world) => world.queue.map((t) => ({ ...t.task, delaySeconds: (t.dueMs - world.nowMs) / 1000, id: t.id }));
const folderState = (world, prefix = PACK) => world.store.builds.get(ID).folders[folderKey(prefix)];

test('the categories file syncs categories and nothing else', async () => {
  const world = new World();
  await world.upload('library/_categories.json', Buffer.from('{}'));
  assert.equal(world.syncs, 1);
  assert.deepEqual(world.queue, []);
});

test('deleting the categories file changes nothing', async () => {
  const world = new World();
  await handleLibraryEvent('library/_categories.json', 'deleted', world.nowMs, world.deps);
  assert.equal(world.syncs, 0);
  assert.deepEqual(world.queue, []);
});

test('reports, junk and staging never schedule builds', async () => {
  const world = new World();
  for (const name of [
    `${PACK}_report.txt`,
    `${PACK}.DS_Store`,
    PACK,
    'library/_staging/Pack/01.png',
    'public/packs/x/v1-0123abcd/pack.zip',
  ]) {
    await handleLibraryEvent(name, 'finalized', world.nowMs, world.deps);
  }
  assert.deepEqual(world.queue, []);
});

test('an upload without a listing pack.json schedules only the quiet build', async () => {
  const world = new World();
  await world.upload(`${PACK}01.png`, png);
  await world.upload(`${PACK}02.png`, png);
  const [only, ...rest] = builds(world);
  assert.deepEqual(rest, []);
  assert.equal(only.category, 'sorry');
  assert.equal(only.folder, 'Sorry Wiggle');
  assert.equal(only.delaySeconds, 45);
  assert.equal(only.quiet, true);
  assert.match(only.id, /^q-sorry-wiggle-[0-9a-z]+-[0-9a-z]+$/);
});

test('the pack.json that completes a new listing, uploaded last, also schedules a build right away', async () => {
  const world = new World();
  for (const file of ['01.png', '02.png', '03.png']) await world.upload(`${PACK}${file}`, png);
  await world.upload(`${PACK}pack.json`, listing);
  const [quiet, fast, ...rest] = builds(world);
  assert.deepEqual(rest, []);
  assert.equal(quiet.delaySeconds, 45);
  assert.equal(fast.delaySeconds, 2);
  assert.equal(fast.quiet, false);
  assert.match(fast.id, /^f-sorry-wiggle-[0-9a-f]{16}$/);
});

test('re-uploading a sticker of a complete folder schedules no fast build: only pack.json, written last, does', async () => {
  const world = new World();
  for (const file of ['01.png', '02.png', '03.png']) await world.upload(`${PACK}${file}`, png);
  await world.upload(`${PACK}pack.json`, listing);
  world.queue.splice(0);
  world.store.builds.clear();

  for (const file of ['01.png', '02.png', '03.png']) await world.upload(`${PACK}${file}`, Buffer.from('new art'));
  assert.deepEqual(builds(world).map((b) => b.quiet), [true]);
});

test('a listing pack.json with files still missing waits for the quiet build', async () => {
  const world = new World();
  await world.upload(`${PACK}01.png`, png);
  await world.upload(`${PACK}pack.json`, listing);
  assert.deepEqual(builds(world).map((b) => b.delaySeconds), [45]);
});

test('a deleted pack file schedules the quiet build (which may unpublish)', async () => {
  const world = new World();
  await world.remove(`${PACK}01.png`);
  assert.deepEqual(builds(world).map((b) => [b.quiet, b.delaySeconds]), [[true, 45]]);
});

test('every pack event records when it happened in its folder', async () => {
  const world = new World();
  await world.remove(`${PACK}01.png`);
  const first = world.nowMs;
  world.tick(5000);
  await world.upload(`${PACK}_report.txt`, Buffer.from('report'));
  await world.upload('library/_categories.json', Buffer.from('{}'));
  assert.deepEqual(folderState(world), {
    category: 'sorry',
    folder: 'Sorry Wiggle',
    lastEventMs: first,
    pending: true,
    pendingSinceMs: first,
  });
});

test('while a quiet build is pending, more events only record their time', async () => {
  const world = new World();
  await world.upload(`${PACK}01.png`, png);
  world.tick(1000);
  await world.upload(`${PACK}02.png`, png);
  world.tick(1000);
  await world.remove(`${PACK}02.png`);
  assert.equal(builds(world).filter((b) => b.quiet).length, 1);
  assert.equal(folderState(world).lastEventMs, world.nowMs);
});

test('two folders that give the same pack id each get their own quiet build', async () => {
  const world = new World();
  const other = 'library/cute/Sorry Wiggle/';
  await world.remove(`${PACK}01.png`);
  await world.upload(`${other}01.png`, png);
  assert.deepEqual(builds(world).map((b) => [b.category, b.quiet]), [['sorry', true], ['cute', true]]);
  assert.equal(folderState(world).pending, true);
  assert.equal(folderState(world, other).pending, true);
});

test('when the quiet build cannot be enqueued, the pending flag is cleared so the next event schedules it', async () => {
  const world = new World();
  world.fault('enqueueBuild');
  await assert.rejects(world.upload(`${PACK}01.png`, png), /injected enqueueBuild failure/);
  assert.equal(folderState(world).pending, false);
  assert.deepEqual(world.queue, []);

  await world.upload(`${PACK}02.png`, png);
  assert.deepEqual(builds(world).map((b) => b.quiet), [true]);
});

test('quiet build ids never repeat, so an id that already ran can never swallow a later event', async () => {
  const ids = new Set();
  for (let i = 0; i < 50; i++) {
    const world = new World();
    await world.remove(`${PACK}01.png`);
    ids.add(world.queue[0].id);
  }
  assert.equal(ids.size, 50);
});
