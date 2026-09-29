'use strict';

const { LIBRARY_PREFIX } = require('./config');
const { CATEGORIES_FILE } = require('./library');
const { parseCategoriesFile } = require('./categories');
const { db, bucket, saveText } = require('./firebase');
const { enqueuePublish } = require('./queue');

const CATEGORIES_REPORT = `${LIBRARY_PREFIX}_categories_report.txt`;

/**
 * library/_categories.json → Firestore categories (the file is the source of truth: categories missing from it
 * are deleted). A file with no valid category changes nothing, so a broken upload never wipes the chips.
 */
async function syncCategories({ now = new Date() } = {}) {
  const [buf] = await bucket().file(CATEGORIES_FILE).download();
  const { errors, categories } = parseCategoriesFile(buf.toString('utf8'));
  const lines = [];
  if (categories.length === 0) {
    lines.push(`❌ _categories.json was not applied: no valid category. Checked ${now.toISOString()}.`);
  } else {
    const collection = db().collection('categories');
    const existing = await collection.listDocuments();
    const batch = db().batch();
    const keep = new Set(categories.map((c) => c.id));
    for (const ref of existing) if (!keep.has(ref.id)) batch.delete(ref);
    for (const { id, ...fields } of categories) batch.set(collection.doc(id), fields);
    await batch.commit();
    await enqueuePublish(now.getTime());
    lines.push(`✅ ${categories.length} categories applied: ${categories.map((c) => c.id).join(', ')}. ${now.toISOString()}.`);
  }
  for (const error of errors) lines.push(`- ${error}`);
  await saveText(CATEGORIES_REPORT, `${lines.join('\n')}\n`);
  return { errors, count: categories.length };
}

module.exports = { syncCategories };
