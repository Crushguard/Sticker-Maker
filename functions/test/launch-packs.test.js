'use strict';

// Builds every pack of the repository's library/ exactly as the cloud build would.

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('fs');
const path = require('path');
const { buildPackFromFiles } = require('../src/build');
const { parseCategoriesFile } = require('../src/categories');
const { slugify } = require('../src/library');

const LIBRARY = path.resolve(__dirname, '..', '..', 'library');
const hasLibrary = fs.existsSync(path.join(LIBRARY, '_categories.json'));

function packDirs() {
  const dirs = [];
  for (const category of fs.readdirSync(LIBRARY)) {
    const categoryDir = path.join(LIBRARY, category);
    if (category.startsWith('_') || !fs.statSync(categoryDir).isDirectory()) continue;
    for (const folder of fs.readdirSync(categoryDir)) {
      if (folder.startsWith('_') || !fs.statSync(path.join(categoryDir, folder)).isDirectory()) continue;
      dirs.push({ category, folder, dir: path.join(categoryDir, folder) });
    }
  }
  return dirs;
}

test('every launch pack in library/ builds cleanly', { skip: !hasLibrary && 'library/ not present yet' }, async () => {
  const { errors, categories } = parseCategoriesFile(fs.readFileSync(path.join(LIBRARY, '_categories.json'), 'utf8'));
  assert.deepEqual(errors, []);
  const byId = new Map(categories.map((c) => [c.id, c]));
  const dirs = packDirs();
  assert.ok(dirs.length >= 14, `found ${dirs.length} packs`);
  for (const { category, folder, dir } of dirs) {
    assert.ok(byId.has(category), `${category} is in _categories.json`);
    const names = fs.readdirSync(dir).filter((f) => !f.startsWith('.') && !f.startsWith('_'));
    const files = names.filter((f) => f !== 'pack.json').map((name) => ({ name, buffer: fs.readFileSync(path.join(dir, name)) }));
    const manifestText = names.includes('pack.json') ? fs.readFileSync(path.join(dir, 'pack.json'), 'utf8') : null;
    const result = await buildPackFromFiles({
      packId: slugify(folder),
      category,
      folder,
      files,
      manifestText,
      categoryIds: new Set(byId.keys()),
      defaultEmojis: byId.get(category).emojis,
      live: null,
    });
    assert.deepEqual(result.errors, [], `${category}/${folder}`);
    const manifest = manifestText ? JSON.parse(manifestText) : {};
    assert.equal(result.animated, manifest.animate === 'wiggle', `${folder} animated flag`);
    assert.ok(result.record.stickers.every((s) => s.text !== undefined && s.emojis.length >= 1));
  }
});
