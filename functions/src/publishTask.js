'use strict';

const crypto = require('crypto');
const zlib = require('zlib');
const { BUCKET, PUBLIC_PREFIX, publicCatalogPath, publicWordsPath } = require('./config');
const { assembleCatalog } = require('./catalog');
const { db, bucket, savePublic } = require('./firebase');
const { TAGS_DOC } = require('./tagsTask');

const KEEP_CATALOG_VERSIONS = 3;

/**
 * How the app turns a public path into a URL. {path} is the URL-encoded object path (Firebase Storage),
 * {rawPath} the path as is (a CDN such as R2). Set PUBLIC_URL_TEMPLATE to move public files without an app update.
 */
function urlTemplate(override) {
  if (override) return override;
  const emulator = process.env.FIREBASE_STORAGE_EMULATOR_HOST;
  const origin = emulator ? `http://${emulator.replace(/^https?:\/\//, '')}` : 'https://firebasestorage.googleapis.com';
  return `${origin}/v0/b/${BUCKET}/o/{path}?alt=media`;
}

async function deleteOldCatalogs(version) {
  const [files] = await bucket().getFiles({ prefix: `${PUBLIC_PREFIX}catalog/` });
  await Promise.all(
    files
      .filter((f) => {
        const match = /\/v(\d+)\.json\.gz$/.exec(f.name);
        return match && Number(match[1]) <= version - KEEP_CATALOG_VERSIONS;
      })
      .map((f) => f.delete({ ignoreNotFound: true }))
  );
}

/** The value with every object's keys sorted, at every depth (lists keep their order): same content, same JSON. */
function sortedKeys(value) {
  if (Array.isArray(value)) return value.map(sortedKeys);
  if (value && typeof value === 'object') {
    return Object.fromEntries(Object.keys(value).sort().map((key) => [key, sortedKeys(value[key])]));
  }
  return value;
}

/**
 * The catalog, naming its search words file: the whole vocabulary of _tags.json (the words of every tag and
 * lettering language; 60% of the catalog's size), keys sorted. The file, and its name, change only when _tags.json
 * does, whatever happens to the packs or their ranking, so phones keep theirs.
 *
 * @returns {{catalog: object, wordsPath: string, wordsJson: string}}
 */
function withWordsFile(catalog, vocabulary = {}) {
  const wordsJson = JSON.stringify(sortedKeys({ tags: vocabulary.tags || {}, languages: vocabulary.languages || {} }));
  const wordsPath = publicWordsPath(crypto.createHash('sha256').update(wordsJson).digest('hex'));
  return { catalog: { ...catalog, words: { path: wordsPath } }, wordsPath, wordsJson };
}

/**
 * Writes public/catalog/v<k>.json.gz (and its words file when that is new) and points catalog/meta at it. Nothing changes when the catalog's content
 * is identical to the live one, so phones never re-download an unchanged catalog, unless the live file is gone
 * from public/ (a wiped folder): then it is published again as a new version. A missing words file is written again.
 */
async function runPublish({ now = new Date(), urlOverride = '' } = {}) {
  const metaRef = db().doc('catalog/meta');
  const [categoriesSnap, packsSnap, metaSnap, tagsSnap] = await Promise.all([
    db().collection('categories').get(),
    db().collection('packs').where('status', '==', 'live').get(),
    metaRef.get(),
    db().doc(TAGS_DOC).get(),
  ]);
  const meta = metaSnap.exists ? metaSnap.data() : null;
  const categories = categoriesSnap.docs.map((d) => ({ id: d.id, ...d.data() }));
  const packs = packsSnap.docs.map((d) => ({ id: d.id, ...d.data() })).filter((p) => p.hidden !== true);
  const vocabulary = tagsSnap.exists ? tagsSnap.data() : {};
  const version = (meta ? meta.version : 0) + 1;
  const { catalog, wordsPath, wordsJson } = withWordsFile(assembleCatalog({ version, now, categories, packs }), vocabulary);
  const template = urlTemplate(urlOverride);
  const contentHash = crypto
    .createHash('sha256')
    .update(JSON.stringify({ categories: catalog.categories, packs: catalog.packs, words: wordsPath, template }))
    .digest('hex');
  const ensureWords = async () => {
    const [exists] = await bucket().file(wordsPath).exists();
    if (!exists) await savePublic(wordsPath, zlib.gzipSync(Buffer.from(wordsJson, 'utf8'), { level: 9 }), 'application/gzip');
  };
  if (meta && meta.contentHash === contentHash) {
    await ensureWords();
    const [exists] = await bucket().file(meta.path).exists();
    if (exists) return { outcome: 'unchanged', version: meta.version };
  }
  // The words before the catalog that names them.
  await ensureWords();

  const gz = zlib.gzipSync(Buffer.from(JSON.stringify(catalog), 'utf8'), { level: 9 });
  const path = publicCatalogPath(version);
  await savePublic(path, gz, 'application/gzip');
  await metaRef.set({
    version,
    path,
    bytes: gz.length,
    packs: catalog.packs.length,
    urlTemplate: template,
    contentHash,
    publishedAt: now,
  });
  await deleteOldCatalogs(version);
  return { outcome: 'published', version, packs: catalog.packs.length, bytes: gz.length };
}

module.exports = { runPublish, urlTemplate, withWordsFile };
