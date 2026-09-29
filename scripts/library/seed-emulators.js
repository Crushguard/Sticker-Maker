#!/usr/bin/env node
/**
 * seed-emulators.js — publishes the repository's library/ into the running Firebase emulators through the real
 * pipeline (upload → stickermaker functions → catalog), for the CI screen tour and local development:
 *   firebase emulators:start --only functions,firestore,storage,tasks
 *   FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 FIREBASE_STORAGE_EMULATOR_HOST=127.0.0.1:9199 \
 *     node scripts/library/seed-emulators.js [--project play-console-f33dd] [--no-design-adds]
 *
 * Test data: unless --no-design-adds, each pack starts with the add count design/catalog.json shows, so the
 * screen tour's frames match the design ("96.4K adds"). Production counts come from Analytics only.
 */
'use strict';

const fs = require('fs');
const path = require('path');
const zlib = require('zlib');
const { listLocalPacks, uploadPack, uploadFile, waitForReport, storageBucket } = require('./upload');

const ROOT = path.resolve(__dirname, '..', '..');
const LIBRARY = path.join(ROOT, 'library');
const TIMEOUT_MS = 6 * 60 * 1000;

function arg(flag, fallback) {
  const i = process.argv.indexOf(flag);
  return i >= 0 && process.argv[i + 1] ? process.argv[i + 1] : fallback;
}

async function main() {
  if (!process.env.FIRESTORE_EMULATOR_HOST || !process.env.FIREBASE_STORAGE_EMULATOR_HOST) {
    throw new Error('set FIRESTORE_EMULATOR_HOST and FIREBASE_STORAGE_EMULATOR_HOST: this script only seeds emulators');
  }
  const project = arg('--project', 'play-console-f33dd');
  const { getApps, initializeApp } = require('firebase-admin/app');
  const { getFirestore } = require('firebase-admin/firestore');
  const app = getApps()[0] || initializeApp({ projectId: project });
  const db = getFirestore(app, 'stickermaker');
  const bucket = storageBucket({ project, bucketName: `${project}-stickermaker` });
  const packs = listLocalPacks(LIBRARY);

  if (!process.argv.includes('--no-design-adds')) {
    const design = JSON.parse(fs.readFileSync(path.join(ROOT, 'design', 'catalog.json'), 'utf8'));
    const batch = db.batch();
    for (const p of design.packs) {
      batch.set(db.collection('packs').doc(p.id), { stats: { adds: p.downloads, adds28d: 0, trend: 0 } }, { merge: true });
    }
    await batch.commit();
    console.log(`test add counts from design/catalog.json for ${design.packs.length} packs`);
  }

  await uploadFile(bucket, path.join(LIBRARY, '_categories.json'), 'library/_categories.json', {});
  // One file at a time, one pack at a time: each upload event runs a Functions emulator worker, and a burst of
  // them exhausts a small machine (workers die with EPIPE). The fast path builds each pack as its pack.json lands.
  for (const pack of packs) {
    const since = Date.now() - 2000;
    const { prefix, counts } = await uploadPack(bucket, pack, { concurrency: 1 });
    const report = await waitForReport(bucket, prefix, since, 120000);
    console.log(
      `${pack.category}/${pack.folder}: ${Object.entries(counts).map(([k, v]) => `${v} ${k}`).join(', ')} — ` +
        (report ? report.split('\n')[0] : 'no report after 120 s')
    );
  }

  const ids = packs.map((p) => p.folder);
  const deadline = Date.now() + TIMEOUT_MS;
  while (Date.now() < deadline) {
    const snap = await db.doc('catalog/meta').get();
    if (snap.exists) {
      const meta = snap.data();
      const url = meta.urlTemplate.replace('{path}', encodeURIComponent(meta.path)).replace('{rawPath}', meta.path);
      const res = await fetch(url);
      if (res.ok) {
        const catalog = JSON.parse(zlib.gunzipSync(Buffer.from(await res.arrayBuffer())).toString('utf8'));
        const listed = new Set(catalog.packs.map((p) => p.id));
        const missing = ids.filter((id) => !listed.has(id));
        if (!missing.length) {
          console.log(`catalog v${meta.version}: ${catalog.packs.length} packs, ${catalog.categories.filter((c) => c.packs > 0).length} categories with packs`);
          return;
        }
      }
    }
    await new Promise((r) => setTimeout(r, 3000));
  }
  const failed = [];
  for (const pack of packs) {
    const report = bucket.file(`library/${pack.category}/${pack.folder}/_report.txt`);
    const [exists] = await report.exists();
    failed.push(`${pack.folder}: ${exists ? (await report.download())[0].toString('utf8').split('\n')[0] : 'no report'}`);
  }
  throw new Error(`the catalog never listed every pack:\n${failed.join('\n')}`);
}

main().then(() => process.exit(0), (err) => {
  console.error(err.message || err);
  process.exit(1);
});
