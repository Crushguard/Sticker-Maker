'use strict';

/**
 * The rules of builds/<packId>, shared by the Firestore store (store.js) and the tests' in-memory one:
 * - folders.<key>: one entry per library folder whose name gives this pack id (a pack can move between category
 *   folders): when it last changed, and whether a quiet build is scheduled for it that hasn't looked at it yet.
 * - lease: the one build of this pack id allowed to run now.
 */

/** A pending flag this old belongs to a task that was dropped, and no longer stops an event from scheduling. */
const PENDING_STALE_MS = 10 * 60 * 1000;
/** Longer than a build may run (540 s): a lease only outlives its build when the instance died. */
const LEASE_MS = 10 * 60 * 1000;

function isPending(entry, nowMs) {
  return !!(entry && entry.pending && nowMs - (entry.pendingSinceMs || 0) <= PENDING_STALE_MS);
}

/** A lease can be taken when nobody holds it, its holder asks again (a retried task), or it has expired. */
function leaseFree(lease, holder, nowMs) {
  return !lease || lease.holder === holder || lease.untilMs <= nowMs;
}

/**
 * Whether packs/<id> still holds what a build started from: same version, content and status. Console edits (pin,
 * hidden) and the weekly stats don't count.
 */
function sameVersion(current, expected) {
  const pick = (r) => [(r && r.version) || null, (r && r.contentHash) || null, (r && r.status) || null];
  const [a, b] = [pick(current), pick(expected)];
  return a.every((value, i) => value === b[i]);
}

module.exports = { isPending, leaseFree, sameVersion, PENDING_STALE_MS, LEASE_MS };
