'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { publishTaskOptions } = require('../src/queue');

// Regression: a deduplicated publish id silently dropped the build's publish when an earlier publish with the same
// id had already run (Cloud Tasks keeps executed ids; the emulator even ignores the delay). Publishes run one at a
// time and skip themselves when nothing changed, so every request must become a task.
test('catalog publish requests are never deduplicated', () => {
  const options = publishTaskOptions();
  assert.equal(options.id, undefined);
  assert.ok(options.scheduleDelaySeconds >= 1 && options.scheduleDelaySeconds <= 5);
});
