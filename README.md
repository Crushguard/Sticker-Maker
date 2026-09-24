# Love Stickers

Native Android WhatsApp sticker app (`com.piptechnologies.stickermaker`) by PIP Technologies.
Kotlin, Jetpack Compose + Material 3, single `:app` module, Hilt, Room, Coil, and
Firebase (Firestore + Storage) for the pack catalog. Packs download on demand and are
handed to WhatsApp through the standard sticker `ContentProvider` contract.

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

The 14 launch packs are committed under `packs/<id>/` — WhatsApp-ready files
(`01..NN.webp` 512×512, static ≤100 KB / animated ≤500 KB, `tray.png` 96×96,
`thumbs/` 160×160 previews) plus a `pack.json` contents entry, all generated from
the `src/` originals by:

```sh
cd scripts && npm install
node prepare-packs.js            # rebuild + verify every pack (or --pack <id>)
```

The script re-encodes statics, synthesizes the 4-frame animations for the seven
animated packs, and fails non-zero if any WhatsApp size/dimension guard breaks —
CI-friendly by construction. `app/src/test` validates the same fixtures on every build.

## Firebase ops (one-time seeding)

Requires a service account key for `play-console-f33dd`
(Project settings → Service accounts → Generate new private key).

```sh
# 1. Deploy security rules (public read, no client writes):
firebase deploy --only firestore:rules,storage --project play-console-f33dd

# 2. Upload all packs to Storage + Firestore (idempotent, MD5-skips unchanged files):
cd scripts && npm install
GOOGLE_APPLICATION_CREDENTIALS=/path/to/service-account.json \
  node upload-pack.js --all            # or --pack gm-gn, add --dry-run to preview
```

Storage layout `packs/{id}/{tray.png,NN.webp,thumbs/NN.webp}`; Firestore
collections `packs` (name, publisher, category, animated, order, downloads, hue,
stickerCount, trayPath, stickerPaths, thumbPaths, emojis) and `categories`
(name, icon, hue, order). Pass `--bucket <name>` if the project's default bucket
is not `play-console-f33dd.firebasestorage.app`.

## Local Firebase emulators

Debug builds can use the Firebase Local Emulator Suite instead of the real
project, which is handy for development and is what the screen tour runs against:

```sh
# Terminal 1: Firestore + Storage emulators with this repo's rules
firebase emulators:start --only firestore,storage --project play-console-f33dd

# Terminal 2: seed them (no credentials needed for the emulators)
FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 FIREBASE_STORAGE_EMULATOR_HOST=127.0.0.1:9199 \
  node scripts/upload-pack.js --all --bucket play-console-f33dd.appspot.com

# Build an APK that talks to them (10.0.2.2 is the host as seen from an Android emulator)
./gradlew :app:installDebug -PfirebaseEmulatorHost=10.0.2.2
```

Use the `storage_bucket` from your `google-services.json` for `--bucket` (the
placeholder file uses `play-console-f33dd.appspot.com`). Only debug builds read
`firebaseEmulatorHost`, and only they allow plain HTTP, to those hosts alone
(`app/src/debug/res/xml/network_security_config.xml`).

## Screen tour and the `screenshots` branch

`.github/workflows/screens.yml` verifies the whole app on a real Android
emulator on every push to a `claude/**` branch:

1. builds the debug APK against the emulators, the instrumented tour
   (`app/src/androidTest/.../tour/ScreenTourTest.kt`) and a WhatsApp test double
   (`testing/whatsapp-stub`, package `com.whatsapp`, CI-only);
2. starts the Firestore and Storage emulators and seeds them with `upload-pack.js`;
3. walks the app through all 42 frames of `design/Screens.dc.html` (first run
   offline, add states including a real failed download, own packs made with
   ML Kit, My Packs, Settings) and screenshots each one. The stub reads every
   pack back through the app's ContentProvider and checks WhatsApp's pack rules;
4. publishes the screenshots to the `screenshots` branch, whose README maps
   each one to its design frame and route.

## Trying it on a phone

1. Download `app-debug.apk` from the latest green CI run and install it
   (enable "install unknown apps" for your browser/file manager).
2. Launch → onboarding → pick themes → Home shows the catalog
   (needs the real `google-services.json` build + seeded Firebase; otherwise the
   offline empty state appears).
3. Add a pack → progress → WhatsApp opens its confirmation sheet → the pack
   appears in WhatsApp's sticker tray (WhatsApp or WA Business must be installed).
4. Create your own pack: Create → photo/video → auto cutout → adjust → name it →
   export → add to WhatsApp the same way.
