# Sticker catalog pipeline: design

Date: 2026-09-29. Status: approved in conversation ("do it"); implemented. Updated after implementation where the build taught
us something (marked "Implementation note").

## Goal

Adding a sticker pack means putting its folder into Cloud Storage. A function turns the folder into a
WhatsApp-ready pack, records it in Firestore and republishes the catalog; the app shows it within seconds. No
hand-edited catalog, no local scripts, no invented numbers.

Success:
- A folder dropped into `library/<category>/<Pack Name>/` is live in the app 15–30 s after its last file lands
  (with a listing `pack.json`), or about a minute later without one.
- Only complete packs that pass WhatsApp's rules go live; a bad upload never replaces a good live version.
- Sticker quality is never worse than the art that was uploaded.
- Storage, Firestore and Functions together cost $0–7 per month at 100K users (see Costs).

## Constraints

- Budget: $0–7/month at 100K users. All on Firebase for now; public files must be movable to a zero-egress host
  (Cloudflare R2) later without an app update.
- Quality first: stickers are the product. Ready art ships byte for byte; nothing is re-encoded below a quality floor.
- Live as soon as the checks pass (no draft step). Folders under `_` are the staging area.
- No AI at runtime and no per-user cost. Per-sticker emoji and text are written once, at authoring time.
- Everything is named `stickermaker` and isolated from the other apps in project `play-console-f33dd`.

## Resources and naming

| Resource | Name | Notes |
|---|---|---|
| Firestore database | `stickermaker` (us-central1) | Replaces `stickers-app`, which was never created. Named databases get no free tier; the app reads one tiny document. |
| Storage bucket | `play-console-f33dd-stickermaker` (us-central1, Standard) | `library/` private sources, `public/` generated files. Bucket names are global, hence the project prefix. The default bucket is not used. Deploy target `stickermaker` in `.firebaserc` (the Storage emulator requires a target). |
| Functions | codebase `stickermaker`, group `stickermaker` (us-central1) | Deployed names `stickermaker-<name>`; deploys never touch other codebases. Runtime nodejs24. |

## Architecture

```
Claude Design ──export──▶ library/<category>/<Pack Name>/ (Storage, private)
                                  │ object finalized / deleted
                                  ▼
              stickermaker-onLibraryUpload / -Delete ──▶ task queue ──▶ stickermaker-buildPack
                                                                           │ writes
                        public/packs/<id>/v<n>/{cover-s.webp, cover-l.webp, pack.zip}
                        Firestore packs/<id>   +   library/…/_report.txt
                                                                           │ enqueues
                                                                           ▼
                                             stickermaker-publishCatalog (one at a time)
                        public/catalog/v<k>.json.gz   +   Firestore catalog/meta {version, path, urlTemplate}
                                                                           │ realtime
                                                                           ▼
                                                          app: Home, pack page, Add to WhatsApp
```

Functions (all min instances 0, capped max instances):

| Function | Trigger | Job |
|---|---|---|
| `onLibraryUpload` | Storage finalize, bucket `play-console-f33dd-stickermaker` | `_categories.json` → sync categories; pack files → schedule a build |
| `onLibraryDelete` | Storage delete, same bucket | schedule a build (which unpublishes an emptied folder) |
| `buildPack` | task queue (2 GiB, 2 vCPU, max 3 at once) | build, validate, publish a new version or report errors |
| `publishCatalog` | task queue (max 1 at once) | write the catalog file and the pointer document |
| `onPackEdited` | Firestore update of `packs/{id}` in `stickermaker` | pin / hidden edited in the console → schedule a publish |
| `weeklyStats` | schedule, Mondays 04:00 UTC | read `pack_added` counts from Analytics, score packs, publish |

## Library conventions

```
library/
  _categories.json            categories: names in 19 languages, order, icon, hue, default emoji, search words
  sorry/                      category id = folder name
    Sorry Wiggle/             pack folder: id = slug of the name ("sorry-wiggle"), display name = the folder name
      01.webp 02.webp …       stickers, in natural file-name order unless pack.json lists them
      tray.png                optional; otherwise made from the first sticker
      pack.json               optional (always written by the Claude Design export)
      _report.txt             written by the build
  _staging/…                  anything under a folder starting with "_" is ignored
```

- Pack id: the folder name lowercased, accents stripped, runs of anything but `a-z0-9` turned into one `-`,
  trimmed; 2–64 characters. Renaming the folder makes a new pack; rename with `pack.json` `name` instead.
- Category: the parent folder. A folder missing from `_categories.json` still works with defaults (English name from
  the folder, heart icon, placed last) and the report says so.
- Inputs: PNG, WebP (static or animated) and GIF, up to 10 MB each. Anything else is skipped with a note.

### `pack.json` (all fields optional)

```json
{
  "name": "Sorry, My Love",
  "names": { "ar": "…" },
  "alsoIn": ["couples"],
  "lang": "en",
  "tags": ["pinky", "apology"],
  "order": 5,
  "animate": "wiggle",
  "cover": ["01.webp", "04.webp", "07.webp", "10.webp", "13.webp", "16.webp"],
  "stickers": [
    { "file": "01.webp", "emojis": ["🥺", "💔"], "text": "I'm sorry" }
  ]
}
```

- `name` (≤ 128 chars) and `names` (per app language) override the folder name.
- `alsoIn`: up to 2 more category ids the pack appears under.
- `lang`: the language of the lettering (BCP 47, e.g. `en`, `ar`, `pt-BR`), or `none` for text-free art. Default `en`.
- `tags`: search words. `order`: tie-breaker in ranking (lower first; default 1000).
- `animate: "wiggle"`: synthesize a 4-frame wiggle from static art (the launch packs' "animated" packs).
- `cover`: the 6 stickers on the Home card (default: the first 6).
- `stickers`: when present it lists exactly the pack's stickers in order; unlisted images are ignored with a note.
  `emojis`: 1–3 (default: the category's). `text`: what the sticker says, verbatim (search and WhatsApp's
  accessibility text).

### `_categories.json`

```json
{
  "categories": [
    {
      "id": "couples", "order": 1, "icon": "heart-handshake", "hue": 10,
      "emojis": ["💑", "❤️"],
      "names": { "en": "Couples", "ar": "…" },
      "keywords": { "pt-BR": ["namorados"] }
    }
  ]
}
```

Launch set (the current 8 plus Miss you, Sorry and Good morning), in chip order: couples, romantic, missyou, sorry,
cute, flirty, goodmorning, goodnight, distance, funny, anime. Existing ids are unchanged. Names in all 19 app
languages; the 8 existing ones come from the app's `theme_*` strings, the 3 new ones are ours pending native review.

## Build

### When a build runs

- Every upload or delete under a pack folder records its time in Firestore `builds/<packId>` (`lastEventAt`, in a
  transaction) and schedules a "quiet" build 45 s out unless one is already pending for the pack (`quietPending`;
  a flag older than 10 minutes no longer blocks). The quiet build clears the flag before it lists the folder, so any
  later event schedules another build and no change is lost. It builds once the folder has been quiet for 30 s
  (newest object and last event, since deletions leave no timestamp); until then it answers 503 and Cloud Tasks
  retries it (30–60 s backoff, 8 attempts), setting the flag again.
- Fast path: when `pack.json` lists the stickers and every listed file exists, the upload that completes the set also
  schedules a build 2 s out, id `f-<packId>-<hash of the listed objects' generations>`. Uploading the stickers first
  and `pack.json` last (what the export does) makes a pack live without the quiet wait.
- Builds are deterministic, so two builds of the same files write the same bytes.
- Implementation note: debouncing by task id (one id per 30 s) was dropped. Cloud Tasks keeps an id blocked after it
  runs, so an event landing after its build already ran was silently dropped, and the Cloud Tasks emulator runs tasks
  immediately, which turned every upload into a retrying task that exhausted the Functions emulator.

### Stickers (quality rules)

- A file that is already WhatsApp-ready ships byte for byte: 512×512 WebP, static ≤ 100 KB, or animated ≤ 500 KB with
  every frame ≥ 8 ms and the loop ≤ 10 s. Claude Design's exports are.
- Otherwise it is fitted into 512×512 on transparency (Lanczos 3, never cropped) and encoded at the best quality that
  fits: static tries lossless first, then quality 95 stepping down by 5 to a floor of 75; animated from 90 down to 60.
  Below the floor the build fails for that file instead of shipping blurry art.
- Implementation note: lossy and animated encodes use libwebp effort 4. Effort 6 measured about 100 times slower
  (15 s instead of 0.16 s per animated sticker) for 2–3% fewer bytes; lossless keeps effort 6 (0.65 s).
- Art smaller than 512 px on its longer side is upscaled and flagged in the report.
- Animated input: frame delays under 8 ms become 100 ms (as browsers play them), noted; loops over 10 s fail.
- `animate: "wiggle"` builds the launch packs' 4-frame wiggle (±1.8°, 150 ms frames) from static art.

### Other outputs

- Tray: `tray.png` if present, else the first sticker's first frame; 96×96, full-colour PNG, reduced to a palette only
  when needed to stay ≤ 50 KB.
- Cover strip: the 6 cover stickers' first frames side by side on transparency, in two sizes: `cover-s.webp` with
  96 px tiles (screens up to 2×) and `cover-l.webp` with 160 px tiles (the card's 46 dp circles are 161 px at 3.5×);
  WebP quality 85, alpha 90 (about 30 and 55 KB).
- `pack.zip`: stored (not deflated), fixed timestamps, entries `contents.json`, `tray.png`, `01.webp` … in pack order.
  `contents.json`: `{ id, name, version, animated, stickers: [{ file, emojis, text }] }`.
- All written under `public/packs/<id>/v<n>/` with `Cache-Control: public, max-age=31536000, immutable`. A build
  keeps the current and previous version and deletes older ones.

### Checks

A pack goes live only when all pass; otherwise the live version (if any) stays up and the report lists why:
3–30 stickers; all static or all animated; a live pack keeps its static/animated kind; 1–3 emoji per sticker;
tray ≤ 50 KB; the pack id is not used by another folder that still has files.

### Versions and records

- The version's fingerprint is the SHA-256 of `pack.zip`. Same fingerprint as the live version: nothing is published,
  only the report is refreshed. New fingerprint: version n + 1 (WhatsApp's `image_data_version`).
- Firestore `packs/<id>` is written after the files, in a transaction that keeps `pin`, `hidden` and `stats`.
- An emptied folder whose pack came from it: status `removed`, catalog republished. People who added it keep it.

### Report (`_report.txt` in the pack folder)

```
✅ Sorry Wiggle is live: version 3, 6 animated stickers, in Sorry (also Couples). Built 2026-09-29 14:05 UTC.
Notes:
- No tray.png: made from 01.webp.
❌ Sorry Wiggle was not published; version 2 stays live.
- Only 2 stickers: WhatsApp needs 3 to 30.
```

## Publish

`publishCatalog` runs one at a time. Publish requests are never deduplicated (an id that already ran would drop a
later request); a publish whose catalog is identical to the live one writes nothing. It reads `categories` and every
`packs` doc with status `live` and `hidden` not true, and writes:

- `public/catalog/v<k>.json.gz` (gzip, immutable, the last 3 kept):

```json
{
  "schema": 1, "version": 12, "publishedAt": "2026-09-29T14:05:00.000Z",
  "categories": [
    { "id": "sorry", "order": 4, "icon": "hand-heart", "hue": 20, "names": { "en": "Sorry" },
      "keywords": { "pt-BR": ["desculpa"] }, "packs": 2 }
  ],
  "packs": [
    { "id": "sorry-wiggle", "name": "Sorry Wiggle", "names": {}, "category": "sorry", "alsoIn": ["couples"],
      "lang": "en", "animated": true, "count": 6, "version": 3, "adds": 0,
      "cover": { "s": "public/packs/sorry-wiggle/v3/cover-s.webp", "l": "public/packs/sorry-wiggle/v3/cover-l.webp", "tiles": 6 },
      "zip": { "path": "public/packs/sorry-wiggle/v3/pack.zip", "bytes": 563412 },
      "keywords": ["sorry", "forgive", "bunny"], "publishedAt": "2026-09-29T14:05:00.000Z" }
  ]
}
```

- Firestore `catalog/meta`: `{ version, path, bytes, urlTemplate, publishedAt }`. `urlTemplate` is
  `https://firebasestorage.googleapis.com/v0/b/play-console-f33dd-stickermaker/o/{path}?alt=media` (`{path}` is the
  URL-encoded object path). Moving public files to R2 later means copying them and setting the template to
  `https://<cdn host>/{rawPath}`; the app needs no update.
- `packs` is in rank order; category `packs` counts include `alsoIn`.

## Ordering, filters, search and counts

- Rank order (computed at publish): pinned packs (`pin` ascending) → score (descending) → `order` → newest.
- Score: weekly from Analytics. `weeklyStats` reads the existing `pack_added` event's `pack_id` parameter through the
  Analytics Data API (28 days by day, plus all time): `trend = Σ adds_d × 0.5^(age_d / 7)`. A pack live under 14 days
  scores at least `median trend × (1 − age / 14)` so new packs are not buried. Without Analytics configured
  (`GA4_PROPERTY_ID` unset) the job does nothing and ranking falls back to pin → order → newest.
- The app partitions the ranked list by lettering language, keeping rank inside each group: the app language or
  `none` first, then English, then the rest.
- Chips: Trending · ♥ Saved · Animated · then categories in `order`, hiding any with no live packs. A pack appears
  under its folder category and its `alsoIn` categories.
- Search: pack names (all languages), category names and keywords (all languages), pack keywords (tags plus the
  words of every sticker's text).
- Counts: "N adds" only once a pack has ≥ 100 real adds (all-time `pack_added`); below that the card shows just the
  sticker count. No invented numbers.

## Security rules

- Firestore (`stickermaker`): `catalog/meta` readable by anyone; everything else server-only.
- Storage (`play-console-f33dd-stickermaker`): `public/**` readable by anyone; everything else closed. Uploads use
  IAM (console, gcloud), which rules do not govern.

## App changes

- Firestore uses the `stickermaker` database. The app reads only `catalog/meta`.
- `CatalogStore`: listens to `catalog/meta`; downloads the catalog file with OkHttp only when `version` changes;
  keeps it in `filesDir/catalog/`; emits the cached catalog immediately on start (Home opens from disk).
- URLs come from `urlTemplate`. Debug builds against the emulators rewrite `127.0.0.1`/`localhost` to the emulator
  host.
- `PackArchive`: downloads `pack.zip` with byte progress, unpacks it atomically to `cacheDir/packs/<id>-<version>/`
  (entry names checked, nothing outside the folder), reused by the pack page and by Add (no second download).
- Home card: one cover strip per card (`cover-s` at density ≤ 2, else `cover-l`) sliced into the tile circles.
- Home order, chips, search and counts as above. Pack page meta line hides counts under 100.
- Room migration 1 → 2 adds `imageDataVersion` (default 1) to `installed_packs` and `own_packs` (the latter is the
  custom-stickers spec's column) and `accessibilityText` to `installed_stickers`; the provider sends them as
  `image_data_version` and `sticker_accessibility_text` (the sticker's lettering, capped at 125/255 characters).
  An installed pack refreshes to a new version when its pack page is opened.
- Downloads are plain HTTPS; the Firebase Storage SDK is no longer used.

## Migration

The 14 launch packs move into `library/` in the repo (sources moved from `packs/<id>/src/`, ids unchanged, a
`pack.json` each with names, per-sticker emoji and text, `order` from `design/catalog.json`, `animate: "wiggle"` for
the 7 animated packs) and are uploaded to the bucket once. Categories: sorry-love and sorry-wiggle → `sorry` (also
couples); miles-apart → `distance` (also missyou); gm-gn and sunny-sleepy → `goodnight` (also goodmorning); the rest
keep their category. `packs/<id>/` keeps the built WhatsApp fixtures for the validator test. `prepare-packs.js` and
`upload-pack.js` are replaced by `scripts/library/`.

## Testing

- Functions: `node --test` unit tests for slugs, path parsing, manifests, readiness, encoding decisions, checks,
  report text, catalog assembly and ranking; an integration test that builds every launch pack from `library/`.
- Emulators: an end-to-end script uploads a library pack to the Storage emulator and waits for `catalog/meta`, the
  catalog file and the pack outputs (Storage, Firestore, Functions and Tasks emulators).
- App: JVM tests for catalog parsing, URL templates, language grouping, zip unpacking and the Room migration.
- CI: the screen tour seeds its emulators through the real pipeline (`scripts/library/seed-emulators.js`, one file
  and one pack at a time; test-only add counts from `design/catalog.json` keep the frames matching the design), and
  `.github/workflows/functions.yml` runs the functions' tests.

## Setup and operations

One time (needs a login with Owner or Editor on `play-console-f33dd`, Blaze plan):
1. Create the database: `firebase firestore:databases:create stickermaker --location us-central1`.
2. Create the bucket in us-central1 and add it to Firebase (console Storage › Add bucket, or gcloud plus the Firebase
   Storage `addFirebase` API).
3. `firebase deploy --only firestore,storage,functions:stickermaker`.
4. Upload `library/` (`scripts/library/upload.js`), stickers first and `pack.json` last per pack.
5. Optional, for ranking: register `pack_id` as an event-scoped custom dimension in Analytics, give the functions'
   service account Viewer on the property, set `GA4_PROPERTY_ID`.

Guardrails: billing budget alerts at $3, $5 and $7; min instances 0 and capped max instances on every function;
bounded task retries; Artifact Registry cleanup policy (kept by the CLI).

## Costs

Estimated monthly, from the launch packs (average pack 525 KB, cover strip about 40 KB, catalog about 10 KB
gzipped for 200 packs). Assumes new users download 3 packs, returning users 1 a month, 40% of monthly users new,
daily users 25% of monthly, Storage's free 100 GB and 50K downloads per month.

| | 100K installs (~30K monthly) | 100K monthly active | 100K daily active |
|---|---|---|---|
| Before this change | ~$23 | ~$100 | ~$430 |
| This design | ~$0.30 | ~$5 | ~$56 |
| This design with public files on R2 | ~$0.10 | ~$0.40 | ~$1.50 |

## Not in v1

R2 delivery (the switch is designed in), animated stickers on the pack page, App Check, automatic "new pack"
notifications, remote template sets for name packs, pack-language variants grouped as one pack.
