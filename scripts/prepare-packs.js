#!/usr/bin/env node
/**
 * prepare-packs.js — builds the committed WhatsApp sticker pack fixtures.
 *
 * Reads  : design/catalog.json  +  packs/<id>/src/NN.webp   (512x512 originals)
 * Writes : packs/<id>/NN.webp        final sticker files
 *            - static packs : re-encoded static WebP, 512x512, <= 100 KB
 *            - animated packs: synthesized 4-frame wiggle animation
 *              (~600 ms loop, first frame = original pose), <= 500 KB
 *          packs/<id>/thumbs/NN.webp 160x160 static previews
 *          packs/<id>/tray.png       96x96 tray icon (<= 50 KB, WhatsApp spec)
 *          packs/<id>/pack.json      WhatsApp contents entry + app metadata
 *
 * The animation is muxed with node-webpmux (RIFF/VP8X/ANIM/ANMF), frames are
 * rendered with sharp. Exits non-zero if any WhatsApp size/dimension guard
 * fails, so CI can rely on the committed output.
 */
'use strict';

const fs = require('fs');
const path = require('path');
const sharp = require('sharp');
const WebP = require('node-webpmux');

const ROOT = path.resolve(__dirname, '..');
const PACKS = path.join(ROOT, 'packs');
const CATALOG = JSON.parse(fs.readFileSync(path.join(ROOT, 'design', 'catalog.json'), 'utf8'));

const STICKER_SIZE = 512;
const THUMB_SIZE = 160;
const TRAY_SIZE = 96;
const STATIC_MAX = 100 * 1024;
const ANIMATED_MAX = 500 * 1024;
const TRAY_MAX = 50 * 1024;
const FRAME_DELAY_MS = 150; // 4 frames -> 600 ms loop

// Two emojis per sticker (WhatsApp allows 1-3), cycled per pack.
const EMOJI_POOLS = {
  'clingy-mango': [['🥭', '🥰'], ['🥭', '🤗'], ['🥭', '😊'], ['🥭', '😘'], ['🥭', '😴'], ['🥭', '😜']],
  'big-words': [['❤️', '💬'], ['💖', '💬'], ['🥰', '💬'], ['😍', '💬'], ['💘', '💬'], ['💝', '💬']],
  'flirty-shy': [['😳', '💕'], ['🙈', '💕'], ['😊', '💗'], ['😉', '💘'], ['😘', '💞'], ['🥺', '💓']],
  'mango-moves': [['🥭', '💃'], ['🥭', '🕺'], ['🥭', '🎶'], ['🥭', '✨'], ['🥭', '😆'], ['🥭', '🎉']],
  'sorry-love': [['🥺', '💔'], ['😔', '💐'], ['🙏', '❤️'], ['😢', '💌'], ['🥀', '🙏'], ['🤍', '🕊️']],
  'gm-gn': [['🌞', '☕'], ['🌅', '💛'], ['🌙', '💤'], ['🌛', '⭐'], ['☀️', '😊'], ['🌜', '😴']],
  'heartbeat': [['❤️', '💓'], ['💗', '💞'], ['💘', '💕'], ['💖', '✨'], ['💝', '💟'], ['❣️', '💗']],
  'bunny-bounce': [['🐰', '💕'], ['🐇', '💗'], ['🐰', '😊'], ['🐰', '🥕'], ['🐇', '✨'], ['🐰', '😘']],
  'pop-words': [['💥', '❤️'], ['✨', '💬'], ['🎉', '💖'], ['💫', '💌'], ['🌟', '💕'], ['⚡', '💘']],
  'dance-w-me': [['💃', '🕺'], ['👫', '🎶'], ['💑', '🎵'], ['🕺', '💕'], ['💃', '❤️'], ['🎶', '💞']],
  'miles-apart': [['✈️', '💔'], ['🌍', '💌'], ['📱', '❤️'], ['🌙', '💭'], ['📮', '💕'], ['🛬', '🤗']],
  'couple-doodles': [['💑', '✏️'], ['👩‍❤️‍👨', '💕'], ['🫶', '❤️'], ['💏', '💗'], ['🤝', '💘'], ['🥰', '✍️']],
  'sorry-wiggle': [['🥺', '🙏'], ['😢', '💔'], ['🙇', '❤️'], ['😞', '💐'], ['🥀', '🙏'], ['💗', '🕊️']],
  'sunny-sleepy': [['🌞', '😊'], ['😴', '🌙'], ['☀️', '🥱'], ['💤', '🌜'], ['🌅', '☕'], ['🌛', '😴']],
};
const FALLBACK_EMOJIS = [['❤️', '😊']];

function emojisFor(packId, index) {
  const pool = EMOJI_POOLS[packId] || FALLBACK_EMOJIS;
  // Guard against accidental non-emoji entries (keep <= 3 real emojis).
  const pair = pool[index % pool.length].filter((e) => e && e.length <= 8).slice(0, 3);
  return pair.length ? pair : FALLBACK_EMOJIS[0];
}

/** Rotate around center + translate on a transparent 512x512 canvas. */
async function wiggleFrame(srcBuffer, angleDeg, dx, dy) {
  const rotated = await sharp(srcBuffer)
    .ensureAlpha()
    .rotate(angleDeg, { background: { r: 0, g: 0, b: 0, alpha: 0 } })
    .toBuffer({ resolveWithObject: true });
  const { width: rw, height: rh } = rotated.info;
  // Center-crop back to 512, applying the bob offset.
  const left = Math.max(0, Math.round((rw - STICKER_SIZE) / 2 - dx));
  const top = Math.max(0, Math.round((rh - STICKER_SIZE) / 2 - dy));
  return sharp(rotated.data)
    .extract({
      left: Math.min(left, rw - STICKER_SIZE),
      top: Math.min(top, rh - STICKER_SIZE),
      width: STICKER_SIZE,
      height: STICKER_SIZE,
    })
    .toBuffer();
}

async function encodeStaticWebp(rawBuffer, maxBytes, startQuality) {
  for (let q = startQuality; q >= 30; q -= 10) {
    const out = await sharp(rawBuffer).webp({ quality: q, effort: 4 }).toBuffer();
    if (out.length <= maxBytes) return { buffer: out, quality: q };
  }
  throw new Error(`could not reach ${maxBytes} bytes`);
}

async function buildAnimated(srcBuffer, outPath) {
  const poses = [
    { angle: 0, dx: 0, dy: 0 },
    { angle: 1.8, dx: 0, dy: -5 },
    { angle: 0, dx: 0, dy: 2 },
    { angle: -1.8, dx: 0, dy: -5 },
  ];
  for (let quality = 80; quality >= 30; quality -= 10) {
    const frames = [];
    for (const pose of poses) {
      const raw =
        pose.angle === 0 && pose.dx === 0 && pose.dy === 0
          ? srcBuffer
          : await wiggleFrame(srcBuffer, pose.angle, pose.dx, pose.dy);
      const enc = await sharp(raw).webp({ quality, effort: 4 }).toBuffer();
      frames.push(await WebP.Image.generateFrame({ buffer: enc, delay: FRAME_DELAY_MS }));
    }
    await WebP.Image.save(outPath, {
      width: STICKER_SIZE,
      height: STICKER_SIZE,
      frames,
      loops: 0,
      bgColor: [255, 255, 255, 0],
    });
    const size = fs.statSync(outPath).size;
    if (size <= ANIMATED_MAX) return { size, quality };
  }
  throw new Error(`animated sticker over ${ANIMATED_MAX} bytes at minimum quality`);
}

async function buildPack(pack) {
  const dir = path.join(PACKS, pack.id);
  const srcDir = path.join(dir, 'src');
  const thumbsDir = path.join(dir, 'thumbs');
  fs.mkdirSync(thumbsDir, { recursive: true });

  const sources = fs
    .readdirSync(srcDir)
    .filter((f) => f.endsWith('.webp'))
    .sort();
  if (sources.length !== pack.src.length) {
    throw new Error(`${pack.id}: expected ${pack.src.length} sources, found ${sources.length}`);
  }

  const stickers = [];
  const problems = [];
  for (let i = 0; i < sources.length; i++) {
    const nn = String(i + 1).padStart(2, '0');
    const srcBuffer = fs.readFileSync(path.join(srcDir, sources[i]));
    const outFile = `${nn}.webp`;
    const outPath = path.join(dir, outFile);

    if (pack.animated) {
      const { size } = await buildAnimated(srcBuffer, outPath);
      if (size > ANIMATED_MAX) problems.push(`${outFile} ${size}B > animated max`);
    } else {
      const { buffer } = await encodeStaticWebp(srcBuffer, STATIC_MAX, 90);
      fs.writeFileSync(outPath, buffer);
    }

    const thumb = await sharp(srcBuffer)
      .resize(THUMB_SIZE, THUMB_SIZE, { fit: 'contain', background: { r: 0, g: 0, b: 0, alpha: 0 } })
      .webp({ quality: 85 })
      .toBuffer();
    fs.writeFileSync(path.join(thumbsDir, outFile), thumb);

    stickers.push({ image_file: outFile, emojis: emojisFor(pack.id, i) });
  }

  // Tray icon: 96x96 palette PNG from the first sticker (WhatsApp: PNG, <= 50 KB).
  const trayPath = path.join(dir, 'tray.png');
  const tray = await sharp(fs.readFileSync(path.join(srcDir, sources[0])))
    .resize(TRAY_SIZE, TRAY_SIZE, { fit: 'contain', background: { r: 0, g: 0, b: 0, alpha: 0 } })
    .png({ palette: true, quality: 90, compressionLevel: 9 })
    .toBuffer();
  if (tray.length > TRAY_MAX) problems.push(`tray.png ${tray.length}B > tray max`);
  fs.writeFileSync(trayPath, tray);

  const packJson = {
    identifier: pack.id,
    name: pack.name,
    publisher: CATALOG.publisher,
    publisher_email: CATALOG.publisherEmail,
    publisher_website: CATALOG.publisherWebsite,
    privacy_policy_website: CATALOG.privacyPolicyWebsite,
    license_agreement_website: CATALOG.licenseAgreementWebsite,
    tray_image_file: 'tray.png',
    image_data_version: '1',
    avoid_cache: false,
    animated_sticker_pack: !!pack.animated,
    category: pack.category,
    order: pack.order,
    downloads: pack.downloads,
    hue: pack.hue,
    stickers,
  };
  fs.writeFileSync(path.join(dir, 'pack.json'), JSON.stringify(packJson, null, 2) + '\n');
  return problems;
}

async function verifyPack(pack) {
  const dir = path.join(PACKS, pack.id);
  const errors = [];
  const contents = JSON.parse(fs.readFileSync(path.join(dir, 'pack.json'), 'utf8'));
  if (contents.stickers.length < 3 || contents.stickers.length > 30) {
    errors.push(`${pack.id}: ${contents.stickers.length} stickers (WhatsApp allows 3-30)`);
  }
  for (const sticker of contents.stickers) {
    const p = path.join(dir, sticker.image_file);
    const size = fs.statSync(p).size;
    const meta = await sharp(p, { animated: true }).metadata();
    const frameH = meta.pageHeight || meta.height;
    if (meta.width !== STICKER_SIZE || frameH !== STICKER_SIZE) {
      errors.push(`${pack.id}/${sticker.image_file}: ${meta.width}x${frameH}, want 512x512`);
    }
    if (pack.animated) {
      if ((meta.pages || 1) < 2) errors.push(`${pack.id}/${sticker.image_file}: not animated`);
      if (size > ANIMATED_MAX) errors.push(`${pack.id}/${sticker.image_file}: ${size}B > 500KB`);
    } else {
      if ((meta.pages || 1) !== 1) errors.push(`${pack.id}/${sticker.image_file}: unexpectedly animated`);
      if (size > STATIC_MAX) errors.push(`${pack.id}/${sticker.image_file}: ${size}B > 100KB`);
    }
    if (!sticker.emojis || sticker.emojis.length < 1 || sticker.emojis.length > 3) {
      errors.push(`${pack.id}/${sticker.image_file}: emoji count out of range`);
    }
  }
  const trayMeta = await sharp(path.join(dir, 'tray.png')).metadata();
  const traySize = fs.statSync(path.join(dir, 'tray.png')).size;
  if (trayMeta.width !== TRAY_SIZE || trayMeta.height !== TRAY_SIZE) {
    errors.push(`${pack.id}/tray.png: ${trayMeta.width}x${trayMeta.height}, want 96x96`);
  }
  if (traySize > TRAY_MAX) errors.push(`${pack.id}/tray.png: ${traySize}B > 50KB`);
  return errors;
}

(async () => {
  const only = process.argv.includes('--pack')
    ? process.argv[process.argv.indexOf('--pack') + 1]
    : null;
  const packs = CATALOG.packs.filter((p) => !only || p.id === only);
  let failed = false;
  for (const pack of packs) {
    const started = Date.now();
    const problems = await buildPack(pack);
    const errors = [...problems, ...(await verifyPack(pack))];
    const finalFiles = fs.readdirSync(path.join(PACKS, pack.id)).filter((f) => /^\d\d\.webp$/.test(f));
    const bytes = finalFiles.reduce((a, f) => a + fs.statSync(path.join(PACKS, pack.id, f)).size, 0);
    console.log(
      `${errors.length ? 'FAIL' : 'ok  '} ${pack.id.padEnd(15)} ${String(finalFiles.length).padStart(2)} stickers ` +
        `${pack.animated ? 'anim  ' : 'static'} ${(bytes / 1024).toFixed(0).padStart(5)} KB  ${Date.now() - started} ms`
    );
    for (const e of errors) {
      console.error(`  - ${e}`);
      failed = true;
    }
  }
  if (failed) process.exit(1);
  console.log('all packs prepared');
})().catch((err) => {
  console.error(err);
  process.exit(1);
});
