'use strict';

const { initializeApp, getApps } = require('firebase-admin/app');
const { getFirestore } = require('firebase-admin/firestore');
const { getStorage } = require('firebase-admin/storage');
const { getFunctions } = require('firebase-admin/functions');
const { BUCKET, DATABASE, IMMUTABLE_CACHE } = require('./config');

let firestore;

function app() {
  return getApps().length ? getApps()[0] : initializeApp();
}

/** The app's own named database. */
function db() {
  if (!firestore) {
    firestore = getFirestore(app(), DATABASE);
    firestore.settings({ ignoreUndefinedProperties: true });
  }
  return firestore;
}

function bucket() {
  return getStorage(app()).bucket(BUCKET);
}

function functions() {
  return getFunctions(app());
}

/** Objects directly inside a folder prefix (not in subfolders). */
async function listFolder(prefix) {
  const [files] = await bucket().getFiles({ prefix, autoPaginate: true });
  return files
    .filter((f) => f.name.length > prefix.length && !f.name.slice(prefix.length).includes('/'))
    .map((f) => ({
      file: f.name.slice(prefix.length),
      name: f.name,
      generation: String(f.metadata.generation),
      updatedMs: Date.parse(f.metadata.updated || f.metadata.timeCreated),
      size: Number(f.metadata.size),
      object: f,
    }));
}

/** Every object under a prefix, subfolders included. */
async function listPaths(prefix) {
  const [files] = await bucket().getFiles({ prefix, autoPaginate: true });
  return files.map((f) => f.name);
}

function deletePath(path) {
  return bucket().file(path).delete({ ignoreNotFound: true });
}

async function readBuffer(entry) {
  const [buf] = await entry.object.download();
  return buf;
}

async function readText(entry) {
  return (await readBuffer(entry)).toString('utf8');
}

/** A public, never-overwritten file: cached by phones and CDNs for a year. */
function savePublic(path, buffer, contentType) {
  return bucket().file(path).save(buffer, { resumable: false, contentType, metadata: { cacheControl: IMMUTABLE_CACHE } });
}

function saveText(path, text) {
  return bucket()
    .file(path)
    .save(Buffer.from(text, 'utf8'), {
      resumable: false,
      contentType: 'text/plain; charset=utf-8',
      metadata: { cacheControl: 'no-store' },
    });
}

module.exports = { app, db, bucket, functions, listFolder, listPaths, deletePath, readBuffer, readText, savePublic, saveText };
