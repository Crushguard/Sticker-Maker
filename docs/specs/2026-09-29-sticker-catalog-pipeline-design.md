# Sticker catalog pipeline: design

Date: 2026-09-29. Status: approved in conversation ("do it"); implemented. Updated after implementation where the build taught
us something (marked "Implementation note").

Update 2026-09-30 (asked for in conversation: "the categories should function based on tags … categories are just search
filters"): packs sit flat in `library/<Pack Name>/`; each pack's tags place it in the app's categories, which are
filters defined in `_categories.json`; `_tags.json` gives every tag and lettering language the words people type in
every app language; `lang` is a list; After Dark packs are parked; `public/` repairs itself after a wipe. The catalog
content moved to the 120-pack Claude Design export, described in `catalog/packs.json`.

## Goal

Adding a sticker pack means putting its folder into Cloud Storage. A function turns the folder into a
WhatsApp-ready pack, records it in Firestore and republishes the catalog; the app shows it within seconds. No
hand-edited catalog, no local scripts, no invented numbers.

Success:
- A folder dropped into `library/<Pack Name>/` is live in the app 15–30 s after its last file lands
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
Claude Design ──export──▶ library/<Pack Name>/ (Storage, private)
                                  │ object finalized / deleted
                                  ▼
              stickermaker-onLibraryUpload / -Delete ──▶ task queue ──▶ stickermaker-buildPack
                                                                           │ writes
                        public/packs/<id>/v<n>-<hash8>/{cover-s.webp, cover-l.webp, pack.zip}
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
| `onLibraryUpload` | Storage finalize, bucket `play-console-f33dd-stickermaker` (retried) | `_categories.json` → sync categories; `_tags.json` → sync `config/tags`; pack files → schedule a build |
| `onLibraryDelete` | Storage delete, same bucket (retried) | schedule a build (which unpublishes an emptied folder) |
| `buildPack` | task queue (2 GiB, 2 vCPU, one build per instance, max 3 at once, one per pack) | build, validate, publish a new version or report errors |
| `publishCatalog` | task queue (max 1 at once) | write the catalog file and the pointer document |
| `onPackEdited` | Firestore update of `packs/{id}` in `stickermaker` | pin / hidden edited in the console → schedule a publish |
| `weeklyStats` | schedule, Mondays 04:00 UTC | read `pack_added` counts from Analytics, score packs, publish |

## Library conventions

```
library/
  _categories.json            the app's categories: names in 19 languages, order, icon, hue, default emoji, search
                              words, and the tags that put a pack in each
  _tags.json                  the words people type for each tag and lettering language, by app language
  Sorry Wiggle/               pack folder: id = slug of the name ("sorry-wiggle"), display name = the folder name
    01.webp 02.webp …         stickers, in natural file-name order unless pack.json lists them
    tray.png                  optional; otherwise made from the first sticker
    pack.json                 written into the export by scripts/library/prepare.js from catalog/packs.json
    _report.txt               written by the build
  _staging/…                  anything under a folder starting with "_" is ignored
```

- Pack id: the folder name lowercased, accents stripped, runs of anything but `a-z0-9` turned into one `-`,
  trimmed; 2–64 characters. Renaming the folder makes a new pack; rename with `pack.json` `name` instead.
- Categories: the pack's tags decide them (see `_categories.json`). The older `library/<category>/<Pack Name>/`
  layout still builds, its category folder counting as the pack's first tag; it stays readable so the packs uploaded
  that way can be deleted and moved.
- Inputs: PNG, WebP (static or animated) and GIF, up to 10 MB each. Anything else is skipped with a note.

### `pack.json` (all fields optional)

```json
{
  "name": "Sorry, My Love",
  "names": { "ar": "…" },
  "lang": ["en"],
  "tags": ["couples", "romantic", "sorry", "blob", "pinky"],
  "emojis": ["🥺", "💗"],
  "keywords": ["forgive me", "my bad"],
  "order": 5,
  "adult": false,
  "animate": "wiggle",
  "cover": ["01.webp", "04.webp", "07.webp", "10.webp", "13.webp", "16.webp"],
  "stickers": [
    { "file": "01.webp", "emojis": ["🥺", "💔"], "text": "I'm sorry" }
  ]
}
```

- `name` (≤ 128 chars) and `names` (per app language) override the folder name.
- `lang`: the languages of the lettering (BCP 47, e.g. `"en"`, `["ar", "hi", "es"]`, `"pt-BR"`), `"none"` for
  text-free art, `"multi"` for one sticker per language (listed with the languages it shows). One code, a list or a
  comma-separated string. Default `["en"]`.
- `tags` (≤ 20): the pack's category ids first (its main one first), then what it shows and is for (cat, sorry,
  coffee, birthday…), then proper names people might type (mango, laddoo). They decide the categories and feed
  search.
- `emojis`: 1–3, the default for stickers without their own (else the main category's, else ❤️).
- `keywords` (≤ 40 phrases): extra search words, such as the stickers' lines.
- `order`: tie-breaker in ranking (lower first; default 1000).
- `adult: true`: After Dark (18+). The pack is parked: never built, nothing reaches `public/`, a live one is taken
  down. The Google Play build never lists these packs.
- `animate: "wiggle"`: makes the pack animated. Static stickers get a synthesized 4-frame wiggle; animated ones ship as
  they are (the launch fixtures' "animated" packs). WhatsApp takes a pack all static or all animated.
- `cover`: the 6 stickers on the Home card (default: the first 6).
- `stickers`: when present it lists exactly the pack's stickers in order; unlisted images are ignored with a note.
  `emojis`: 1–3. `text`: what the sticker says, verbatim (search and WhatsApp's accessibility text).

### `_categories.json`

```json
{
  "categories": [
    {
      "id": "saudi", "order": 14, "icon": "moon-star", "hue": 250,
      "emojis": ["☕", "🌙"],
      "names": { "en": "Saudi & Gulf", "ar": "…" },
      "keywords": { "ar": ["خليجي"] },
      "tags": ["gulf"]
    }
  ]
}
```

A category is a filter: a pack shows under every category whose id, or one of whose `tags`, is among the pack's tags.
The first such tag in the pack's own order is its main category (its card's hue, "category" in the catalog). A pack
with no category tag shows in Trending, Animated and search only. Membership is worked out at every publish, so
editing `_categories.json` regroups packs without rebuilding them.

The 14 categories are the design's Stickers page themes, in its order: cute, couples, romantic, flirty, funny, anime,
goodnight, distance, occasions, family, world ("Love words"), india, brazil ("Brasil"), saudi ("Saudi & Gulf").
After Dark is not a category: its packs are parked.

### `_tags.json`

```json
{
  "tags": { "cat": { "en": ["cat", "kitty"], "ar": ["قطة", "بسة"], "fr": ["chat"] } },
  "languages": { "ar": { "en": ["arabic"], "ar": ["عربي", "العربية"] } }
}
```

Synced to Firestore `config/tags` (report `library/_tags_report.txt`); the catalog carries the entries its packs use.
Category ids are translated in `_categories.json` (names and keywords), not here.

## Build

### When a build runs

- Every upload or delete under a pack folder records its time in that folder's entry of Firestore `builds/<packId>`
  (`folders.<key>.lastEventAt`, in a transaction; one pack id can have two folders, as during a move to another
  category) and schedules the folder's "quiet" build 45 s out unless one is already pending for it (`pending`; a flag
  older than 10 minutes no longer blocks). The quiet build clears the flag before it lists the folder, so any later
  event schedules another build and no change is lost. It builds once the folder has been quiet for 30 s (newest
  object and last event, since deletions leave no timestamp); until then it answers 503 and Cloud Tasks retries it
  (30–60 s backoff), setting the flag again. A build still waiting on its 8th and last attempt hands the wait to a
  fresh task, so a long upload never runs out of retries. The Storage triggers retry when they fail; a failed
  enqueue clears its flag first.
- Fast path, for a pack never published: when `pack.json` is uploaded after every image (the export writes it last)
  and lists stickers that have all arrived, its upload also schedules a build 2 s out, id `f-<packId>-<hash of the
  listed objects' generations>`. A fast build never waits: if the pack already has a version, or anything else stops
  it, it ends, because the folder's quiet build always follows. So an update of a live pack always waits for its
  folder to settle, and a half-replaced set of stickers never goes live, whatever order the files arrive in.
- One build per pack id runs at a time: a lease in `builds/<packId>`, held by the Cloud Tasks task id for up to
  10 minutes (the build timeout is 540 s). A second quiet build waits for it; a second fast build ends. A build that
  sees the folder change while it reads it waits again instead of publishing a mix.
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
- Animated input: frame delays of 10 ms or less play at 100 ms (as browsers play them, and as libwebp writes them),
  noted; loops over 10 s fail. Every re-encoded sticker is probed again and must pass WhatsApp's rules.
- `animate: "wiggle"` builds the launch packs' 4-frame wiggle (±1.8°, 150 ms frames) from static art.

### Other outputs

- Tray: `tray.png` if present, else the first sticker's first frame; 96×96, full-colour PNG, reduced to a palette only
  when needed to stay ≤ 50 KB.
- Cover strip: the 6 cover stickers' first frames side by side on transparency, in two sizes: `cover-s.webp` with
  96 px tiles (screens up to 2×) and `cover-l.webp` with 160 px tiles (the card's 46 dp circles are 161 px at 3.5×);
  WebP quality 85, alpha 90 (about 30 and 55 KB).
- `pack.zip`: stored (not deflated), fixed timestamps, entries `contents.json`, `tray.png`, `01.webp` … in pack order.
  `contents.json`: `{ id, name, version, animated, stickers: [{ file, emojis, text }] }`.
- All written under `public/packs/<id>/v<n>-<hash8>/` (the first 8 hex digits of the version's fingerprint, so a path
  only ever holds one set of bytes) with `Cache-Control: public, max-age=31536000, immutable`. A build keeps the three
  newest versions, for phones mid-download or on a catalog a publish or two behind, and deletes older ones.

### Checks

A pack goes live only when all pass; otherwise the live version (if any) stays up and the report lists why:
3–30 stickers; all static or all animated; a live pack keeps its static/animated kind; 1–3 emoji per sticker;
tray ≤ 50 KB; the pack id is not used by another folder that still has files.

### Versions and records

- The version's fingerprint is the SHA-256 of `pack.zip` (built with version 0) and both cover strips. Same
  fingerprint as the live version: nothing is published, only the report is refreshed. New fingerprint: a version
  (WhatsApp's `image_data_version`) above the live one and above every version folder in `public/`, so a number never
  names two sets of files, even after the record was deleted; a retry reuses the number of its own unfinished upload.
- Firestore `packs/<id>` is written after the files, in a transaction that checks the version, fingerprint and status
  the build started from (a changed record sends the build back to wait) and keeps `pin`, `hidden` and `stats`.
- Every build that looked at its folder asks for a publish, which skips an identical catalog: a retry after a crash
  between the record and the publish still publishes.
- A failed build leaves the live version and its record; putting the live files back clears the failure.
- An emptied folder whose pack came from it: status `removed`, catalog republished, and any other folder holding the
  same pack id builds next (a move out of an older category folder). People who added it keep it.
- A removed pack that comes back is a new pack: its old static or animated kind no longer binds it, its version still
  goes up, and it counts as new for ranking.
- Records written before tags decided categories carry `category`, `alsoIn` and `lang`; the catalog still reads them
  as tags until the pack's next build drops them.
- Self-repair: an unchanged pack whose version files are missing from `public/` has them written again (same bytes,
  same path); a publish whose catalog is unchanged but whose live catalog file is missing publishes a new version. A
  wiped `public/` is back as soon as each pack builds again.

### Report (`_report.txt` in the pack folder)

```
✅ Sorry Wiggle is live: version 3, 6 animated stickers, in Couples, Romantic. Built 2026-09-29 14:05 UTC.
Notes:
- No tray.png: made from 01.webp.
❌ Sorry Wiggle was not published; version 2 stays live.
- Only 2 stickers: WhatsApp needs 3 to 30.
⏸ Rude Love is not published: After Dark (18+) packs stay out of the Google Play build.
```

## Publish

`publishCatalog` runs one at a time. Publish requests are never deduplicated (an id that already ran would drop a
later request); a publish whose catalog is identical to the live one writes nothing. It reads `categories`,
`config/tags` and every `packs` doc with status `live` and `hidden` not true, and writes:

- `public/catalog/v<k>.json.gz` (gzip, immutable, the last 3 kept):

```json
{
  "schema": 1, "version": 12, "publishedAt": "2026-09-29T14:05:00.000Z",
  "categories": [
    { "id": "couples", "order": 2, "icon": "heart-handshake", "hue": 10, "names": { "en": "Couples" },
      "keywords": { "pt-BR": ["casal"] }, "tags": ["couples"], "packs": 21 }
  ],
  "packs": [
    { "id": "sorry-wiggle", "name": "Sorry Wiggle", "names": {}, "category": "couples", "alsoIn": ["romantic"],
      "lang": "en", "langs": ["en"], "tags": ["couples", "romantic", "sorry", "blob", "animated"],
      "animated": true, "count": 6, "version": 3, "adds": 0,
      "cover": { "s": "public/packs/sorry-wiggle/v3-a1b2c3d4/cover-s.webp", "l": "…/cover-l.webp", "tiles": 6 },
      "zip": { "path": "public/packs/sorry-wiggle/v3-a1b2c3d4/pack.zip", "bytes": 563412 },
      "keywords": ["couples", "romantic", "sorry", "blob", "animated", "forgive"], "publishedAt": "2026-09-29T14:05:00.000Z" }
  ],
  "tags": { "sorry": { "en": ["sorry", "apology"], "ar": ["آسف"] } },
  "languages": { "en": { "en": ["english"] } }
}
```

- Firestore `catalog/meta`: `{ version, path, bytes, urlTemplate, publishedAt }`. `urlTemplate` is
  `https://firebasestorage.googleapis.com/v0/b/play-console-f33dd-stickermaker/o/{path}?alt=media` (`{path}` is the
  URL-encoded object path). Moving public files to R2 later means copying them and setting the template to
  `https://<cdn host>/{rawPath}`; the app needs no update.
- `packs` is in rank order; category `packs` counts every pack the category's tags bring. An animated pack gets the
  `animated` tag. `category` and `alsoIn` are the pack's categories (main, then the rest), so app builds from before
  tags keep working; `tags` and `languages` hold only the entries the listed packs use.

## Ordering, filters, search and counts

- Rank order (computed at publish): pinned packs (`pin` ascending) → score (descending) → `order` → newest.
- Score: weekly from Analytics. `weeklyStats` reads the existing `pack_added` event's `pack_id` parameter through the
  Analytics Data API (28 days by day, plus all time): `trend = Σ adds_d × 0.5^(age_d / 7)`. A pack live under 14 days
  scores at least `median trend × (1 − age / 14)` so new packs are not buried. Without Analytics configured
  (`GA4_PROPERTY_ID` unset) the job does nothing and ranking falls back to pin → order → newest.
- The app shows readable packs first, keeping rank inside each group: packs with no text, in English, in many
  languages (`multi`) or in the app language are readable; the rest follow.
- Chips: Trending · ♥ Saved · Animated · then categories in `order`, hiding any with no live packs. A pack appears
  under every category its tags name.
- Search: pack names (all languages), category names and keywords (all languages), pack keywords (tags, the words
  of `_tags.json` for its tags and lettering languages in every app language, its `keywords`, and the words of every
  sticker's text): "cat" and "قطة" find Clingy Mango, "arabic" the Arabic packs.
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
  An installed pack refreshes to a new version when its pack page is opened: it is re-installed from the zip the page
  just unpacked, stays added, and the provider serves the new `image_data_version`.
- Home's offline Retry asks `CatalogStore` to try the latest pointer again; "still offline" shows only when that
  retry brings nothing.
- Downloads are plain HTTPS; the Firebase Storage SDK is no longer used.

## Migration

The 14 launch packs move into `library/` in the repo (sources moved from `packs/<id>/src/`, ids unchanged, a
`pack.json` each with names, per-sticker emoji and text, `order` from `design/catalog.json`, `animate: "wiggle"` for
the 7 animated packs) and are uploaded to the bucket once. Categories: sorry-love and sorry-wiggle → `sorry` (also
couples); miles-apart → `distance` (also missyou); gm-gn and sunny-sleepy → `goodnight` (also goodmorning); the rest
keep their category. `packs/<id>/` keeps the built WhatsApp fixtures for the validator test. `prepare-packs.js` and
`upload-pack.js` are replaced by `scripts/library/`.

2026-09-30, tags: the catalog moves to Claude Design's WhatsApp export (120 folders: 91 static packs, 25 animated
variants, 4 After Dark). The animated variants stay packs of their own ("<Name> (Animated)", from the export's
`<pack>-animated` folders): WhatsApp takes a pack all static or all animated, and the static packs are what most people
send every day (the author's decision, after trying the merge). `catalog/packs.json` holds their reviewed metadata: tags and languages from the Stickers page
manifest and the Pack Ideas page (moods, characters, sticker lines), checked sticker by sticker against contact
sheets; the launch packs' per-sticker emoji and text; an initial order from the design's download figures, with an
animated pack after every three static ones. `scripts/library/prepare.js` writes each folder's `pack.json`. The
switch is a clean slate: after the functions that read tags are deployed and `_categories.json` and `_tags.json` are
applied, the author deletes everything in `library/` and `public/`, waits two minutes (the old packs' removal builds
settle), then uploads the 120 folders flat into `library/`. Old records come back as new packs with the next version
number; their `category`, `alsoIn` and `lang` fields are dropped by the build. The repo's `library/` keeps the 14
launch packs as fixtures for the emulators and the screen tour, flat, with the reviewed tags (their `animate:
"wiggle"` stays, so the tour still has animated packs).

## Testing

- Functions: `node --test` unit tests for slugs, path parsing, manifests, readiness, encoding decisions, checks,
  report text, catalog assembly and ranking; an integration test that builds every launch pack from `library/`; the
  build task and the library events against an in-memory bucket, database and task queue on a simulated clock
  (collisions, failures, removals, moves, retries after partial writes, overlapping builds, long uploads).
- Emulators: an end-to-end script uploads a library pack to the Storage emulator and waits for `catalog/meta`, the
  catalog file and the pack outputs, re-uploads it unchanged, replaces two stickers slowly (exactly one complete new
  version must go live) and deletes it (Storage, Firestore, Functions and Tasks emulators).
- App: JVM tests for catalog parsing (search words, language lists), URL templates, language grouping, zip unpacking
  and the Room migration.
- Content: a functions test checks `catalog/packs.json` against `_categories.json` and `_tags.json`: every pack has a
  category tag (After Dark aside), and every tag and language has search words.
- CI: the screen tour seeds its emulators through the real pipeline (`scripts/library/seed-emulators.js`, one file
  and one pack at a time; test-only add counts from `design/catalog.json` keep the frames matching the design), and
  `.github/workflows/functions.yml` runs the functions' tests.

## Setup and operations

One time (needs a login with Owner or Editor on `play-console-f33dd`, Blaze plan):
1. Create the database: `firebase firestore:databases:create stickermaker --location us-central1`.
2. Create the bucket in us-central1 and add it to Firebase (console Storage › Add bucket, or gcloud plus the Firebase
   Storage `addFirebase` API).
3. `firebase deploy --only firestore,storage,functions:stickermaker`.
4. Upload `library/` (`scripts/library/upload.js`): `_categories.json` and `_tags.json` first (the script waits until
   each is applied), then per pack the stickers first and `pack.json` last. Read every `_report.txt` (`--wait` prints
   them). For a Claude Design export: `node scripts/library/prepare.js <exportDir>` writes each folder's
   `pack.json`, then the folders go into `library/` as they are.
5. Optional, for ranking: register `pack_id` as an event-scoped custom dimension in Analytics, give the functions'
   service account Viewer on the property, set `GA4_PROPERTY_ID`.

Guardrails: billing budget alerts at $3, $5 and $7; min instances 0 and capped max instances on every function;
bounded task retries; Artifact Registry cleanup policy (kept by the CLI).

## Costs

Estimated monthly, from the launch packs (average pack 525 KB, cover strip about 40 KB, catalog about 10 KB
gzipped for 200 packs). Assumes new users download 3 packs, returning users 1 a month, 40% of monthly users new,
daily users 25% of monthly, Storage's free 100 GB and 50K downloads per month. A pack download is a pack page opened
(the page shows the stickers from the zip), not only an add; each catalog publish is also downloaded once by every
phone that opens the app afterwards (about 10 KB).

| | 100K installs (~30K monthly) | 100K monthly active | 100K daily active |
|---|---|---|---|
| Before this change | ~$23 | ~$100 | ~$430 |
| This design | ~$0.30 | ~$5 | ~$56 |
| This design with public files on R2 | ~$0.10 | ~$0.40 | ~$1.50 |

## Not in v1

R2 delivery (the switch is designed in), animated stickers on the pack page, App Check, automatic "new pack"
notifications, remote template sets for name packs, pack-language variants grouped as one pack.
