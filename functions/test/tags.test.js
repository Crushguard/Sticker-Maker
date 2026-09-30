'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { parseTagsFile } = require('../src/tags');

test('tags and languages are read as search words by app language', () => {
  const { errors, tags, languages } = parseTagsFile(
    JSON.stringify({
      tags: {
        cat: { en: ['Cat', ' kitty ', 'cat'], ar: ['قطة'], xx_bad: ['nope'], fr: [] },
        'miss-you': { en: ['miss you'], 'pt-BR': ['saudade'] },
      },
      languages: { ar: { en: ['arabic'], ar: ['عربي'] }, multi: { en: ['all languages'] } },
    })
  );
  assert.deepEqual(errors, []);
  assert.deepEqual(tags, {
    cat: { en: ['cat', 'kitty'], ar: ['قطة'] },
    'miss-you': { en: ['miss you'], 'pt-BR': ['saudade'] },
  });
  assert.deepEqual(languages, { ar: { en: ['arabic'], ar: ['عربي'] }, multi: { en: ['all languages'] } });
});

test('bad ids and entries without words are skipped with an error each', () => {
  const { errors, tags } = parseTagsFile(
    JSON.stringify({ tags: { 'Big Cat': { en: ['x'] }, empty: { en: [' '] }, ok: { en: ['ok'] } }, languages: [] })
  );
  assert.deepEqual(Object.keys(tags), ['ok']);
  assert.equal(errors.length, 3);
});

test('malformed JSON is an error and yields nothing', () => {
  const result = parseTagsFile('{');
  assert.match(result.errors[0], /^_tags\.json is not valid JSON: /);
  assert.deepEqual([result.tags, result.languages], [{}, {}]);
});
