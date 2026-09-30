# Custom stickers (name pack): design

Date: 2026-09-29. Status: design delivered by Claude Design; implementation decisions recorded here.

Visual reference, screen by screen with every measurement: `docs/specs/2026-09-29-custom-stickers-screens.md`
(derived from `design/Prototype.dc.html`, `design/Custom Stickers.dc.html` and `design/Screens.dc.html`).
Where this file and the screens file disagree, this file wins; it records the decisions the design left open.

## Goal

A personal first-run moment. The user picks a character, types their own name (optional) and their love's name,
picks who the pack is for, and the app letters a 12-sticker WhatsApp pack with those names in the app's language.
The user adds it to WhatsApp and lands on Home. No AI at runtime, no per-user cost, works offline.

## Decisions

- The flow replaces the theme picker as the first-run step after the intro. **The theme picker is removed
  entirely**: the first-run step, Settings › Edit themes, the `selected_themes` preference and Home's dependency on
  it. Home shows every category (Trending, ♥ Saved · n, Animated, then every category chip).
- The sticker language is the app language. A language chip on the intro and on both name steps opens a sheet with
  the app's 19 languages (the shipped `AppLanguages` list, in its order, in the design's sheet style; no "System"
  row). Picking one switches the whole app, as Settings › Language does.
- Characters: Mango, Pinky, Bunny, Capy ("Who says it?"), Mango preselected. Each has 12 text-free template poses.
- Relations, same order in every language: Girlfriend, Boyfriend, Wife, Husband, Crush, Partner, Mom, Friend.
  Default Girlfriend. Mom and Friend are "family": Flirty is hidden and family phrase overrides apply.
- Tones: Sweet (default) and Flirty. Nothing explicit.
- Their name is required; yours is optional.
- The pack is saved to My Packs when the user taps Add, taps "Not now", or when WhatsApp is missing. (The prototype
  discards it on "Not now"; with no designed way back into the flow, keeping it is the safer default.)
- The onboarded flag is written when the flow ends (Home reached by Skip, "Not now" or a confirmed add), not when
  the intro ends, so a kill mid-flow restarts the intro.
- A confirmed add during first run arms the rating prompt like any other add (first confirmed pack earns it).
- Every pack sends the app's Play listing as `android_play_store_link` (done 2026-09-29).

## Flow

1. **Intro** (existing two slides). Top strip: language chip at the start, Skip at the end, on both slides. Skip and
   "Get started" open the name flow. The intro stays under it in the back stack, so Back on the first name step
   returns to the intro.
2. **Your name** — step bar 1/2, "What's your name?", "Who says it?" with 4 character tiles, the name field (optional),
   a 210 dp live preview of the character's `name_only` sticker (letters the placeholder "Your name" when empty),
   "Continue". Skip clears your name and goes on. Emoji in the name disables Continue.
3. **Their name** — step bar 2/2, "Who's your love?", the field, 8 relation chips, a 210 dp live preview of
   `love_you` (uses the localized sample name "Sara" while empty), "Make our stickers". Tapping it with no name shows
   "Add a name to make the pack." Skip goes to Home with no pack (onboarding done). Back returns to Your name.
4. **Building** — the character's waiting art (Mango's still for now, gently bobbing; animated loops per character
   when Claude Design exports them at ≤ 240 KB each), a 200 × 4 bar bound to the 12 real renders (never faster than
   1.7 s, hold 420 ms at 100 %), "Lettering 12 stickers…", "Made on this phone. Nothing is uploaded." Back cancels
   and returns to Their name. A render failure returns to Their name with a toast.
5. **Reveal** — header with the 28 dp tray heart and the pack name; meta "12 stickers · girlfriend" and the tone
   chips (only Sweet for Mom/Friend); the character row (re-casts all 12); a 3-column grid of the 12 stickers
   (entry pop animation); pinned footer with the Add bar and "Not now". Tone and character switches re-letter with
   a 160 ms crossfade. Back returns to Their name (the render cache is kept).
6. **Add** saves the pack, then opens WhatsApp's add dialog. Confirmed: Home, flow cleared, "Added to WhatsApp"
   toast (the app's usual dark toast with a check, at its usual length; the design shows it for 2.4 s). Cancelled:
   stay on the reveal. WhatsApp missing: pack saved, the existing "WhatsApp isn't
   installed" sheet. "Not now": pack saved, Home, no toast.

Reduced motion (animator duration scale 0): still waiting art and no tile pop; Compose plays every other animation
instantly at that scale, so tiles and steps simply appear.

Reopening the flow later ("Create › Name stickers" in the design) is not designed yet. The route needs no flag for
it: the flow writes the onboarded flag, and logs `onboarding_complete`, only when the flag is not set yet, so a later
entry point can open the same route.

## Content

### Names

- Trim, collapse runs of spaces. At most 14 grapheme clusters (BreakIterator); input beyond 14 is refused and the
  note "That's the limit. Nicknames work too." shows (counter turns amber at 14).
- Allowed: letters, combining marks, digits, space, apostrophes (' ’), hyphen, dot, ZWNJ. Anything else (emoji,
  pictographs, symbols) shows "Emoji can't be lettered. Letters only." and blocks the step's button.
- Names are lettered verbatim: never transliterated, never re-cased.

### Phrases

`app/src/main/assets/templates/phrases.json`: `{lang: {sweet: {slot: text}, flirty: {...}, family: {...}}}` for all
19 app languages (Claude Design wrote en, ar, fr, hi; the other 15 are ours, pending native review). `{n}` is their
name, `{a}` yours. Rule: no phrase reveals the sender's gender, and addressee forms are neutral in writing (the
design's fr family `be_mine` and hi `be_mine` were gendered and are replaced with neutral lines).

Resolution for (lang, tone, relation, slot):
- Family relation: `lang.family[slot]` → `lang.sweet[slot]` → `en.sweet[slot]` (tone is forced to Sweet).
- Otherwise: `lang.<tone>[slot]` → `lang.sweet[slot]` → `en.<tone>[slot]` → `en.sweet[slot]`.
- `our_names` is `"{a} ❤ {n}"`; with no own name it letters `{n}` alone.

Slots, in template and grid order: love_you, miss_you, good_morning, good_night, kiss, hug, sorry, be_mine, for_you,
call_me, our_names, name_only.

## Templates and assets

Bundled from Claude Design, file names unchanged, under `app/src/main/assets/templates/`:
- `{mango,pinky,bunny,capy}-{1..12}.webp` — 512 × 512 RGBA, single frame, text-free, no outline (15–40 KB).
- `{mango,pinky,bunny,capy}.json` — per sticker: `file, slot, zone{cx,cy,w,h,rotate}, emojis[2], ink, stroke,
  strokeColor?, surface`.
- `phrases.json` (above), `mango-wait.webp` (still). Later: `live/{char}-wait.webp`, `{char}-wait.webp`.

Fonts (SIL OFL 1.1, from github.com/google/fonts, trimmed to the single 800 instance, no Reserved Font Names), in
`app/src/main/res/font/`, licences in `app/src/main/assets/licenses/`:
- `lettering_baloo2.ttf` (Baloo 2 ExtraBold, 422 KB): Latin incl. Turkish/Vietnamese, Devanagari.
- `lettering_baloo_bhaijaan2.ttf` (Baloo Bhaijaan 2 ExtraBold, 182 KB): Arabic, Persian, Urdu.
- `lettering_rubik.ttf` (Rubik ExtraBold, 212 KB): Hebrew, Cyrillic.

## Rendering (on device, `Dispatchers.Default`)

- **Font by string**: any Arabic-script char (U+0600–06FF, 0750–077F, 08A0–08FF, FB50–FDFF, FE70–FEFF) →
  Bhaijaan, unless the string holds a Pashto letter Bhaijaan lacks (ټ ځ څ ډ ړ ږ ښ ګ ڼ ۍ ې) → the system bold typeface for
  the whole string. Hebrew (U+0590–05FF) or Cyrillic (U+0400–04FF) → Rubik. Anything else → Baloo 2 (Chinese,
  Burmese and Hausa hook letters fall back per glyph to system fonts).
- **Direction**: RTL paragraph when the app language is right-to-left (ar, fa, he, ps, ur); otherwise the paragraph
  follows the string's first strong character, and bidi does the rest. (The prototype's rule, RTL whenever the
  string contains Arabic or Hebrew, would flip an English phrase around an Arabic name.)
- **Fit** (512 space): start at `floor(min(zone.h × 0.82, 128))`, step −2 down to 20 (20 itself is always
  tried, also when an odd start steps from 21 to 19). Line height 1.08 × size,
  tracking −0.01 em. At each size: one line if it fits `zone.w`; else, if the text has a space and
  `2 × lineHeight ≤ zone.h`, the split on a space whose longer line is narrowest, if it fits. Never three lines.
  At the floor: size 20, one line, overflow allowed, nothing clipped.
- **Draw**: block centred on (cx, cy), each line centred, rotated by `zone.rotate` around the centre. If `stroke > 0`
  the text is stroked first (width `stroke`, round joins, `strokeColor`), then filled with `ink`.
- **Heart**: the "❤" in `our_names` is the app-mark heart path (viewBox 24,
  `M12 21s-7.5-4.7-9.6-9.2C.7 8 3 4.5 6.6 4.5c2 0 3.6 1.1 4.4 2.6.8-1.5 2.4-2.6 4.4-2.6 3.6 0 5.9 3.5 4.2 7.3C19.5 16.3 12 21 12 21z`)
  in a 0.78 em square box, bottom 0.12 em below the baseline, 0.08 em side margins, ink colour, never stroked.
- **Composite**: template art + lettering, then an 8 px white die-cut outline around the union drawn beneath
  (`StickerRenderer.outlineOf(…, 8f)`), 512 × 512 WebP starting at quality 84, stepping down until ≤ 100 KB.
- **Tray**: 96 × 96 PNG: the heart path ×4 filled #C23359; white initials (first grapheme of your name then
  theirs), 35 px for two, 44 px for one, −0.03 em tracking, centred horizontally, vertically centred in the box from
  the top to 12 % of the size above the bottom. Font chosen from the initials string.
- **Cache**: renders keyed by (character, slot, tone, family, lang, your name, their name); the grid keeps a 320 px
  display bitmap and the encoded bytes, so tone and character switches back and forth are instant.

## Pack

- Identifier: `own-np-` + the first 12 hex digits of SHA-256(`lang \u0000 yourName \u0000 theirName`). The `own-`
  prefix keeps analytics anonymising it and the provider resolving it through `own_packs`. Same names and language
  update the same pack; different names make a new one.
- Name: `"{you} ❤ {them}"`, or `"For {them}"` (localized) without your name. Publisher: "PIP Technologies", as the
  library packs. 12 static stickers; emojis from the character JSON; tray as above. Validated with
  `StickerPackValidator` and the provider's metadata before it is written.
- Storage: files in `filesDir/own/<id>/` (tray.png, 01.webp … 12.webp) written atomically; Room `own_packs` gains
  `imageDataVersion` (migration 1 → 2, default 1). Every save of an existing id increments it and keeps the
  whitelisted flag, so WhatsApp refreshes a pack that was re-lettered. The provider sends it as
  `image_data_version` for own packs (library packs stay at 1).

## Code

- `feature/namepack/engine/`: `NameInput`, `PhraseBook`, `TemplateSet`, `LetteringFit`, `LetteringFonts`,
  `HeartPath`, `Lettering`, `StickerComposer`, `TrayRenderer`, `NamePackId`, `NamePackAssets`, `NamePackBuilder`.
  Pure parts (input, phrases, templates, fit, fonts choice, id) have no Android dependencies beyond `org.json`.
- `feature/namepack/`: `NamePackViewModel` (Hilt; state in `SavedStateHandle`: step, names, relation, character,
  tone), `NamePackScreen` (one route, internal steps, `BackHandler`), step composables, shared UI pieces.
- `feature/language/LanguageSheet.kt`: `LanguageChip` and the sheet, used by the intro and the name steps.
- Navigation: `Routes.NAME_PACK = "namePack"`; Onboarding → NAME_PACK (the intro stays underneath, and its
  hand-over is a one-shot event so coming Back does not bounce into the flow again); the flow's end → HOME with the
  stack cleared. `CUSTOMIZE` and `CUSTOMIZE_EDIT` are deleted.
- A process-wide `PendingToasts` hands the "Added to WhatsApp" toast to Home.

## Analytics

Names are never logged, hashed or sent.
- `onboarding_complete` {`name_pack`: added / saved / skipped} replaces {`themes`}.
- `name_pack_built` {`relation`, `tone`, `character`, `has_your_name` 0/1, `language`} once per build.
- Existing `pack_added` / `pack_add_cancelled` report the pack as "own".

## Privacy and policy

Names stay on the phone: in the stickers' pixels, the pack name and Room. Nothing is uploaded ("Made on this
phone. Nothing is uploaded."). Sweet and Flirty only; re-check the IARC answers before release.

## Testing

- JVM: `NameInputTest`, `PhraseBookTest` (19 languages × tone × relation × slot, placeholders intact, neutral
  fallbacks), `TemplateSetTest` (the 4 bundled JSONs: 12 unique slots, zones inside 512, 1–3 emojis, files exist
  and are 512 × 512 single-frame WebP), `LetteringFitTest`, `LetteringFontsTest` (script choice, and each bundled
  font covers its scripts, read from its cmap table; unit tests compile against android.jar, which has no java.awt.Font), `NamePackIdTest`, string tests extended by the new keys.
- Robolectric: `LoveDbMigrationTest` creates a version-1 database from the v1 schema SQL, opens it with Room and
  `MIGRATION_1_2` (Room validates the migrated schema), and reads an existing pack back with version 1.
- Robolectric (native graphics): build a full pack from the bundled assets and run it through
  `StickerPackValidator` (sizes, tray, emojis).
- Robolectric (native graphics): `NamePackSheetsTest` writes contact sheets of the lettering per language,
  character and tone, for checking by eye; opt-in with `NAMEPACK_SHEETS=1`, since it renders 456 stickers (the CI tour cannot reach the flow again after first run, so it cannot
  capture the reveal in every language).
- Screen tour (CI): first run goes through the name flow. Your name and Their name replace the two Customize frames
  (4 and 5), the reveal is an extra state, and Back then Skip leave first run without a pack so later frames keep
  their state. `LocaleTourTest` is unchanged apart from the removed picker references.
- JVM: `OnboardingViewModelTest` pins the one-shot hand-over.

## Not in v1

Animated name packs (design §08 "Live" pack), 18+ tier, remote template sets, editing single phrases, the Create
entry point (awaiting design), per-character waiting loops (awaiting export).
