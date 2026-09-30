'use strict';

const TAG = /^[a-z0-9]+(-[a-z0-9]+)*$/;
const LANG = /^([a-z]{2,3}(-[A-Za-z0-9]{2,8})*|none|multi)$/;
const MAX_WORDS = 8;
const MAX_WORD = 48;

/** { <lang>: [words] } with trimmed, lowercased, unique words; languages with none left out. */
function cleanWordsByLang(value) {
  const out = {};
  if (!value || typeof value !== 'object' || Array.isArray(value)) return out;
  for (const [lang, words] of Object.entries(value)) {
    if (!LANG.test(lang) || !Array.isArray(words)) continue;
    const clean = words
      .filter((w) => typeof w === 'string')
      .map((w) => w.trim().toLowerCase().slice(0, MAX_WORD).trim())
      .filter((w) => w !== '');
    if (clean.length) out[lang] = [...new Set(clean)].slice(0, MAX_WORDS);
  }
  return out;
}

function section(json, name, key, errors) {
  const out = {};
  const entries = json[name];
  if (entries === undefined) return out;
  if (!entries || typeof entries !== 'object' || Array.isArray(entries)) {
    errors.push(`"${name}" must be an object.`);
    return out;
  }
  for (const [id, byLang] of Object.entries(entries)) {
    if (!key.test(id)) {
      errors.push(`${name}: "${id}" is not a valid id.`);
      continue;
    }
    const words = cleanWordsByLang(byLang);
    if (Object.keys(words).length === 0) {
      errors.push(`${name}: "${id}" has no words.`);
      continue;
    }
    out[id] = words;
  }
  return out;
}

/**
 * Reads library/_tags.json: the words people search for each pack tag and each lettering language, by app
 * language. Bad entries are skipped with an error each; the rest stay usable.
 *
 *   { "tags": { "cat": { "en": ["cat", "kitty"], "ar": ["قطة"] } },
 *     "languages": { "ar": { "en": ["arabic"], "ar": ["عربي"] } } }
 */
function parseTagsFile(text) {
  let json;
  try {
    json = JSON.parse(text);
  } catch (err) {
    return { errors: [`_tags.json is not valid JSON: ${err.message}`], tags: {}, languages: {} };
  }
  if (!json || typeof json !== 'object' || Array.isArray(json)) {
    return { errors: ['_tags.json must be an object with "tags" and "languages".'], tags: {}, languages: {} };
  }
  const errors = [];
  const tags = section(json, 'tags', TAG, errors);
  const languages = section(json, 'languages', LANG, errors);
  return { errors, tags, languages };
}

module.exports = { parseTagsFile };
