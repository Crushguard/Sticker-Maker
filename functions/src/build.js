'use strict';

const crypto = require('crypto');
const { COVER_TILES } = require('./config');
const { isImageName } = require('./library');
const { parsePackManifest } = require('./manifest');
const { encodeSticker, firstFramePng, StickerError } = require('./stickers');
const { makeTray, makeCoverStrip } = require('./images');
const { makePackZip } = require('./zip');
const { checkPack } = require('./checks');

const MAX_INPUT_BYTES = 10 * 1024 * 1024;

const stickerName = (index) => `${String(index + 1).padStart(2, '0')}.webp`;

function failure(errors, notes) {
  return { ok: false, errors, notes, unchanged: false, version: null, contentHash: null, animated: false, count: 0, outputs: null, record: null };
}

/**
 * One pack folder's files in, the published outputs and the Firestore record out. Pure apart from CPU:
 * no Storage or Firestore access, so it runs the same in the cloud, the emulators and tests.
 *
 * @param {object} input
 * @param {string} input.packId
 * @param {string} input.category
 * @param {string} input.folder
 * @param {{name: string, buffer: Buffer}[]} input.files every file of the folder except pack.json
 * @param {string|null} input.manifestText pack.json, if any
 * @param {Set<string>} input.categoryIds
 * @param {string[]} input.defaultEmojis the category's emoji
 * @param {{version: number, contentHash: string, animated: boolean}|null} input.live the live version, if any
 */
async function buildPackFromFiles({ packId, category, folder, files, manifestText, categoryIds, defaultEmojis, live }) {
  const notes = [];
  const skipped = files.filter((f) => !isImageName(f.name)).map((f) => f.name);
  if (skipped.length) notes.push(`Skipped files that aren't PNG, WebP or GIF: ${skipped.join(', ')}.`);

  const errors = [];
  const images = [];
  for (const file of files.filter((f) => isImageName(f.name))) {
    if (file.buffer.length > MAX_INPUT_BYTES) errors.push(`${file.name} is larger than 10 MB.`);
    else images.push(file);
  }
  const manifest = parsePackManifest(manifestText, images.map((f) => f.name), {
    folder,
    category,
    categoryIds,
    defaultEmojis,
  });
  errors.push(...manifest.errors);
  notes.push(...manifest.notes);
  if (errors.length) return failure(errors, notes);

  const source = new Map(images.map((f) => [f.name, f.buffer]));
  const encoded = [];
  for (const sticker of manifest.stickers) {
    try {
      const out = await encodeSticker(source.get(sticker.file), { wiggle: manifest.animate === 'wiggle' });
      for (const note of out.notes) notes.push(`${sticker.file}: ${note}.`);
      encoded.push({ ...sticker, out });
    } catch (err) {
      if (!(err instanceof StickerError)) throw err;
      errors.push(`${sticker.file}: ${err.message}.`);
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
  const first = manifest.stickers[0].file;
  if (!manifest.tray) notes.push(`No tray.png: made from ${first}.`);
  const tray = await makeTray(source.get(manifest.tray || first));
  if (tray.palette) notes.push('The tray icon was reduced to 256 colours to stay under 50 KB.');

  const stickers = encoded.map((e, i) => ({ name: stickerName(i), buffer: e.out.buffer }));
  const coverFrames = await Promise.all(manifest.cover.map((file) => firstFramePng(source.get(file))));
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
  const version = unchanged ? live.version : (live ? live.version : 0) + 1;
  const zip = makePackZip({ contents: contents(version), tray: tray.buffer, stickers });

  const record = {
    name: manifest.name,
    names: manifest.names,
    category,
    alsoIn: manifest.alsoIn,
    lang: manifest.lang,
    tags: manifest.tags,
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
    outputs: { coverS, coverL, zip },
    record,
  };
}

module.exports = { buildPackFromFiles };
