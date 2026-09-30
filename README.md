# Love Stickers

Native Android WhatsApp sticker app (`com.piptechnologies.stickermaker`) by PIP Technologies.
Kotlin, Jetpack Compose + Material 3, single `:app` module, Hilt, Room, Coil, and
Firebase (Storage + Cloud Functions + Firestore) for the pack catalog, with Firebase Analytics and
Crashlytics like the other PIP apps. Packs download on demand and are handed to WhatsApp through the
standard sticker `ContentProvider` contract.

## Build

**CI (the build gate):** every push to `main` (and `claude/**` branches) runs
`.github/workflows/android.yml` — `:app:assembleDebug` + `:app:testDebugUnitTest` on
`ubuntu-latest`, uploading `app-debug.apk` as a workflow artifact. Debug builds only;
there is no release keystore. It needs the repo secrets `CRASH_REPORTING_ACCESS_CODE` and
`CRASH_REPORTING_SECRET_CODE` (the crash-reporting SDK project's pair, below).

**Local:** the crash-reporting SDK project's access/secret pair stays out of git, in
`local.properties` (gitignored). `settings.gradle.kts` uses it for the SDK's Maven repository
and `app/build.gradle.kts` hands it to the app:

```properties
crashReporting.accessCode=…
crashReporting.secretCode=…
```

```sh
./gradlew :app:assembleDebug
```

Requires JDK 17 and the Android SDK (compileSdk 35).

## Firebase configuration

The app belongs to the PIP Technologies Firebase project `play-console-f33dd`, next to
the other PIP apps (All Recovery, PDF Reader, Pedometer), as the Android app
"Love Stickers" (`com.piptechnologies.stickermaker`). One time, register it and download
its config (needs a Firebase login with access to the project):

```sh
firebase apps:create ANDROID "Love Stickers" --package-name com.piptechnologies.stickermaker --project play-console-f33dd
firebase apps:sdkconfig ANDROID <app id printed above> --project play-console-f33dd --out app/google-services.json
```

`app/google-services.json` is **never committed** (gitignored). Until it is provided,
the build writes a clearly-marked placeholder so compilation stays green — the app
builds and runs, but cannot reach Firebase (catalog, Analytics, Crashlytics).

- **CI:** add the repo secret `GOOGLE_SERVICES_JSON` (the full JSON above); the workflow
  writes it to `app/google-services.json` before building.
- **Local:** drop the real file at `app/google-services.json`.

## Configuration (`config.xml`)

`app/src/main/res/values/config.xml` is the one place for the app's external values, laid
out like the other PIP apps: the support address (`support@piptechnologies.co`, for Contact
us, the feedback mail and every pack's publisher email in WhatsApp), the privacy policy URL,
the Play developer page behind Settings › More apps, and the app's Firestore database. The
crash-reporting project codes are not here: they stay out of git (see Build). `ConfigXmlTest`
fails if code reads a `config_*` key the file lacks, if the support address is not the
publisher's, if `firebase.json` or the catalog functions (`functions/src/config.js`) target another
database, if a tracked build file or config.xml holds the crash-reporting codes, or if a pack
(`packs/*/pack.json`, `design/catalog.json`) names another address or privacy policy.

## Crash reporting and analytics

- **Crash screen:** crash reporting is armed first in `LoveStickersApp.onCreate`, as in the
  other PIP apps. A crash shows the app's own crash screen (`crash_title` / `crash_message` in
  every language) and is forwarded to Crashlytics, best-effort. Try it on a debug build, which
  carries a crash-on-start test activity (only adb can start it):
  `adb shell am start -n com.piptechnologies.stickermaker/.debug.CrashTestActivity`.
- **Crashlytics** also gets non-fatals where the app swallows a failure (pack download,
  pack export, ML Kit cutout, and the Custom Stickers flow's art, preview, build, save, finish
  and add intent, as the exception type and stack only, never names): `CrashReporting` in
  `core/telemetry/Telemetry.kt`.
- **Analytics** (`AppAnalytics`, same file): a `screen_view` per navigation destination, plus
  `onboarding_complete` (how first run ended: `added`, `saved` or `skipped`),
  `name_pack_built`, `pack_add_started`, `pack_download_failed`, `pack_added`,
  `pack_add_cancelled`, `pack_created`, `share`, `app_rating` and `language_changed`. Only
  coarse values go out: catalog pack ids, counts, enum-like strings; a pack the user made is
  just `own`, and names typed in the Custom Stickers flow never do.
- Builds made with `-PfirebaseEmulatorHost` (local development, the CI screen tour) switch
  Analytics and Crashlytics collection off, so their sessions stay out of the production data.

## Rating prompt

Like the other apps (`feature/rating/`): a pack WhatsApp confirmed arms the rating sheet, and it
rises at the next natural pause, back on Home, a pack page, Saved or My Packs, about 1.5 s after
the "Added to WhatsApp" toast. The first pack WhatsApp confirms earns it, on install day as on
any other; at most once per app launch. "Maybe later" re-asks after 2, then 3, then 4 more added
packs; a swipe-away or Cancel after 4, then 6; three of those stop it for good, and a rating or
a feedback note ends it. Settings › Rate us bypasses all of this. `RatingEligibilityTest` pins
the rules.

## Notifications

Settings › Notifications is the switch for alerts and updates. Send them from the Firebase
console (Messaging › New campaign › Notifications) to the Love Stickers app; target languages or
countries with the campaign's user-segment filters, one campaign per language. Firebase Cloud
Messaging stays off until the switch and Android both allow notifications (on Android 13+ the
switch asks for the permission first), and turning the switch off deletes the device's token.
Everything lands in one channel, "Alerts and updates".

## Sticker packs

Packs are folders. Everything about the catalog is named `stickermaker` in Firebase project
`play-console-f33dd`: bucket `play-console-f33dd-stickermaker`, Firestore database
`stickermaker`, Cloud Functions codebase `stickermaker` (`functions/`). Design:
`docs/specs/2026-09-29-sticker-catalog-pipeline-design.md`.

```
library/                        (bucket play-console-f33dd-stickermaker, private)
  _categories.json              the app's 14 categories (Home chips): names in the 19 app languages, order,
                                icon, hue, emoji, search words, and the tags that put a pack in each
  _tags.json                    what people type for each tag and lettering language, in every app language
  clingy-mango/                 one folder per pack: pack id = folder name as a slug, name = the folder name
    01.webp 02.webp …           stickers (PNG, WebP or GIF), in file-name order unless pack.json lists them
    tray.png                    optional; otherwise made from the first sticker
    pack.json                   name, lang, tags, emojis, keywords, order, adult, animate, cover, stickers
    _report.txt                 written by the build: live (which version, which categories) or why not
  _staging/…                    folders starting with "_" are ignored
```

- **Tags decide the categories.** A pack shows under every category one of its tags names
  (`"tags": ["cute", "funny", "cat", "mango"]` puts Clingy Mango under Cute and Funny); its first
  category tag is its main one. Search matches the pack's names, its tags in every app language
  (from `_tags.json`: "قطة" finds the cat packs), its lettering languages ("arabic"), its
  `keywords` and its stickers' text. A category is a filter: adding a chip is one entry in
  `_categories.json`, no folder moves.
- **Languages:** `lang` lists the lettering's languages (`"ar"`, `["ar", "hi", "es"]`), `"none"`
  for no text, `"multi"` for one sticker per language. The app shows packs readable to the user
  first: no text, English, many languages, or the app language.
- **After Dark:** `"adult": true` parks a pack. It is never built for the Google Play catalog and
  nothing of it reaches `public/`.
- **Add or update packs:** upload the pack folders into `library/` (Google Cloud console: open the
  bucket, `library/`, drag the folders in; or `node scripts/library/upload.js <folder> --wait`).
  `node scripts/library/prepare.js <exportDir>` first writes each folder's `pack.json` from
  `catalog/packs.json`, the reviewed metadata of every pack. The functions convert packs to
  WhatsApp's rules (ready 512×512 WebP ships byte for byte), make the tray and the Home cover
  strips, zip each pack, and publish the catalog. A new pack whose `pack.json` lists the stickers
  and is uploaded last goes live about 15–30 s after it; anything else, updates of a live pack
  included, goes live once its folder has been quiet for 30 s, so a half-uploaded update never
  ships. A pack that breaks a rule is not published and the live version stays; `_report.txt` says
  why.
- **Take a pack down:** delete its folder, or set `hidden: true` on `packs/<id>` in the Firestore
  console. **Pin** one to the top with `pin: 1` (2, 3…). A pack that comes back after its removal
  is a new pack with the next version number.
- **public/ repairs itself:** a build that finds its pack's published files missing writes them
  again, and a publish that finds the live catalog file missing publishes a new one. Deleting
  `public/` is safe; the next upload restores what the catalog needs.
- **Ranking:** pinned, then popularity (weekly from the Analytics `pack_added` event, when
  `GA4_PROPERTY_ID` is set in `functions/.env`), then `order`, then newest. The app shows "N adds"
  only from 100 real adds.
- **The app** reads one Firestore document (`catalog/meta`), downloads the gzipped catalog file it
  points to only when it changes, and fetches one `pack.zip` per pack (the pack page and Add share it).

`library/` in this repo holds the 14 launch packs the emulators and the screen tour use (sources
plus `pack.json`) and the real `_categories.json` and `_tags.json`; `packs/<id>/` keeps their built
WhatsApp files as fixtures for `StickerPackValidatorTest`. Tools in `scripts/library/`:
`prepare.js` (writes `pack.json` into an export), `upload.js` (library to bucket),
`seed-emulators.js`, `e2e.js`, `contact-sheet.js` (numbered sheet of a pack's stickers for writing
`pack.json`). `npm test --prefix functions` runs the functions' tests, including a build of every
launch pack and a check of `catalog/packs.json` against `_categories.json` and `_tags.json`.

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
   offline through the Custom Stickers name steps, add states including a real
   failed download, own packs made with ML Kit, My Packs, Settings) and
   screenshots each one. The stub reads every pack back through the app's
   ContentProvider and checks WhatsApp's pack rules;
4. switches the app through every language (`LocaleTourTest`) and captures
   Home, a pack page, My Packs and Settings in each;
5. publishes the screenshots to the `screenshots` branch, whose README maps
   each one to its design frame and route, with a grid of every language.

## Custom stickers

First run ends with the Custom Stickers flow (`feature/namepack/`): pick a character, type your
name (optional) and your love's name, and the app letters 12 stickers on the phone, in the app
language, then adds them to WhatsApp as your own pack (`own-np-…`, listed under My Packs › Made
by you). Names stay on the phone: in the stickers' pixels, the pack name and Room.

- Art, templates and phrases (19 languages): `app/src/main/assets/templates/`.
- Lettering fonts, trimmed to weight 800: Baloo 2, Baloo Bhaijaan 2 and Rubik
  (`app/src/main/res/font/lettering_*.ttf`), under the SIL Open Font License 1.1
  (`app/src/main/assets/licenses/`). Letters they lack (Chinese, Burmese, some Pashto and
  Hausa letters) fall back to the system font.
- `NamePackSheetsTest` writes contact sheets to `app/build/namepack-sheets/` for checking the
  lettering by eye: every character in both tones for English and Arabic, and Mango in both
  tones for 11 more languages, each with sample names in its own script. It is opt-in:
  `NAMEPACK_SHEETS=1 ./gradlew :app:testDebugUnitTest --tests '*NamePackSheetsTest*' --rerun`.

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
2. Launch → onboarding → your name → their name → 12 lettered stickers → Add to WhatsApp
   (or Not now) → Home shows the catalog (needs the real `google-services.json` build + a published
   catalog, see Firebase setup; otherwise the offline empty state appears).
3. Add a pack → progress → WhatsApp opens its confirmation sheet → the pack
   appears in WhatsApp's sticker tray (WhatsApp or WA Business must be installed).
4. Create your own pack: Create → photo/video → auto cutout → adjust → name it →
   export → add to WhatsApp the same way.
