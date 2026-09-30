'use strict';

const { WHATSAPP, COVER_TILES } = require('./config');
const { naturalCompare } = require('./library');

const DEFAULT_ORDER = 1000;
const MAX_NAME = 128;
const MAX_TEXT = 255;
const MAX_TAGS = 20;
const MAX_TAG = 32;
const MAX_KEYWORDS = 40;
const MAX_KEYWORD = 64;
const TRAY = /^tray\.png$/i;
const LANG = /^[a-z]{2,3}(-[A-Za-z0-9]{2,8})*$/;
/** "none": no text on the stickers. "multi": one sticker per language, readable anywhere. */
const TEXT_FREE = 'none';
const MULTILINGUAL = 'multi';
const EMOJI = /\p{Extended_Pictographic}|\p{Regional_Indicator}/u;
const FALLBACK_EMOJIS = ['❤️'];

/** Keeps real emoji only (1–3); anything else is dropped. */
function cleanEmojis(value) {
  if (!Array.isArray(value)) return [];
  return value
    .filter((e) => typeof e === 'string' && e.trim() !== '' && e.length <= 16 && EMOJI.test(e))
    .map((e) => e.trim())
    .slice(0, WHATSAPP.maxEmojis);
}

function cleanText(value) {
  return typeof value === 'string' ? value.trim().slice(0, MAX_TEXT) : '';
}

function cleanNames(value) {
  const names = {};
  if (value && typeof value === 'object' && !Array.isArray(value)) {
    for (const [lang, name] of Object.entries(value)) {
      if (typeof name === 'string' && name.trim() !== '') names[lang] = name.trim().slice(0, MAX_NAME);
    }
  }
  return names;
}

/** Lowercase, trimmed, unique, capped strings. */
function cleanWords(value, maxLength, maxCount) {
  if (!Array.isArray(value)) return [];
  const words = value
    .filter((t) => typeof t === 'string')
    .map((t) => t.trim().toLowerCase().slice(0, maxLength).trim())
    .filter((t) => t !== '');
  return [...new Set(words)].slice(0, maxCount);
}

function cleanTags(value) {
  return cleanWords(value, MAX_TAG, MAX_TAGS);
}

/**
 * pack.json lang: one code, a list, or a comma-separated string, e.g. "ar", ["ar", "hi", "es"], "none", or
 * ["multi", "fr", "it"]. Always at least one code; "en" when none is given.
 */
function parseLangs(value, notes) {
  if (value === undefined) return ['en'];
  const list = Array.isArray(value) ? value : typeof value === 'string' ? value.split(',') : [];
  const langs = [];
  for (const raw of list) {
    const code = typeof raw === 'string' ? raw.trim() : '';
    if (code === TEXT_FREE || code === MULTILINGUAL || LANG.test(code)) {
      if (!langs.includes(code)) langs.push(code);
    } else {
      notes.push(`lang "${raw}" is not a language code: ignored.`);
    }
  }
  if (langs.length === 0) {
    notes.push('lang has no language code: used "en".');
    return ['en'];
  }
  if (langs.includes(TEXT_FREE) && langs.length > 1) {
    notes.push('lang lists languages and "none" (no text): "none" was ignored.');
    return langs.filter((code) => code !== TEXT_FREE);
  }
  return langs;
}

/**
 * Reads a pack folder's optional pack.json against the images actually in the folder.
 *
 * Stickers keep only their own emoji here; fillEmojis adds the pack's (or its category's) to the others.
 *
 * @param {string|null} text pack.json contents, or null when the folder has none
 * @param {string[]} imageNames image files in the folder (png, webp, gif)
 * @param {{folder: string}} opts
 */
function parsePackManifest(text, imageNames, opts) {
  const errors = [];
  const notes = [];
  let json = {};
  if (text != null) {
    try {
      json = JSON.parse(text);
    } catch (err) {
      errors.push(`pack.json is not valid JSON: ${err.message}`);
      json = {};
    }
    if (errors.length === 0 && (json === null || typeof json !== 'object' || Array.isArray(json))) {
      errors.push('pack.json must be an object');
      json = {};
    }
  }

  const tray = imageNames.find((f) => TRAY.test(f)) || null;
  const images = imageNames.filter((f) => !TRAY.test(f)).sort(naturalCompare);

  let name = opts.folder.trim();
  if (json.name !== undefined) {
    if (typeof json.name === 'string' && json.name.trim() !== '') name = json.name.trim();
    else notes.push('pack.json name is empty: used the folder name.');
  }
  if (name.length > MAX_NAME) errors.push(`The pack name is longer than ${MAX_NAME} characters.`);

  const langs = parseLangs(json.lang, notes);

  const emojis = cleanEmojis(json.emojis);
  if (json.emojis !== undefined && emojis.length === 0) notes.push('pack.json emojis has no emoji: ignored.');

  let adult = false;
  if (json.adult !== undefined) {
    if (typeof json.adult === 'boolean') adult = json.adult;
    else notes.push('adult is not true or false: ignored.');
  }

  let order = DEFAULT_ORDER;
  if (json.order !== undefined) {
    if (typeof json.order === 'number' && Number.isFinite(json.order)) order = json.order;
    else notes.push('order is not a number: ignored.');
  }

  let animate = null;
  if (json.animate !== undefined) {
    if (json.animate === 'wiggle') animate = 'wiggle';
    else notes.push(`animate "${json.animate}" is not supported (only "wiggle"): ignored.`);
  }

  const sticker = (file, entry) => ({ file, emojis: cleanEmojis(entry && entry.emojis), text: cleanText(entry && entry.text) });

  let listed = false;
  let stickers;
  if (Array.isArray(json.stickers)) {
    listed = true;
    stickers = [];
    const seen = new Set();
    for (const entry of json.stickers) {
      const file = entry && typeof entry.file === 'string' ? entry.file : null;
      if (!file) {
        notes.push('A pack.json sticker entry has no file: ignored.');
        continue;
      }
      if (seen.has(file)) continue;
      seen.add(file);
      if (!images.includes(file)) {
        errors.push(`${file} is listed but missing`);
        continue;
      }
      stickers.push(sticker(file, entry));
    }
    const unlisted = images.filter((f) => !seen.has(f));
    if (unlisted.length) notes.push(`Not listed in pack.json, so not in the pack: ${unlisted.join(', ')}.`);
  } else {
    const overrides =
      json.stickers && typeof json.stickers === 'object' ? json.stickers : {};
    stickers = images.map((file) => sticker(file, overrides[file]));
  }
  const stickerFiles = stickers.map((s) => s.file);
  let cover = [];
  if (Array.isArray(json.cover)) {
    for (const file of json.cover) {
      if (stickerFiles.includes(file) && !cover.includes(file)) cover.push(file);
      else notes.push(`cover: "${file}" is not a sticker of this pack: ignored.`);
    }
    cover = cover.slice(0, COVER_TILES.count);
  }
  if (cover.length === 0) cover = stickerFiles.slice(0, COVER_TILES.count);

  return {
    errors,
    notes,
    name,
    names: cleanNames(json.names),
    langs,
    tags: cleanTags(json.tags),
    keywords: cleanWords(json.keywords, MAX_KEYWORD, MAX_KEYWORDS),
    emojis,
    adult,
    order,
    animate,
    tray,
    cover,
    stickers,
    listed,
  };
}

/**
 * Gives every sticker without emoji of its own the pack's emoji, else its first category's, else ❤️.
 *
 * @param {{stickers: {emojis: string[]}[], emojis: string[]}} manifest parsePackManifest's result
 * @param {string[]} categoryEmojis the emoji of the pack's first category
 * @returns {{stickers: object[], note: string|null}} the note says which default a sticker got, unless the pack chose it
 */
function fillEmojis(manifest, categoryEmojis) {
  const fromCategory = cleanEmojis(categoryEmojis);
  const [defaults, note] = manifest.emojis.length
    ? [manifest.emojis, null]
    : fromCategory.length
      ? [fromCategory, `Stickers without emoji use the category's emoji ${fromCategory.join(' ')}.`]
      : [FALLBACK_EMOJIS, `Stickers without emoji use ${FALLBACK_EMOJIS.join(' ')}.`];
  let used = false;
  const stickers = manifest.stickers.map((s) => {
    if (s.emojis.length) return s;
    used = true;
    return { ...s, emojis: defaults };
  });
  return { stickers, note: used ? note : null };
}

module.exports = { parsePackManifest, fillEmojis, cleanEmojis, cleanNames, DEFAULT_ORDER, TEXT_FREE, MULTILINGUAL };
