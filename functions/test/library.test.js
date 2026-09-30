'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { folderKey, slugify, libraryPrefix, parseLibraryPath, isImageName, naturalCompare } = require('../src/library');

test('slugify turns a folder name into a pack id', () => {
  assert.equal(slugify('Sorry Wiggle'), 'sorry-wiggle');
  assert.equal(slugify('Flirty & Shy'), 'flirty-shy');
  assert.equal(slugify('Café Crème'), 'cafe-creme');
  assert.equal(slugify('  --Big   Words!!  '), 'big-words');
  assert.equal(slugify('gm-gn'), 'gm-gn');
});

test('slugify keeps ids at most 64 characters without a trailing hyphen', () => {
  const id = slugify('a'.repeat(63) + ' b');
  assert.ok(id.length <= 64);
  assert.ok(!id.endsWith('-'));
});

test('slugify falls back to a stable hashed id for names with no latin letters', () => {
  const arabic = slugify('حب');
  assert.match(arabic, /^pack-[0-9a-f]{8}$/);
  assert.equal(slugify('حب'), arabic);
  assert.match(slugify('💖'), /^pack-[0-9a-f]{8}$/);
  assert.notEqual(slugify('💖'), arabic);
  assert.match(slugify('x'), /^pack-[0-9a-f]{8}$/);
});

test('parseLibraryPath recognizes the categories and tags files', () => {
  assert.deepEqual(parseLibraryPath('library/_categories.json'), { kind: 'categories' });
  assert.deepEqual(parseLibraryPath('library/_tags.json'), { kind: 'tags' });
});

test('parseLibraryPath splits a pack file into folder, id and file', () => {
  assert.deepEqual(parseLibraryPath('library/Sorry Wiggle/01.webp'), {
    kind: 'pack',
    category: null,
    folder: 'Sorry Wiggle',
    packId: 'sorry-wiggle',
    file: '01.webp',
    prefix: 'library/Sorry Wiggle/',
  });
});

test('parseLibraryPath still reads the older library/<category>/<folder>/ layout', () => {
  assert.deepEqual(parseLibraryPath('library/sorry/Sorry Wiggle/01.webp'), {
    kind: 'pack',
    category: 'sorry',
    folder: 'Sorry Wiggle',
    packId: 'sorry-wiggle',
    file: '01.webp',
    prefix: 'library/sorry/Sorry Wiggle/',
  });
});

test('parseLibraryPath reports the build report as its own kind', () => {
  assert.deepEqual(parseLibraryPath('library/Sorry Wiggle/_report.txt'), { kind: 'report' });
  assert.deepEqual(parseLibraryPath('library/sorry/Sorry Wiggle/_report.txt'), { kind: 'report' });
});

test('parseLibraryPath ignores staging, underscored folders and junk', () => {
  for (const name of [
    'library/_staging/Pack/01.png',
    'library/_old/01.png',
    'library/sorry/_old/01.png',
    'library/Pack/',
    'library/sorry/Pack/',
    'library/Pack/.DS_Store',
    'library/sorry/Pack/Thumbs.db',
    'library/Pack/desktop.ini',
    'library/Pack/._01.png',
    'library/Pack/_notes.txt',
    'library/contents.json',
    'library/_categories_report.txt',
    'library/a/Pack/extra/01.png',
    'public/packs/sorry-wiggle/v1/pack.zip',
    'library/',
  ]) {
    assert.deepEqual(parseLibraryPath(name), { kind: 'ignored' }, name);
  }
});

test('isImageName accepts png, webp and gif in any case', () => {
  assert.ok(isImageName('01.png'));
  assert.ok(isImageName('01.WEBP'));
  assert.ok(isImageName('dance.Gif'));
  assert.ok(!isImageName('01.jpg'));
  assert.ok(!isImageName('pack.json'));
  assert.ok(!isImageName('tray'));
});

test('naturalCompare orders numbers inside names numerically', () => {
  const names = ['10.png', '2.png', '1.png', 'b 3.png', 'b 12.png'];
  assert.deepEqual([...names].sort(naturalCompare), ['1.png', '2.png', '10.png', 'b 3.png', 'b 12.png']);
});

test('folderKey gives each folder prefix a stable key Firestore accepts', () => {
  const key = folderKey('library/Love Notes/');
  assert.match(key, /^[0-9a-f]{20}$/);
  assert.equal(folderKey('library/Love Notes/'), key);
  assert.notEqual(folderKey('library/cute/Love Notes/'), key);
  assert.notEqual(folderKey('library/love notes/'), key);
});

test('libraryPrefix names a flat folder, or one inside a category folder', () => {
  assert.equal(libraryPrefix(null, 'Love Notes'), 'library/Love Notes/');
  assert.equal(libraryPrefix('cute', 'Love Notes'), 'library/cute/Love Notes/');
});
