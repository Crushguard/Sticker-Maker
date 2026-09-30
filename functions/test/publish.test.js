'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { withWordsFile } = require('../src/publishTask');

const assembled = (tags) => ({ schema: 1, version: 3, categories: [], packs: [{ id: 'a' }], tags, languages: { ar: { en: ['arabic'] } } });

test('the search words move to their own file, named by their content', () => {
  const { catalog, wordsPath, wordsJson } = withWordsFile(assembled({ cat: { en: ['cat'] } }));
  assert.match(wordsPath, /^public\/catalog\/words-[0-9a-f]{16}\.json\.gz$/);
  assert.deepEqual(catalog, { schema: 1, version: 3, categories: [], packs: [{ id: 'a' }], words: { path: wordsPath } });
  assert.deepEqual(JSON.parse(wordsJson), { tags: { cat: { en: ['cat'] } }, languages: { ar: { en: ['arabic'] } } });
});

test('the same words keep their file across catalog versions; other words get another', () => {
  const a = withWordsFile(assembled({ cat: { en: ['cat'] } }));
  const b = withWordsFile({ ...assembled({ cat: { en: ['cat'] } }), version: 9, packs: [] });
  const c = withWordsFile(assembled({ cat: { en: ['cat', 'kitty'] } }));
  assert.equal(a.wordsPath, b.wordsPath);
  assert.notEqual(a.wordsPath, c.wordsPath);
});
