'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { handleLibraryEvent } = require('../src/events');

/** Fake Storage folder + recorded side effects. */
function fakes(folderFiles = {}, { quietPending = false } = {}) {
  const calls = { builds: [], syncs: 0, touches: [] };
  let pending = quietPending;
  return {
    calls,
    deps: {
      listFolder: async (prefix) =>
        Object.entries(folderFiles).map(([file, { generation = '1', text }]) => ({ file, generation, text, prefix })),
      readText: async (entry) => entry.text,
      enqueueBuild: async (task) => calls.builds.push(task),
      // Records the event; true when no quiet build is pending yet (the caller must schedule one).
      recordEvent: async (packId, where, nowMs) => {
        calls.touches.push({ packId, ...where, nowMs });
        if (pending) return false;
        pending = true;
        return true;
      },
      syncCategories: async () => {
        calls.syncs += 1;
      },
    },
  };
}

const NOW = 1790000000000;

test('the categories file syncs categories and nothing else', async () => {
  const { calls, deps } = fakes();
  await handleLibraryEvent('library/_categories.json', 'finalized', NOW, deps);
  assert.equal(calls.syncs, 1);
  assert.deepEqual(calls.builds, []);
});

test('deleting the categories file changes nothing', async () => {
  const { calls, deps } = fakes();
  await handleLibraryEvent('library/_categories.json', 'deleted', NOW, deps);
  assert.equal(calls.syncs, 0);
  assert.deepEqual(calls.builds, []);
});

test('reports, junk and staging never schedule builds', async () => {
  const { calls, deps } = fakes();
  for (const name of [
    'library/sorry/Pack/_report.txt',
    'library/sorry/Pack/.DS_Store',
    'library/sorry/Pack/',
    'library/_staging/Pack/01.png',
    'public/packs/x/v1/pack.zip',
  ]) {
    await handleLibraryEvent(name, 'finalized', NOW, deps);
  }
  assert.deepEqual(calls.builds, []);
});

test('an upload without a listing pack.json schedules only the quiet build', async () => {
  const { calls, deps } = fakes({ '01.png': {}, '02.png': {} });
  await handleLibraryEvent('library/sorry/Sorry Wiggle/02.png', 'finalized', NOW, deps);
  assert.equal(calls.builds.length, 1);
  assert.equal(calls.builds[0].category, 'sorry');
  assert.equal(calls.builds[0].folder, 'Sorry Wiggle');
  assert.equal(calls.builds[0].delaySeconds, 45);
  assert.equal(calls.builds[0].quiet, true);
  assert.match(calls.builds[0].id, /^q-sorry-wiggle-[0-9a-z]+-[0-9a-z]+$/);
});

test('the upload that completes a listing pack.json also schedules a build right away', async () => {
  const listing = JSON.stringify({ stickers: [{ file: '01.png' }, { file: '02.png' }, { file: '03.png' }] });
  const { calls, deps } = fakes({
    '01.png': { generation: '11' },
    '02.png': { generation: '12' },
    '03.png': { generation: '13' },
    'pack.json': { generation: '14', text: listing },
  });
  await handleLibraryEvent('library/sorry/Sorry Wiggle/pack.json', 'finalized', NOW, deps);
  assert.equal(calls.builds.length, 2);
  assert.equal(calls.builds[0].delaySeconds, 45);
  assert.equal(calls.builds[1].delaySeconds, 2);
  assert.match(calls.builds[1].id, /^f-sorry-wiggle-[0-9a-f]{16}$/);
});

test('a listing pack.json with files still missing waits for the quiet build', async () => {
  const listing = JSON.stringify({ stickers: [{ file: '01.png' }, { file: '02.png' }, { file: '03.png' }] });
  const { calls, deps } = fakes({ '01.png': {}, 'pack.json': { text: listing } });
  await handleLibraryEvent('library/sorry/Sorry Wiggle/pack.json', 'finalized', NOW, deps);
  assert.equal(calls.builds.length, 1);
  assert.equal(calls.builds[0].delaySeconds, 45);
});

test('a deleted pack file schedules the quiet build (which may unpublish)', async () => {
  const { calls, deps } = fakes({});
  await handleLibraryEvent('library/sorry/Sorry Wiggle/01.png', 'deleted', NOW, deps);
  assert.equal(calls.builds.length, 1);
  assert.equal(calls.builds[0].delaySeconds, 45);
});

test('every pack event records when it happened, before any build is scheduled', async () => {
  const { calls, deps } = fakes({ '01.png': {} });
  await handleLibraryEvent('library/sorry/Sorry Wiggle/01.png', 'deleted', NOW, deps);
  await handleLibraryEvent('library/sorry/Sorry Wiggle/_report.txt', 'finalized', NOW, deps);
  await handleLibraryEvent('library/_categories.json', 'finalized', NOW, deps);
  assert.deepEqual(calls.touches, [{ packId: 'sorry-wiggle', category: 'sorry', folder: 'Sorry Wiggle', nowMs: NOW }]);
});

test('while a quiet build is pending, more events only record their time', async () => {
  const { calls, deps } = fakes({ '01.png': {}, '02.png': {} });
  await handleLibraryEvent('library/sorry/Sorry Wiggle/01.png', 'finalized', NOW, deps);
  await handleLibraryEvent('library/sorry/Sorry Wiggle/02.png', 'finalized', NOW + 1000, deps);
  await handleLibraryEvent('library/sorry/Sorry Wiggle/02.png', 'deleted', NOW + 2000, deps);
  assert.equal(calls.builds.filter((b) => b.quiet).length, 1);
  assert.equal(calls.touches.length, 3);
});

test('quiet build ids never repeat, so an id that already ran can never swallow a later event', async () => {
  const ids = new Set();
  for (let i = 0; i < 50; i++) {
    const { calls, deps } = fakes({});
    await handleLibraryEvent('library/sorry/Sorry Wiggle/01.png', 'deleted', NOW, deps);
    ids.add(calls.builds[0].id);
  }
  assert.equal(ids.size, 50);
});
