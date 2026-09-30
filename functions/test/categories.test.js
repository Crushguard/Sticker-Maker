'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { parseCategoriesFile, categoriesOf, categoryTags } = require('../src/categories');

test('categories are read with their fields and defaults', () => {
  const { errors, categories } = parseCategoriesFile(
    JSON.stringify({
      categories: [
        {
          id: 'couples',
          order: 1,
          icon: 'heart-handshake',
          hue: 10,
          emojis: ['💑', '❤️'],
          names: { en: 'Couples', ar: 'الأزواج' },
          keywords: { 'pt-BR': ['namorados', ' '] },
          tags: ['Couple', ' ', 'couples'],
        },
        { id: 'missyou', names: { en: 'Miss you' } },
      ],
    })
  );
  assert.deepEqual(errors, []);
  assert.deepEqual(categories[0], {
    id: 'couples',
    order: 1,
    icon: 'heart-handshake',
    hue: 10,
    emojis: ['💑', '❤️'],
    names: { en: 'Couples', ar: 'الأزواج' },
    keywords: { 'pt-BR': ['namorados'] },
    tags: ['couples', 'couple'],
  });
  assert.deepEqual(categories[1], {
    id: 'missyou',
    order: 2,
    icon: 'heart',
    hue: 340,
    emojis: ['❤️'],
    names: { en: 'Miss you' },
    keywords: {},
    tags: ['missyou'],
  });
});

test("a category's tags are its id plus the ones it lists", () => {
  assert.deepEqual(categoryTags({ id: 'saudi' }), ['saudi']);
  assert.deepEqual(categoryTags({ id: 'saudi', tags: ['gulf', 'saudi', 'Khaleeji'] }), ['saudi', 'gulf', 'khaleeji']);
});

test("a pack is in every category one of its tags names, in the order of its tags", () => {
  const categories = [
    { id: 'cute', tags: ['cute'] },
    { id: 'funny' },
    { id: 'saudi', tags: ['gulf'] },
  ];
  assert.deepEqual(categoriesOf(['cat', 'funny', 'cute', 'funny'], categories), ['funny', 'cute']);
  assert.deepEqual(categoriesOf(['gulf', 'coffee'], categories), ['saudi']);
  assert.deepEqual(categoriesOf(['coffee'], categories), []);
});

test('malformed JSON is an error', () => {
  const { errors, categories } = parseCategoriesFile('{');
  assert.match(errors[0], /^_categories\.json is not valid JSON: /);
  assert.deepEqual(categories, []);
});

test('a category without an English name, with a bad id or a duplicate id is skipped with an error', () => {
  const { errors, categories } = parseCategoriesFile(
    JSON.stringify({
      categories: [
        { id: 'sorry', names: { en: 'Sorry' } },
        { id: 'Good Morning', names: { en: 'Good morning' } },
        { id: 'flirty', names: { fr: 'Coquin' } },
        { id: 'sorry', names: { en: 'Sorry again' } },
      ],
    })
  );
  assert.deepEqual(categories.map((c) => c.id), ['sorry']);
  assert.equal(errors.length, 3);
});
