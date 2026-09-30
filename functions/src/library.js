'use strict';

const crypto = require('crypto');
const { LIBRARY_PREFIX } = require('./config');

const CATEGORIES_FILE = `${LIBRARY_PREFIX}_categories.json`;
const TAGS_FILE = `${LIBRARY_PREFIX}_tags.json`;
const REPORT_FILE = '_report.txt';
const JUNK_FILES = new Set(['.ds_store', 'thumbs.db', 'desktop.ini']);
const MAX_ID_LENGTH = 64;

/**
 * A pack id from a folder name: accents stripped, lowercase, anything but a-z/0-9 collapsed into one hyphen.
 * Names with no usable latin letters (Arabic, emoji…) get a stable hashed id instead.
 */
function slugify(name) {
  const slug = String(name)
    .normalize('NFKD')
    .replace(/\p{M}/gu, '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, MAX_ID_LENGTH)
    .replace(/-+$/g, '');
  if (slug.length >= 2) return slug;
  const hash = crypto.createHash('sha1').update(String(name), 'utf8').digest('hex').slice(0, 8);
  return `pack-${hash}`;
}

function isJunk(file) {
  return file === '' || file.startsWith('.') || JUNK_FILES.has(file.toLowerCase());
}

/**
 * A pack's library folder: library/<folder>/, or library/<category>/<folder>/ in the older layout, whose
 * category folder still counts as one of the pack's tags.
 */
function libraryPrefix(category, folder) {
  return category ? `${LIBRARY_PREFIX}${category}/${folder}/` : `${LIBRARY_PREFIX}${folder}/`;
}

/**
 * What a library object is: the categories or tags file, a pack's build report, a file of a pack
 * (library/<folder>/<file>, or library/<category>/<folder>/<file>), or something the pipeline ignores (staging
 * folders, junk, folder placeholders, anything at another depth).
 */
function parseLibraryPath(objectName) {
  if (objectName === CATEGORIES_FILE) return { kind: 'categories' };
  if (objectName === TAGS_FILE) return { kind: 'tags' };
  if (!objectName.startsWith(LIBRARY_PREFIX)) return { kind: 'ignored' };
  const parts = objectName.slice(LIBRARY_PREFIX.length).split('/');
  if (parts.length !== 2 && parts.length !== 3) return { kind: 'ignored' };
  const [file, folder, category = null] = [...parts].reverse();
  if (!folder || folder.startsWith('_')) return { kind: 'ignored' };
  if (category !== null && (!category || category.startsWith('_'))) return { kind: 'ignored' };
  if (file === REPORT_FILE) return { kind: 'report' };
  if (isJunk(file) || file.startsWith('_')) return { kind: 'ignored' };
  return { kind: 'pack', category, folder, packId: slugify(folder), file, prefix: libraryPrefix(category, folder) };
}

/** A Firestore-safe key for a library folder prefix (folder names may hold any character). */
function folderKey(prefix) {
  return crypto.createHash('sha1').update(prefix, 'utf8').digest('hex').slice(0, 20);
}

function isImageName(file) {
  return /\.(png|webp|gif)$/i.test(file);
}

const collator = new Intl.Collator('en', { numeric: true, sensitivity: 'base' });

/** "2.png" before "10.png". */
function naturalCompare(a, b) {
  return collator.compare(a, b);
}

module.exports = {
  slugify,
  folderKey,
  libraryPrefix,
  parseLibraryPath,
  isImageName,
  naturalCompare,
  isJunk,
  REPORT_FILE,
  CATEGORIES_FILE,
  TAGS_FILE,
};
