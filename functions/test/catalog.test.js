'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { rankPacks, assembleCatalog } = require('../src/catalog');

const NOW = new Date('2026-10-05T12:00:00Z');
const daysAgo = (d) => new Date(NOW.getTime() - d * 86400000);

const pack = (id, extra = {}) => ({
  id,
  name: id,
  names: {},
  langs: ['en'],
  tags: ['sorry'],
  animated: false,
  count: 6,
  version: 1,
  contentHash: '0123abcd'.repeat(8),
  zipBytes: 1000,
  coverTiles: 6,
  order: 1000,
  stickers: [],
  publishedAt: daysAgo(60),
  stats: { adds: 0, adds28d: 0, trend: 0 },
  ...extra,
});

const ids = (list) => list.map((p) => p.id);

test('pinned packs come first by pin, then by score', () => {
  const ranked = rankPacks(
    [
      pack('hot', { stats: { adds: 9, adds28d: 9, trend: 50 } }),
      pack('pin2', { pin: 2 }),
      pack('warm', { stats: { adds: 5, adds28d: 5, trend: 10 } }),
      pack('pin1', { pin: 1 }),
    ],
    NOW
  );
  assert.deepEqual(ids(ranked), ['pin1', 'pin2', 'hot', 'warm']);
});

test('ties fall back to order, then newest', () => {
  const ranked = rankPacks(
    [
      pack('late', { order: 5 }),
      pack('early', { order: 1 }),
      pack('newer', { order: 5, publishedAt: daysAgo(20) }),
    ],
    NOW
  );
  assert.deepEqual(ids(ranked), ['early', 'newer', 'late']);
});

test('a new pack starts from the median of mature packs and fades over 14 days', () => {
  const mature = [10, 20, 30].map((t, i) => pack(`m${i}`, { stats: { adds: t, adds28d: t, trend: t } }));
  const brandNew = pack('new', { publishedAt: daysAgo(0) });
  const halfway = pack('week', { publishedAt: daysAgo(7) });
  const ranked = rankPacks([...mature, brandNew, halfway], NOW);
  // new: max(0, 20 × 1) = 20 ties m1 (20) and wins on newest; week: 20 × 0.5 = 10 ties m0 (10), newer wins.
  assert.deepEqual(ids(ranked), ['m2', 'new', 'm1', 'week', 'm0']);
});

test('a new pack never scores below its own trend', () => {
  const ranked = rankPacks(
    [pack('m', { stats: { adds: 1, adds28d: 1, trend: 1 } }), pack('viral', { publishedAt: daysAgo(2), stats: { adds: 99, adds28d: 99, trend: 99 } })],
    NOW
  );
  assert.deepEqual(ids(ranked), ['viral', 'm']);
});

const CATEGORIES = [
  { id: 'sorry', order: 4, icon: 'hand-heart', hue: 20, emojis: ['🥺'], names: { en: 'Sorry' }, keywords: { 'pt-BR': ['desculpa'] } },
  { id: 'couples', order: 1, icon: 'heart-handshake', hue: 10, emojis: ['💑'], names: { en: 'Couples' }, keywords: {} },
  { id: 'saudi', order: 11, icon: 'moon-star', hue: 250, emojis: ['☕'], names: { en: 'Saudi & Gulf' }, keywords: {}, tags: ['saudi', 'gulf'] },
];

test('the catalog lists categories in order with the live packs their tags bring', () => {
  const catalog = assembleCatalog({
    version: 7,
    now: NOW,
    categories: CATEGORIES,
    packs: [pack('a', { tags: ['sorry', 'couples', 'hug'] }), pack('b')],
  });
  assert.equal(catalog.schema, 1);
  assert.equal(catalog.version, 7);
  assert.equal(catalog.publishedAt, NOW.toISOString());
  assert.deepEqual(
    catalog.categories.map((c) => [c.id, c.packs]),
    [['couples', 1], ['sorry', 2], ['saudi', 0]]
  );
  assert.deepEqual(catalog.categories[1], {
    id: 'sorry',
    order: 4,
    icon: 'hand-heart',
    hue: 20,
    names: { en: 'Sorry' },
    keywords: { 'pt-BR': ['desculpa'] },
    tags: ['sorry'],
    packs: 2,
  });
  assert.deepEqual(catalog.categories[2].tags, ['saudi', 'gulf']);
});

test("a pack's first category tag is its category; the others are alsoIn", () => {
  const catalog = assembleCatalog({
    version: 1,
    now: NOW,
    categories: CATEGORIES,
    packs: [pack('coffee', { tags: ['coffee', 'gulf', 'couples'] }), pack('plain', { tags: ['coffee'] })],
  });
  const [coffee, plain] = catalog.packs;
  assert.deepEqual([coffee.category, coffee.alsoIn], ['saudi', ['couples']]);
  assert.deepEqual([plain.category, plain.alsoIn], ['', []]);
});

test('a record built before tags decided categories keeps its folder category and alsoIn', () => {
  const catalog = assembleCatalog({
    version: 1,
    now: NOW,
    categories: CATEGORIES,
    packs: [pack('old', { category: 'couples', alsoIn: ['sorry'], lang: 'ar', langs: undefined, tags: ['bunny'] })],
  });
  const [old] = catalog.packs;
  assert.deepEqual([old.category, old.alsoIn, old.lang, old.langs], ['couples', ['sorry'], 'ar', ['ar']]);
});

test('the catalog itself carries no search words: they have their own file', () => {
  const catalog = assembleCatalog({ version: 1, now: NOW, categories: CATEGORIES, packs: [pack('a')] });
  assert.deepEqual(Object.keys(catalog), ['schema', 'version', 'publishedAt', 'categories', 'packs']);
});

test('a catalog pack carries what the app needs and its public paths', () => {
  const catalog = assembleCatalog({
    version: 1,
    now: NOW,
    categories: CATEGORIES,
    packs: [
      pack('sorry-wiggle', {
        name: 'Sorry Wiggle',
        names: { ar: 'آسف' },
        langs: ['en', 'ar'],
        animated: true,
        version: 3,
        contentHash: 'a1b2c3d4'.repeat(8),
        zipBytes: 563412,
        tags: ['bunny', 'sorry', 'couples'],
        keywords: ['forgive me', 'pls'],
        stickers: [{ text: "I'm sorry!" }, { text: 'Forgive me' }, { text: '' }, { text: 'Sorry sorry' }],
        publishedAt: new Date('2026-09-29T14:05:00Z'),
        stats: { adds: 150, adds28d: 20, trend: 5 },
      }),
    ],
  });
  assert.deepEqual(catalog.packs[0], {
    id: 'sorry-wiggle',
    name: 'Sorry Wiggle',
    names: { ar: 'آسف' },
    category: 'sorry',
    alsoIn: ['couples'],
    lang: 'en',
    langs: ['en', 'ar'],
    tags: ['bunny', 'sorry', 'couples', 'animated'],
    animated: true,
    count: 6,
    version: 3,
    adds: 150,
    cover: {
      s: 'public/packs/sorry-wiggle/v3-a1b2c3d4/cover-s.webp',
      l: 'public/packs/sorry-wiggle/v3-a1b2c3d4/cover-l.webp',
      tiles: 6,
    },
    zip: { path: 'public/packs/sorry-wiggle/v3-a1b2c3d4/pack.zip', bytes: 563412 },
    keywords: ['bunny', 'sorry', 'couples', 'animated', 'forgive', 'me', 'pls'],
    publishedAt: '2026-09-29T14:05:00.000Z',
  });
});

test('keywords are capped at 60', () => {
  const words = Array.from({ length: 80 }, (_, i) => `word${i}`).join(' ');
  const catalog = assembleCatalog({ version: 1, now: NOW, categories: [], packs: [pack('a', { stickers: [{ text: words }] })] });
  assert.equal(catalog.packs[0].keywords.length, 60);
});
