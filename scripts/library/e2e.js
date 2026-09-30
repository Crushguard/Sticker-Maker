#!/usr/bin/env node
/**
 * e2e.js — the whole pipeline against the Firebase emulators:
 *   firebase emulators:exec --only functions,firestore,storage,tasks "node scripts/library/e2e.js"
 *
 * Uploads one pack (from library/ when present, else from packs/gm-gn/src), waits for the catalog, downloads the
 * catalog, a cover strip and the pack zip through the same URLs the app uses, re-uploads to prove nothing is
 * republished, replaces two stickers slowly to prove an update goes live once and whole, then deletes the folder and
 * waits for the pack to leave the catalog.
 */
'use strict';

const fs = require('fs');
const os = require('os');
const path = require('path');
const zlib = require('zlib');
const { unzipSync, strFromU8 } = require('fflate');
const { listLocalPacks, packName, uploadPack, uploadFile, storageBucket } = require('./upload');

const ROOT = path.resolve(__dirname, '..', '..');
const PROJECT = process.env.GCLOUD_PROJECT || 'play-console-f33dd';
const BUCKET = `${PROJECT}-stickermaker`;

function fail(message) {
  console.error(`E2E FAILED: ${message}`);
  process.exit(1);
}

async function poll(what, fn, timeoutMs) {
  const deadline = Date.now() + timeoutMs;
  let last;
  while (Date.now() < deadline) {
    last = await fn().catch((err) => ({ error: err.message }));
    if (last && !last.error) return last;
    await new Promise((r) => setTimeout(r, 2000));
  }
  fail(`timed out waiting for ${what}${last && last.error ? ` (${last.error})` : ''}`);
}

/** A throwaway library with one real launch pack and the categories its tags name. */
function testLibrary() {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'stickermaker-e2e-'));
  const repoLibrary = path.join(ROOT, 'library');
  if (fs.existsSync(path.join(repoLibrary, '_categories.json'))) {
    const pack = listLocalPacks(repoLibrary)[0];
    fs.copyFileSync(path.join(repoLibrary, '_categories.json'), path.join(dir, '_categories.json'));
    fs.cpSync(pack.dir, path.join(dir, pack.folder), { recursive: true });
    return dir;
  }
  const packDir = path.join(dir, 'Good Morning, Good Night');
  fs.mkdirSync(packDir, { recursive: true });
  const src = path.join(ROOT, 'packs', 'gm-gn', 'src');
  const files = fs.readdirSync(src).filter((f) => f.endsWith('.webp')).sort();
  for (const f of files) fs.copyFileSync(path.join(src, f), path.join(packDir, f));
  fs.writeFileSync(
    path.join(packDir, 'pack.json'),
    JSON.stringify({ tags: ['goodnight'], stickers: files.map((file) => ({ file, emojis: ['🌙'], text: 'good night' })) })
  );
  fs.writeFileSync(
    path.join(dir, '_categories.json'),
    JSON.stringify({ categories: [{ id: 'goodnight', order: 1, icon: 'moon', hue: 250, emojis: ['🌙', '😴'], names: { en: 'Good night' } }] })
  );
  return dir;
}

async function main() {
  if (!process.env.FIRESTORE_EMULATOR_HOST || !process.env.FIREBASE_STORAGE_EMULATOR_HOST) {
    fail('run me inside firebase emulators:exec (FIRESTORE_EMULATOR_HOST / FIREBASE_STORAGE_EMULATOR_HOST unset)');
  }
  const { getApps, initializeApp } = require('firebase-admin/app');
  const { getFirestore } = require('firebase-admin/firestore');
  const app = getApps()[0] || initializeApp({ projectId: PROJECT });
  const db = getFirestore(app, 'stickermaker');
  const bucket = storageBucket({ project: PROJECT, bucketName: BUCKET });

  const library = testLibrary();
  const pack = listLocalPacks(library)[0];
  const packId = JSON.parse(fs.readFileSync(path.join(pack.dir, 'pack.json'), 'utf8')).id || null;
  const stickerCount = pack.files.filter((f) => /\.(png|webp|gif)$/i.test(f) && !/^tray\.png$/i.test(f)).length;
  console.log(`library ${library}: ${packName(pack)} (${stickerCount} stickers)`);

  await uploadFile(bucket, path.join(library, '_categories.json'), 'library/_categories.json', {});
  await poll('categories in Firestore', async () => {
    const snap = await db.collection('categories').get();
    return snap.size > 0 ? snap : null;
  }, 60000);
  console.log('✓ categories synced');

  const { prefix } = await uploadPack(bucket, pack, {});
  const fetchCatalog = async (meta) => {
    const url = meta.urlTemplate.replace('{path}', encodeURIComponent(meta.path)).replace('{rawPath}', meta.path);
    const res = await fetch(url);
    if (!res.ok) throw new Error(`catalog HTTP ${res.status}`);
    return JSON.parse(zlib.gunzipSync(Buffer.from(await res.arrayBuffer())).toString('utf8'));
  };
  const urlOf = (meta, p) => meta.urlTemplate.replace('{path}', encodeURIComponent(p)).replace('{rawPath}', p);

  const { meta, catalog, entry } = await poll('the pack in the catalog', async () => {
    const snap = await db.doc('catalog/meta').get();
    if (!snap.exists) return null;
    const m = snap.data();
    const c = await fetchCatalog(m);
    const e = c.packs.find((p) => (packId ? p.id === packId : true));
    return e ? { meta: m, catalog: c, entry: e } : null;
  }, 180000);
  console.log(`✓ catalog v${meta.version}: ${catalog.packs.length} pack(s), ${meta.bytes} bytes gzipped`);
  if (entry.count !== stickerCount) fail(`catalog count ${entry.count}, expected ${stickerCount}`);
  if (entry.version !== 1) fail(`first version is ${entry.version}`);
  if (!/\/v1-[0-9a-f]{8}\/pack\.zip$/.test(entry.zip.path)) fail(`zip path ${entry.zip.path} is not content-addressed`);
  if (!entry.category) fail('the pack has no category: its tags name none');
  if (!catalog.categories.some((c) => c.id === entry.category && c.packs >= 1)) fail('category count missing');

  const cover = await fetch(urlOf(meta, entry.cover.l));
  if (!cover.ok || !(cover.headers.get('content-type') || '').startsWith('image/webp')) fail(`cover HTTP ${cover.status}`);
  const zipRes = await fetch(urlOf(meta, entry.zip.path));
  if (!zipRes.ok) fail(`zip HTTP ${zipRes.status}`);
  const zip = Buffer.from(await zipRes.arrayBuffer());
  if (zip.length !== entry.zip.bytes) fail(`zip is ${zip.length} bytes, catalog says ${entry.zip.bytes}`);
  const files = unzipSync(new Uint8Array(zip));
  const contents = JSON.parse(strFromU8(files['contents.json']));
  if (contents.stickers.length !== stickerCount) fail(`zip holds ${contents.stickers.length} stickers`);
  for (const s of contents.stickers) {
    const b = Buffer.from(files[s.file]);
    if (b.toString('ascii', 0, 4) !== 'RIFF' || b.toString('ascii', 8, 12) !== 'WEBP') fail(`${s.file} is not WebP`);
  }
  if (!files['tray.png']) fail('no tray.png in the zip');
  console.log(`✓ cover and zip downloaded (${zip.length} bytes, ${contents.stickers.length} stickers + tray)`);

  const report = (await bucket.file(`${prefix}_report.txt`).download())[0].toString('utf8');
  if (!report.startsWith('✅')) fail(`report: ${report}`);
  console.log(`✓ report: ${report.split('\n')[0]}`);

  // The same files again: nothing new is published.
  const againSince = Date.now() - 1000;
  await bucket.upload(path.join(pack.dir, 'pack.json'), { destination: `${prefix}pack.json`, resumable: false });
  const unchanged = await poll('the unchanged report', async () => {
    const [m] = await bucket.file(`${prefix}_report.txt`).getMetadata();
    if (Date.parse(m.updated) < againSince) return null;
    const text = (await bucket.file(`${prefix}_report.txt`).download())[0].toString('utf8');
    return text.includes('unchanged') ? text : null;
  }, 180000);
  const metaAfter = (await db.doc('catalog/meta').get()).data();
  if (metaAfter.version !== meta.version) fail(`an unchanged pack republished the catalog (v${metaAfter.version})`);
  console.log(`✓ re-upload: ${unchanged.split('\n')[0]}`);

  // A slow update of a live pack: two stickers swap places, 4 s apart, pack.json untouched. Only the settled
  // folder may go live, as one new version.
  const listed = JSON.parse(fs.readFileSync(path.join(pack.dir, 'pack.json'), 'utf8')).stickers.map((s) => (typeof s === 'string' ? s : s.file));
  const [first, second] = listed;
  const updateSince = Date.now() - 1000;
  await uploadFile(bucket, path.join(pack.dir, second), `${prefix}${first}`, {});
  await new Promise((r) => setTimeout(r, 4000));
  await uploadFile(bucket, path.join(pack.dir, first), `${prefix}${second}`, {});
  const updated = await poll('the update report', async () => {
    const [m] = await bucket.file(`${prefix}_report.txt`).getMetadata();
    if (Date.parse(m.updated) < updateSince) return null;
    const text = (await bucket.file(`${prefix}_report.txt`).download())[0].toString('utf8');
    return text.startsWith('✅') && !text.includes('unchanged') ? text : null;
  }, 240000);
  if (!updated.includes('version 2')) fail(`update report: ${updated}`);
  const record = (await db.collection('packs').doc(entry.id).get()).data();
  if (record.version !== 2) fail(`the update published version ${record.version}`);
  const [publicFiles] = await bucket.getFiles({ prefix: `public/packs/${entry.id}/` });
  const versions = [...new Set(publicFiles.map((f) => f.name.split('/')[3]))].sort();
  if (versions.length !== 2 || !versions[1].startsWith('v2-')) fail(`version folders ${versions.join(', ')}`);
  const v2 = unzipSync(new Uint8Array((await bucket.file(`public/packs/${entry.id}/${versions[1]}/pack.zip`).download())[0]));
  if (Buffer.compare(Buffer.from(v2['01.webp']), Buffer.from(files['02.webp'])) !== 0) fail('version 2 does not hold both swapped stickers');
  if (Buffer.compare(Buffer.from(v2['02.webp']), Buffer.from(files['01.webp'])) !== 0) fail('version 2 does not hold both swapped stickers');
  console.log(`✓ slow update: ${updated.split('\n')[0]} (${versions.join(', ')})`);

  // Delete the folder: the pack leaves the catalog.
  await bucket.deleteFiles({ prefix });
  await poll('the pack to leave the catalog', async () => {
    const m = (await db.doc('catalog/meta').get()).data();
    if (m.version === metaAfter.version) return null;
    const c = await fetchCatalog(m);
    return c.packs.some((p) => p.id === entry.id) ? null : c;
  }, 180000);
  const doc = (await db.collection('packs').doc(entry.id).get()).data();
  if (doc.status !== 'removed') fail(`pack status ${doc.status}`);
  console.log('✓ deleting the folder removed the pack');
  console.log('E2E PASSED');
  process.exit(0);
}

main().catch((err) => fail(err.stack || err.message));
