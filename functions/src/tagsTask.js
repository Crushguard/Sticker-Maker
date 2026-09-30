'use strict';

const { LIBRARY_PREFIX } = require('./config');
const { TAGS_FILE } = require('./library');
const { parseTagsFile } = require('./tags');
const { db, bucket, saveText } = require('./firebase');
const { enqueuePublish } = require('./queue');

const TAGS_REPORT = `${LIBRARY_PREFIX}_tags_report.txt`;
const TAGS_DOC = 'config/tags';

/**
 * library/_tags.json → Firestore config/tags, which the catalog copies for the tags its packs use. A file with no
 * valid entry changes nothing, so a broken upload never wipes search.
 */
async function syncTags({ now = new Date() } = {}) {
  const [buf] = await bucket().file(TAGS_FILE).download();
  const { errors, tags, languages } = parseTagsFile(buf.toString('utf8'));
  const tagCount = Object.keys(tags).length;
  const languageCount = Object.keys(languages).length;
  const lines = [];
  if (tagCount + languageCount === 0) {
    lines.push(`❌ _tags.json was not applied: no valid entry. Checked ${now.toISOString()}.`);
  } else {
    await db().doc(TAGS_DOC).set({ tags, languages, updatedAt: now });
    await enqueuePublish();
    lines.push(`✅ ${tagCount} tags and ${languageCount} languages applied. ${now.toISOString()}.`);
  }
  for (const error of errors) lines.push(`- ${error}`);
  await saveText(TAGS_REPORT, `${lines.join('\n')}\n`);
  return { errors, tags: tagCount, languages: languageCount };
}

module.exports = { syncTags, TAGS_DOC };
