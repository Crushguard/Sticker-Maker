# Sticker Catalog Pipeline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Folders dropped into `library/<category>/<Pack Name>/` of bucket `play-console-f33dd-stickermaker` become
WhatsApp-ready packs in Firestore database `stickermaker` and in the app, automatically.

**Architecture:** Storage triggers schedule Cloud Tasks builds (quiet-period or manifest fast path); `buildPack`
turns a folder into `cover-s.webp`, `cover-l.webp` and `pack.zip` under `public/packs/<id>/v<n>/` and a Firestore
record; `publishCatalog` writes a gzipped catalog file plus the `catalog/meta` pointer the app listens to. The app
reads only that pointer, the catalog file, cover strips and pack zips over HTTPS.

**Tech Stack:** Cloud Functions for Firebase 2nd gen (firebase-functions 7.4, firebase-admin 14.5, Node 24,
plain CommonJS JavaScript, `node --test`), sharp 0.35, fflate 0.8, @google-analytics/data 7.2; Android Kotlin,
Compose, Hilt, Room, OkHttp, Coil 2, Robolectric.

**Spec:** `docs/specs/2026-09-29-sticker-catalog-pipeline-design.md`

## Global Constraints

- Firestore database id `stickermaker`, location us-central1. Bucket `play-console-f33dd-stickermaker`, us-central1.
- Library prefix `library/`, public prefix `public/`. Functions codebase `stickermaker`, exported as group
  `stickermaker`, region us-central1, runtime nodejs24, min instances 0 everywhere, max instances capped.
- WhatsApp: 512×512 WebP, static ≤ 100 KB (102400 B), animated ≤ 500 KB (512000 B), frames ≥ 8 ms, loop ≤ 10 s,
  3–30 stickers, all static or all animated, 1–3 emoji per sticker, tray 96×96 PNG ≤ 50 KB (51200 B).
- Quality floors: static lossy ≥ 75, animated ≥ 60. Ready art passes through byte for byte.
- Public files: `Cache-Control: public, max-age=31536000, immutable`, never overwritten, versioned paths.
- No invented numbers: counts shown only when ≥ 100 real adds.
- Never write the personal address anywhere; publisher email stays the company address from config.xml.
- Never name the crash-reporting SDK in docs, code comments or copy (public repo).

## Review Focus

1. Folder names with spaces, accents, capitals, non-Latin script or emoji → a stable slug id (non-Latin falls back
   to `pack-<8 hex of sha1(name)>`); two folders producing the same id → the second fails with a report naming the
   first, never overwrites it. (Task 1 tests.)
2. Console "Create folder" placeholders (`…/`, 0 bytes), `.DS_Store`, `Thumbs.db`, `desktop.ini` → ignored, never
   counted as stickers or as "new files" for the quiet period. (Tasks 1 and 7 tests.)
3. Re-uploading identical art → same `pack.zip` fingerprint, no new version, report refreshed only. (Task 5 test.)
4. Animated GIF with 0 ms frames or a loop over 10 s → delays normalized to 100 ms with a note / build fails with a
   clear report. (Task 3 tests.)
5. App cold start offline with a cached catalog → Home renders from cache; a failed catalog download keeps the
   previous catalog. (Task 11 tests.)
6. Malformed `pack.json` → build fails with "pack.json is not valid JSON: …", live version untouched. (Task 2 test.)

---

## File structure

```
functions/
  package.json, .gitignore, index.js
  src/config.js        constants (bucket, database, prefixes, limits)
  src/library.js       parseLibraryPath, slugify, isImageName, naturalCompare
  src/manifest.js      parsePackManifest
  src/categories.js    parseCategoriesFile, syncCategories
  src/stickers.js      probe, isWhatsAppReady, encodeSticker, wiggle, firstFramePng
  src/images.js        makeTray, makeCoverStrip
  src/zip.js           makePackZip
  src/checks.js        checkPack
  src/report.js        renderReport
  src/build.js         buildPackFromFiles (pure: files in, outputs out)
  src/catalog.js       rankPacks, assembleCatalog
  src/stats.js         trendFromDaily, statsFromReport
  src/readiness.js     quietTaskId, fastTaskId, isQuiet, fastPathReady
  src/firebase.js      admin app, db(), bucket()
  src/queue.js         enqueueBuild, enqueuePublish
  src/buildTask.js     runBuild (IO around buildPackFromFiles)
  src/publishTask.js   runPublish
  src/statsTask.js     runWeeklyStats
  test/*.test.js
library/_categories.json, library/<category>/<id>/{NN.webp, pack.json}
scripts/library/upload.js, scripts/library/e2e.js, scripts/library/contact-sheet.js
firebase.json, .firebaserc, firestore.rules, firestore.indexes.json, storage.rules
app/src/main/kotlin/com/piptechnologies/stickermaker/core/data/catalog/
  CatalogFile.kt, CatalogUrls.kt, LanguageOrder.kt, CatalogStore.kt, PackArchive.kt
```

## Phase A: functions core (pure, unit-tested)

### Task 1: Scaffold, config and library paths

**Files:** Create `functions/package.json`, `functions/.gitignore`, `functions/src/config.js`,
`functions/src/library.js`, `functions/test/library.test.js`.

**Interfaces (produces):**
- `slugify(name: string): string` — NFKD, strip marks, lowercase, `[^a-z0-9]+` → `-`, trim `-`, max 64; empty or
  < 2 chars → `pack-` + first 8 hex of sha1(name).
- `parseLibraryPath(objectName: string)` → `{ kind: 'categories' }` for `library/_categories.json`;
  `{ kind: 'pack', category, folder, packId, file, prefix }` for `library/<cat>/<folder>/<file>` where no segment
  starts with `_` (except the file `_report.txt` → `{ kind: 'report' }`) and file is not junk;
  otherwise `{ kind: 'ignored' }`. Junk: empty file segment (folder placeholder), `.DS_Store`, `Thumbs.db`,
  `desktop.ini`, names starting with `.`.
- `isImageName(file)` → `.png|.webp|.gif` (case-insensitive). `naturalCompare(a, b)` → "2.png" < "10.png".

- [ ] Write tests: slug cases (`"Sorry Wiggle"`→`sorry-wiggle`, `"Flirty & Shy"`→`flirty-shy`,
  `"Café Crème"`→`cafe-creme`, `"حب"`→`pack-<hex>`, `"💖"`→`pack-<hex>`); path cases (categories file, pack file,
  `_staging/x/y.png` ignored, `sorry/_old/1.png` ignored, `sorry/Pack/` placeholder ignored, `.DS_Store` ignored,
  `sorry/Pack/_report.txt` → report, depth ≠ 4 ignored); natural sort.
- [ ] Run `npm test --prefix functions` → fail; implement; pass; commit.

### Task 2: Pack and category manifests

**Files:** Create `functions/src/manifest.js`, `functions/src/categories.js` (parse only),
`functions/test/manifest.test.js`, `functions/test/categories.test.js`.

**Interfaces:**
- `parsePackManifest(text: string|null, imageNames: string[], opts: { folder, categoryIds: Set, defaultEmojis })`
  → `{ errors: string[], notes: string[], name, names, alsoIn, lang, tags, order, animate, cover: string[],
  stickers: [{ file, emojis, text }], listed: boolean }`.
  Rules: invalid JSON → error `pack.json is not valid JSON: <message>`; `name` default = folder; `alsoIn` keeps known
  ids ≠ own category, max 2 (unknown → note); `lang` default `en`; `order` default 1000; `animate` only `"wiggle"`;
  `stickers` given → listed=true, exactly those in that order (missing file → error `<file> is listed but missing`,
  unlisted images → note); otherwise all images in natural order; emojis 1–3 else category defaults (note once);
  `text` trimmed ≤ 255; `cover` defaults to first 6 files, unknown cover files → note and default.
- `parseCategoriesFile(text)` → `{ errors, categories: [{ id, order, icon, hue, emojis, names, keywords }] }`;
  ids must be slugs; `names.en` required.

- [ ] Tests for every rule above, including malformed JSON and `emojis: []`. Implement; pass; commit.

### Task 3: Sticker encoding

**Files:** Create `functions/src/stickers.js`, `functions/test/stickers.test.js` (images generated with sharp in
the test: a 512 transparent PNG with a shape, a 300 px PNG, a noisy 512 PNG that cannot be lossless under 100 KB,
an animated WebP, a GIF with 0 ms delays, a GIF longer than 10 s).

**Interfaces:**
- `probe(buf)` → `{ format, width, height, pages, delays: number[], loop, bytes }` (height is per frame).
- `isWhatsAppReady(p)` → true for 512×512 WebP, static ≤ 102400 B, or animated ≤ 512000 B, all delays ≥ 8,
  sum ≤ 10000.
- `encodeSticker(buf, { wiggle })` → `{ buffer, animated, passthrough, quality, lossless, notes }`; throws
  `StickerError(message)` when a limit cannot be met. Static: lossless effort 6 if ≤ 102400, else quality 95→75 step
  5. Animated: quality 90→60 step 5, delays < 8 → 100 (note), sum > 10000 → error. Resize: 512×512 `contain`,
  transparent background, `kernel: lanczos3`; longer side < 512 → note "upscaled from N px".
- `wiggle(buf)` → animated WebP, 4 frames (0°, +1.8° −5 px, 0° +2 px, −1.8° −5 px), 150 ms, loop 0.
- `firstFramePng(buf)` → 512×512 PNG of frame 1.

- [ ] Tests: pass-through returns the same bytes; PNG → lossless WebP 512; noisy PNG → lossy ≥ 75 and ≤ 102400;
  300 px → upscaled note; 0 ms GIF → delays 100 + note; 12 s GIF → StickerError; wiggle → 4 pages 512×512
  ≤ 512000. Implement; pass; commit.

### Task 4: Tray, cover strip and zip

**Files:** Create `functions/src/images.js`, `functions/src/zip.js`, `functions/test/images.test.js`,
`functions/test/zip.test.js`.

**Interfaces:**
- `makeTray(buf)` → `{ buffer, palette: boolean }` 96×96 PNG ≤ 51200 B (palette only if needed).
- `makeCoverStrip(framesPng: Buffer[], tile: 96|192)` → WebP buffer, width `tile × n`, height `tile`, quality 88,
  alpha kept.
- `makePackZip({ contents: object, tray: Buffer, stickers: [{ name, buffer }] })` → `Buffer`; fflate `zipSync`,
  level 0, mtime `1980-01-01T00:00:00Z`, entry order `contents.json`, `tray.png`, stickers.

- [ ] Tests: tray size/dims; strip dims for 6 and 3 tiles; zip round-trip with `unzipSync`, entry order, identical
  inputs → identical bytes. Implement; pass; commit.

### Task 5: Checks, report and the pure build

**Files:** Create `functions/src/checks.js`, `functions/src/report.js`, `functions/src/build.js`,
`functions/test/build.test.js`, `functions/test/launch-packs.test.js`.

**Interfaces:**
- `checkPack({ count, kinds: Set<'static'|'animated'>, liveAnimated: boolean|null, emojiCounts: number[] })` →
  errors (3–30; mixed kinds; kind change vs live; emoji 1–3).
- `renderReport({ ok, name, version, count, animated, category, alsoIn, liveVersion, errors, notes, at })` → text
  (formats in the spec).
- `buildPackFromFiles({ packId, category, folder, files: [{ name, buffer }], manifestText, categoryIds,
  defaultEmojis, live: { version, contentHash, animated } | null })` → `{ ok, errors, notes, unchanged, version,
  contentHash, animated, count, outputs: { coverS, coverL, zip }, record }` where `record` holds the Firestore
  fields of the spec (`name, names, category, alsoIn, lang, tags, order, animated, count, version, contentHash,
  stickers[{ file, emojis, text, source, passthrough, bytes }], zipBytes, coverTiles`).
  Version: `live && sameHash ? live.version : (live?.version ?? 0) + 1`; `unchanged = live && sameHash`.

- [ ] Tests: happy path (3 generated stickers) → ok, version 1, zip entries; same input with `live` from the first
  result → unchanged, same version; 2 stickers → not ok with the WhatsApp message; mixed static/animated → error.
  `launch-packs.test.js` builds every `library/*/*/` pack (skipped until Task 9 lands) and asserts ok, count and
  animated flag. Implement; pass; commit.

### Task 6: Ranking and catalog assembly

**Files:** Create `functions/src/stats.js`, `functions/src/catalog.js`, `functions/test/catalog.test.js`,
`functions/test/stats.test.js`.

**Interfaces:**
- `trendFromDaily(daily: { date: 'YYYYMMDD', count }[], today: Date)` → `Σ count × 0.5^(ageDays / 7)`, age of
  yesterday = 0.
- `statsFromReport(rows: { date, packId, count }[], totals: { packId, count }[], today)` →
  `Map<packId, { adds, adds28d, trend }>`.
- `rankPacks(packs, now)` → sorted copy: pinned (`pin` asc) → `score` desc → `order` asc → `publishedAt` desc,
  where `score = max(trend, medianOfMature × (1 − ageDays / 14))` for packs live < 14 days
  (`medianOfMature` = median trend of packs live ≥ 14 days, 0 if none).
- `assembleCatalog({ version, now, categories, packs })` → catalog object of the spec; category `packs` counts
  include `alsoIn`; pack `keywords` = lowercase unique words of `tags` + sticker texts (letters/digits, ≥ 2 chars),
  max 40.

- [ ] Tests: decay math; new pack floor; pins first; tie-breaks; counts with alsoIn; keywords dedupe. Implement;
  pass; commit.

## Phase B: functions wiring, rules and emulator end-to-end

### Task 7: Triggers, tasks and Firebase config

**Files:** Create `functions/index.js`, `functions/src/firebase.js`, `functions/src/queue.js`,
`functions/src/readiness.js`, `functions/src/buildTask.js`, `functions/src/publishTask.js`,
`functions/src/statsTask.js`, `functions/test/readiness.test.js`; modify `functions/src/categories.js`
(`syncCategories`); replace `firebase.json`, `firestore.rules`, `storage.rules`; create `firestore.indexes.json`,
`.firebaserc`.

**Interfaces:**
- `quietTaskId(packId, nowMs)` = `q-${packId}-${Math.floor(nowMs / 30000)}`;
  `fastTaskId(packId, generations: string[])` = `f-${packId}-${sha1(generations.join()).slice(0, 16)}`;
  `isQuiet(newestUpdatedMs, nowMs)` = `nowMs − newest ≥ 30000`;
  `fastPathReady(manifest, existingNames: Set)` = `manifest.listed && every listed file exists`.
- `enqueueBuild({ category, folder, delaySeconds, id })`, `enqueuePublish()` (`id p-${floor(now/5000)}`, delay 3);
  `functions/already-exists` swallowed.
- `runBuild({ category, folder })`: list `library/<category>/<folder>/`, ignore junk and `_report.txt`; empty →
  mark removed if `packs/<id>.source.folder` equals this folder, publish, return; newest object < 30 s old and not
  fast-path-ready → throw `NotQuietYet` (task retry); id collision with another non-empty folder → report error;
  download files; `buildPackFromFiles`; on ok and changed: upload three outputs, transaction-write `packs/<id>`
  (keep `pin`, `hidden`, `stats`, `publishedAt`), delete versions ≤ n − 2; write `_report.txt`; enqueue publish when
  the live record changed.
- `runPublish()`: read categories + live, not hidden packs; `rankPacks`; `assembleCatalog`; gzip; upload
  `public/catalog/v<k>.json.gz`; set `catalog/meta`; delete catalog files older than k − 3.
- `runWeeklyStats()`: no `GA4_PROPERTY_ID` → log and return; else two `runReport` calls (28 days by date and
  pack_id; all-time totals by pack_id, filter `eventName == pack_added`), write `stats` per pack, enqueue publish.
- `index.js`: `exports.stickermaker = { onLibraryUpload, onLibraryDelete, buildPack, publishCatalog, onPackEdited,
  weeklyStats }` with options from the spec (bucket, region, memory, cpu, maxInstances, retryConfig, rateLimits,
  database `stickermaker`).
- `firebase.json`: `firestore: { database: "stickermaker", rules, indexes }`,
  `storage: [{ bucket: "play-console-f33dd-stickermaker", rules: "storage.rules" }]`,
  `functions: [{ source: "functions", codebase: "stickermaker", runtime: "nodejs24" }]`, emulators
  (firestore 8080, storage 9199, functions 5001, tasks 9499, ui off).

- [ ] Readiness tests; implement wiring; `node -e "require('./functions')"` loads; commit.

### Task 8: Library upload tool and emulator end-to-end

**Files:** Create `scripts/library/upload.js`, `scripts/library/e2e.js`; modify `scripts/package.json`.

- `upload.js <dir> [--pack <path>] [--emulator] [--bucket]`: walks a local library tree; per pack uploads images and
  `tray.png` first, `pack.json` last; `_categories.json` first of all; skips objects whose md5 matches; ADC or the
  Storage emulator (`FIREBASE_STORAGE_EMULATOR_HOST`).
- `e2e.js`: with emulators running, uploads one launch pack, polls `catalog/meta` (60 s), downloads the catalog and
  the pack's zip through the emulator URL, asserts the pack is listed with its count.
- [ ] Run `firebase emulators:exec --only functions,firestore,storage,tasks "node scripts/library/e2e.js"` → pass;
  commit.

## Phase C: library migration

### Task 9: Launch packs into `library/`

**Files:** `git mv packs/<id>/src/*.webp library/<category>/<id>/NN.webp` (14 packs); create
`library/<category>/<id>/pack.json` ×14, `library/_categories.json`; delete `scripts/prepare-packs.js`,
`scripts/upload-pack.js`; create `scripts/library/contact-sheet.js`.

- Categories and `alsoIn`: spec § Migration. `order` from `design/catalog.json`. `animate: "wiggle"` for mango-moves,
  heartbeat, bunny-bounce, pop-words, dance-w-me, sorry-wiggle, sunny-sleepy. Names from `design/catalog.json`.
- Per sticker: `emojis` (1–3, starting from the old `EMOJI_POOLS`, adjusted to the art) and `text` (the lettering,
  verbatim; empty for text-free art) read from a numbered contact sheet of each pack.
- `_categories.json`: 11 categories, spec order, icons/hues, default emoji, names in 19 languages (existing 8 from
  `values-*/strings.xml` `theme_*`), keywords.
- [ ] `npm test --prefix functions` (launch-packs test now builds all 14: ok) → commit.

## Phase D: app

### Task 10: Catalog model, URLs and language order (pure Kotlin)

**Files:** Create `core/data/catalog/CatalogFile.kt`, `CatalogUrls.kt`, `LanguageOrder.kt`; modify
`core/model/StickerPack.kt`, `core/model/Category.kt`; tests `CatalogFileTest`, `CatalogUrlsTest`,
`LanguageOrderTest`.

**Interfaces:**
- `data class Catalog(val version: Int, val categories: List<Category>, val packs: List<StickerPack>)`.
- `object CatalogFile { fun parse(json: String, urls: CatalogUrls): Catalog }` (org.json; unknown fields ignored;
  a pack missing `id`, `name` or `zip` is skipped).
- `class CatalogUrls(template: String, emulatorHost: String?) { fun url(path: String): String }` — `{path}` →
  `URLEncoder` with `%20` and `/` → `%2F`; `{rawPath}` → path; emulator rewrites `127.0.0.1`/`localhost`.
- `fun List<StickerPack>.inLanguageOrder(appLanguage: String): List<StickerPack>` — stable groups: primary subtag
  match or `none`, then `en`, then the rest.
- `StickerPack` gains `version`, `lang`, `alsoIn`, `keywords`, `names`, `coverSmallUrl`, `coverLargeUrl`,
  `coverTiles`, `zipUrl`, `zipBytes`; `downloads` now carries real adds. `Category` gains `names`, `keywords`,
  `packCount`.

- [ ] Tests from a fixture catalog JSON (the spec example); URL encoding of spaces and `/`; language groups. Commit.

### Task 11: Catalog store, pack archive and data layer

**Files:** Create `core/data/catalog/CatalogStore.kt`, `PackArchive.kt`; rewrite
`core/data/firebase/CatalogDataSource.kt`, `core/data/download/PackDownloader.kt`; modify
`core/data/di/DataModule.kt` (database `stickermaker`, `OkHttpClient`, no `FirebaseStorage`),
`gradle/libs.versions.toml`, `app/build.gradle.kts` (okhttp, drop firebase-storage); tests `PackArchiveTest`,
`CatalogStoreTest`.

**Interfaces:**
- `CatalogStore.catalog: Flow<Catalog?>` — emits the cached catalog first (from `filesDir/catalog/`), then new ones;
  `null` only when nothing is cached and the first fetch failed. Download only when `catalog/meta.version` differs
  from the cached version.
- `PackArchive.ensure(pack, onProgress: (Float) -> Unit): File` — returns the unpacked directory
  `filesDir/packs/.v/<id>-<version>/` (download `pack.zip`, verify size, unzip to temp, rename);
  `readContents(dir): List<Sticker>`.
- `CatalogDataSource.observeCategories()/observePacks()/getPack(id)` keep their signatures; `getPack` returns
  `stickerUrls` as local `file://` paths from `PackArchive.ensure`.
- `PackDownloader.download(pack)` reuses `PackArchive.ensure` and copies into `filesDir/packs/<id>/`.
- [ ] Tests (Robolectric + MockWebServer-free: a local zip file served through a fake fetcher): unzip atomically,
  corrupt zip leaves nothing behind, cached catalog emitted offline, failed download keeps the previous catalog.
  Commit.

### Task 12: Room migration and provider version

**Files:** modify `core/data/db/Entities.kt`, `LoveDb.kt`, `di/DatabaseModule.kt`, `repo/CatalogRepository.kt`,
`whatsapp/StickerContentProvider.kt`; test `LoveDbMigrationTest`.

- `imageDataVersion: Int = 1` on `InstalledPackEntity` and `OwnPackEntity`; `MIGRATION_1_2` adds both columns
  (`INTEGER NOT NULL DEFAULT 1`); the provider sends the pack's value; `insertInstalled` stores `pack.version`.
- [ ] Migration test from the v1 schema; provider test reads version 3 for a v3 pack. Commit.

### Task 13: Home, pack page and card

**Files:** modify `feature/home/HomeViewModel.kt`, `feature/detail/PackDetailViewModel.kt`,
`core/design/components/PackCard.kt`, `feature/home/HomeScreen.kt` (cover model), tests `HomeOrderingTest`.

- Order: catalog order → `inLanguageOrder(appLanguage)`; categories chip membership `category == id || id in
  alsoIn`; chips hide categories with `packCount == 0`; search matches names (all languages), category names and
  keywords, pack keywords; `downloadsLabel` null when `< 100` (card and meta line show only the sticker count).
- `PackCard` takes `coverUrl` + `coverTiles` and draws each circle from its slice of the strip.
- [ ] Tests for ordering, chips, search and label threshold. Commit.

## Phase E: CI, docs and production

### Task 14: CI seeding and README

**Files:** modify `.github/workflows/screens.yml`, `README.md`, `app/src/androidTest/.../tour/*` where a pack name
or count is asserted.
- Screens: `npm ci --prefix functions`, emulators `functions,firestore,storage,tasks`, `node
  scripts/library/upload.js library --emulator`, wait for `catalog/meta`.
- README: Sticker packs (library conventions), Firebase ops (setup steps), emulators.
- [ ] Commit; push only when the user asks.

### Task 15: Production setup and first publish (needs the user's logins)

- [ ] `firebase use play-console-f33dd`; confirm Blaze.
- [ ] `firebase firestore:databases:create stickermaker --location us-central1`.
- [ ] `gcloud storage buckets create gs://play-console-f33dd-stickermaker --location=us-central1
  --uniform-bucket-level-access`; add it to Firebase (`POST https://firebasestorage.googleapis.com/v1beta/projects/
  play-console-f33dd/buckets/play-console-f33dd-stickermaker:addFirebase`).
- [ ] `firebase deploy --only firestore,storage,functions:stickermaker`.
- [ ] `node scripts/library/upload.js library`; read every `_report.txt`; fetch `catalog/meta` and the catalog.
- [ ] Budget alerts ($3/$5/$7) in Cloud Billing (console, the user).
