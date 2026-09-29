#!/usr/bin/env node
/**
 * upload.js — puts a local sticker library into the stickermaker bucket, the way the pipeline likes it:
 * _categories.json first, then per pack every sticker (and tray.png) before pack.json, so a listing pack.json
 * makes the pack build the moment it lands. Files whose MD5 already matches are skipped.
 *
 * Usage:
 *   node scripts/library/upload.js <libraryDir> [--pack <category>/<folder>] [--wait] [--dry-run]
 *                                   [--bucket <name>] [--project <id>] [--emulator]
 *
 * Credentials: Application Default Credentials (gcloud auth application-default login). With --emulator (or
 * FIREBASE_STORAGE_EMULATOR_HOST set) it talks to the local Storage emulator instead.
 * --wait prints each pack's _report.txt once the build has written it.
 */
'use strict';

const crypto = require('crypto');
const fs = require('fs');
const path = require('path');

const DEFAULT_PROJECT = 'play-console-f33dd';
const CONTENT_TYPES = { '.png': 'image/png', '.webp': 'image/webp', '.gif': 'image/gif', '.json': 'application/json' };
const UPLOAD_CONCURRENCY = 6;

function arg(flag, fallback) {
  const i = process.argv.indexOf(flag);
  return i >= 0 && process.argv[i + 1] ? process.argv[i + 1] : fallback;
}

const skipName = (name) => name.startsWith('.') || name.startsWith('_');

/** Packs of a local library: [{ category, folder, dir, files: [names] }]. */
function listLocalPacks(libraryDir) {
  const packs = [];
  for (const category of fs.readdirSync(libraryDir).sort()) {
    const categoryDir = path.join(libraryDir, category);
    if (skipName(category) || !fs.statSync(categoryDir).isDirectory()) continue;
    for (const folder of fs.readdirSync(categoryDir).sort()) {
      const dir = path.join(categoryDir, folder);
      if (skipName(folder) || !fs.statSync(dir).isDirectory()) continue;
      const files = fs.readdirSync(dir).filter((f) => !skipName(f) && fs.statSync(path.join(dir, f)).isFile());
      packs.push({ category, folder, dir, files });
    }
  }
  return packs;
}

function md5(file) {
  return crypto.createHash('md5').update(fs.readFileSync(file)).digest('base64');
}

function storageBucket({ project, bucketName }) {
  const { initializeApp, getApps } = require('firebase-admin/app');
  const { getStorage } = require('firebase-admin/storage');
  const app = getApps()[0] || initializeApp({ projectId: project });
  return getStorage(app).bucket(bucketName);
}

async function uploadFile(bucket, localPath, remotePath, { dryRun }) {
  const file = bucket.file(remotePath);
  const [exists] = await file.exists();
  if (exists) {
    const [meta] = await file.getMetadata();
    if (meta.md5Hash === md5(localPath)) return 'unchanged';
  }
  if (dryRun) return 'would-upload';
  const contentType = CONTENT_TYPES[path.extname(localPath).toLowerCase()] || 'application/octet-stream';
  await bucket.upload(localPath, { destination: remotePath, resumable: false, contentType });
  return 'uploaded';
}

async function inBatches(items, size, fn) {
  const results = [];
  for (let i = 0; i < items.length; i += size) results.push(...(await Promise.all(items.slice(i, i + size).map(fn))));
  return results;
}

/**
 * Uploads one pack: stickers and tray first, pack.json last. Returns counts per outcome.
 * options.concurrency: parallel uploads (default 6; 1 keeps the Functions emulator to one worker).
 */
async function uploadPack(bucket, pack, options) {
  const prefix = `library/${pack.category}/${pack.folder}/`;
  const first = pack.files.filter((f) => f !== 'pack.json');
  const outcomes = await inBatches(first, options.concurrency || UPLOAD_CONCURRENCY, (f) =>
    uploadFile(bucket, path.join(pack.dir, f), prefix + f, options)
  );
  if (pack.files.includes('pack.json')) {
    outcomes.push(await uploadFile(bucket, path.join(pack.dir, 'pack.json'), `${prefix}pack.json`, options));
  }
  const counts = {};
  for (const o of outcomes) counts[o] = (counts[o] || 0) + 1;
  return { prefix, counts, changed: outcomes.some((o) => o === 'uploaded') };
}

async function waitForReport(bucket, prefix, sinceMs, timeoutMs = 240000) {
  const file = bucket.file(`${prefix}_report.txt`);
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const [exists] = await file.exists();
    if (exists) {
      const [meta] = await file.getMetadata();
      if (Date.parse(meta.updated) >= sinceMs) return (await file.download())[0].toString('utf8');
    }
    await new Promise((r) => setTimeout(r, 3000));
  }
  return null;
}

async function main() {
  const libraryDir = process.argv[2];
  if (!libraryDir || libraryDir.startsWith('--') || !fs.existsSync(libraryDir)) {
    console.error('usage: upload.js <libraryDir> [--pack <category>/<folder>] [--wait] [--dry-run] [--bucket name] [--project id] [--emulator]');
    process.exit(2);
  }
  const project = arg('--project', DEFAULT_PROJECT);
  if (process.argv.includes('--emulator') && !process.env.FIREBASE_STORAGE_EMULATOR_HOST) {
    process.env.FIREBASE_STORAGE_EMULATOR_HOST = '127.0.0.1:9199';
  }
  const options = { dryRun: process.argv.includes('--dry-run') };
  const bucket = storageBucket({ project, bucketName: arg('--bucket', `${project}-stickermaker`) });
  const only = arg('--pack', null);
  const startedMs = Date.now() - 5000;

  const categoriesFile = path.join(libraryDir, '_categories.json');
  if (!only && fs.existsSync(categoriesFile)) {
    console.log(`_categories.json: ${await uploadFile(bucket, categoriesFile, 'library/_categories.json', options)}`);
  }
  const packs = listLocalPacks(libraryDir).filter((p) => !only || `${p.category}/${p.folder}` === only);
  if (only && !packs.length) {
    console.error(`no pack ${only} in ${libraryDir}`);
    process.exit(2);
  }
  const uploaded = [];
  for (const pack of packs) {
    const { prefix, counts, changed } = await uploadPack(bucket, pack, options);
    console.log(`${pack.category}/${pack.folder}: ${Object.entries(counts).map(([k, v]) => `${v} ${k}`).join(', ')}`);
    if (changed) uploaded.push(prefix);
  }
  if (process.argv.includes('--wait') && !options.dryRun) {
    for (const prefix of uploaded) {
      const report = await waitForReport(bucket, prefix, startedMs);
      console.log(`\n${prefix}\n${report ? report.trim() : '(no report yet: check the functions logs)'}`);
    }
  }
}

if (require.main === module) {
  main().catch((err) => {
    console.error(err.message || err);
    process.exit(1);
  });
}

module.exports = { listLocalPacks, uploadPack, uploadFile, waitForReport, storageBucket };
