'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const sharp = require('sharp');
const { unzipSync, strFromU8 } = require('fflate');
const { buildPackFromFiles } = require('../src/build');
const { checkPack } = require('../src/checks');
const { renderReport } = require('../src/report');
const { shapePng, animated, meta } = require('./helpers');

const COLORS = ['#C23359', '#2E9E6B', '#3B6DD4', '#E0A64B'];
const stickerFiles = (n) =>
  Promise.all(
    Array.from({ length: n }, async (_, i) => ({
      name: `${i + 1}.png`,
      buffer: await shapePng({ color: COLORS[i % COLORS.length], cx: 0.4 + i * 0.05 }),
    }))
  );

const CATEGORIES = [
  { id: 'sorry', emojis: ['🥺', '🙏'] },
  { id: 'couples', emojis: ['💑'] },
  { id: 'saudi', tags: ['gulf'], emojis: ['☕'] },
];

const base = (files, extra = {}) => ({
  packId: 'sorry-wiggle',
  folder: 'Sorry Wiggle',
  files,
  manifestText: '{"tags":["sorry"]}',
  categories: CATEGORIES,
  live: null,
  ...extra,
});

test('a folder of three stickers builds version 1 with a zip and two cover strips', async () => {
  const result = await buildPackFromFiles(base(await stickerFiles(3)));
  assert.deepEqual(result.errors, []);
  assert.equal(result.ok, true);
  assert.equal(result.unchanged, false);
  assert.equal(result.version, 1);
  assert.equal(result.count, 3);
  assert.equal(result.animated, false);
  assert.match(result.contentHash, /^[0-9a-f]{64}$/);

  const files = unzipSync(new Uint8Array(result.outputs.zip));
  assert.deepEqual(Object.keys(files), ['contents.json', 'tray.png', '01.webp', '02.webp', '03.webp']);
  const contents = JSON.parse(strFromU8(files['contents.json']));
  assert.equal(contents.version, 1);
  assert.equal(contents.name, 'Sorry Wiggle');
  assert.deepEqual(contents.stickers[0], { file: '01.webp', emojis: ['🥺', '🙏'], text: '' });

  assert.deepEqual([(await meta(result.outputs.coverS)).width, (await meta(result.outputs.coverS)).height], [288, 96]);
  assert.deepEqual([(await meta(result.outputs.coverL)).width, (await meta(result.outputs.coverL)).height], [480, 160]);

  const r = result.record;
  assert.equal(r.name, 'Sorry Wiggle');
  assert.deepEqual(r.tags, ['sorry']);
  assert.deepEqual(r.langs, ['en']);
  assert.deepEqual(result.categories, ['sorry']);
  assert.equal(r.version, 1);
  assert.equal(r.count, 3);
  assert.equal(r.zipBytes, result.outputs.zip.length);
  assert.equal(r.coverTiles, 3);
  assert.deepEqual(
    r.stickers.map((s) => [s.file, s.source]),
    [['01.webp', '1.png'], ['02.webp', '2.png'], ['03.webp', '3.png']]
  );
  assert.ok(result.notes.some((n) => n.includes('No tray.png')));
});

test("pack.json tags choose the categories; the pack's emoji fill stickers without their own", async () => {
  const manifestText = JSON.stringify({
    lang: ['ar', 'en'],
    tags: ['gulf', 'coffee', 'couples'],
    keywords: ['gahwa'],
    emojis: ['☕', '❤️'],
    stickers: { '2.png': { emojis: ['🌙'] } },
  });
  const result = await buildPackFromFiles(base(await stickerFiles(3), { manifestText }));
  assert.deepEqual(result.errors, []);
  assert.deepEqual(result.categories, ['saudi', 'couples']);
  assert.deepEqual(result.record.tags, ['gulf', 'coffee', 'couples']);
  assert.deepEqual(result.record.langs, ['ar', 'en']);
  assert.deepEqual(result.record.keywords, ['gahwa']);
  assert.deepEqual(result.record.stickers.map((s) => s.emojis), [['☕', '❤️'], ['🌙'], ['☕', '❤️']]);
  assert.ok(!result.notes.some((n) => n.includes('emoji')), result.notes.join(' | '));
  assert.equal(result.record.category, undefined);
});

test("a pack with no emoji of its own takes its first category's", async () => {
  const result = await buildPackFromFiles(base(await stickerFiles(3), { manifestText: '{"tags":["couples","sorry"]}' }));
  assert.deepEqual(result.record.stickers[0].emojis, ['💑']);
  assert.ok(result.notes.some((n) => n.includes("category's emoji 💑")));
});

test('building the same files again against the live version changes nothing', async () => {
  const files = await stickerFiles(3);
  const first = await buildPackFromFiles(base(files));
  const again = await buildPackFromFiles(
    base(files, { live: { version: first.version, contentHash: first.contentHash, animated: false } })
  );
  assert.equal(again.ok, true);
  assert.equal(again.unchanged, true);
  assert.equal(again.version, 1);
  assert.equal(again.contentHash, first.contentHash);
});

test('changed art against a live version becomes the next version', async () => {
  const files = await stickerFiles(3);
  const live = { version: 4, contentHash: 'f'.repeat(64), animated: false };
  const result = await buildPackFromFiles(base(files, { live }));
  assert.equal(result.unchanged, false);
  assert.equal(result.version, 5);
  const contents = JSON.parse(strFromU8(unzipSync(new Uint8Array(result.outputs.zip))['contents.json']));
  assert.equal(contents.version, 5);
});

test('a changed pack takes a version above every published one, even when its record is gone', async () => {
  const files = await stickerFiles(3);
  const result = await buildPackFromFiles(base(files, { published: [{ version: 4, hash: 'deadbeef' }, { version: 2, hash: null }] }));
  assert.equal(result.version, 5);
  const live = { version: 6, contentHash: 'f'.repeat(64), animated: false };
  assert.equal((await buildPackFromFiles(base(files, { live, published: [{ version: 4, hash: 'deadbeef' }] }))).version, 7);
});

test('a retry reuses the version its earlier attempt published these exact files under', async () => {
  const files = await stickerFiles(3);
  const first = await buildPackFromFiles(base(files));
  const hash = first.contentHash.slice(0, 8);
  const retry = await buildPackFromFiles(base(files, { published: [{ version: 1, hash }] }));
  assert.equal(retry.version, 1);
  assert.deepEqual(retry.outputs.zip, first.outputs.zip);
  // Files that went out under an older number never bring that number back.
  const later = await buildPackFromFiles(base(files, { published: [{ version: 1, hash }, { version: 2, hash: 'deadbeef' }] }));
  assert.equal(later.version, 3);
});

test('two stickers are not a pack', async () => {
  const result = await buildPackFromFiles(base(await stickerFiles(2)));
  assert.equal(result.ok, false);
  assert.ok(result.errors.includes('Only 2 stickers: WhatsApp needs 3 to 30.'));
  assert.equal(result.outputs, null);
});

test('mixing static and animated stickers fails', async () => {
  const files = await stickerFiles(2);
  files.push({ name: '3.webp', buffer: await animated() });
  const result = await buildPackFromFiles(base(files));
  assert.equal(result.ok, false);
  assert.ok(result.errors.some((e) => e.includes('all one or the other')));
});

test('a live static pack cannot become animated', async () => {
  const files = [
    { name: '1.webp', buffer: await animated() },
    { name: '2.webp', buffer: await animated() },
    { name: '3.webp', buffer: await animated() },
  ];
  const result = await buildPackFromFiles(base(files, { live: { version: 2, contentHash: 'x', animated: false } }));
  assert.equal(result.ok, false);
  assert.ok(result.errors.some((e) => e.includes("can't switch")));
});

test('pack.json errors stop the build before any image work', async () => {
  const result = await buildPackFromFiles(base(await stickerFiles(3), { manifestText: '{oops' }));
  assert.equal(result.ok, false);
  assert.match(result.errors[0], /^pack\.json is not valid JSON/);
});

test('files that are not images are skipped with a note', async () => {
  const files = await stickerFiles(3);
  files.push({ name: 'notes.txt', buffer: Buffer.from('hi') }, { name: 'art.psd', buffer: Buffer.from('x') });
  const result = await buildPackFromFiles(base(files));
  assert.equal(result.ok, true);
  assert.ok(result.notes.some((n) => n.includes('notes.txt') && n.includes('art.psd')));
});

test('a sticker that cannot meet the limits fails the pack and names the file', async () => {
  const files = await stickerFiles(3);
  files[1] = { name: '2.gif', buffer: await animated({ format: 'gif', delays: [4000, 4000, 4000] }) };
  const result = await buildPackFromFiles(base(files));
  assert.equal(result.ok, false);
  assert.ok(result.errors.some((e) => e.startsWith('2.gif: ')));
});

test('wiggle packs come out animated and the tray is made from the art', async () => {
  const files = await stickerFiles(3);
  const result = await buildPackFromFiles(base(files, { manifestText: '{"animate":"wiggle"}' }));
  assert.equal(result.ok, true);
  assert.equal(result.animated, true);
  const zip = unzipSync(new Uint8Array(result.outputs.zip));
  assert.equal((await sharp(Buffer.from(zip['01.webp']), { animated: true }).metadata()).pages, 4);
  assert.equal((await meta(Buffer.from(zip['tray.png']))).width, 96);
});

test('checkPack reports counts, mixed kinds and kind changes', () => {
  assert.deepEqual(checkPack({ count: 3, kinds: new Set(['static']), liveAnimated: null, emojiCounts: [1, 2, 3] }), []);
  assert.deepEqual(checkPack({ count: 31, kinds: new Set(['static']), liveAnimated: null, emojiCounts: [] }), [
    '31 stickers: WhatsApp needs 3 to 30.',
  ]);
  assert.equal(checkPack({ count: 1, kinds: new Set(['static']), liveAnimated: null, emojiCounts: [1] })[0],
    'Only 1 sticker: WhatsApp needs 3 to 30.');
  assert.equal(
    checkPack({ count: 3, kinds: new Set(['static']), liveAnimated: true, emojiCounts: [1, 1, 1] }).length,
    1
  );
});

test('the report says what went live, or why not', () => {
  const at = new Date('2026-09-29T14:05:30Z');
  assert.equal(
    renderReport({
      ok: true, unchanged: false, name: 'Sorry Wiggle', version: 3, count: 6, animated: true,
      categories: ['Sorry', 'Couples'], liveVersion: 2, errors: [], notes: ['No tray.png: made from 01.webp.'], at,
    }),
    '✅ Sorry Wiggle is live: version 3, 6 animated stickers, in Sorry, Couples. Built 2026-09-29 14:05 UTC.\n' +
      'Notes:\n- No tray.png: made from 01.webp.\n'
  );
  assert.equal(
    renderReport({
      ok: false, name: 'Sorry Wiggle', version: null, count: 2, animated: false, categories: [],
      liveVersion: 2, errors: ['Only 2 stickers: WhatsApp needs 3 to 30.'], notes: [], at,
    }),
    '❌ Sorry Wiggle was not published; version 2 stays live. Checked 2026-09-29 14:05 UTC.\n' +
      '- Only 2 stickers: WhatsApp needs 3 to 30.\n'
  );
  assert.match(
    renderReport({
      ok: true, unchanged: true, name: 'Pack', version: 3, count: 6, animated: false, categories: ['Sorry'],
      liveVersion: 3, errors: [], notes: [], at,
    }),
    /^✅ Pack is live and unchanged: version 3, 6 static stickers, in Sorry\./
  );
  assert.match(
    renderReport({ ok: true, name: 'Loose', version: 1, count: 3, animated: false, categories: [], errors: [], notes: [], at }),
    /in no category \(no tag names one\): it shows in Trending and search\./
  );
  assert.match(
    renderReport({ ok: false, name: 'New', version: null, count: 0, animated: false, categories: [],
      liveVersion: null, errors: ['x'], notes: [], at }),
    /^❌ New was not published\. Checked/
  );
  assert.equal(
    renderReport({ parked: true, name: 'Night Pack', at }),
    '⏸ Night Pack is not published: After Dark (18+) packs stay out of the Google Play build. Checked 2026-09-29 14:05 UTC.\n'
  );
});

test('a corrupt tray.png fails the pack with a report line instead of crashing the build', async () => {
  const files = await stickerFiles(3);
  files.push({ name: 'tray.png', buffer: Buffer.from('not an image at all') });
  const result = await buildPackFromFiles(base(files));
  assert.equal(result.ok, false);
  assert.ok(result.errors.some((e) => e.startsWith('tray.png: ')), result.errors.join(' | '));
});

test('a truncated sticker that cannot be decoded fails the pack and names the file', async () => {
  const files = await stickerFiles(3);
  const whole = await sharp(await shapePng({ color: '#123456' })).png({ compressionLevel: 0 }).toBuffer();
  files[2] = { name: '3.png', buffer: whole.subarray(0, Math.floor(whole.length / 2)) };
  const result = await buildPackFromFiles(base(files));
  assert.equal(result.ok, false);
  assert.ok(result.errors.some((e) => e.startsWith('3.png: ')), result.errors.join(' | '));
});
