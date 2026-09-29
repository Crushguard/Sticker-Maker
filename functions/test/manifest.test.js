'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { parsePackManifest } = require('../src/manifest');

const OPTS = {
  folder: 'Sorry Wiggle',
  category: 'sorry',
  categoryIds: new Set(['sorry', 'couples', 'romantic', 'missyou']),
  defaultEmojis: ['🥺', '🙏'],
};
const IMAGES = ['10.png', '2.png', '1.png', 'tray.png'];

test('without pack.json every image except the tray is a sticker, in natural order, with defaults', () => {
  const m = parsePackManifest(null, IMAGES, OPTS);
  assert.deepEqual(m.errors, []);
  assert.equal(m.listed, false);
  assert.equal(m.name, 'Sorry Wiggle');
  assert.deepEqual(m.names, {});
  assert.deepEqual(m.alsoIn, []);
  assert.equal(m.lang, 'en');
  assert.deepEqual(m.tags, []);
  assert.equal(m.order, 1000);
  assert.equal(m.animate, null);
  assert.equal(m.tray, 'tray.png');
  assert.deepEqual(m.stickers.map((s) => s.file), ['1.png', '2.png', '10.png']);
  assert.deepEqual(m.stickers[0].emojis, ['🥺', '🙏']);
  assert.equal(m.stickers[0].text, '');
  assert.deepEqual(m.cover, ['1.png', '2.png', '10.png']);
  assert.equal(m.notes.filter((n) => n.includes("category's emoji")).length, 1);
});

test('malformed pack.json is an error naming the parser message', () => {
  const m = parsePackManifest('{ "name": ', IMAGES, OPTS);
  assert.equal(m.errors.length, 1);
  assert.match(m.errors[0], /^pack\.json is not valid JSON: /);
});

test('pack.json that is not an object is an error', () => {
  const m = parsePackManifest('["a"]', IMAGES, OPTS);
  assert.match(m.errors[0], /pack\.json must be an object/);
});

test('name, names, lang, tags, order and animate come from pack.json', () => {
  const m = parsePackManifest(
    JSON.stringify({
      name: '  Sorry, My Love ',
      names: { ar: 'آسف يا حبيبي', fr: '' },
      lang: 'pt-BR',
      tags: ['Pinky', 'pinky', ' apology '],
      order: 5,
      animate: 'wiggle',
    }),
    IMAGES,
    OPTS
  );
  assert.deepEqual(m.errors, []);
  assert.equal(m.name, 'Sorry, My Love');
  assert.deepEqual(m.names, { ar: 'آسف يا حبيبي' });
  assert.equal(m.lang, 'pt-BR');
  assert.deepEqual(m.tags, ['pinky', 'apology']);
  assert.equal(m.order, 5);
  assert.equal(m.animate, 'wiggle');
});

test('lang "none" marks text-free art and junk lang falls back to en with a note', () => {
  assert.equal(parsePackManifest('{"lang":"none"}', IMAGES, OPTS).lang, 'none');
  const m = parsePackManifest('{"lang":"English please"}', IMAGES, OPTS);
  assert.equal(m.lang, 'en');
  assert.ok(m.notes.some((n) => n.includes('lang')));
});

test('a name longer than 128 characters is an error', () => {
  const m = parsePackManifest(JSON.stringify({ name: 'x'.repeat(129) }), IMAGES, OPTS);
  assert.ok(m.errors.some((e) => e.includes('128')));
});

test('alsoIn keeps at most two known categories other than its own', () => {
  const m = parsePackManifest(
    JSON.stringify({ alsoIn: ['sorry', 'couples', 'nope', 'romantic', 'missyou'] }),
    IMAGES,
    OPTS
  );
  assert.deepEqual(m.alsoIn, ['couples', 'romantic']);
  assert.ok(m.notes.some((n) => n.includes('nope')));
});

test('unknown animate values are ignored with a note', () => {
  const m = parsePackManifest('{"animate":"spin"}', IMAGES, OPTS);
  assert.equal(m.animate, null);
  assert.ok(m.notes.some((n) => n.includes('animate')));
});

test('a stickers array lists exactly the pack, in its order, with emojis and text', () => {
  const m = parsePackManifest(
    JSON.stringify({
      stickers: [
        { file: '10.png', emojis: ['😢'], text: '  Forgive me  ' },
        { file: '1.png', emojis: ['🥺', '💔', '🙏', '❤️'] },
        { file: '2.png', emojis: [] },
      ],
    }),
    IMAGES,
    OPTS
  );
  assert.deepEqual(m.errors, []);
  assert.equal(m.listed, true);
  assert.deepEqual(m.stickers.map((s) => s.file), ['10.png', '1.png', '2.png']);
  assert.deepEqual(m.stickers[0], { file: '10.png', emojis: ['😢'], text: 'Forgive me' });
  assert.deepEqual(m.stickers[1].emojis, ['🥺', '💔', '🙏']);
  assert.deepEqual(m.stickers[2].emojis, ['🥺', '🙏']);
});

test('a listed file that is missing is an error and unlisted images are noted', () => {
  const m = parsePackManifest(
    JSON.stringify({ stickers: [{ file: '1.png' }, { file: '3.png' }] }),
    IMAGES,
    OPTS
  );
  assert.ok(m.errors.includes('3.png is listed but missing'));
  assert.ok(m.notes.some((n) => n.includes('2.png') && n.includes('10.png')));
});

test('a stickers object only overrides emojis and text of the files it names', () => {
  const m = parsePackManifest(
    JSON.stringify({ stickers: { '2.png': { emojis: ['😘'], text: 'Kiss' } } }),
    IMAGES,
    OPTS
  );
  assert.equal(m.listed, false);
  assert.deepEqual(m.stickers.map((s) => s.file), ['1.png', '2.png', '10.png']);
  assert.deepEqual(m.stickers[1], { file: '2.png', emojis: ['😘'], text: 'Kiss' });
});

test('emoji entries that are not emoji are dropped', () => {
  const m = parsePackManifest(
    JSON.stringify({ stickers: [{ file: '1.png', emojis: ['love', '❤️', ''] }] }),
    IMAGES,
    OPTS
  );
  assert.deepEqual(m.stickers[0].emojis, ['❤️']);
});

test('text is capped at 255 characters', () => {
  const m = parsePackManifest(
    JSON.stringify({ stickers: [{ file: '1.png', text: 'a'.repeat(300) }] }),
    IMAGES,
    OPTS
  );
  assert.equal(m.stickers[0].text.length, 255);
});

test('cover keeps listed stickers only and falls back to the first six', () => {
  const images = ['1.png', '2.png', '3.png', '4.png', '5.png', '6.png', '7.png'];
  assert.deepEqual(
    parsePackManifest(JSON.stringify({ cover: ['7.png', '3.png', 'zz.png'] }), images, OPTS).cover,
    ['7.png', '3.png']
  );
  assert.deepEqual(parsePackManifest(null, images, OPTS).cover, images.slice(0, 6));
  assert.deepEqual(parsePackManifest(JSON.stringify({ cover: ['zz.png'] }), images, OPTS).cover, images.slice(0, 6));
});

test('a pack without tray.png has no tray file', () => {
  assert.equal(parsePackManifest(null, ['1.png', '2.png', '3.png'], OPTS).tray, null);
  assert.equal(parsePackManifest(null, ['1.png', 'TRAY.PNG'], OPTS).tray, 'TRAY.PNG');
});
