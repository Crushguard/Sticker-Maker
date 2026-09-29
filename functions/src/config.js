'use strict';

/**
 * Names and limits shared by the whole pipeline. Everything the pipeline owns is named "stickermaker" and kept
 * apart from the other apps in the Firebase project.
 */

const PROJECT_ID = 'play-console-f33dd';
const BUCKET = `${PROJECT_ID}-stickermaker`;
const DATABASE = 'stickermaker';
const REGION = 'us-central1';

/** Private sources: library/<category>/<Pack Name>/… */
const LIBRARY_PREFIX = 'library/';
/** Generated, publicly readable files. */
const PUBLIC_PREFIX = 'public/';

/** WhatsApp's third-party sticker rules. */
const WHATSAPP = {
  size: 512,
  staticMaxBytes: 100 * 1024,
  animatedMaxBytes: 500 * 1024,
  minFrameMs: 8,
  maxLoopMs: 10000,
  minStickers: 3,
  maxStickers: 30,
  minEmojis: 1,
  maxEmojis: 3,
  traySize: 96,
  trayMaxBytes: 50 * 1024,
};

/** Quality floors: below these the build fails instead of shipping blurry art. */
const QUALITY = {
  staticStart: 95,
  staticFloor: 75,
  animatedStart: 90,
  animatedFloor: 60,
  step: 5,
  cover: 85,
  coverAlpha: 90,
};

/** Home card cover tiles (the card's circles are 46 dp): small for screens up to 2x, large (1:1 up to 3.5x) above. */
const COVER_TILES = { small: 96, large: 160, count: 6 };

const IMMUTABLE_CACHE = 'public, max-age=31536000, immutable';

/** Cloud Tasks retries of a build. Waiting for a folder to settle is a retry too (see buildTask.runBuildTask). */
const BUILD_RETRY = { maxAttempts: 8, minBackoffSeconds: 30, maxBackoffSeconds: 60 };

/**
 * Folder of one published version of a pack. The content hash in the name means a path only ever holds one set of
 * bytes, whatever happens to the pack's record, so phones and CDNs may cache it forever.
 */
function publicPackBase(packId, version, contentHash) {
  return `${PUBLIC_PREFIX}packs/${packId}/v${version}-${contentHash.slice(0, 8)}/`;
}

/** The catalog file of one catalog version. */
function publicCatalogPath(version) {
  return `${PUBLIC_PREFIX}catalog/v${version}.json.gz`;
}

module.exports = {
  PROJECT_ID,
  BUCKET,
  DATABASE,
  REGION,
  LIBRARY_PREFIX,
  PUBLIC_PREFIX,
  WHATSAPP,
  QUALITY,
  COVER_TILES,
  IMMUTABLE_CACHE,
  BUILD_RETRY,
  publicPackBase,
  publicCatalogPath,
};
