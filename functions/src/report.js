'use strict';

function stamp(at) {
  return `${at.toISOString().slice(0, 16).replace('T', ' ')} UTC`;
}

/**
 * The _report.txt written into a pack folder after every build. `categories` are the names of the categories its
 * tags put it in; `parked` is an After Dark pack, which the Google Play build never lists.
 */
function renderReport({ ok, parked, unchanged, name, version, count, animated, categories, liveVersion, errors, notes, at }) {
  const lines = [];
  if (parked) {
    lines.push(`⏸ ${name} is not published: After Dark (18+) packs stay out of the Google Play build. Checked ${stamp(at)}.`);
  } else if (ok) {
    const where = categories && categories.length
      ? `in ${categories.join(', ')}`
      : 'in no category (no tag names one): it shows in Trending and search';
    const what = `${count} ${animated ? 'animated' : 'static'} sticker${count === 1 ? '' : 's'}`;
    lines.push(
      unchanged
        ? `✅ ${name} is live and unchanged: version ${version}, ${what}, ${where}. Checked ${stamp(at)}.`
        : `✅ ${name} is live: version ${version}, ${what}, ${where}. Built ${stamp(at)}.`
    );
  } else {
    lines.push(
      liveVersion
        ? `❌ ${name} was not published; version ${liveVersion} stays live. Checked ${stamp(at)}.`
        : `❌ ${name} was not published. Checked ${stamp(at)}.`
    );
    for (const error of errors) lines.push(`- ${error}`);
  }
  if (notes && notes.length) {
    lines.push('Notes:');
    for (const note of notes) lines.push(`- ${note}`);
  }
  return `${lines.join('\n')}\n`;
}

module.exports = { renderReport };
