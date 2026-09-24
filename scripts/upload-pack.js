#!/usr/bin/env node
/**
 * upload-pack.js — seeds Firebase with the committed sticker packs.
 *
 * Uploads packs/<id>/{tray.png, NN.webp, thumbs/NN.webp} to Cloud Storage and
 * writes one Firestore document per pack (collection "packs") plus the eight
 * category documents (collection "categories") from design/catalog.json.
 *
 * Usage:
 *   GOOGLE_APPLICATION_CREDENTIALS=service-account.json \
 *     node scripts/upload-pack.js --all [--project play-console-f33dd] \
 *       [--bucket <name>] [--dry-run]
 *   node scripts/upload-pack.js --pack gm-gn
 *
 * Idempotent: files are skipped when the remote MD5 already matches, and
 * Firestore writes use { merge: true }. Never deletes anything.
 */
'use strict';

const crypto = require('crypto');
const fs = require('fs');
const path = require('path');

const ROOT = path.resolve(__dirname, '..');
const PACKS = path.join(ROOT, 'packs');
const CATALOG = JSON.parse(fs.readFileSync(path.join(ROOT, 'design', 'catalog.json'), 'utf8'));

function arg(flag, fallback) {
  const i = process.argv.indexOf(flag);
  return i >= 0 && process.argv[i + 1] ? process.argv[i + 1] : fallback;
}
const ALL = process.argv.includes('--all');
const ONLY = arg('--pack', null);
const DRY = process.argv.includes('--dry-run');
const PROJECT = arg('--project', process.env.FIREBASE_PROJECT || 'play-console-f33dd');
const BUCKET = arg('--bucket', `${PROJECT}.firebasestorage.app`);

if (!ALL && !ONLY) {
  console.error('usage: upload-pack.js (--all | --pack <id>) [--project id] [--bucket name] [--dry-run]');
  process.exit(2);
}

const { initializeApp, applicationDefault } = require('firebase-admin/app');
const { getFirestore, FieldValue } = require('firebase-admin/firestore');
const { getStorage } = require('firebase-admin/storage');

initializeApp({
  credential: applicationDefault(),
  projectId: PROJECT,
  storageBucket: BUCKET,
});
const db = getFirestore();
const bucket = getStorage().bucket();

function md5(file) {
  return crypto.createHash('md5').update(fs.readFileSync(file)).digest('base64');
}

async function uploadFile(localPath, remotePath) {
  const [exists] = await bucket.file(remotePath).exists();
  if (exists) {
    const [meta] = await bucket.file(remotePath).getMetadata();
    if (meta.md5Hash === md5(localPath)) return 'skip';
  }
  if (DRY) return 'would-upload';
  await bucket.upload(localPath, {
    destination: remotePath,
    metadata: { cacheControl: 'public, max-age=31536000, immutable' },
  });
  return 'uploaded';
}

async function uploadPack(pack) {
  const dir = path.join(PACKS, pack.id);
  const contents = JSON.parse(fs.readFileSync(path.join(dir, 'pack.json'), 'utf8'));
  const stickerFiles = contents.stickers.map((s) => s.image_file);

  const jobs = [
    ['tray.png', `packs/${pack.id}/tray.png`],
    ...stickerFiles.map((f) => [f, `packs/${pack.id}/${f}`]),
    ...stickerFiles.map((f) => [path.join('thumbs', f), `packs/${pack.id}/thumbs/${f}`]),
  ];
  const counts = { uploaded: 0, skip: 0, 'would-upload': 0 };
  for (const [local, remote] of jobs) {
    counts[await uploadFile(path.join(dir, local), remote)]++;
  }

  const doc = {
    name: contents.name,
    publisher: contents.publisher,
    category: contents.category,
    animated: !!contents.animated_sticker_pack,
    order: contents.order,
    downloads: contents.downloads,
    hue: contents.hue,
    stickerCount: stickerFiles.length,
    trayPath: `packs/${pack.id}/tray.png`,
    stickerPaths: stickerFiles.map((f) => `packs/${pack.id}/${f}`),
    thumbPaths: stickerFiles.map((f) => `packs/${pack.id}/thumbs/${f}`),
    // Keyed by full file name ("01.webp") to match the app's CatalogDataSource.
    // Dots in keys are safe because the map is always set whole, never by FieldPath.
    emojis: Object.fromEntries(contents.stickers.map((s) => [s.image_file, s.emojis])),
    updatedAt: FieldValue.serverTimestamp(),
  };
  if (!DRY) await db.collection('packs').doc(pack.id).set(doc, { merge: true });
  console.log(
    `${pack.id.padEnd(15)} ${counts.uploaded} uploaded, ${counts.skip} unchanged` +
      (DRY ? `, ${counts['would-upload']} pending (dry run)` : '')
  );
}

async function seedCategories() {
  for (const cat of CATALOG.categories) {
    const doc = { name: cat.name, icon: cat.icon, hue: cat.hue, order: cat.order };
    if (!DRY) await db.collection('categories').doc(cat.id).set(doc, { merge: true });
  }
  console.log(`categories seeded: ${CATALOG.categories.length}`);
}

(async () => {
  const packs = CATALOG.packs.filter((p) => ALL || p.id === ONLY);
  if (!packs.length) {
    console.error(`no pack named "${ONLY}" in design/catalog.json`);
    process.exit(2);
  }
  await seedCategories();
  for (const pack of packs) await uploadPack(pack);
  console.log(DRY ? 'dry run complete' : 'upload complete');
  process.exit(0);
})().catch((err) => {
  console.error(err.message || err);
  process.exit(1);
});
