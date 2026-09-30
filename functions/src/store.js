'use strict';

const { FieldValue, Timestamp } = require('firebase-admin/firestore');
const { db } = require('./firebase');
const { isPending, leaseFree, sameVersion, LEASE_MS, DROPPED_PACK_FIELDS } = require('./state');

const toMs = (t) => (t && typeof t.toMillis === 'function' ? t.toMillis() : null);

/** builds/<packId>.folders.<key> as the build task reads it. */
function folderEntry(raw) {
  return {
    folder: raw.folder,
    lastEventMs: toMs(raw.lastEventAt),
    pending: !!raw.pending,
    pendingSinceMs: toMs(raw.pendingSince),
  };
}

/**
 * The pipeline's records in the stickermaker database: packs/<id>, builds/<id> and categories/. Every rule that
 * decides something (pending, lease, version check) comes from state.js, shared with the tests' in-memory store.
 */
function firestoreStore() {
  const packRef = (packId) => db().collection('packs').doc(packId);
  const buildsRef = (packId) => db().collection('builds').doc(packId);

  // Sets the folder's pending flag unless a quiet build is already pending; an event also stamps its time.
  const claim = (packId, key, { folder }, nowMs, event) =>
    db().runTransaction(async (tx) => {
      const ref = buildsRef(packId);
      const snap = await tx.get(ref);
      const raw = snap.exists ? (snap.data().folders || {})[key] : null;
      const schedule = !isPending(raw ? folderEntry(raw) : null, nowMs);
      const at = Timestamp.fromMillis(nowMs);
      const entry = { folder };
      if (event) entry.lastEventAt = at;
      if (schedule) Object.assign(entry, { pending: true, pendingSince: at });
      tx.set(ref, { folders: { [key]: entry } }, { merge: true });
      return schedule;
    });

  return {
    async readPack(packId) {
      const snap = await packRef(packId).get();
      return snap.exists ? snap.data() : null;
    },
    async readFolders(packId) {
      const snap = await buildsRef(packId).get();
      const folders = (snap.exists && snap.data().folders) || {};
      return Object.fromEntries(Object.entries(folders).map(([key, raw]) => [key, folderEntry(raw)]));
    },
    recordEvent: (packId, key, meta, nowMs) => claim(packId, key, meta, nowMs, true),
    claimPending: (packId, key, meta, nowMs) => claim(packId, key, meta, nowMs, false),
    clearPending: (packId, key) => buildsRef(packId).set({ folders: { [key]: { pending: false } } }, { merge: true }),
    acquireLease: (packId, holder, nowMs) =>
      db().runTransaction(async (tx) => {
        const ref = buildsRef(packId);
        const snap = await tx.get(ref);
        const lease = snap.exists ? snap.data().lease : null;
        if (!leaseFree(lease ? { holder: lease.holder, untilMs: toMs(lease.until) } : null, holder, nowMs)) return false;
        tx.set(ref, { lease: { holder, until: Timestamp.fromMillis(nowMs + LEASE_MS) } }, { merge: true });
        return true;
      }),
    releaseLease: (packId, holder) =>
      db().runTransaction(async (tx) => {
        const ref = buildsRef(packId);
        const snap = await tx.get(ref);
        const lease = snap.exists ? snap.data().lease : null;
        if (lease && lease.holder === holder) tx.set(ref, { lease: FieldValue.delete() }, { merge: true });
      }),
    /** Writes the record only if it still holds what the build started from (state.sameVersion). */
    commitPack: (packId, expected, doc) =>
      db().runTransaction(async (tx) => {
        const ref = packRef(packId);
        const snap = await tx.get(ref);
        if (!sameVersion(snap.exists ? snap.data() : null, expected)) return false;
        const write = { ...doc, ...Object.fromEntries(DROPPED_PACK_FIELDS.map((f) => [f, FieldValue.delete()])) };
        tx.set(ref, write, { mergeFields: Object.keys(write) });
        return true;
      }),
    mergePack: (packId, fields) => packRef(packId).set(fields, { merge: true }),
    async categories() {
      const snap = await db().collection('categories').get();
      return new Map(snap.docs.map((d) => [d.id, d.data()]));
    },
  };
}

module.exports = { firestoreStore };
