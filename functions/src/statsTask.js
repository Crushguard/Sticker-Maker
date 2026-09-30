'use strict';

const { statsFromReport } = require('./stats');
const { db } = require('./firebase');
const { enqueuePublish } = require('./queue');

const EVENT = 'pack_added';
const PACK_DIMENSION = 'customEvent:pack_id';
const ALL_TIME_START = '2026-01-01';

function eventFilter() {
  return { filter: { fieldName: 'eventName', stringFilter: { value: EVENT } } };
}

/**
 * Weekly popularity from the pack_added event the app already sends (its pack_id parameter must be registered as
 * an event-scoped custom dimension). Without a GA4 property id it does nothing and ranking stays pin → order → newest.
 */
async function runWeeklyStats({ propertyId, now = new Date(), client } = {}) {
  if (!propertyId) return { outcome: 'skipped' };
  const analytics = client || new (require('@google-analytics/data').BetaAnalyticsDataClient)();
  const property = `properties/${propertyId}`;
  const [daily] = await analytics.runReport({
    property,
    dateRanges: [{ startDate: '28daysAgo', endDate: 'yesterday' }],
    dimensions: [{ name: 'date' }, { name: PACK_DIMENSION }],
    metrics: [{ name: 'eventCount' }],
    dimensionFilter: eventFilter(),
    limit: 100000,
  });
  const [total] = await analytics.runReport({
    property,
    dateRanges: [{ startDate: ALL_TIME_START, endDate: 'today' }],
    dimensions: [{ name: PACK_DIMENSION }],
    metrics: [{ name: 'eventCount' }],
    dimensionFilter: eventFilter(),
    limit: 10000,
  });
  const rows = (daily.rows || []).map((r) => ({
    date: r.dimensionValues[0].value,
    packId: r.dimensionValues[1].value,
    count: Number(r.metricValues[0].value),
  }));
  const totals = (total.rows || []).map((r) => ({ packId: r.dimensionValues[0].value, count: Number(r.metricValues[0].value) }));
  const stats = statsFromReport(rows, totals, now);

  const packs = await db().collection('packs').get();
  const batch = db().batch();
  for (const doc of packs.docs) {
    const s = stats.get(doc.id) || { adds: 0, adds28d: 0, trend: 0 };
    batch.set(doc.ref, { stats: { ...s, at: now } }, { mergeFields: ['stats'] });
  }
  await batch.commit();
  await enqueuePublish();
  return { outcome: 'updated', packs: packs.size };
}

module.exports = { runWeeklyStats };
