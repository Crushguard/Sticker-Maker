'use strict';

function stamp(at) {
  return `${at.toISOString().slice(0, 16).replace('T', ' ')} UTC`;
}

/** The _report.txt written into a pack folder after every build. */
function renderReport({ ok, unchanged, name, version, count, animated, category, alsoIn, liveVersion, errors, notes, at }) {
  const lines = [];
  if (ok) {
    const where = `in ${category}${alsoIn && alsoIn.length ? ` (also ${alsoIn.join(', ')})` : ''}`;
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
