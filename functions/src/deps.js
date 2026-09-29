'use strict';

/**
 * The real clock, bucket, database and queues behind the build task and the library events. Tests pass
 * test/fakes.js's in-memory ones instead.
 */
function buildDeps() {
  const storage = require('./firebase');
  const { firestoreStore } = require('./store');
  const { enqueueBuild, enqueuePublish } = require('./queue');
  return {
    now: () => new Date(),
    listFolder: storage.listFolder,
    readBuffer: storage.readBuffer,
    readText: storage.readText,
    listPaths: storage.listPaths,
    deletePath: storage.deletePath,
    savePublic: storage.savePublic,
    saveText: storage.saveText,
    store: firestoreStore(),
    enqueueBuild,
    enqueuePublish,
  };
}

function libraryDeps() {
  const { syncCategories } = require('./categoriesTask');
  return { ...buildDeps(), syncCategories };
}

module.exports = { buildDeps, libraryDeps };
