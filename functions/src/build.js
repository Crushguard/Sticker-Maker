'use strict';

const crypto = require('crypto');
const { COVER_TILES } = require('./config');
const { isImageName } = require('./library');
const { parsePackManifest, fillEmojis } = require('./manifest');
const { categoriesOf } = require('./categories');
const { encodeSticker, firstFramePng, StickerError } = require('./stickers');
const { makeTray, makeCoverStrip } = require('./images');
const { makePackZip } = require('./zip');
const { checkPack } = require('./checks');

const MAX_INPUT_BYTES = 10 * 1024 * 1024;

const stickerName = (index) => `${String(index + 1).padStart(2, '0')}.webp`;

/** A file's problem as a report line: our own message, or what the image decoder said. */
function problem(err) {
  return err instanceof StickerError ? err.message : `can't read the image (${err.message})`;
}

/**
 * The version of a changed pack: above the live one and above every version ever published, so a number never
 * names two sets of files (not even after the record was deleted), unless the newest published folder already holds
 * these exact files, above the live version: an earlier attempt of this build that died before its record, which
 * the retry then finishes.
 *
 * @param {{version: number}|null} live
 * @param {{version: number, hash: string|null}[]} published the pack's version folders in public/
 * @param {string} contentHash
 */
function nextVersion(live, published, contentHash) {
  const liveVersion = live ? live.version : 0;
  const highest = Math.max(liveVersion, ...published.map((p) => p.version));
  const retry =
    highest > liveVersion && published.some((p) => p.version === highest && p.hash && contentHash.startsWith(p.hash));
  return retry ? highest : highest + 1;
}

function failure(errors, notes) {
  return {
    ok: false,
    errors,
    notes,
    unchanged: false,
    version: null,
    contentHash: null,
    animated: false,
    count: 0,
    categories: [],
    outputs: null,
    record: null,
  };
}

/**
 * One pack folder's files in, the published outputs and the Firestore record out. Pure apart from CPU:
 * no Storage or Firestore access, so it runs the same in the cloud, the emulators and tests.
 *
 * @param {object} input
 * @param {string} input.packId
 * @param {string} input.folder
 * @param {{name: string, buffer: Buffer}[]} input.files every file of the folder except pack.json
 * @param {string|null} input.manifestText pack.json, if any
 * @param {{id: string, tags?: string[], emojis?: string[]}[]} [input.categories] the catalog's categories
 * @param {{version: number, contentHash: string, animated: boolean}|null} input.live the version the pack had, if
 *   any, live or removed: its kind binds the id for good
 * @param {{version: number, hash: string|null}[]} [input.published] the version folders already in public/
 */
async function buildPackFromFiles({ packId, folder, files, manifestText, categories = [], live, published = [] }) {
  const notes = [];
  const skipped = files.filter((f) => !isImageName(f.name)).map((f) => f.name);
  if (skipped.length) notes.push(`Skipped files that aren't PNG, WebP or GIF: ${skipped.join(', ')}.`);

  const errors = [];
  const images = [];
  for (const file of files.filter((f) => isImageName(f.name))) {
    if (file.buffer.length > MAX_INPUT_BYTES) errors.push(`${file.name} is larger than 10 MB.`);
    else images.push(file);
  }
  const manifest = parsePackManifest(manifestText, images.map((f) => f.name), { folder });
  errors.push(...manifest.errors);
  notes.push(...manifest.notes);
  if (errors.length) return failure(errors, notes);

  const tags = manifest.tags;
  const packCategories = categoriesOf(tags, categories);
  const firstCategory = categories.find((c) => c.id === packCategories[0]);
  const filled = fillEmojis(manifest, firstCategory ? firstCategory.emojis : []);
  if (filled.note) notes.push(filled.note);

  const source = new Map(images.map((f) => [f.name, f.buffer]));
  const encoded = [];
  for (const sticker of filled.stickers) {
    try {
      const out = await encodeSticker(source.get(sticker.file), { wiggle: manifest.animate === 'wiggle' });
      for (const note of out.notes) notes.push(`${sticker.file}: ${note}.`);
      encoded.push({ ...sticker, out });
    } catch (err) {
      errors.push(`${sticker.file}: ${problem(err)}.`);
    }
  }
  const kinds = new Set(encoded.map((e) => (e.out.animated ? 'animated' : 'static')));
  errors.push(
    ...checkPack({
      count: manifest.stickers.length,
      kinds,
      liveAnimated: live ? live.animated : null,
      emojiCounts: encoded.map((e) => e.emojis.length),
    })
  );
  if (errors.length) return failure(errors, notes);

  const animated = kinds.has('animated');
  const firstSticker = manifest.stickers[0].file;
  if (!manifest.tray) notes.push(`No tray.png: made from ${firstSticker}.`);
  const trayFile = manifest.tray || firstSticker;
  let tray;
  try {
    tray = await makeTray(source.get(trayFile));
  } catch (err) {
    return failure([`${trayFile}: ${problem(err)}.`], notes);
  }
  if (tray.palette) notes.push('The tray icon was reduced to 256 colours to stay under 50 KB.');

  const stickers = encoded.map((e, i) => ({ name: stickerName(i), buffer: e.out.buffer }));
  let coverFrames;
  try {
    coverFrames = await Promise.all(manifest.cover.map((file) => firstFramePng(source.get(file))));
  } catch (err) {
    return failure([`cover: ${problem(err)}.`], notes);
  }
  const coverS = await makeCoverStrip(coverFrames, COVER_TILES.small);
  const coverL = await makeCoverStrip(coverFrames, COVER_TILES.large);

  const contents = (version) => ({
    id: packId,
    name: manifest.name,
    version,
    animated,
    stickers: encoded.map((e, i) => ({ file: stickerName(i), emojis: e.emojis, text: e.text })),
  });
  // The fingerprint covers everything published except the version number itself.
  const contentHash = crypto
    .createHash('sha256')
    .update(makePackZip({ contents: contents(0), tray: tray.buffer, stickers }))
    .update(coverS)
    .update(coverL)
    .digest('hex');
  const unchanged = !!live && live.contentHash === contentHash;
  const version = unchanged ? live.version : nextVersion(live, published, contentHash);
  const zip = makePackZip({ contents: contents(version), tray: tray.buffer, stickers });

  const record = {
    name: manifest.name,
    names: manifest.names,
    langs: manifest.langs,
    tags,
    keywords: manifest.keywords,
    order: manifest.order,
    animated,
    count: encoded.length,
    version,
    contentHash,
    stickers: encoded.map((e, i) => ({
      file: stickerName(i),
      source: e.file,
      emojis: e.emojis,
      text: e.text,
      passthrough: e.out.passthrough,
      quality: e.out.quality,
      bytes: e.out.buffer.length,
    })),
    zipBytes: zip.length,
    coverTiles: coverFrames.length,
  };
  return {
    ok: true,
    errors: [],
    notes,
    unchanged,
    version,
    contentHash,
    animated,
    count: encoded.length,
    categories: packCategories,
    outputs: { coverS, coverL, zip },
    record,
  };
}

module.exports = { buildPackFromFiles };
