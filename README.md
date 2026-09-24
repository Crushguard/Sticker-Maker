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
