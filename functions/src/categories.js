'use strict';

const { cleanEmojis, cleanNames } = require('./manifest');

const ID = /^[a-z0-9]+(-[a-z0-9]+)*$/;
const MAX_TAG = 32;
const DEFAULT_ICON = 'heart';
const DEFAULT_HUE = 340;
const FALLBACK_EMOJIS = ['❤️'];

function cleanKeywords(value) {
  const keywords = {};
  if (value && typeof value === 'object' && !Array.isArray(value)) {
    for (const [lang, words] of Object.entries(value)) {
      if (!Array.isArray(words)) continue;
      const clean = words.filter((w) => typeof w === 'string').map((w) => w.trim()).filter((w) => w !== '');
      if (clean.length) keywords[lang] = [...new Set(clean)];
    }
  }
  return keywords;
}

/** The pack tags that put a pack in a category: its own id, plus any the file lists (e.g. "gulf" for saudi). */
function categoryTags(category) {
  const listed = Array.isArray(category.tags) ? category.tags : [];
  const tags = listed.filter((t) => typeof t === 'string').map((t) => t.trim().toLowerCase().slice(0, MAX_TAG));
  return [...new Set([category.id, ...tags.filter((t) => t !== '')])];
}

/**
 * The categories a pack shows under: every category one of its tags belongs to, in the order of the pack's tags,
 * so the first is the one its first category tag names.
 *
 * @param {string[]} tags the pack's tags
 * @param {{id: string, tags?: string[]}[]} categories
 */
function categoriesOf(tags, categories) {
  const ids = [];
  for (const tag of tags) {
    for (const category of categories) {
      if (!ids.includes(category.id) && categoryTags(category).includes(tag)) ids.push(category.id);
    }
  }
  return ids;
}

/**
 * Reads library/_categories.json. Bad entries are skipped with an error each; the rest stay usable.
 */
function parseCategoriesFile(text) {
  const errors = [];
  let json;
  try {
    json = JSON.parse(text);
  } catch (err) {
    return { errors: [`_categories.json is not valid JSON: ${err.message}`], categories: [] };
  }
  const list = json && Array.isArray(json.categories) ? json.categories : null;
  if (!list) return { errors: ['_categories.json needs a "categories" list.'], categories: [] };

  const categories = [];
  const seen = new Set();
  list.forEach((entry, index) => {
    const id = entry && entry.id;
    if (typeof id !== 'string' || !ID.test(id)) {
      errors.push(`Category ${index + 1}: id "${id}" must be lowercase letters, digits and hyphens.`);
      return;
    }
    if (seen.has(id)) {
      errors.push(`Category "${id}" appears twice: the second one is ignored.`);
      return;
    }
    const names = cleanNames(entry.names);
    if (!names.en) {
      errors.push(`Category "${id}" has no English name (names.en).`);
      return;
    }
    seen.add(id);
    const emojis = cleanEmojis(entry.emojis);
    categories.push({
      id,
      order: typeof entry.order === 'number' && Number.isFinite(entry.order) ? entry.order : index + 1,
      icon: typeof entry.icon === 'string' && entry.icon.trim() ? entry.icon.trim() : DEFAULT_ICON,
      hue: Number.isInteger(entry.hue) && entry.hue >= 0 && entry.hue < 360 ? entry.hue : DEFAULT_HUE,
      emojis: emojis.length ? emojis : FALLBACK_EMOJIS,
      names,
      keywords: cleanKeywords(entry.keywords),
      tags: categoryTags({ id, tags: entry.tags }),
    });
  });
  return { errors, categories };
}

module.exports = { parseCategoriesFile, categoriesOf, categoryTags };
