'use strict';

const DAY_MS = 86400000;
const HALF_LIFE_DAYS = 7;

function dayMs(yyyymmdd) {
  return Date.UTC(Number(yyyymmdd.slice(0, 4)), Number(yyyymmdd.slice(4, 6)) - 1, Number(yyyymmdd.slice(6, 8)));
}

/** Recent adds weigh most: yesterday counts fully, and a day's weight halves every 7 days. */
function trendFromDaily(daily, today) {
  const yesterday = Date.UTC(today.getUTCFullYear(), today.getUTCMonth(), today.getUTCDate()) - DAY_MS;
  let trend = 0;
  for (const { date, count } of daily) {
    const age = Math.max(0, (yesterday - dayMs(date)) / DAY_MS);
    trend += count * Math.pow(0.5, age / HALF_LIFE_DAYS);
  }
  return trend;
}

/**
 * Per-pack popularity from Analytics' pack_added counts: rows by day for the last 28 days, totals for all time.
 * @returns {Map<string, {adds: number, adds28d: number, trend: number}>}
 */
function statsFromReport(rows, totals, today) {
  const daily = new Map();
  for (const row of rows) {
    if (!daily.has(row.packId)) daily.set(row.packId, []);
    daily.get(row.packId).push(row);
  }
  const stats = new Map();
  const ids = new Set([...daily.keys(), ...totals.map((t) => t.packId)]);
  for (const id of ids) {
    const days = daily.get(id) || [];
    const total = totals.find((t) => t.packId === id);
    const adds28d = days.reduce((sum, d) => sum + d.count, 0);
    stats.set(id, { adds: total ? total.count : adds28d, adds28d, trend: trendFromDaily(days, today) });
  }
  return stats;
}

module.exports = { trendFromDaily, statsFromReport };
