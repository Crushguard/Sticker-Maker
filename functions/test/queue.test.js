'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { publishTaskOptions } = require('../src/queue');

const WINDOW_START = Date.UTC(2026, 8, 30, 12, 0, 0);

test('publish requests in one 20 s window share one task that runs after the window closes', () => {
  const first = publishTaskOptions(WINDOW_START + 100, false);
  const last = publishTaskOptions(WINDOW_START + 19999, false);
  assert.equal(first.id, last.id);
  assert.match(first.id, /^publish-[0-9a-f]{8}-\d+$/);
  assert.equal(first.scheduleTime.getTime(), WINDOW_START + 20000 + 5000);
  assert.ok(first.scheduleTime.getTime() > WINDOW_START + 19999, 'it runs after every request of its window');
});

test('the next window is a new task', () => {
  const a = publishTaskOptions(WINDOW_START + 19999, false);
  const b = publishTaskOptions(WINDOW_START + 20000, false);
  assert.notEqual(a.id, b.id);
  assert.equal(b.scheduleTime.getTime(), WINDOW_START + 40000 + 5000);
});

// Regression: a deduplicated publish id silently dropped the build's publish when an earlier publish with the same
// id had already run. The Cloud Tasks emulator ignores schedule times, so a shared window id would run at the first
// request and swallow the rest: there every request stays its own task.
test('in the emulator catalog publish requests are never deduplicated', () => {
  const options = publishTaskOptions(WINDOW_START, true);
  assert.equal(options.id, undefined);
  assert.ok(options.scheduleDelaySeconds >= 1 && options.scheduleDelaySeconds <= 5);
});
