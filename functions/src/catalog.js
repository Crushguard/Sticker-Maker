'use strict';

const { publicPackBase } = require('./config');
const { categoriesOf, categoryTags } = require('./categories');

const DAY_MS = 86400000;
const NEW_PACK_DAYS = 14;
const MAX_KEYWORDS = 60;
/** Every animated pack carries this tag, so "animated" (and its translations) finds them. */
const ANIMATED_TAG = 'animated';

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

/**
 * A pack's tags as the catalog lists them, plus "animated" for an animated pack. A record built before tags decided
 * categories still counts its category folder and alsoIn as tags until its next build drops them.
 */
function tagsOf(p) {
  const legacy = [p.category, ...(Array.isArray(p.alsoIn) ? p.alsoIn : [])].filter((t) => typeof t === 'string' && t);
  const tags = [...new Set([...legacy, ...(Array.isArray(p.tags) ? p.tags : [])])];
  return p.animated && !tags.includes(ANIMATED_TAG) ? [...tags, ANIMATED_TAG] : tags;
}

/** The languages of a pack's lettering; records built before lists had one "lang". */
function langsOf(p) {
  return Array.isArray(p.langs) && p.langs.length ? p.langs : [p.lang || 'en'];
}

/**
 * Search words: the pack's tags, its keywords (phrases from its stickers), then the words of every sticker's text;
 * lowercase, unique, 2+ characters.
 */
function keywordsOf(p) {
  const words = [];
  const add = (text) => {
    for (const word of String(text).toLowerCase().split(/[^\p{L}\p{N}]+/u)) {
      if (word.length >= 2 && !words.includes(word)) words.push(word);
    }
  };
  tagsOf(p).forEach(add);
  (p.keywords || []).forEach(add);
  (p.stickers || []).forEach((s) => add(s.text || ''));
  return words.slice(0, MAX_KEYWORDS);
}

/**
 * The entries of a { id: words } dictionary that the catalog uses, in the order first used, under the ids the packs
 * use. A language code without an entry of its own takes its primary language's ("pt-BR" → "pt").
 */
function pick(dictionary, ids) {
  const out = {};
  for (const id of ids) {
    const words = dictionary && (dictionary[id] || dictionary[id.split('-')[0]]);
    if (words && !out[id]) out[id] = words;
  }
  return out;
}

/**
 * The catalog file the app downloads: categories in chip order (with live pack counts), live packs in rank order,
 * and the search words of the tags and languages those packs use (from _tags.json).
 *
 * A pack's categories come from its tags: it shows under every category one of its tags names (categoriesOf), the
 * first being its main one ("category"; the rest are "alsoIn"). A pack with no category tag has category "" and
 * shows in Trending and search only.
 *
 * @param {{version: number, now: Date, categories: object[], packs: object[], vocabulary?: {tags?: object,
 *   languages?: object}}} input
 */
function assembleCatalog({ version, now, categories, packs, vocabulary = {} }) {
  const ranked = rankPacks(packs, now);
  const sorted = [...categories].sort((a, b) => a.order - b.order || (a.id < b.id ? -1 : 1));
  const membership = new Map(ranked.map((p) => [p.id, categoriesOf(tagsOf(p), sorted)]));
  const counts = new Map();
  for (const ids of membership.values()) for (const id of ids) counts.set(id, (counts.get(id) || 0) + 1);

  const catalogCategories = sorted.map((c) => ({
    id: c.id,
    order: c.order,
    icon: c.icon,
    hue: c.hue,
    names: c.names,
    keywords: c.keywords || {},
    tags: categoryTags(c),
    packs: counts.get(c.id) || 0,
  }));

  const catalogPacks = ranked.map((p) => {
    const base = publicPackBase(p.id, p.version, p.contentHash);
    const published = toMs(p.publishedAt);
    const [category = '', ...alsoIn] = membership.get(p.id);
    const langs = langsOf(p);
    return {
      id: p.id,
      name: p.name,
      names: p.names || {},
      category,
      alsoIn,
      lang: langs[0],
      langs,
      tags: tagsOf(p),
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
    tags: pick(vocabulary.tags, catalogPacks.flatMap((p) => p.tags)),
    languages: pick(vocabulary.languages, catalogPacks.flatMap((p) => p.langs)),
  };
}

module.exports = { rankPacks, assembleCatalog, toMs };
