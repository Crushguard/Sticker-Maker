'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { withWordsFile } = require('../src/publishTask');

const catalog = (packs) => ({ schema: 1, version: 3, publishedAt: 'x', categories: [], packs });
const vocabulary = {
  tags: { hug: { en: ['hug'], ar: ['حضن'] }, cat: { en: ['cat', 'kitty'] } },
  languages: { pt: { en: ['portuguese'] }, ar: { en: ['arabic'] } },
  updatedAt: new Date(0),
};

test('the search words file is the whole vocabulary, keys sorted, and the catalog names it', () => {
  const { catalog: named, wordsPath, wordsJson } = withWordsFile(catalog([{ id: 'a' }]), vocabulary);
  assert.match(wordsPath, /^public\/catalog\/words-[0-9a-f]{16}\.json\.gz$/);
  assert.deepEqual(named, { ...catalog([{ id: 'a' }]), words: { path: wordsPath } });
  assert.equal(
    wordsJson,
    JSON.stringify({
      languages: { ar: { en: ['arabic'] }, pt: { en: ['portuguese'] } },
      tags: { cat: { en: ['cat', 'kitty'] }, hug: { ar: ['حضن'], en: ['hug'] } },
    })
  );
});

test('its name changes only with the vocabulary, not with the packs, their ranking or key order', () => {
  const a = withWordsFile(catalog([{ id: 'a' }, { id: 'b' }]), vocabulary);
  const reordered = { languages: { ar: vocabulary.languages.ar, pt: vocabulary.languages.pt }, tags: { cat: vocabulary.tags.cat, hug: vocabulary.tags.hug } };
  const b = withWordsFile(catalog([{ id: 'b' }]), reordered);
  const c = withWordsFile(catalog([{ id: 'a' }]), { ...vocabulary, tags: { ...vocabulary.tags, cat: { en: ['cat'] } } });
  assert.equal(a.wordsPath, b.wordsPath);
  assert.notEqual(a.wordsPath, c.wordsPath);
});
