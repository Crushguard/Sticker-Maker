'use strict';

const { publicPackBase } = require('./config');

const DAY_MS = 86400000;
const NEW_PACK_DAYS = 14;
const MAX_KEYWORDS = 40;
const DEFAULT_CATEGORY = { order: 1000, icon: 'heart', hue: 340 };

function toMs(value) {
  if (!value) return null;
  if (value instanceof Date) return value.getTime();
  if (typeof value.toMillis === 'function') return value.toMillis(); // Firestore Timestamp
  const ms = new Date(value).getTime();
  return Number.isNaN(ms) ? null : ms;
}

const trendOf = (p) => (p.stats && Number.isFinite(p.stats.trend) ? p.stats.trend : 0);
const isPinned = (p) => typeof p.pin === 'number' && Number.isFinite(p.pin);

function median(values) {
  if (!values.length) return 0;
  const sorted = [...values].sort((a, b) => a - b);
  const mid = Math.floor(sorted.length / 2);
  return sorted.length % 2 ? sorted[mid] : (sorted[mid - 1] + sorted[mid]) / 2;
}

/**
 * The order packs are shown in: pinned first (lowest pin first), then popularity, then the pack's own order,
 * then newest. A pack live under 14 days scores at least the median of mature packs, fading to its own trend.
 */
function rankPacks(packs, now) {
  const nowMs = now.getTime();
  const age = (p) => {
    const published = toMs(p.publishedAt);
    return published === null ? 0 : Math.max(0, (nowMs - published) / DAY_MS);
  };
  const floor = median(packs.filter((p) => age(p) >= NEW_PACK_DAYS).map(trendOf));
  const score = (p) => {
    const a = age(p);
    return a < NEW_PACK_DAYS ? Math.max(trendOf(p), floor * (1 - a / NEW_PACK_DAYS)) : trendOf(p);
  };
  const keyed = packs.map((p) => ({ p, score: score(p), published: toMs(p.publishedAt) ?? nowMs }));
  keyed.sort((a, b) => {
    const pa = isPinned(a.p);
    const pb = isPinned(b.p);
    if (pa !== pb) return pa ? -1 : 1;
    if (pa && pb && a.p.pin !== b.p.pin) return a.p.pin - b.p.pin;
    if (a.score !== b.score) return b.score - a.score;
    if ((a.p.order ?? 1000) !== (b.p.order ?? 1000)) return (a.p.order ?? 1000) - (b.p.order ?? 1000);
    if (a.published !== b.published) return b.published - a.published;
    return a.p.id < b.p.id ? -1 : a.p.id > b.p.id ? 1 : 0;
  });
  return keyed.map((k) => k.p);
}

/** Search words: the pack's tags, then the words of every sticker's text; lowercase, unique, 2+ characters. */
function keywordsOf(p) {
  const words = [];
  const add = (text) => {
    for (const word of String(text).toLowerCase().split(/[^\p{L}\p{N}]+/u)) {
      if (word.length >= 2 && !words.includes(word)) words.push(word);
    }
  };
  (p.tags || []).forEach(add);
  (p.stickers || []).forEach((s) => add(s.text || ''));
  return words.slice(0, MAX_KEYWORDS);
}

function titleCase(id) {
  return id
    .split('-')
    .filter(Boolean)
    .map((w) => w[0].toUpperCase() + w.slice(1))
    .join(' ');
}

/**
 * The catalog file the app downloads: categories (with live pack counts, alsoIn included) and live packs in
 * rank order. A pack whose folder category is missing from _categories.json brings a default category.
 */
function assembleCatalog({ version, now, categories, packs }) {
  const ranked = rankPacks(packs, now);
  const known = new Map(categories.map((c) => [c.id, c]));
  for (const p of ranked) {
    if (!known.has(p.category)) {
      known.set(p.category, { id: p.category, ...DEFAULT_CATEGORY, names: { en: titleCase(p.category) }, keywords: {} });
    }
  }
  const counts = new Map();
  for (const p of ranked) {
    for (const id of new Set([p.category, ...(p.alsoIn || [])])) counts.set(id, (counts.get(id) || 0) + 1);
  }
  const catalogCategories = [...known.values()]
    .sort((a, b) => a.order - b.order || (a.id < b.id ? -1 : 1))
    .map((c) => ({
      id: c.id,
      order: c.order,
      icon: c.icon,
      hue: c.hue,
      names: c.names,
      keywords: c.keywords || {},
      packs: counts.get(c.id) || 0,
    }));

  const catalogPacks = ranked.map((p) => {
    const base = publicPackBase(p.id, p.version);
    const published = toMs(p.publishedAt);
    return {
      id: p.id,
      name: p.name,
      names: p.names || {},
      category: p.category,
      alsoIn: p.alsoIn || [],
      lang: p.lang || 'en',
      animated: !!p.animated,
      count: p.count,
      version: p.version,
      adds: (p.stats && p.stats.adds) || 0,
      cover: { s: `${base}cover-s.webp`, l: `${base}cover-l.webp`, tiles: p.coverTiles },
      zip: { path: `${base}pack.zip`, bytes: p.zipBytes },
      keywords: keywordsOf(p),
      publishedAt: new Date(published ?? now.getTime()).toISOString(),
    };
  });

  return {
    schema: 1,
    version,
    publishedAt: now.toISOString(),
    categories: catalogCategories,
    packs: catalogPacks,
  };
}

module.exports = { rankPacks, assembleCatalog, toMs };
