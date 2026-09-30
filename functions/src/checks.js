'use strict';

const { WHATSAPP } = require('./config');

/** Pack-level WhatsApp rules; each broken rule is one line of the report. */
function checkPack({ count, kinds, liveAnimated, emojiCounts }) {
  const errors = [];
  if (count < WHATSAPP.minStickers) {
    errors.push(`Only ${count} sticker${count === 1 ? '' : 's'}: WhatsApp needs ${WHATSAPP.minStickers} to ${WHATSAPP.maxStickers}.`);
  } else if (count > WHATSAPP.maxStickers) {
    errors.push(`${count} stickers: WhatsApp needs ${WHATSAPP.minStickers} to ${WHATSAPP.maxStickers}.`);
  }
  if (kinds.size > 1) {
    errors.push('Some stickers are animated and some are static: a pack is all one or the other.');
  } else if (kinds.size === 1 && liveAnimated !== null && liveAnimated !== undefined) {
    const animated = kinds.has('animated');
    if (animated !== liveAnimated) {
      errors.push(
        `This pack was published ${liveAnimated ? 'animated' : 'static'}, and WhatsApp can't switch a pack people have ` +
          `added; put the ${animated ? 'animated' : 'static'} version in a new folder.`
      );
    }
  }
  emojiCounts.forEach((n, i) => {
    if (n < WHATSAPP.minEmojis || n > WHATSAPP.maxEmojis) {
      errors.push(`Sticker ${i + 1} has ${n} emoji: WhatsApp needs ${WHATSAPP.minEmojis} to ${WHATSAPP.maxEmojis}.`);
    }
  });
  return errors;
}

module.exports = { checkPack };
