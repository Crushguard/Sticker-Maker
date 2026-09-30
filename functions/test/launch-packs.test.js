'use strict';

// Builds every pack of the repository's library/ (the launch packs the emulators and the screen tour use) exactly as
// the cloud build would.

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('fs');
const path = require('path');
const { buildPackFromFiles } = require('../src/build');
const { parseCategoriesFile } = require('../src/categories');
const { slugify } = require('../src/library');

const LIBRARY = path.resolve(__dirname, '..', '..', 'library');
const hasLibrary = fs.existsSync(path.join(LIBRARY, '_categories.json'));

/** The pack folders of library/ (library/<folder>/). */
function packDirs() {
  return fs
    .readdirSync(LIBRARY)
    .filter((folder) => !folder.startsWith('_') && !folder.startsWith('.') && fs.statSync(path.join(LIBRARY, folder)).isDirectory())
    .map((folder) => ({ folder, dir: path.join(LIBRARY, folder) }));
}

test('every launch pack in library/ builds cleanly into at least one category', { skip: !hasLibrary && 'library/ not present yet' }, async () => {
  const { errors, categories } = parseCategoriesFile(fs.readFileSync(path.join(LIBRARY, '_categories.json'), 'utf8'));
  assert.deepEqual(errors, []);
  const dirs = packDirs();
  assert.ok(dirs.length >= 14, `found ${dirs.length} packs`);
  for (const { folder, dir } of dirs) {
    const names = fs.readdirSync(dir).filter((f) => !f.startsWith('.') && !f.startsWith('_'));
    const files = names.filter((f) => f !== 'pack.json').map((name) => ({ name, buffer: fs.readFileSync(path.join(dir, name)) }));
    const manifestText = names.includes('pack.json') ? fs.readFileSync(path.join(dir, 'pack.json'), 'utf8') : null;
    const result = await buildPackFromFiles({ packId: slugify(folder), folder, files, manifestText, categories, live: null });
    assert.deepEqual(result.errors, [], folder);
    assert.ok(result.categories.length >= 1, `${folder} is in a category`);
    const manifest = manifestText ? JSON.parse(manifestText) : {};
    assert.equal(result.animated, manifest.animate === 'wiggle', `${folder} animated flag`);
    assert.ok(result.record.stickers.every((s) => s.text !== undefined && s.emojis.length >= 1));
  }
});
