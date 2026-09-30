#!/usr/bin/env node
/**
 * upload.js — puts a local sticker library into the stickermaker bucket, the way the pipeline likes it:
 * _categories.json and _tags.json first (each, when it changed, with a wait until it is applied), then per pack
 * folder (library/<folder>/) every sticker (and tray.png) before pack.json, so a listing pack.json makes a new pack
 * build the moment it lands. Files whose MD5 already matches are skipped. A folder of pack folders, such as a Claude
 * Design export, is a library too.
 *
 * Usage:
 *   node scripts/library/upload.js <libraryDir> [--pack <folder>] [--rebuild] [--wait] [--dry-run]
 *                                   [--bucket <name>] [--project <id>] [--emulator]
 *
 * Credentials: Application Default Credentials (gcloud auth application-default login). With --emulator (or
 * FIREBASE_STORAGE_EMULATOR_HOST set) it talks to the local Storage emulator instead.
 * --wait prints each pack's _report.txt once the build has written it.
 * --rebuild uploads every pack.json again even when it is unchanged, so every pack builds again: after a change to
 * the pipeline itself (the builds only run when a folder changes).
 */
'use strict';

const crypto = require('crypto');
const fs = require('fs');
const path = require('path');

const DEFAULT_PROJECT = 'play-console-f33dd';
const CONTENT_TYPES = { '.png': 'image/png', '.webp': 'image/webp', '.gif': 'image/gif', '.json': 'application/json' };
const UPLOAD_CONCURRENCY = 6;
/** The library's shared files, uploaded before any pack, and the report the pipeline writes for each. */
const SHARED_FILES = [
  { name: '_categories.json', report: 'library/_categories_report.txt' },
  { name: '_tags.json', report: 'library/_tags_report.txt' },
];

function arg(flag, fallback) {
  const i = process.argv.indexOf(flag);
  return i >= 0 && process.argv[i + 1] ? process.argv[i + 1] : fallback;
}

const skipName = (name) => name.startsWith('.') || name.startsWith('_');

const filesIn = (dir) => fs.readdirSync(dir).filter((f) => !skipName(f) && fs.statSync(path.join(dir, f)).isFile());
const foldersIn = (dir) =>
  fs.readdirSync(dir).sort().filter((f) => !skipName(f) && fs.statSync(path.join(dir, f)).isDirectory());

/**
 * Packs of a local library: [{ category, folder, dir, files: [names] }]. A folder with files is a pack
 * (library/<folder>/, category null); a folder holding only folders is a category folder of the older layout.
 */
function listLocalPacks(libraryDir) {
  const packs = [];
  for (const name of foldersIn(libraryDir)) {
    const dir = path.join(libraryDir, name);
    const files = filesIn(dir);
    if (files.length) {
      packs.push({ category: null, folder: name, dir, files });
      continue;
    }
    for (const folder of foldersIn(dir)) {
      const packDir = path.join(dir, folder);
      packs.push({ category: name, folder, dir: packDir, files: filesIn(packDir) });
    }
  }
  return packs;
}

/** A pack's folder in the bucket. */
function packPrefix(pack) {
  return pack.category ? `library/${pack.category}/${pack.folder}/` : `library/${pack.folder}/`;
}

/** How a pack is named on the command line and in logs. */
function packName(pack) {
  return pack.category ? `${pack.category}/${pack.folder}` : pack.folder;
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

/** Every object's MD5 under a prefix, from one listing: comparing against it costs no request per file. */
async function remoteHashes(bucket, prefix) {
  const [files] = await bucket.getFiles({ prefix, autoPaginate: true });
  return new Map(files.map((f) => [f.name, f.metadata.md5Hash]));
}

/**
 * Uploads a file unless the bucket already holds the same bytes. `remote` (remoteHashes) answers that from one
 * listing; without it each file is looked up.
 */
async function uploadFile(bucket, localPath, remotePath, { dryRun, force = false, remote }) {
  const file = bucket.file(remotePath);
  if (!force) {
    let remoteMd5 = remote ? remote.get(remotePath) : undefined;
    if (!remote) {
      const [exists] = await file.exists();
      if (exists) remoteMd5 = (await file.getMetadata())[0].md5Hash;
    }
    if (remoteMd5 && remoteMd5 === md5(localPath)) return 'unchanged';
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
  const prefix = packPrefix(pack);
  const first = pack.files.filter((f) => f !== 'pack.json');
  const outcomes = await inBatches(first, options.concurrency || UPLOAD_CONCURRENCY, (f) =>
    uploadFile(bucket, path.join(pack.dir, f), prefix + f, options)
  );
  if (pack.files.includes('pack.json')) {
    const again = { ...options, force: !!options.rebuild };
    outcomes.push(await uploadFile(bucket, path.join(pack.dir, 'pack.json'), `${prefix}pack.json`, again));
  }
  const counts = {};
  for (const o of outcomes) counts[o] = (counts[o] || 0) + 1;
  return { prefix, counts, changed: outcomes.some((o) => o === 'uploaded') };
}

/** A text object (a build or categories report) once it has been written after sinceMs, or null on timeout. */
async function waitForText(bucket, objectPath, sinceMs, timeoutMs = 240000) {
  const file = bucket.file(objectPath);
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

function waitForReport(bucket, prefix, sinceMs, timeoutMs) {
  return waitForText(bucket, `${prefix}_report.txt`, sinceMs, timeoutMs);
}

/**
 * Uploads a shared file (_categories.json or _tags.json) and, when it changed, waits until the pipeline has applied
 * it, so the packs that follow build and publish with it.
 */
async function uploadShared(bucket, libraryDir, { name, report: reportPath }, options) {
  const localPath = path.join(libraryDir, name);
  if (!fs.existsSync(localPath)) return;
  const sinceMs = Date.now() - 2000;
  const outcome = await uploadFile(bucket, localPath, `library/${name}`, options);
  console.log(`${name}: ${outcome}`);
  if (outcome !== 'uploaded') return;
  const report = await waitForText(bucket, reportPath, sinceMs, 120000);
  if (!report) throw new Error(`${name} was not applied within 2 minutes: check the functions logs`);
  console.log(report.trim());
  if (report.startsWith('❌')) throw new Error(`fix ${name} before uploading packs`);
}

/** Uploads the library's shared files, _categories.json then _tags.json, each applied before the next step. */
async function uploadSharedFiles(bucket, libraryDir, options) {
  for (const shared of SHARED_FILES) await uploadShared(bucket, libraryDir, shared, options);
}

async function main() {
  const libraryDir = process.argv[2];
  if (!libraryDir || libraryDir.startsWith('--') || !fs.existsSync(libraryDir)) {
    console.error('usage: upload.js <libraryDir> [--pack <folder>] [--rebuild] [--wait] [--dry-run] [--bucket name] [--project id] [--emulator]');
    process.exit(2);
  }
  const project = arg('--project', DEFAULT_PROJECT);
  if (process.argv.includes('--emulator') && !process.env.FIREBASE_STORAGE_EMULATOR_HOST) {
    process.env.FIREBASE_STORAGE_EMULATOR_HOST = '127.0.0.1:9199';
  }
  const bucket = storageBucket({ project, bucketName: arg('--bucket', `${project}-stickermaker`) });
  const options = {
    dryRun: process.argv.includes('--dry-run'),
    rebuild: process.argv.includes('--rebuild'),
    remote: await remoteHashes(bucket, 'library/'),
  };
  const only = arg('--pack', null);
  const startedMs = Date.now() - 5000;

  if (!only) await uploadSharedFiles(bucket, libraryDir, options);
  const packs = listLocalPacks(libraryDir).filter((p) => !only || packName(p) === only);
  if (only && !packs.length) {
    console.error(`no pack ${only} in ${libraryDir}`);
    process.exit(2);
  }
  const uploaded = [];
  for (const pack of packs) {
    const { prefix, counts, changed } = await uploadPack(bucket, pack, options);
    console.log(`${packName(pack)}: ${Object.entries(counts).map(([k, v]) => `${v} ${k}`).join(', ')}`);
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

module.exports = { listLocalPacks, packPrefix, packName, uploadPack, uploadFile, uploadSharedFiles, waitForReport, storageBucket };
