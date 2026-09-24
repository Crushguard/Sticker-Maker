# Love Stickers — Implementation Plan

**Goal:** Native Android WhatsApp sticker app "Love Stickers" (`com.piptechnologies.stickermaker`), built exactly to the claude.ai/design project "Sticker Maker", green CI on `main` producing an installable `app-debug.apk`, packs served from Firebase project `play-console-f33dd`.

**Verification loop:** push to `main` → GitHub Actions (`assembleDebug` + validator unit test) → read result → fix → repeat until green. No other gates.

---

## 1. Inputs (resolved)

| Input | Resolution |
|---|---|
| Repo | `crushguard/sticker-maker`, empty. First push creates `main` (becomes default). Work may use short-lived branches merged to `main` (user instruction 2026-09-24). |
| Design | claude.ai/design project `5a651902-b0fc-49e9-8171-4dd7969da732` — `Design System.dc.html`, `Screens.dc.html` (13 screens / 42 frames), `Prototype.dc.html` (full interactive logic + copy), `assets/stickers/*.webp` (real 512×512 WebP art). Reference copies committed under `design/`. |
| google-services.json | **No Firebase MCP exists in this session** (verified) — Claude cannot fetch it. CI reads repo secret `GOOGLE_SERVICES_JSON`; until the user adds it, CI writes a clearly-marked placeholder so builds stay green, and the APK connects to Firebase only once the real secret exists. Local dev: `app/google-services.json` (gitignored). |
| Sample packs | Extracted from the design project via DesignSync (base64) → `packs/` in repo. 14 packs, 131 sticker files. |
| Categories | From the design's `themeList` (8): Couples, Cute, Funny, Anime, Romantic, Flirty, Good night, Long distance. All 8 seeded to Firestore `categories`; 6 have packs at launch. |
| Screens | All 13 in the design, including Create flow, Search, Share — the brief's "unless the design shows it" clause puts them in scope. |

## 2. Locked decisions (from brief) 

Kotlin, Jetpack Compose + Material 3, single Gradle module `:app`, MVVM (one ViewModel per screen), Hilt, Coil, Room, Firebase Firestore + Storage KTX, Coroutines/Flow, minSdk 24, targetSdk 35, version catalog. CI on `ubuntu-latest` with preinstalled SDK. WhatsApp contract ported from `WhatsApp/stickers` sample (ContentProvider, StickerPackValidator, WhitelistCheck). Firestore schema and Storage layout as specified in the brief. No `firebase deploy`, no writes to Firebase, no release keystore, no committed google-services.json, no PRs to Play/main-protection changes.

## 3. Judgment calls (flagged for approval)

1. **"Animated" packs are synthesized.** The design marks 6 packs ANIMATED but ships static frames (motion lines drawn in the art). Shipping them as `animated=true` with static WebPs would make WhatsApp reject the add. Resolution: `scripts/prepare-packs.js` generates real animated WebPs (4-frame gentle wiggle from each static sticker, ~600 ms loop, ≤500 KB) via `node-webpmux`; committed under `packs/` so CI validates them deterministically. Animated chip/badge then work end-to-end.
2. **Validator test runs on the JVM.** The sample validator uses Fresco for image checks; Fresco is replaced by a small pure-Kotlin WebP header parser (`WebpInfo`) used by both the app validator and the CI test (Robolectric for the `android.*` bits). Every other check is ported verbatim. This is the "one validator test" gate: it validates all 14 seeded packs from `packs/`.
3. **Fonts bundled** (Hanken Grotesk, JetBrains Mono TTFs in `res/font`) — deterministic offline rendering; no Play-Services font provider.
4. **Lucide icons vendored** as Compose `ImageVector`s for the ~28 glyphs the design uses (fallback: closest `material-icons-extended`).
5. **Create flow tech:** ML Kit selfie segmentation (bundled model, on-device, free) for auto cutout; Compose Canvas mask editor (Auto/Brush/Erase/Text/Zoom/undo/white outline); export via `Bitmap.compress(WEBP)` with quality loop ≤100 KB; video → frames via `MediaMetadataRetriever` → Kotlin `AnimatedWebpMuxer` (RIFF/ANIM/ANMF packing of per-frame WebP) ≤500 KB. Nothing uploads anywhere.
6. **Rating flow** is the in-app sheet from the design (stars → Play Store listing on 5★, feedback text on 1–4★ via mailto), not the Play In-App Review API (design shows its own sheet).
7. **Contact us** submits via `mailto:` intent with device info appended (no backend exists).
8. **"More apps (AD)"** row links to the Play publisher page. "Privacy policy" opens a placeholder URL constant (single place to change).

## 4. Architecture

```
app/src/main/kotlin/com/piptechnologies/stickermaker/
  LoveStickersApp.kt            // @HiltAndroidApp
  MainActivity.kt               // single activity, edge-to-edge, NavHost
  navigation/AppNavHost.kt      // routes: splash, onboarding, customize(edit), home,
                                // detail/{id}, create, editor, packDetails, myPacks,
                                // saved, settings, language, contact
  core/design/                  // Theme.kt (tokens), Type.kt, Icons.kt (Lucide vectors),
                                // components/: PackCard, AddPill, AddBar, Chips, Segmented,
                                // AppSwitch, Sheets, ConfirmSheet, DarkToast, EmptyState,
                                // TopBars, BottomNav, ThemeTile, StickerTile, HonestyLine
  core/model/                   // StickerPack, Sticker, Category, AddState, PackSource
  core/data/
    firebase/CatalogDataSource.kt      // Firestore packs+categories, Storage URL resolve
    db/                                // Room: InstalledPackEntity, InstalledStickerEntity,
                                       // OwnPackEntity(+stickers), dao, LoveDb
    prefs/PrefsRepository.kt           // DataStore: onboarded, themes, hearts, alerts, language
    download/PackDownloader.kt         // Storage→filesDir/packs/{id}, Flow<progress>
    repo/CatalogRepository.kt, MyPacksRepository.kt
  whatsapp/                     // ported: StickerContentProvider, StickerPackValidator,
                                // WhitelistCheck, AddStickerPackFlow (ADD_PACK intent),
                                // WebpInfo.kt, AnimatedWebpMuxer.kt
  feature/<screen>/             // <Screen>Screen.kt + <Screen>ViewModel.kt per screen
app/src/test/                   // StickerPackValidatorTest (validates packs/ fixtures)
packs/<packId>/                 // pack.json, tray.webp, 01..NN.webp, thumbs/01..NN.webp
scripts/upload-pack.js          // Admin SDK: Storage upload + Firestore doc (schema locked)
scripts/prepare-packs.js        // one-time: tray/thumb/animated generation (committed output)
firestore.rules  storage.rules  // public read, no client write
design/                         // committed reference: 3 dc.html files + TOKENS.md
.github/workflows/android.yml   // push→build+test+upload app-debug.apk artifact
```

**Data flow:** Splash loads Firestore `categories`+`packs` (offline persistence on) → Home renders cards streaming `thumbPaths` via Coil from Storage URLs. Add = download tray+stickers to `filesDir/packs/{id}` → insert Room → ContentProvider serves → `com.whatsapp.intent.action.ENABLE_STICKER_PACK` via ActivityResult → state machine idle→downloading(%)→sent→added / failed(retry). My Packs = Room (installed + own) + WhitelistCheck refresh. Hearts/themes/flags = DataStore. Own packs from Create flow live in Room+filesDir and go through the identical add pipeline. `<queries>` declares com.whatsapp + com.whatsapp.w4b.

**Design tokens (from Design System.dc.html):** canvas #FAFBFC, surface #FFF, subtle #F3F5F8, ink #1E2128, ink2 #626873, border #E7EAEF, primary rose #C23359 (tint #FFEEF0 / line #FFCED5), added green #2E9E6B, attention amber #B8792A (tint #FEFBF5 / line #F0DFC4), destructive #C4553D, gold #E0A64B, muted #8B929D/#A2A9B4; radii 10·14·20·pill; buttons 52/14, 48/13, pill 36; nav 68 + raised create 52/16 at −18; Hanken Grotesk display 26/800 · title 16/700 · body 15/400, JetBrains Mono meta 11.5; sticker tints 8×oklch(.93 .045). Full table in `design/TOKENS.md`.

## 5. Execution phases (subagent split)

Orchestrator (this session) owns: git, CI reads, shared files (settings.gradle, libs.versions.toml, AppNavHost, DI wiring), merges, and the fix loop. Agents get disjoint file sets; every agent prompt points at `design/` reference files, never pasted HTML.

- **P0 Scaffold** (1 agent, blocking): Gradle 8.10 wrapper, AGP 8.7, Kotlin 2.0, Compose BOM, catalog with all deps, manifest, Theme/Type/Icons, empty NavHost, google-services (secret/placeholder logic), CI workflow. Exit: **CI run #1 green** with shell APK artifact.
- **P1 Assets** (3 fetch agents in parallel + 1 prep step): DesignSync-fetch 131 catalog WebPs + 2 onboarding images → `packs/` layout; run prepare-packs.js (tray 96×96, thumbs 160×160, 6 animated packs, size guards); write pack.json per pack (id, name, publisher "PIP Technologies", category, animated, order = catalog order). No Gradle dependency — runs parallel to P0.
- **P2 Platform wave** (3 agents in parallel, after P0):
  - *whatsapp*: port sample files + WebpInfo + AnimatedWebpMuxer + Room + add-flow + validator test wired to `packs/`. Exit: `testDebugUnitTest` green in CI.
  - *data*: Firebase datasources, repos, DataStore, PackDownloader, Hilt modules (own files).
  - *design-system*: every component in §4 with previews matching Screens.dc.html specs.
- **P3 Screens wave** (4 agents in parallel, after P2):
  - *flow-a*: Splash, Onboarding, Customization(+edit), Home (chips/search/offline).
  - *flow-b*: Detail + full add-state machine, My Packs, Saved, card menus, confirms.
  - *create*: Import→Editor→PackDetails, segmentation, export, own-pack save.
  - *settings*: Settings, Language (AppCompat per-app locales), Contact, Rate sheet, notif permission, clear-downloads.
- **P4 Firebase ops** (1 agent, after P1): upload-pack.js (idempotent, `--pack <id>|--all`, uses Admin SDK w/ GOOGLE_APPLICATION_CREDENTIALS), firestore.rules, storage.rules, README (deploy rules, upload packs, install APK, phone checklist).
- **P5 Integration & green loop** (orchestrator + fix agents as needed): NavHost wiring, DI assembly, design-parity pass against the 42 frames, CI to green, final artifact link.

After each phase lands: `✅ [step] — [files] — [CI status]` progress line.

## 6. CI workflow (android.yml)

on: push(main + work branches), workflow_dispatch. Steps: checkout → setup-java 17 (temurin) → setup-gradle (caching) → write `app/google-services.json` from `secrets.GOOGLE_SERVICES_JSON` else placeholder (with loud warning) → `./gradlew :app:assembleDebug :app:testDebugUnitTest --stacktrace` → upload artifacts `app-debug.apk` + test reports. Concurrency: cancel superseded runs on same ref.

## 7. Risks

| Risk | Mitigation |
|---|---|
| Secret absent → APK can't reach Firebase | Placeholder keeps CI green; README + final report tell user to add secret and re-run; app shows offline empty-state gracefully. |
| node-webpmux/sharp unavailable locally | Verified at P1 start; fallback pure-JS muxer already planned; worst case animated packs seed as static (flagged in report). |
| Proxy blocks a maven/google host in CI | CI runs on GitHub's network, not the proxy — unaffected. |
| WhatsApp/stickers clone blocked locally | Fallback `add_repo` read-access for WhatsApp/stickers. |
| ML Kit + minSdk 24 desugaring quirks | Standard coreLibraryDesugaring enabled from P0. |
| Robolectric first-run download in CI | Pinned version, gradle cache action. |

## 8. Definition of done

CI on `main` green: `assembleDebug` + `StickerPackValidatorTest` (all 14 packs). `app-debug.apk` downloadable from the run. All 13 design screens reachable; add flow has idle/downloading/sent/added(+failed retry). `scripts/upload-pack.js`, `firestore.rules`, `storage.rules`, README committed. Final report: CI run link, artifact link, phone checklist, rule-deploy + pack-upload commands.
