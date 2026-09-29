'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { trendFromDaily, statsFromReport } = require('../src/stats');

const TODAY = new Date('2026-10-05T04:00:00Z'); // yesterday = 2026-10-04

test('yesterday counts fully and older days halve every 7 days', () => {
  assert.equal(trendFromDaily([{ date: '20261004', count: 10 }], TODAY), 10);
  assert.ok(Math.abs(trendFromDaily([{ date: '20260927', count: 10 }], TODAY) - 5) < 1e-9);
  assert.ok(Math.abs(trendFromDaily([{ date: '20260920', count: 8 }], TODAY) - 2) < 1e-9);
});

test('days add up and today (incomplete) counts like yesterday', () => {
  const t = trendFromDaily(
    [
      { date: '20261004', count: 4 },
      { date: '20261005', count: 2 },
      { date: '20260927', count: 4 },
    ],
    TODAY
  );
  assert.ok(Math.abs(t - (4 + 2 + 2)) < 1e-9);
});

test('statsFromReport groups rows per pack with 28-day and all-time adds', () => {
  const stats = statsFromReport(
    [
      { date: '20261004', packId: 'a', count: 3 },
      { date: '20260927', packId: 'a', count: 2 },
      { date: '20261003', packId: 'b', count: 1 },
    ],
    [
      { packId: 'a', count: 40 },
      { packId: 'b', count: 1 },
      { packId: 'c', count: 7 },
    ],
    TODAY
  );
  assert.equal(stats.get('a').adds, 40);
  assert.equal(stats.get('a').adds28d, 5);
  assert.ok(Math.abs(stats.get('a').trend - 4) < 1e-9);
  assert.equal(stats.get('b').adds28d, 1);
  assert.deepEqual(stats.get('c'), { adds: 7, adds28d: 0, trend: 0 });
});
