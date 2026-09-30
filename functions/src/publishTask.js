'use strict';

const crypto = require('crypto');
const zlib = require('zlib');
const { BUCKET, PUBLIC_PREFIX, publicCatalogPath } = require('./config');
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

/**
 * Writes public/catalog/v<k>.json.gz and points catalog/meta at it. Nothing changes when the catalog's content
 * is identical to the live one, so phones never re-download an unchanged catalog, unless the live file is gone
 * from public/ (a wiped folder): then it is published again as a new version.
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
  const catalog = assembleCatalog({ version, now, categories, packs, vocabulary });
  const template = urlTemplate(urlOverride);
  const { categories: catalogCategories, packs: catalogPacks, tags, languages } = catalog;
  const contentHash = crypto
    .createHash('sha256')
    .update(JSON.stringify({ categories: catalogCategories, packs: catalogPacks, tags, languages, template }))
    .digest('hex');
  if (meta && meta.contentHash === contentHash) {
    const [exists] = await bucket().file(meta.path).exists();
    if (exists) return { outcome: 'unchanged', version: meta.version };
  }

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

module.exports = { runPublish, urlTemplate };
