# Love Stickers

Native Android WhatsApp sticker app (`com.piptechnologies.stickermaker`) by PIP Technologies.
Kotlin, Jetpack Compose + Material 3, single `:app` module, Hilt, Room, Coil, and
Firebase (Storage + Cloud Functions + Firestore) for the pack catalog. Packs download on
demand and are handed to WhatsApp through the standard sticker `ContentProvider` contract.

## Build

**CI (the build gate):** every push to `main` (and `claude/**` branches) runs
`.github/workflows/android.yml` — `:app:assembleDebug` + `:app:testDebugUnitTest` on
`ubuntu-latest`, uploading `app-debug.apk` as a workflow artifact. Debug builds only;
there is no release keystore.

**Local:**

```sh
./gradlew :app:assembleDebug
```

Requires JDK 17 and the Android SDK (compileSdk 35).

## Firebase configuration

`app/google-services.json` is **never committed** (gitignored). Until it is provided,
the build writes a clearly-marked placeholder so compilation stays green — the app
builds and runs, but cannot reach Firebase.

- **CI:** add the repo secret `GOOGLE_SERVICES_JSON` (the full JSON for Firebase
  project `play-console-f33dd`); the workflow writes it to `app/google-services.json`
  before building.
- **Local:** drop the real file at `app/google-services.json`.

The Firebase config is finalized locally later; the placeholder keeps everything
building until then.

## Sticker packs

Packs are folders. Everything about the catalog is named `stickermaker` in Firebase project
`play-console-f33dd`: bucket `play-console-f33dd-stickermaker`, Firestore database
`stickermaker`, Cloud Functions codebase `stickermaker` (`functions/`). Design:
`docs/specs/2026-09-29-sticker-catalog-pipeline-design.md`.

```
library/                        (bucket play-console-f33dd-stickermaker, private)
  _categories.json              the 11 categories: names in the 19 app languages, order, icon, hue, emoji, search words
  sorry/                        category = folder name
    Sorry Wiggle/               pack id = folder name as a slug ("sorry-wiggle"), name = the folder name
      01.webp 02.webp …         stickers (PNG, WebP or GIF), in file-name order unless pack.json lists them
      tray.png                  optional; otherwise made from the first sticker
      pack.json                 optional: name, names, alsoIn, lang, tags, order, animate, cover, stickers
      _report.txt               written by the build: live (and which version) or why not
  _staging/…                    folders starting with "_" are ignored
```

- **Add or update a pack:** put its folder under its category in the bucket (Google Cloud console,
  Upload folder, or `node scripts/library/upload.js <libraryDir> --wait`). The functions convert it
  to WhatsApp's rules (ready 512×512 WebP ships byte for byte), make the tray and the Home cover
  strips, zip it, and publish the catalog. A new pack whose `pack.json` lists the stickers and is
  uploaded last goes live about 15–30 s after it; anything else, updates of a live pack included,
  goes live once its folder has been quiet for 30 s, so a half-uploaded update never ships. A pack
  that breaks a rule is not published and the live version stays; `_report.txt` says why.
- **Take a pack down:** delete its folder, or set `hidden: true` on `packs/<id>` in the Firestore
  console. **Pin** one to the top with `pin: 1` (2, 3…). Categories live in `_categories.json`.
- **Ranking:** pinned, then popularity (weekly from the Analytics `pack_added` event, when
  `GA4_PROPERTY_ID` is set in `functions/.env`), then `order`, then newest. The app shows "N adds"
  only from 100 real adds.
- **The app** reads one Firestore document (`catalog/meta`), downloads the gzipped catalog file it
  points to only when it changes, and fetches one `pack.zip` per pack (the pack page and Add share it).

`library/` in this repo holds the 14 launch packs (sources plus `pack.json`); `packs/<id>/` keeps
their built WhatsApp files as fixtures for `StickerPackValidatorTest`. Tools in `scripts/library/`:
`upload.js` (library to bucket), `seed-emulators.js`, `e2e.js`, `contact-sheet.js` (numbered sheet
of a pack's stickers for writing `pack.json`). `npm test --prefix functions` runs the functions'
tests, including a build of every launch pack.

## Firebase setup (one time)

Needs a login with Owner or Editor on `play-console-f33dd` (Blaze plan: Storage and Functions
require it) and the Google Cloud SDK:

```sh
firebase login --reauth
gcloud auth login && gcloud auth application-default login
gcloud storage buckets create gs://play-console-f33dd-stickermaker --location=us-central1 --uniform-bucket-level-access --project play-console-f33dd
```

Add the bucket to Firebase (console › Storage › Add bucket › import it), then:

```sh
firebase deploy --only firestore,storage,functions:stickermaker --project play-console-f33dd
node scripts/library/upload.js library --wait     # the 14 launch packs and the categories
```

`firebase deploy` creates the `stickermaker` database (us-central1) if needed; rules open only
`catalog/meta` (Firestore) and `public/` (Storage). Set Cloud Billing budget alerts at $3, $5 and $7.
Optional, for ranking: register `pack_id` as an event-scoped custom dimension in Analytics, give the
functions' service account Viewer on the property, and set `GA4_PROPERTY_ID` in `functions/.env`.
Moving public files to a CDN later: copy `public/` and set `PUBLIC_URL_TEMPLATE`
(e.g. `https://cdn.example.com/{rawPath}`); the app needs no update.

## Local Firebase emulators

Debug builds can use the Firebase Local Emulator Suite instead of the real
project, which is handy for development and is what the screen tour runs against:

```sh
# Terminal 1: the pipeline locally (Java 21 for the Firestore and Storage emulators)
npm ci --prefix functions && npm ci --prefix scripts
firebase emulators:start --only functions,firestore,storage,tasks --project play-console-f33dd

# Terminal 2: publish library/ through the functions
FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 FIREBASE_STORAGE_EMULATOR_HOST=127.0.0.1:9199 \
  node scripts/library/seed-emulators.js

# Build an APK that talks to them (10.0.2.2 is the host as seen from an Android emulator)
./gradlew :app:installDebug -PfirebaseEmulatorHost=10.0.2.2
```

`firebase emulators:exec --only functions,firestore,storage,tasks "node scripts/library/e2e.js"`
checks the whole pipeline (upload, build, catalog, the app's URLs, re-upload, delete). Only debug
builds read `firebaseEmulatorHost`, and only they allow plain HTTP, to those hosts alone
(`app/src/debug/res/xml/network_security_config.xml`).

## Screen tour and the `screenshots` branch

`.github/workflows/screens.yml` verifies the whole app on a real Android
emulator on every push to a `claude/**` branch:

1. builds the debug APK against the emulators, the instrumented tour
   (`app/src/androidTest/.../tour/ScreenTourTest.kt`) and a WhatsApp test double
   (`testing/whatsapp-stub`, package `com.whatsapp`, CI-only);
2. starts the Firestore, Storage, Functions and Tasks emulators and publishes `library/`
   through the real pipeline (`scripts/library/seed-emulators.js`);
3. walks the app through all 42 frames of `design/Screens.dc.html` (first run
   offline, add states including a real failed download, own packs made with
   ML Kit, My Packs, Settings) and screenshots each one. The stub reads every
   pack back through the app's ContentProvider and checks WhatsApp's pack rules;
4. switches the app through every language (`LocaleTourTest`) and captures
   Home, a pack page, My Packs and Settings in each;
5. publishes the screenshots to the `screenshots` branch, whose README maps
   each one to its design frame and route, with a grid of every language.

## Languages

The app ships English plus the 18 languages of the RecoverMe and Status Saver
apps: Arabic, German, Spanish, Persian, French, Hausa, Hebrew, Hindi,
Indonesian, Italian, Burmese, Pashto, Portuguese (Portugal), Portuguese
(Brazil), Russian, Turkish, Urdu and Simplified Chinese. Arabic, Persian,
Hebrew, Pashto and Urdu lay out right to left.

- All copy lives in `app/src/main/res/values/strings.xml`; each language has a
  `values-xx/strings.xml` (Indonesian is `values-in`, Hebrew `values-iw`,
  Brazilian Portuguese `values-pt-rBR`). Pack names are catalog data and stay
  as published.
- Settings › Language sets Android's per-app locale through AppCompat, which
  also backs Android 13's per-app language setting
  (`res/xml/locales_config.xml`); `AppLanguages` lists the languages.
- Adding or changing a string: edit `values/strings.xml`, then every
  `values-xx/strings.xml`. `StringResourcesTest` fails on a missing key, a
  broken placeholder or missing plural forms, `LocalizedFormattingTest`
  formats every string and plural in every language, and `LocalesConfigTest`
  keeps `locales_config.xml` in step with the Language screen.
- In right-to-left languages a string that would start with Latin text or a
  pack-name placeholder begins with `&#x200F;` (RLM) so its paragraph stays
  right to left.

## Trying it on a phone

1. Download `app-debug.apk` from the latest green CI run and install it
   (enable "install unknown apps" for your browser/file manager).
2. Launch → onboarding → pick themes → Home shows the catalog
   (needs the real `google-services.json` build + a published catalog, see Firebase setup; otherwise the
   offline empty state appears).
3. Add a pack → progress → WhatsApp opens its confirmation sheet → the pack
   appears in WhatsApp's sticker tray (WhatsApp or WA Business must be installed).
4. Create your own pack: Create → photo/video → auto cutout → adjust → name it →
   export → add to WhatsApp the same way.
