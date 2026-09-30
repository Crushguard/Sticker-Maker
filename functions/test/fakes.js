'use strict';

/**
 * In-memory stand-ins for the stickermaker bucket, database and build queue, on a simulated clock. Tests drive the
 * library events and the build task the way they run in the cloud: events schedule tasks, due tasks run, a wait
 * (NotQuietYet) or an error retries with Cloud Tasks' backoff, and the last attempt is the last.
 */

const { handleLibraryEvent } = require('../src/events');
const { runBuildTask } = require('../src/buildTask');
const { isPending, leaseFree, sameVersion, LEASE_MS, DROPPED_PACK_FIELDS } = require('../src/state');
const { BUILD_RETRY } = require('../src/config');

const T0 = Date.UTC(2026, 8, 29, 12, 0, 0);
const HOUR = 60 * 60 * 1000;

const CATEGORIES = [
  { id: 'cute', order: 1, names: { en: 'Cute' }, emojis: ['🥰'] },
  { id: 'romantic', order: 2, names: { en: 'Romantic' }, emojis: ['❤️'] },
];

const isMap = (v) => v !== null && typeof v === 'object' && !Array.isArray(v) && !(v instanceof Date);

/** Firestore's set(…, { merge: true }): nested maps merge, everything else is replaced. */
function mergeDeep(target, patch) {
  const out = { ...target };
  for (const [k, v] of Object.entries(patch)) out[k] = isMap(v) && isMap(out[k]) ? mergeDeep(out[k], v) : v;
  return out;
}

/** packs/, builds/ and categories/ with the rules of src/store.js (both take them from src/state.js). */
function memoryStore(categories = CATEGORIES) {
  const packs = new Map();
  const builds = new Map();
  const buildsDoc = (id) => {
    if (!builds.has(id)) builds.set(id, { folders: {} });
    return builds.get(id);
  };
  const claim = (id, key, { category, folder }, nowMs, event) => {
    const doc = buildsDoc(id);
    const entry = doc.folders[key] || null;
    const schedule = !isPending(entry, nowMs);
    doc.folders[key] = {
      ...entry,
      category,
      folder,
      ...(event ? { lastEventMs: nowMs } : {}),
      ...(schedule ? { pending: true, pendingSinceMs: nowMs } : {}),
    };
    return schedule;
  };
  return {
    packs,
    builds,
    readPack: async (id) => structuredClone(packs.get(id) ?? null),
    readFolders: async (id) => structuredClone(buildsDoc(id).folders),
    recordEvent: async (id, key, meta, nowMs) => claim(id, key, meta, nowMs, true),
    claimPending: async (id, key, meta, nowMs) => claim(id, key, meta, nowMs, false),
    clearPending: async (id, key) => {
      const doc = buildsDoc(id);
      doc.folders[key] = { ...doc.folders[key], pending: false };
    },
    acquireLease: async (id, holder, nowMs) => {
      const doc = buildsDoc(id);
      if (!leaseFree(doc.lease, holder, nowMs)) return false;
      doc.lease = { holder, untilMs: nowMs + LEASE_MS };
      return true;
    },
    releaseLease: async (id, holder) => {
      const doc = buildsDoc(id);
      if (doc.lease && doc.lease.holder === holder) delete doc.lease;
    },
    commitPack: async (id, expected, record) => {
      if (!sameVersion(packs.get(id) ?? null, expected)) return false;
      const next = { ...packs.get(id), ...structuredClone(record) };
      for (const field of DROPPED_PACK_FIELDS) delete next[field];
      packs.set(id, next);
      return true;
    },
    mergePack: async (id, fields) => {
      packs.set(id, mergeDeep(packs.get(id) || {}, structuredClone(fields)));
    },
    categories: async () => new Map(categories.map(({ id, ...c }) => [id, structuredClone(c)])),
  };
}

class World {
  constructor({ categories } = {}) {
    this.nowMs = T0;
    this.objects = new Map();
    this.store = memoryStore(categories);
    /** Build tasks: { id, task, dueMs, retryCount }. */
    this.queue = [];
    this.usedIds = new Set();
    this.publishes = 0;
    this.syncs = 0;
    this.tagSyncs = 0;
    /** Every build attempt: { id, folder, quiet, retryCount, outcome } or { …, error }. */
    this.runs = [];
    this.generation = 0;
    this.faults = {};
    this.hooks = {};
    const store = Object.fromEntries(
      Object.entries(this.store).map(([name, fn]) => [name, typeof fn === 'function' ? this.faulty(name, fn) : fn])
    );
    this.deps = {
      now: () => new Date(this.nowMs),
      listFolder: async (prefix) => this.listFolder(prefix),
      readBuffer: async (entry) => {
        if (this.hooks.readBuffer) await this.hooks.readBuffer(entry);
        return this.read(entry);
      },
      readText: async (entry) => this.read(entry).toString('utf8'),
      listPaths: async (prefix) => this.paths(prefix),
      deletePath: async (path) => {
        this.objects.delete(path);
      },
      savePublic: async (path, buffer, contentType) => {
        if (this.hooks.savePublic) await this.hooks.savePublic(path);
        this.put(path, buffer, contentType);
      },
      saveText: async (path, text) => this.put(path, Buffer.from(text, 'utf8'), 'text/plain'),
      store,
      enqueueBuild: this.faulty('enqueueBuild', async (task) => this.enqueueBuild(task)),
      enqueuePublish: this.faulty('enqueuePublish', async () => {
        this.publishes += 1;
      }),
      syncCategories: async () => {
        this.syncs += 1;
      },
      syncTags: async () => {
        this.tagSyncs += 1;
      },
    };
  }

  /** Makes the next `times` calls of a dependency (a store method, enqueueBuild, enqueuePublish) throw. */
  fault(name, times = 1) {
    this.faults[name] = (this.faults[name] || 0) + times;
  }

  faulty(name, fn) {
    return async (...args) => {
      if (this.faults[name] > 0) {
        this.faults[name] -= 1;
        throw new Error(`injected ${name} failure`);
      }
      return fn(...args);
    };
  }

  // ---- Storage ----

  put(name, buffer, contentType = 'application/octet-stream') {
    this.generation += 1;
    this.objects.set(name, { buffer, contentType, updatedMs: this.nowMs, generation: String(this.generation) });
  }

  read(entry) {
    const object = this.objects.get(entry.name);
    if (!object) throw Object.assign(new Error(`No such object: ${entry.name}`), { code: 404 });
    return object.buffer;
  }

  listFolder(prefix) {
    return [...this.objects.entries()]
      .filter(([name]) => name.startsWith(prefix) && name.length > prefix.length && !name.slice(prefix.length).includes('/'))
      .map(([name, o]) => ({ file: name.slice(prefix.length), name, generation: o.generation, updatedMs: o.updatedMs, size: o.buffer.length }));
  }

  paths(prefix) {
    return [...this.objects.keys()].filter((name) => name.startsWith(prefix)).sort();
  }

  text(name) {
    const object = this.objects.get(name);
    return object ? object.buffer.toString('utf8') : null;
  }

  /** An upload and its Storage event. */
  async upload(name, buffer) {
    this.put(name, buffer);
    await handleLibraryEvent(name, 'finalized', this.nowMs, this.deps);
  }

  /** A deletion and its Storage event. */
  async remove(name) {
    this.objects.delete(name);
    await handleLibraryEvent(name, 'deleted', this.nowMs, this.deps);
  }

  // ---- Build queue ----

  enqueueBuild({ category = null, folder, quiet = false, delaySeconds = 0, id }) {
    const taskId = id || `task-${this.usedIds.size + 1}`;
    // Cloud Tasks refuses an id it has seen; queue.js reports that as "not enqueued".
    if (this.usedIds.has(taskId)) return false;
    this.usedIds.add(taskId);
    this.queue.push({ id: taskId, task: { category, folder, quiet }, dueMs: this.nowMs + delaySeconds * 1000, retryCount: 0 });
    return true;
  }

  async runTask(entry) {
    const run = { id: entry.id, folder: entry.task.folder, quiet: entry.task.quiet, retryCount: entry.retryCount };
    try {
      run.outcome = (await runBuildTask(entry.task, { retryCount: entry.retryCount, taskId: entry.id }, this.deps)).outcome;
    } catch (err) {
      run.error = err;
      if (entry.retryCount + 1 < BUILD_RETRY.maxAttempts) {
        const backoff = Math.min(BUILD_RETRY.maxBackoffSeconds, BUILD_RETRY.minBackoffSeconds * 2 ** entry.retryCount);
        this.queue.push({ ...entry, retryCount: entry.retryCount + 1, dueMs: this.nowMs + backoff * 1000 });
      }
    }
    this.runs.push(run);
  }

  /** Runs due tasks in time order (the clock jumps to each) up to untilMs, or until the queue is empty. */
  async settle({ untilMs = this.nowMs + HOUR } = {}) {
    for (;;) {
      this.queue.sort((a, b) => a.dueMs - b.dueMs);
      if (!this.queue.length || this.queue[0].dueMs > untilMs) break;
      const next = this.queue.shift();
      this.nowMs = Math.max(this.nowMs, next.dueMs);
      await this.runTask(next);
    }
  }

  tick(ms) {
    this.nowMs += ms;
  }

  /** Lets `ms` of simulated time pass, running whatever falls due. */
  async advance(ms) {
    const end = this.nowMs + ms;
    await this.settle({ untilMs: end });
    this.nowMs = end;
  }
}

module.exports = { World, memoryStore, T0, CATEGORIES };
