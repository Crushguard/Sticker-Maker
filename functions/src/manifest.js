'use strict';

const { WHATSAPP, COVER_TILES } = require('./config');
const { naturalCompare } = require('./library');

const DEFAULT_ORDER = 1000;
const MAX_NAME = 128;
const MAX_TEXT = 255;
const MAX_ALSO_IN = 2;
const MAX_TAGS = 20;
const MAX_TAG = 32;
const TRAY = /^tray\.png$/i;
const LANG = /^[a-z]{2,3}(-[A-Za-z0-9]{2,8})*$/;
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

function cleanTags(value) {
  if (!Array.isArray(value)) return [];
  const tags = value
    .filter((t) => typeof t === 'string')
    .map((t) => t.trim().toLowerCase().slice(0, MAX_TAG))
    .filter((t) => t !== '');
  return [...new Set(tags)].slice(0, MAX_TAGS);
}

/**
 * Reads a pack folder's optional pack.json against the images actually in the folder.
 *
 * @param {string|null} text pack.json contents, or null when the folder has none
 * @param {string[]} imageNames image files in the folder (png, webp, gif)
 * @param {{folder: string, category: string, categoryIds: Set<string>, defaultEmojis: string[]}} opts
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

  const alsoIn = [];
  if (json.alsoIn !== undefined) {
    const wanted = Array.isArray(json.alsoIn) ? json.alsoIn : [];
    for (const id of wanted) {
      if (id === opts.category || alsoIn.includes(id)) continue;
      if (!opts.categoryIds.has(id)) {
        notes.push(`alsoIn: unknown category "${id}" ignored.`);
        continue;
      }
      if (alsoIn.length < MAX_ALSO_IN) alsoIn.push(id);
      else notes.push(`alsoIn: only ${MAX_ALSO_IN} extra categories are used; "${id}" ignored.`);
    }
  }

  let lang = 'en';
  if (json.lang !== undefined) {
    if (json.lang === 'none' || (typeof json.lang === 'string' && LANG.test(json.lang))) lang = json.lang;
    else notes.push(`lang "${json.lang}" is not a language code: used "en".`);
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

  const defaults = cleanEmojis(opts.defaultEmojis).length ? cleanEmojis(opts.defaultEmojis) : FALLBACK_EMOJIS;
  let usedDefaults = false;
  const sticker = (file, entry) => {
    let emojis = cleanEmojis(entry && entry.emojis);
    if (emojis.length === 0) {
      emojis = defaults;
      usedDefaults = true;
    }
    return { file, emojis, text: cleanText(entry && entry.text) };
  };

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
  if (usedDefaults) notes.push(`Stickers without emoji use the category's emoji ${defaults.join(' ')}.`);

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
    alsoIn,
    lang,
    tags: cleanTags(json.tags),
    order,
    animate,
    tray,
    cover,
    stickers,
    listed,
  };
}

module.exports = { parsePackManifest, cleanEmojis, cleanNames, DEFAULT_ORDER };
