#!/usr/bin/env node
// Render every frame of design/Screens.dc.html (42 x <dc-import name="Prototype">) to PNG.
//
//   node render.mjs                 render all frames + manifest + contact sheet into ./out
//   node render.mjs --only 8,26     render a subset (1-based NN) (manifest/contact sheet still list all frames)
//   node render.mjs --serve         only start the static server and print a URL per frame (manual debugging)
//
// Env overrides: REPO (default /home/user/Sticker-Maker), FRAMES (design_frames.json), OUT (default ./out),
//                PLAYWRIGHT_MODULE, PLAYWRIGHT_BROWSERS_PATH, WAIT_MS (default 1500), CHROMIUM_ARGS (extra launch flags).
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { createRequire } from 'node:module';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const REPO = process.env.REPO || '/home/user/Sticker-Maker';
const DESIGN = path.join(REPO, 'design');
const FONT_DIR = path.join(REPO, 'app/src/main/res/font');
const FRAMES = process.env.FRAMES || path.join(HERE, '..', 'design_frames.json');
const OUT = process.env.OUT ? path.resolve(process.env.OUT) : path.join(HERE, 'out');
const WAIT_MS = Number(process.env.WAIT_MS || 1500);
const VIEW = { width: 390, height: 844 };
const DSF = 3;

const args = process.argv.slice(2);
const argVal = (k) => { const i = args.indexOf(k); return i >= 0 ? args[i + 1] : null; };
const ONLY = argVal('--only') ? new Set(argVal('--only').split(',').map(Number)) : null;
const SERVE_ONLY = args.includes('--serve');

process.env.PLAYWRIGHT_BROWSERS_PATH ||= '/opt/pw-browsers';
const require = createRequire(import.meta.url);

// ------------------------------------------------------------------ frames
const frames = JSON.parse(fs.readFileSync(FRAMES, 'utf8'));
const camel = (k) => k.replace(/-([a-z])/g, (_, c) => c.toUpperCase());
function evalAttr(v) {
  const m = /^\s*\{\{([\s\S]*)\}\}\s*$/.exec(v);
  return m ? Function('"use strict"; return (' + m[1] + ');')() : v;
}
function toProps(attrs) {
  const p = {};
  for (const [k, v] of Object.entries(attrs)) p[camel(k)] = evalAttr(v);
  p.embedded = true; // every frame on Screens.dc.html: embedded="{{ true }}" hint-size="390px,844px"
  return p;
}
function slugify(s) {
  return s.normalize('NFKD').replace(/[̀-ͯ]/g, '').replace(/&/g, ' and ')
    .toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '').slice(0, 72).replace(/-+$/, '');
}
const jobs = frames.map((f, i) => {
  const n = i + 1, NN = String(n).padStart(2, '0');
  const slug = slugify(f.section.replace(/^\d+\s*/, '') + ' ' + f.title);
  return { n, NN, section: f.section, title: f.title, attrs: f.attrs, props: toProps(f.attrs), file: `${NN}-${slug}.png` };
});

// Cross-check against Screens.dc.html so a stale frames file is noticed.
(function crossCheck() {
  const s = fs.readFileSync(path.join(DESIGN, 'Screens.dc.html'), 'utf8');
  const tags = [...s.matchAll(/<dc-import\b([^>]*)>/g)].map((m) => {
    const a = {}; for (const [, k, v] of m[1].matchAll(/\s([a-z-]+)="([^"]*)"/g)) a[k] = v;
    for (const k of ['name', 'embedded', 'hint-size', 'style']) delete a[k];
    return a;
  });
  if (tags.length !== jobs.length) console.warn(`WARN Screens.dc.html has ${tags.length} dc-imports, frames file has ${jobs.length}`);
  tags.forEach((a, i) => { if (jobs[i] && JSON.stringify(a) !== JSON.stringify(jobs[i].attrs)) console.warn(`WARN frame ${i + 1} attrs differ from Screens.dc.html:`, a, jobs[i].attrs); });
})();

// ------------------------------------------------------------------ server
const assetMap = JSON.parse(fs.readFileSync(path.join(DESIGN, 'fetch/all.json'), 'utf8'));
const MIME = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.css': 'text/css; charset=utf-8', '.json': 'application/json',
  '.ttf': 'font/ttf', '.webp': 'image/webp', '.jpg': 'image/jpeg', '.jpeg': 'image/jpeg', '.png': 'image/png', '.svg': 'image/svg+xml' };
const inside = (root, p) => { const r = path.resolve(root, '.' + p); return r.startsWith(path.resolve(root) + path.sep) ? r : null; };
function resolve(p) {
  if (p === '/design/host.html') return path.join(HERE, 'host.html');
  if (p === '/shim.js' || p === '/fonts.css') return path.join(HERE, p);
  if (p === '/vendor/react.production.min.js') return path.join(HERE, 'node_modules/react/umd/react.production.min.js');
  if (p === '/vendor/react-dom.production.min.js') return path.join(HERE, 'node_modules/react-dom/umd/react-dom.production.min.js');
  if (p === '/vendor/lucide.min.js') return path.join(HERE, 'node_modules/lucide/dist/umd/lucide.min.js');
  if (p.startsWith('/fonts/')) return inside(FONT_DIR, p.slice('/fonts'.length));
  if (p.startsWith('/out/')) return inside(OUT, p.slice('/out'.length));
  if (p.startsWith('/design/assets/')) {
    const key = p.slice('/design/'.length);                 // "assets/stickers/mango-a-1.webp"
    if (assetMap[key]) return assetMap[key];
    return inside(path.join(DESIGN, 'assets'), p.slice('/design/assets'.length));
  }
  if (/^\/design\/[^/]+\.dc\.html$/.test(p)) return inside(DESIGN, p.slice('/design'.length));
  return null;
}
const served404 = new Set();
const server = http.createServer((req, res) => {
  const p = decodeURIComponent(new URL(req.url, 'http://x').pathname);
  const file = resolve(p);
  if (!file || !fs.existsSync(file) || !fs.statSync(file).isFile()) {
    served404.add(p); res.writeHead(404, { 'content-type': 'text/plain' }); res.end('404 ' + p); return;
  }
  res.writeHead(200, { 'content-type': MIME[path.extname(file).toLowerCase()] || 'application/octet-stream', 'cache-control': 'no-cache' });
  fs.createReadStream(file).pipe(res);
});
await new Promise((r) => server.listen(0, '127.0.0.1', r));
const ORIGIN = `http://127.0.0.1:${server.address().port}`;
const frameUrl = (j) => `${ORIGIN}/design/host.html?src=./Prototype.dc.html&props=${encodeURIComponent(JSON.stringify(j.props))}`;

if (SERVE_ONLY) {
  console.log(`serving on ${ORIGIN}  (Ctrl-C to stop)`);
  for (const j of jobs) console.log(`${j.NN} ${j.title}\n   ${frameUrl(j)}`);
  await new Promise(() => {});
}

// ------------------------------------------------------------------ render
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || '/opt/node22/lib/node_modules/playwright');
fs.mkdirSync(OUT, { recursive: true });
const LAUNCH_ARGS = (process.env.CHROMIUM_ARGS ?? '').split(/\s+/).filter(Boolean);
const browser = await chromium.launch({ args: LAUNCH_ARGS });
const context = await browser.newContext({ viewport: VIEW, deviceScaleFactor: DSF, locale: 'en-US', timezoneId: 'UTC', colorScheme: 'light', reducedMotion: 'no-preference' });
const external = [];
await context.route('**/*', (route) => {
  const u = route.request().url();
  if (u.startsWith(ORIGIN) || u.startsWith('data:') || u.startsWith('blob:')) return route.continue();
  external.push(u); return route.abort();                    // nothing may leave the machine
});

// Which platform fonts actually drew the text (CDP), aggregated by family.
async function platformFonts(page) {
  const cdp = await context.newCDPSession(page);
  await cdp.send('DOM.enable'); await cdp.send('CSS.enable');
  const { root } = await cdp.send('DOM.getDocument', { depth: -1, pierce: true });
  const ids = [];
  (function walk(n) {
    if (n.children && n.children.some((c) => c.nodeType === 3 && c.nodeValue.trim())) ids.push(n.nodeId);
    for (const c of n.children || []) walk(c);
    for (const c of n.shadowRoots || []) walk(c);
  })(root);
  const fams = {};
  for (const nodeId of ids) {
    try {
      const { fonts } = await cdp.send('CSS.getPlatformFontsForNode', { nodeId });
      for (const f of fonts) fams[f.familyName] = (fams[f.familyName] || 0) + f.glyphCount;
    } catch { /* node gone */ }
  }
  await cdp.detach();
  return fams;
}

const report = [];
for (const j of jobs) {
  if (ONLY && !ONLY.has(j.n)) continue;
  const page = await context.newPage();
  const logs = [], bad = [];
  page.on('console', (m) => { if (m.type() === 'error' || m.type() === 'warning') logs.push(`${m.type()}: ${m.text()}`); });
  page.on('pageerror', (e) => logs.push('pageerror: ' + (e.stack || e.message)));
  page.on('response', (r) => { if (r.status() >= 400) bad.push(`${r.status()} ${r.url().replace(ORIGIN, '')}`); });
  page.on('requestfailed', (r) => { if (r.url().startsWith(ORIGIN)) bad.push(`failed ${r.url().replace(ORIGIN, '')}`); });

  await page.goto(frameUrl(j), { waitUntil: 'load' });
  await page.waitForFunction(() => window.__dcReady === true || window.__dcReady === 'error', null, { timeout: 20000 });
  await page.waitForLoadState('networkidle');
  await page.waitForTimeout(WAIT_MS);                          // timers, sheet/fade animations (0.2–0.4 s)
  await page.evaluate(() => window.DC && window.DC.settle());   // anything that re-rendered meanwhile

  const info = await page.evaluate(() => {
    const phone = document.querySelector('#root [data-screen-label]');
    const r = phone ? phone.getBoundingClientRect() : null;
    const broken = [...document.images].filter((i) => !i.naturalWidth).map((i) => i.getAttribute('src'));
    const loaded = [...document.images].filter((i) => i.naturalWidth).length;
    const faces = []; document.fonts.forEach((f) => faces.push(`${f.family} ${f.weight} ${f.status}`));
    return {
      ready: window.__dcReady, screenLabel: phone && phone.getAttribute('data-screen-label'),
      box: r && [r.x, r.y, r.width, r.height], errors: window.__dcErrors || [], errorOverlay: !!document.querySelector('[data-dc-error]'),
      imgsLoaded: loaded, imgsBroken: [...new Set(broken)], lucide: !!window.lucide, svgIcons: document.querySelectorAll('#root svg').length,
      faces, effectiveProps: window.__dcProps,
    };
  });
  const fonts = await platformFonts(page);
  const outFile = path.join(OUT, j.file);
  await page.screenshot({ path: outFile, clip: { x: 0, y: 0, ...VIEW }, animations: 'disabled', caret: 'hide', scale: 'device' });
  await page.close();

  // 404s on design assets are content the repo doesn't have (reported separately);
  // everything else (script errors, shim errors, other 4xx, layout) is a real problem.
  const http4xx = [...new Set(bad)];
  const missingAssets = http4xx.filter((b) => /^404 \/design\/assets\//.test(b)).map((b) => b.replace(/^404 \/design\//, '')).sort();
  const problems = [];
  if (info.ready !== true) problems.push('not ready: ' + info.ready);
  if (!info.box || info.box.join() !== '0,0,390,844') problems.push('phone box ' + JSON.stringify(info.box));
  if (info.errorOverlay) problems.push('error overlay');
  problems.push(...info.errors.map((e) => 'shim ' + e));
  problems.push(...http4xx.filter((b) => !/^404 \/design\/assets\//.test(b)));
  problems.push(...logs.filter((l) => !/Failed to load resource: the server responded with a status of 404/.test(l)));
  const rec = { n: j.n, file: j.file, title: j.title, ...info, fonts, missingAssets, problems };
  report.push(rec);
  const fam = Object.entries(fonts).sort((a, b) => b[1] - a[1]).map(([k, v]) => `${k}:${v}`).join(' ');
  console.log(`${j.NN} ${j.file}  label="${info.screenLabel}"  imgs ok=${info.imgsLoaded} broken=${info.imgsBroken.length}  svg=${info.svgIcons}  fonts[${fam}]` +
    (missingAssets.length ? `\n   missing assets (${missingAssets.length}): ${missingAssets.slice(0, 4).join(', ')}${missingAssets.length > 4 ? ', …' : ''}` : '') +
    (problems.length ? `\n   PROBLEMS: ${problems.join(' | ')}` : ''));
}

// ------------------------------------------------------------------ outputs
const manifest = jobs.map((j) => ({ n: j.n, section: j.section, title: j.title, props: j.props, file: j.file }));
fs.writeFileSync(path.join(OUT, 'manifest.json'), JSON.stringify(manifest, null, 2) + '\n');
if (!ONLY) {
  fs.writeFileSync(path.join(OUT, 'report.json'), JSON.stringify({ external, served404: [...served404].sort(), frames: report }, null, 2) + '\n');
} else {
  const prev = fs.existsSync(path.join(OUT, 'report.json')) ? JSON.parse(fs.readFileSync(path.join(OUT, 'report.json'), 'utf8')) : { frames: [] };
  const byN = new Map(prev.frames.map((f) => [f.n, f])); for (const r of report) byN.set(r.n, r);
  fs.writeFileSync(path.join(OUT, 'report.json'), JSON.stringify({ external, served404: [...new Set([...(prev.served404 || []), ...served404])].sort(), frames: [...byN.values()].sort((a, b) => a.n - b.n) }, null, 2) + '\n');
}

// Contact sheet: all frames that exist on disk, 7 per row, half size, NN + title under each.
const esc = (s) => s.replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
const cells = jobs.filter((j) => fs.existsSync(path.join(OUT, j.file))).map((j) =>
  `<figure><img src="${encodeURIComponent(j.file)}" width="195" height="422" alt=""><figcaption><b>${j.NN}</b> ${esc(j.title)}<small>${esc(j.section)}</small></figcaption></figure>`).join('\n');
const sheetHtml = `<!DOCTYPE html><html><head><meta charset="utf-8"><link rel="stylesheet" href="/fonts.css"><style>
  *{box-sizing:border-box} body{margin:0;background:#EAECF1;font:500 12px/1.35 'Hanken Grotesk',sans-serif;color:#1E2128}
  header{padding:20px 24px 4px;font:800 20px 'Hanken Grotesk'} header span{font:500 12px 'JetBrains Mono';color:#626873;margin-left:10px}
  main{display:grid;grid-template-columns:repeat(7,195px);gap:18px 14px;padding:16px 24px 28px}
  figure{margin:0} img{display:block;width:195px;height:422px;border-radius:15px;box-shadow:0 1px 3px rgba(20,22,28,.12)}
  figcaption{margin-top:7px;min-height:34px} b{font:700 12px 'JetBrains Mono';color:#C23359;margin-right:3px}
  small{display:block;font:400 10.5px 'JetBrains Mono';color:#8b929d;margin-top:2px}
</style></head><body><header>Love Stickers · design reference frames<span>design/Screens.dc.html → Prototype.dc.html · 390×844 @3x</span></header><main>
${cells}
</main></body></html>`;
fs.writeFileSync(path.join(OUT, 'contact-sheet.html'), sheetHtml);
{
  const page = await browser.newPage({ viewport: { width: 1500, height: 1000 }, deviceScaleFactor: 1 });
  await page.goto(`${ORIGIN}/out/contact-sheet.html`, { waitUntil: 'load' });
  await page.evaluate(async () => { await document.fonts.ready; await Promise.all([...document.images].map((i) => i.decode().catch(() => {}))); });
  const w = await page.evaluate(() => document.querySelector('main').scrollWidth + 0);
  await page.setViewportSize({ width: Math.max(w, 600), height: 1000 });
  await page.screenshot({ path: path.join(OUT, 'contact-sheet.png'), fullPage: true });
  await page.close();
}

await browser.close();
server.close();
const withProblems = report.filter((r) => r.problems.length);
const withMissing = report.filter((r) => r.missingAssets.length);
const allMissing = [...new Set(report.flatMap((r) => r.missingAssets))].sort();
console.log(`\nrendered ${report.length} frame(s) -> ${OUT}` +
  `\nexternal requests blocked: ${external.length ? [...new Set(external)].join(', ') : 'none'}` +
  `\nmissing design assets (${allMissing.length}, not in design/fetch/all.json or design/assets/): ${allMissing.length ? allMissing.join(', ') : 'none'}` +
  `\nframes showing missing assets: ${withMissing.length ? withMissing.map((r) => r.n).join(', ') : 'none'}` +
  `\nframes with runtime problems: ${withProblems.length ? withProblems.map((r) => r.n).join(', ') : 'none'}`);
if (withProblems.length) process.exitCode = 1;
