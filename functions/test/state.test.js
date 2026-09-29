'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { isPending, leaseFree, sameVersion, PENDING_STALE_MS, LEASE_MS } = require('../src/state');

const NOW = 1790000000000;

test('a pending flag counts until it is ten minutes old, when its task must have been dropped', () => {
  assert.equal(isPending(null, NOW), false);
  assert.equal(isPending({ pending: false, pendingSinceMs: NOW }, NOW), false);
  assert.equal(isPending({ pending: true, pendingSinceMs: NOW - 1000 }, NOW), true);
  assert.equal(isPending({ pending: true, pendingSinceMs: NOW - PENDING_STALE_MS }, NOW), true);
  assert.equal(isPending({ pending: true, pendingSinceMs: NOW - PENDING_STALE_MS - 1 }, NOW), false);
});

test('a lease is free when nobody holds it, its holder asks again, or it outlived any build', () => {
  assert.ok(LEASE_MS > 540 * 1000, 'longer than the build timeout');
  assert.equal(leaseFree(undefined, 'a', NOW), true);
  assert.equal(leaseFree({ holder: 'a', untilMs: NOW + 1000 }, 'a', NOW), true);
  assert.equal(leaseFree({ holder: 'a', untilMs: NOW + 1000 }, 'b', NOW), false);
  assert.equal(leaseFree({ holder: 'a', untilMs: NOW }, 'b', NOW), true);
});

test('a record still holds the version a build started from only if version, content and status all match', () => {
  const live = { version: 3, contentHash: 'abc', status: 'live', stats: { adds: 5 } };
  const expected = { version: 3, contentHash: 'abc', status: 'live' };
  assert.equal(sameVersion(live, expected), true);
  assert.equal(sameVersion({ ...live, pin: 1 }, expected), true, 'console and stats edits do not count');
  assert.equal(sameVersion({ ...live, version: 4 }, expected), false);
  assert.equal(sameVersion({ ...live, contentHash: 'def' }, expected), false);
  assert.equal(sameVersion({ ...live, status: 'removed' }, expected), false);
  assert.equal(sameVersion(null, expected), false);
  assert.equal(sameVersion(null, { version: undefined, contentHash: undefined, status: undefined }), true);
  assert.equal(sameVersion({ stats: { adds: 5 } }, { version: null, contentHash: null, status: null }), true);
  assert.equal(sameVersion({ status: 'failed' }, { version: null, contentHash: null, status: null }), false);
});
