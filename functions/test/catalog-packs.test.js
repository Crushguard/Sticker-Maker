'use strict';

// Checks catalog/packs.json, the metadata scripts/library/prepare.js writes into every exported pack folder, against
// the library's _categories.json and _tags.json, with the pipeline's own parsers.

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('fs');
const path = require('path');
const { parseCategoriesFile, categoriesOf } = require('../src/categories');
const { parseTagsFile } = require('../src/tags');
const { parsePackManifest } = require('../src/manifest');
const { packJson } = require('../../scripts/library/prepare');

const ROOT = path.resolve(__dirname, '..', '..');
const CATALOG = path.join(ROOT, 'catalog', 'packs.json');
const read = (file) => fs.readFileSync(path.join(ROOT, file), 'utf8');
const present = fs.existsSync(CATALOG) && fs.existsSync(path.join(ROOT, 'library', '_tags.json'));
const skip = !present && 'catalog/packs.json or library/_tags.json not present yet';

function load() {
  const categories = parseCategoriesFile(read('library/_categories.json'));
  const vocabulary = parseTagsFile(read('library/_tags.json'));
  const { packs } = JSON.parse(read('catalog/packs.json'));
  return { categories, vocabulary, packs };
}

test('the categories and tags files parse without errors', { skip }, () => {
  const { categories, vocabulary } = load();
  assert.deepEqual(categories.errors, []);
  assert.deepEqual(vocabulary.errors, []);
  assert.equal(categories.categories.length, 14);
});

test('every listed pack is in a category, and every tag and language has search words', { skip }, () => {
  const { categories, vocabulary, packs } = load();
  const categoryIds = new Set(categories.categories.flatMap((c) => c.tags));
  const problems = [];
  for (const [id, pack] of Object.entries(packs)) {
    for (const lang of pack.lang) {
      const words = vocabulary.languages[lang] || vocabulary.languages[lang.split('-')[0]];
      if (lang !== 'none' && !words) problems.push(`${id}: language "${lang}" has no search words`);
    }
    if (pack.adult) continue;
    if (categoriesOf(pack.tags, categories.categories).length === 0) problems.push(`${id}: no tag names a category`);
    if (!categoryIds.has(pack.tags[0])) problems.push(`${id}: the first tag "${pack.tags[0]}" is not a category`);
    for (const tag of pack.tags) {
      if (!categoryIds.has(tag) && !vocabulary.tags[tag]) problems.push(`${id}: tag "${tag}" has no search words`);
    }
  }
  assert.deepEqual(problems, []);
});

test('every pack.json prepare.js writes parses cleanly', { skip }, () => {
  const { packs } = load();
  for (const [id, pack] of Object.entries(packs)) {
    const files = [...new Set(['01.webp', '02.webp', '03.webp', ...Object.keys(pack.stickers || {})])].sort();
    const text = JSON.stringify(packJson(pack, files));
    const manifest = parsePackManifest(text, [...files, 'tray.png'], { folder: id });
    assert.deepEqual([manifest.errors, manifest.notes], [[], []], id);
    assert.equal(manifest.adult, !!pack.adult, id);
    if (!pack.adult) {
      assert.deepEqual(manifest.tags, pack.tags, id);
      assert.ok(manifest.emojis.length >= 1, `${id} has pack emoji`);
    }
  }
});
