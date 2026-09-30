# Custom Stickers: implementation spec for Compose (from the design files)

Reference frame: 390 × 844, where 1 CSS px = 1 dp and font px = sp. Status bar and nav bar are the system's; the prototype's 38 px fake status bar is not part of the layout.

**Sources, most authoritative first**
- `design/Prototype.dc.html`: markup at L1-606 and logic at L608-1206. Key lines: `T` at 691-702, `cs` view-model at 1046-1070, `letteredEl`/`fitText`/tray at 713-757, `addBar` at 937-948, `confirmWA` at 869-874.
- `design/Custom Stickers.dc.html`: brainstorm, lettering spec, handoff note §07.
- `design/Screens.dc.html`: frame list and captions. Some captions are stale; they are flagged below.
- `app/src/main/assets/templates/{mango,pinky,bunny,capy}.json` and `phrases.json` (copied from Claude Design).

**Conflict rule:** what the prototype renders beats the handoff text, and the handoff text beats the captions. When the prototype contradicts a written rule, it is flagged with ⚠.

---

## 0. Shared tokens, type and icons

### Colours
Where an app token exists, the name is the one in `core/design/Color.kt`.

| Hex | App token | Used for |
|---|---|---|
| #FAFBFC | `Canvas` | background of every flow screen |
| #FFFFFF | `Surface` | field, unselected chips and tiles, reveal footer, sheet |
| #F3F5F8 | `Subtle` | RTL badge background |
| #1E2128 | `Ink` | field text, pack name, sheet item text, toast background |
| #171A20 | (private `TitleInk` in Home/Onboarding) | 26/22 titles |
| #626873 | `Ink2` | helper, "Who says it?", unselected chip/tile label, meta, Skip, "Not now" |
| #565C67 | (local) | onboarding body |
| #3D4550 | (private `ToolbarIcon`) | language chip content |
| #8B929D | `Muted` | field icon, limit note, sheet subtitle, RTL badge text |
| #A2A9B4 | `Muted2` | counter (normal) |
| #99A0AC | (local `FootnoteGrey`) | preview hint, reveal footnote, honesty line |
| #E7EAEF | `Border` | chip/tile/chip-pill borders, step-bar track, building-bar track |
| #E1E5EB | `BorderStrong` | sheet handle |
| #EEF0F4 | (local `FooterLine`) | reveal footer top border |
| #F6F7F9 | (local `DetailTileBg`) | reveal grid tile |
| #D8DCE3 | (local) | onboarding inactive dot |
| #C23359 | `Rose` | primary, selected border and text, step and bar fill, tray heart |
| #FFEEF0 | `RoseTint` | selected chip, tile and sheet-item background |
| #FFCED5 | `RoseLine` | add bar border (downloading and sent) |
| #2E9E6B | `Green` | add bar "added" |
| #4CB98A | `GreenSoft` | toast check |
| #B8792A | `Amber` | counter at 14, emoji/needName note text, add bar failed |
| #FEFBF5 / #F0DFC4 | `AmberTint` / `AmberLine` | add bar failed |
| **#E0B778** | **none (new)** | name-field border on error (emoji, missing name) |
| #3B2114 | none (from the template JSON `ink`) | lettering brown |
| rgba(20,22,28,.42) | `0x6B14161C` | sheet scrim |
| rgba(194,51,89,.16) | `0x29C23359` | add bar progress fill |

### Type
- UI font: Hanken Grotesk (app `Hanken`). Meta and counter: JetBrains Mono (app `Mono`).
- Lettering fonts, not in the app yet: Baloo 2 800, Baloo Bhaijaan 2 800 and Rubik 800 (see §j).

### Icons
The design uses Lucide with a stroke of 1.75; the app vendors Lucide at 2.0 in `LoveIcons`. Keep the app's convention.

| Where | Icon | Size | Stroke | Colour | In the app? |
|---|---|---|---|---|---|
| Language chip | `globe` | 15 | 1.75 | #3D4550 | ❌ add it. Path is in design/lucide/globe.svg (see §i) |
| Name field | `user-round` | 17 | 1.75 | Muted | ❌ add it. Not vendored in design/lucide (see §i) |
| Language sheet, current language | `check` | 14 | 2.4 | Rose | ✅ `LoveIcons.Check` |
| Honesty line (onboarding) | `heart-handshake` | 14 | | #99A0AC | ✅ |
| Add bar, idle | `message-circle`, drawn as the custom WhatsApp glyph | 19 | 1.9 | White | ✅ `LoveIcons.MessageCircle` |
| Add bar, downloading | `download` | 18 | | Rose | ✅ |
| Add bar, sent | spinner (2 px ring, one side transparent, 0.9 s linear) | 16 | | Rose | existing |
| Add bar, added | `check` | 19 | 2.2 | Green | ✅ |
| Add bar, failed | `rotate-ccw` | 17 | | Amber | ✅ |
| Toast "ok" | `check` | 17 | 2.4 | #4CB98A | ✅ (the app's DarkToast draws it at 15) |
| No-WhatsApp sheet | WhatsApp glyph | 22 | | Rose on RoseTint | ✅ existing sheet |
| Tray heart, `our_names` heart | **custom app-mark heart path** (not Lucide) | | | Rose or ink | ❌ add it (§i) |

### Flow and back stack in the prototype
- Onboarding (both Skip and "Get started") replaces the stack with `[nameYou]`, so onboarding is not reachable by back.
- `nameYou`, with Continue or Skip, pushes `[nameYou, nameLove]`.
- `nameLove`, with "Make our stickers" and a valid name, replaces the stack with `[building]`. When the bar reaches 100%, it holds 420 ms and replaces with `[reveal]`.
- On the reveal:
  - WhatsApp confirm replaces with `[home]` and shows the toast.
  - WhatsApp cancel stays on the reveal.
  - "Not now" replaces with `[home]` without a toast.
- `nameLove` Skip replaces with `[home]`.

| Screen | System back (prototype stack) | Status |
|---|---|---|
| Onboarding | unchanged | — |
| nameYou | stack root: nothing | ⚠ not designed. Choose between finishing the activity (first-run root) and returning to onboarding |
| nameLove | to nameYou; values are kept (state lives in one `cs` object) | designed via the stack |
| building | root: nothing | ⚠ not designed. Suggest blocking back, or cancelling to nameLove |
| reveal | root: nothing | ⚠ not designed. Suggest behaving like "Not now" |
| Language sheet | scrim tap closes it | back should close it too |

---

## a. Onboarding (intro) changes

- **Top strip** (40 dp, `SpaceBetween`, vertically centred) is now on both slides:
  - Language chip at the start edge (§b).
  - Ghost Skip at the end edge.
- ⚠ **Skip is now shown on both slides.** In the committed design and the current app, Skip only showed on slide 1 (`ob.first`).
- **Skip on either slide goes to Your name.** "Get started" on slide 2 goes to Your name. "Next" on slide 1 goes to slide 2. Onboarding is dropped from the back stack. In the app, `Routes.ONBOARDING` must now navigate to the name flow instead of `Routes.CUSTOMIZE`.
- In RTL (ar, fa, ur, ps, he) the chip sits at the right edge and Skip at the left.
- **Skip button:** height 36, horizontal padding 12, radius 10, no background, 13.5sp/600, `Ink2`. Copy: `skip`.
- **Unchanged:** container padding 4/24/26 (top/sides/bottom), artwork, title 26/800 with line height 1.15 and letter-spacing −0.02em in #171A20, body 15/400 with line height 1.55 in #565C67 and 10 dp above, the dots (8 tall; active 22 wide in Rose, inactive 8 wide in #D8DCE3; gap 6; margins 10 top and 22 bottom), primary 52/14 (`next` / `start`), honesty line (12sp #99A0AC with heart-handshake 14, gap 6, 14 above).
- All onboarding copy now comes from `T`: `obTitle1`, `obBody1`, `obTitle2`, `obBody2`, `next`, `start`, `skip`, `honesty`.
- Changing the language must keep the slide index. On Android the activity is recreated, so the index has to be saved.
- ⚠ **When the onboarded flag is persisted is not designed.** Today the app persists it when onboarding exits. If the app is killed mid-flow, the next launch goes to Home and skips the name steps.

## b. Language chip and language sheet

### Chip
- Shown on Onboarding, nameYou and nameLove only. Not on building or reveal.
- Pill, height 36, padding start 10 and end 12, border 1 dp `Border`, background `Surface`.
- Content colour #3D4550, text 13sp/600, gap 6, `globe` 15 then the current language's **native name** (`langNative`: "English", "العربية" …).
- Accessibility label: "Language" (hardcoded English in the prototype).
- Tap opens the sheet.

### Sheet
Layout:
- Modal bottom sheet over a scrim of rgba(20,22,28,.42). The scrim fades in over 200 ms.
- Sheet background `Surface`, **top radius 24** (the 30 bottom radius is the device frame), padding 14 top, 12 sides, 16 bottom, max height 80% of the screen.
- Enter: slides up over 320 ms, cubic-bezier(.2,.7,.2,1) ("320 ease-out" in the handoff).
- Handle: 36 × 4, radius 2, `BorderStrong`, centred, 12 below.
- Title: `language` ("Language"), 15sp/700, padding 2/12/2.
- Subtitle: `languageSub` ("Changes the app and the stickers."), 12sp/400 `Muted`, padding 0/12/10.
- Grid: 2 equal columns, gap 6 both ways, padding 0/4/4, scrolls when taller than the sheet. At 390 each column is about 176 wide. With 19 items (10 rows) the sheet is about 630 tall, so it just fits without scrolling at 844.

Each item:
- Height 48, horizontal padding 12, radius 12, gap 8, border 1 dp.
- Name: `native`, 14sp/600, one line, ellipsis, aligned to start.
- Then a **RTL badge** if the language is RTL: text "RTL", JetBrains Mono 9sp/600, `Muted` text on `Subtle`, padding 3/6, radius 5.
- Then, on the current language, `check` 14 in Rose.

| State | Background | Border | Text |
|---|---|---|---|
| Current | `RoseTint` | 1 dp `Rose` | `Rose` |
| Other | `Surface` | 1 dp `Border` | `Ink` |

The sheet shows only the native name. The English name appears only on Settings › Language.

### Order in the prototype (`langs`)
Row by row, left then right; mirrored in RTL.

| # | id | native (shown) | English | RTL |
|---|---|---|---|---|
| 1 | en | English | English | |
| 2 | es | Español | Spanish | |
| 3 | pt | Português (BR) | Portuguese | |
| 4 | fr | Français | French | |
| 5 | de | Deutsch | German | |
| 6 | it | Italiano | Italian | |
| 7 | tr | Türkçe | Turkish | |
| 8 | id | Bahasa Indonesia | Indonesian | |
| 9 | ms | Bahasa Melayu | Malay | |
| 10 | tl | Filipino | Filipino | |
| 11 | hi | हिन्दी | Hindi | |
| 12 | mr | मराठी | Marathi | |
| 13 | ru | Русский | Russian | |
| 14 | uk | Українська | Ukrainian | |
| 15 | he | עברית | Hebrew | ✔ |
| 16 | ar | العربية | Arabic | ✔ |
| 17 | fa | فارسی | Persian | ✔ |
| 18 | ur | اردو | Urdu | ✔ |
| 19 | ps | پښتو | Pashto | ✔ |

⚠ **This set does not match what the app ships** (`feature/language/AppLanguages.kt` and the `values-*` folders):
- The design adds ms, tl, mr and uk.
- The app ships pt (European) plus pt-BR "Português (Brasil)", zh "简体中文", ha "Hausa" and my "မြန်မာ", which the design lacks.
- The committed prototype had the app's first 10, in the app's order.
- ⇒ Decide which list the sheet uses. It is probably the shipped `AppLanguages` in the design's sheet style.
- If so, lettering-font coverage must include zh and my (not covered by Baloo, Rubik or Bhaijaan) and Hausa's ɓ ɗ ƙ ƴ.
- ⚠ The design has **no "System default" item** in the sheet, although the app's Settings › Language has one. The design says "phone language preselected".

### Behaviour
- Tapping an item sets the app language and closes the sheet. No toast here. (Settings › Language toasts "Language: {native}" and goes back.)
- The whole UI re-renders and the RTL root flips immediately. The live preview re-letters with the new phrase set, and the "sticker language = app language".
- On Android, switching the per-app locale recreates the activity, so names, relation, character and tone must survive (SavedStateHandle).
- Tapping the scrim closes the sheet.

## c. Your-name step (`nameYou`)

**Container:** Column, background `Canvas`, padding 4 top, 24 sides, 22 bottom (plus insets). The preview area takes the leftover height.

Top to bottom (the character row comes after the title and helper, before the field):

| # | Element | Spec |
|---|---|---|
| 1 | Top strip | 40 tall: chip (§b) at the start, Skip at the end (`skip`) |
| 2 | Step bar | Margin 6 above and 16 below. Two segments of equal weight, 4 tall, radius 2, gap 6. **Segment 1 `Rose`, segment 2 `Border`.** Fills from the right in RTL |
| 3 | Title | `youTitle` "What's your name?", 26sp/800, line height 1.15, letter-spacing −0.02em, #171A20 |
| 4 | Helper | `youHelper`, 14sp/400, line height 1.5, `Ink2`, margin 8 above and 14 below |
| 5 | Label | `pickChar` "Who says it?", 12.5sp/600, `Ink2`, 8 below |
| 6 | Character row | Row, gap 8, 14 below, **4 tiles of equal weight** (about 79.5 wide at 390, about 80 tall). Tile described below |
| 7 | Name field | Described below |
| 8 | Note (conditional) | 12.5sp/400, line height 1.4, 8 above. See the states table |
| 9 | Preview area | Takes the remaining height, 8 padding top and bottom, content centred. **Sticker 210 × 210**: slot **`name_only`**, the **selected character**, text = the typed name, or **the placeholder `youPh` ("Your name") itself when empty** |
| 10 | Preview hint | `previewHint` "Live preview. It re-letters as you type.", 11.5sp/400 #99A0AC, centred, 12 below |
| 11 | CTA | 52 tall, full width, radius 14, `Rose` background, white 16sp/600, `cont` "Continue" |

**Character tile:**
- Column centred, gap 4, padding 8 top, 4 sides, 6 bottom, radius 14, 1 dp border.
- Art 46 × 46, fit contain, from `assets/templates/{id}-6.webp` (the hug pose "reads at any size"), with no outline.
- Label 11sp/600: Mango, Pinky, Bunny, Capy. These are proper names, not in `T`, and not localized.
- Selected: `RoseTint` background, `Rose` border and label. Unselected: `Surface` background, `Border` border, `Ink2` label.
- Background and border transition over 120 ms.
- **Mango is preselected.**
- Tapping a tile swaps the preview **instantly** (art and lettering).
- The same choice drives the building screen (⚠ see §e) and the reveal.

**Name field:**
- Row 52 tall, radius 12, `Surface` background, horizontal padding 14, gap 9.
- Border 1 dp: **`Rose` while active**, **#E0B778 on error**. The prototype has no unfocused state; the handoff says "rose while active", so use `Border` when unfocused.
- Leading `user-round` 17 in `Muted`.
- Input: 16sp/400 `Ink`, one line, placeholder `youPh`. The placeholder colour is not specified; the browser default was used.
- Trailing counter `{n}/14` in Mono 11sp/400.

### States
Notes take this priority: emoji, then missing name, then limit.

| State | Counter | Border | Note | CTA |
|---|---|---|---|---|
| Empty (initial) | "0/14" `Muted2` | Rose (active) | — | enabled |
| Typing | "n/14" `Muted2` | Rose | — | enabled |
| At limit (14 graphemes; input blocks more) | **Amber #B8792A** | Rose | `limit` "That's the limit. Nicknames work too." in **`Muted` #8B929D** | enabled |
| Emoji present (`\p{Extended_Pictographic}`, which includes ❤ © ®) | as above | **#E0B778** | `emoji` "Emoji can't be lettered. Letters only." in **Amber** | ⚠ the prototype leaves Continue enabled and proceeds. The written rule says "no emoji … button disabled", so disable it |
| Lettering hits the 20 px floor (the fit returns `ok=false`) | — | — | Spec: show `limit`. ⚠ The prototype never wires this | — |

### Behaviour
- The preview re-letters on **every keystroke with no debounce**. The spec wants a **120 ms crossfade** of the lettering layer only; the prototype is instant.
- **Continue** always goes to nameLove. An empty name is equivalent to Skip.
- **Skip** goes to nameLove and **clears your name** (`you = ''`). The character choice is kept.
- The optional name feeds `our_names`, the pack name and the tray initials.
- IME action: not designed. Suggest Next behaving like Continue.
- ⚠ **Keyboard overlap is not designed.** The preview container has `min-height:0` and the sticker is a fixed 210, so with the keyboard up the preview cannot fit. Suggest `imePadding()` with the preview scaling down or hiding, and the CTA staying visible.

## d. Their-name step (`nameLove`)

Same container and parts as §c, with these differences.

**Layout order:** top strip, step bar (**both segments `Rose`**), title, helper, field, note, **relation chips**, preview, hint, CTA. There is no character row here.

| Element | Spec |
|---|---|
| Title / helper | `loveTitle` "Who's your love?" / `loveHelper` "We letter 12 stickers with this name, in your language." |
| Field | placeholder `lovePh` "Their name"; same field, counter and notes as §c |
| Relation chips | Wrapping flow row, gap 8 both ways, 14 above. Chip: height 36, horizontal padding 15, pill, 13sp/600, one line, 1 dp border. Selected: `RoseTint`/`Rose`/`Rose`. Unselected: `Surface`, border `Border`, text `Ink2`. Background transition 120 ms. Wraps to about 3 rows at 390 in English |
| Preview | 210 × 210, slot **`love_you`**, **selected character**, text = `phrase('love_you', tone, appLang, you, love.ifEmpty { sampleName })`. So an empty field shows "Love you, Sara", or "أحبك يا سارة" in Arabic. The tone is Sweet on this screen (the default; there is no tone control here) |
| CTA | `make` "Make our stickers" |

### Relation chips
- **Order, the same in every language:** Girlfriend · Boyfriend · Wife · Husband · Crush · Partner · Mom · Friend. The keys are `girlfriend … friend`; the labels are in `T.<lang>.rels`.
- **Default: Girlfriend.** Single-select, and always one is selected.
- ⚠ **There is no per-language ordering rule in the design.** The repo's pre-design draft (`docs/specs/2026-09-29-custom-stickers-design.md`) proposed Wife and Husband first in ar, fa, ur, ps and ha. That rule is not in the design; it is an open decision.
- **Mom and Friend (family):**
  - On the reveal only the **Sweet** chip shows. The Flirty chip is removed and Sweet stays visible alone.
  - Phrases use the `family` overrides from phrases.json. English overrides `kiss` "Big kiss, {n}", `hug` "Big hug, {n}", `be_mine` "You're the best, {n}", plus four entries identical to Sweet. ar, fr and hi override only `kiss`, `hug` and `be_mine`.
  - Other slots use Sweet.
  - The Screens caption says "swap two phrases"; it is three.
  - ⚠ **Prototype fallback bug:** `phrase()` falls back to `P.en.family[slot]` before the local Sweet. Arabic with Mom therefore letters English "Love you, سارة" on `love_you`, `call_me`, `for_you` and `name_only`. The correct order is `L.family[slot]`, then `L.sweet[slot]`, then `en.sweet[slot]`.
  - Force tone = sweet whenever the relation is family.
- The relation also feeds the reveal meta line (`{rel}` in lower case).

### States (in addition to §c)

| State | Visual | CTA |
|---|---|---|
| Empty | counter 0/14; preview uses `sampleName` | **Looks disabled** (alpha 0.45) but **stays tappable**: a tap sets the error |
| Missing name after tapping (prototype prop `csError`) | Border **#E0B778**; note `needName` "Add a name to make the pack." in **Amber** | disabled look. The error **clears on the next keystroke** |
| Valid (trimmed not empty, no emoji) | normal | enabled: go to building |
| Emoji | amber border and `emoji` note | disabled look; a tap does nothing |
| At limit | amber counter and muted `limit` note | enabled if otherwise valid. In the "Anastasia-Mari" frame the preview wraps to two lines, "Love you," / "Anastasia-Mari", with nothing clipped |

### Behaviour
- **Skip** goes to **Home with no pack**: flow cleared, Trending chip, all themes on.
- The chip and sheet behave as in §b.
- Changing the relation re-letters only if its phrase differs.

## e. Building (`building`)

**Layout:** Column filling the screen, content centred both ways, horizontal padding 24, 60 bottom (the group sits about 30 above centre), gap 6, text centred. There is no top strip, chip, Skip or back affordance.

| # | Element | Spec |
|---|---|---|
| 1 | Character loop | 224 × 224, fit contain, −6 margin below. White die-cut look: 3 dp white outline (CSS drop-shadow at ±3 px in 4 directions) plus a shadow of 0, 8, 14 rgba(30,20,30,.14). Accessibility text: "Mango checking its watch, waiting" (English only) |
| 2 | Progress bar | 18 above (image to bar is 18 dp net). 200 × 4, radius 2, track `Border` #E7EAEF, fill `Rose`, width = progress %, each update animated over 120 ms linear. Fills from the right in RTL |
| 3 | Title | 14 above (bar to title is 20 net). `building` "Lettering 12 stickers…", 22sp/800, letter-spacing −0.02em, #171A20 |
| 4 | Sub | 6 below the title. `buildingSub` "Made on this phone. Nothing is uploaded.", 14sp/400, line height 1.5, `Ink2` |

**Which art:**
- ⚠ The prototype **always** shows `assets/templates/live/mango-wait.webp`, regardless of the chosen character. It is an animated WebP of 33 frames at 10 fps, a 3 s loop, 375 KB, played from frame 0.
- The brainstorm text says "the chosen character waits … Needs one waiting loop per character (4 clips)".
- Only Mango's loop exists. The app bundles only the **still** `mango-wait.webp` (22.9 KB) for now; the 375 KB animated file was over the 256 KB file-sync cap, and Claude Design has been asked to re-export all four loops at 240 KB or less.
- ⇒ **Pinky, Bunny and Capy have no wait loop and no design.** Options: always use Mango's loop (prototype behaviour), or show the chosen character's still (for example `{char}-6.webp`) until clips exist.

**Progress binding:**
- Real progress = finished renders / 12. A render is template + lettering + outline + encode.
- Displayed = min(real, elapsed / 1700 ms), so the bar is linear, **never finishes in under 1.7 s**, and never runs ahead of reality.
- At 100%: **hold 420 ms**, then **cut** to the reveal (no transition; it replaces the screen).
- The prototype fakes the progress: +2.4% every 40 ms, 100% at about 1.68 s.
- The Screens frame is held at 62%.
- The renders run on a background thread.

**Reduced motion:**
- The spec says "no pulse or burst; heart fades to the grid over 300". This is **stale**: it refers to a filling-heart building screen that is no longer rendered. `heartEl()`, a 132 dp filling heart with initials plus `heartpulse` and `heartburst` keyframes, is still defined but unused.
- The Screens caption "Building · العربية: Initials in Arabic script, heart identical" is stale for the same reason.
- Suggested interpretation: a still frame instead of the loop, the bar still moving, and the reveal tiles fading in over 300 ms with no scale and no stagger.

**Errors:** none designed (see §h).

## f. Reveal (`reveal`)

The container fades in over **300 ms**.

| # | Element | Spec |
|---|---|---|
| 1 | Header | 52 tall, horizontal padding 20; a centred row with gap 9: **tray heart 28 dp** (§j), then the **pack name** in 16sp/700 `Ink`, one line, ellipsis |
| 2 | Scroll area | Takes the remaining height, scrolls vertically, padding 2 top, 20 sides, 12 bottom |
| 2a | Meta row | `SpaceBetween`, gap 8, 12 below. **Meta** at the start: `stickersMeta` with `{rel}` = relation label in lower case, e.g. "12 stickers · girlfriend" or "١٢ ملصقًا · صديقتي"; Mono 12sp/400 `Ink2`, one line, ellipsis, shrinks. **Tone chips** at the end: row with gap 6. Chip: height 32, horizontal padding 13, pill, 12.5sp/600, same selected and unselected colours as the relation chips. `sweet` / `flirty`. **Family relation: only the Sweet chip** |
| 2b | Character row | Same tiles as §c but with **40 × 40 art** (about 81.5 wide and 74 tall at 390), gap 8, 12 below. No label above it |
| 2c | Grid | **3 columns, gap 10.** Tiles are square, **110 × 110 at 390**, radius 16, background #F6F7F9, sticker **104** centred. Order = slot order: love_you, miss_you, good_morning, good_night, kiss, hug, sorry, be_mine, for_you, call_me, our_names, name_only. In RTL it flows right to left |
| 2d | Footnote | 14 above, `buildingSub` "Made on this phone. Nothing is uploaded.", 11.5sp/400 #99A0AC, centred |
| 3 | Footer, pinned | Background `Surface`, 1 dp top border #EEF0F4, padding 12 top, 20 sides, 14 bottom (plus nav inset) |
| 3a | Add bar | 52 × full width, radius 14, 16sp/600, icon and label with gap 8. States below. **No hint line under it** (Pack detail has one; the reveal doesn't) |
| 3b | "Not now" | Ghost, height 44, full width, radius 12, 14sp/600 `Ink2`, 6 above. `notNow` |

At 390 the scroll content (about 643) is slightly taller than the space available (about 625), so it scrolls about 18 dp.

**Pack name:**
- With your name: `"{you} ❤ {love}"`. Without it: `forName`, which is "For {n}" in English and "إلى {n}" in Arabic.
- In the header the ❤ is **plain text**. ⚠ On Android, U+2764 may render as a colour emoji. Append U+FE0E, or draw the vector heart inline.
- Use the normalized names. The prototype uses the raw strings.

**Tone chips:** switching re-letters all 12 with a **160 ms crossfade of the lettering only**. Tiles don't move or pop again.

**Character row (re-cast):**
- Tapping a character re-renders all 12 stickers with that character's art and zones. The same names, tone and language re-letter at runtime ("the whole pack can be re-cast after seeing it, for free").
- The tray initials don't change.
- No animation is specified; the prototype swaps instantly. Suggest the same 160 ms crossfade.
- ⚠ The spec's cache key omits the character; add it (§j).

**Entry animation:**
- Container: fade 300.
- Tiles, keyframes `pop`: opacity 0→1 and scale 0.2→1, **420 ms**, `cubic-bezier(.2,.9,.3,1.3)` (overshoot), fill both.
- Delay = round(hypot(col−1, row−1.5) × 95) ms. Per index 0..11: **171, 143, 171, 106, 48, 106, 106, 48, 106, 171, 143, 171**. The spec's "0, 95, 135, 170…" is in tile units; no tile sits exactly at the centre.
- Spec, not in the prototype: "header and meta fade 300; Add bar fades 300 after the last tile", which is at about 591 ms.

**Add bar states.** These are the library's five states, reused from the app's `AddBar`.

| State | Background | Border | Content colour | Icon | Label (`T` key) | Fill |
|---|---|---|---|---|---|---|
| idle | `Rose` | none | white | WhatsApp glyph 19 | `addWA` "Add to WhatsApp" | — |
| downloading | `RoseTint` | 1 dp `RoseLine` | `Rose` | `download` 18 | `downloading` + " · " + pct + "%", e.g. "Downloading · 64%" | rgba(194,51,89,.16), width = pct, 150 ms linear |
| sent | `RoseTint` | 1 dp `RoseLine` | `Rose` | spinner 16 | `sentWA` "Sent to WhatsApp…" | — |
| added | `Surface` | 1 dp `Border` | `Green` | `check` 19 (stroke 2.2) | `addedWA` "Added to WhatsApp" | — |
| failed | `AmberTint` | 1 dp `AmberLine` | `Amber` | `rotate-ccw` 17 | `failedRetry` "Download failed · Retry" | — |

- Background and colour transition over 200 ms.
- Taps are ignored while downloading or sent. A tap while added toasts "Already in WhatsApp"; on the reveal this is unreachable because a confirm navigates away.
- ⚠ The prototype's fill uses a physical `left:0`, so it doesn't mirror in RTL. Follow the app's existing AddBar.
- For this pack, the handoff says "existing Add flow (download is instant here: files are local)". So `downloading` is effectively skipped or instant.
- ⚠ The `failed` copy "Download failed" doesn't fit a local pack. What an export or validation failure shows is not designed.
- ⚠ The prototype's offline check (toast "You're offline. Connect to download.") also runs for this pack. It shouldn't: nothing is downloaded.
- Suggest disabling the tone and character controls while the add is in the sent state. Not designed.

**Tap Add:**
- If WhatsApp is missing, show the existing **"WhatsApp isn't installed"** confirmation sheet:
  - Icon box 44/13 with the WhatsApp glyph 22, Rose on `RoseTint`.
  - Title 18/700, body 13.5.
  - Buttons 50/13: "Not now" (grey) and "Get WhatsApp" (Rose), which goes to the Play Store with the toast "Opening Play Store…".
  - The handoff says the pack **is still saved** under My Packs › Made by you. The prototype doesn't do this.
- Otherwise the state goes to sent, WhatsApp's own add dialog appears, and §g follows.

## g. After Add, "Not now", My Packs, and reopening the flow

- **WhatsApp confirmed** (`confirmWA`):
  - Add state becomes `added`.
  - Navigate to **Home**, clearing the flow from the stack. The handoff says "Home slides in".
  - Home has the **Trending** chip selected and **all themes on**.
  - Show a **dark toast "Added to WhatsApp"** (`addedToast`) with a check for **2400 ms**.
- **Toast:**
  - 20 from each side, centred, **84 above the bottom on Home** (above the 68 dp nav; 24 on screens without the nav).
  - Background #1E2128, white 13.5sp/600, padding 12/16, radius 13, gap 9, shadow 0, 10, 28, −8 rgba(20,30,60,.35).
  - Enters with `fadeup` over 200 ms (opacity plus 8 dp rise).
  - This is the app's existing `DarkToast` with the check.
  - Arabic: "أُضيفت إلى واتساب".
- **WhatsApp cancelled:** the Add state goes back to idle and the user stays on the reveal.
- **The pack in My Packs:**
  - Yes, after a confirmed add it appears under **"Made by you · N"**. It is excluded from "In WhatsApp", because that list skips own packs.
  - Card: title = pack name; meta `12 stickers · yours` (`labels.stickers`, `labels.yours`); 6 round thumbs of the lettered stickers (40 inside 46 circles, #F3F5F8); the labelled pill in the "Added" state.
  - The ⋯ menu offers Re-add to WhatsApp, Remove from this app and Delete pack.
  - Pack detail: 12 lettered tiles, meta "12 stickers · Made by you", and the Add bar.
  - The prototype keeps **one** custom pack (id `custom`, replaced on every run).
  - The spec's identifier `custom-{hash(names+lang)}` means the same names and language update the same pack, and different names create a new pack.
- **"Not now"** (`finishToHome`):
  - Home with Trending and all themes on, no toast.
  - ⚠ In the prototype **the pack is NOT saved**; only a confirmed add saves it.
  - The design text is silent here. It only says the pack is saved when WhatsApp is missing.
  - The repo draft spec said "Not now … the pack stays in My Packs". This is an open decision.
- **Rating prompt:** not in the design. The repo draft: the first confirmed add counts.
- **Reopening later:**
  - The handoff says "the entry point stays available later from **Create › "Name stickers"** (not designed here)".
  - No such UI exists in the prototype. Create/Import has only Photos, Camera and Video.
  - The repo draft proposed a Home card and My Packs. Open.

## h. Error states specific to this flow

| Where | Trigger | What shows |
|---|---|---|
| nameLove | CTA tapped with an empty or whitespace-only name (prop `csError`) | Field border #E0B778 and the note "Add a name to make the pack." (`needName`, Amber). The CTA keeps its disabled look; the error clears on typing |
| nameYou and nameLove | the name contains an emoji or pictograph | Border #E0B778 and the note "Emoji can't be lettered. Letters only." (`emoji`, Amber). On nameLove the CTA is disabled (⚠ on nameYou the prototype still allows Continue) |
| nameYou and nameLove | 14 graphemes | Counter Amber and the note "That's the limit. Nicknames work too." (`limit`, **Muted**, not amber; the border stays Rose) |
| Lettering | the fit reaches the 20 px floor and still overflows | Keep 20 px, clip nothing, show `limit` (spec only; not wired in the prototype) |
| Reveal Add | WhatsApp not installed | The existing no-WhatsApp sheet (§f); the pack is saved to My Packs › Made by you (handoff) |
| Reveal Add | failure | The `failed` bar state ("Download failed · Retry", Amber); a tap retries from zero. ⚠ The copy assumes a download |
| Reveal Add | offline | Prototype: toast "You're offline. Connect to download." ⚠ It should not apply to a local pack |
| Building | a render, font or encode failure | ⚠ **Not designed**: no error screen and no copy |
| Language | lettering font lacks glyphs (Pashto ټ ځ څ ډ ړ ږ ښ ګ ڼ ۍ ې; the app letters such a string whole in the system bold face) | Per-glyph fallback to Noto Sans Arabic ExtraBold (spec); nothing is shown to the user |

## i. Vector paths (copy exactly)

### App-mark heart
Used for the `our_names` "❤" token, the tray icon (96) and the reveal header heart (28). It comes from `heartPath()`, which is the same in both design files.

```
viewBox="0 0 24 24"
d="M12 21s-7.5-4.7-9.6-9.2C.7 8 3 4.5 6.6 4.5c2 0 3.6 1.1 4.4 2.6.8-1.5 2.4-2.6 4.4-2.6 3.6 0 5.9 3.5 4.2 7.3C19.5 16.3 12 21 12 21z"
```

The same path with spaces added, identical geometry, safe for `addPathNodes` and `pathData`:

```
M 12 21 s -7.5 -4.7 -9.6 -9.2 C 0.7 8 3 4.5 6.6 4.5 c 2 0 3.6 1.1 4.4 2.6 c 0.8 -1.5 2.4 -2.6 4.4 -2.6 c 3.6 0 5.9 3.5 4.2 7.3 C 19.5 16.3 12 21 12 21 z
```

In absolute coordinates, for a manual `Path`: `moveTo(12,21)`, then:

```
cubicTo(12,21, 4.5,16.3, 2.4,11.8)
cubicTo(0.7,8, 3,4.5, 6.6,4.5)
cubicTo(8.6,4.5, 10.2,5.6, 11,7.1)
cubicTo(11.8,5.6, 13.4,4.5, 15.4,4.5)
cubicTo(19,4.5, 21.3,8, 19.6,11.8)
cubicTo(19.5,16.3, 12,21, 12,21)
close()
```

- Geometry: the bounding box is x 1.829–20.171 and y 4.5–21.0, so its centre is (11.0, 12.75). The tip is at (12, 21) and the notch at (11, 7.1). It is hand-drawn and slightly asymmetric: keep it as is. Do not recentre it.
- ⚠ This is **not** the heart the app uses today. `ic_splash_heart.xml` and `ic_launcher_foreground.xml` use the Material "favorite" path `M12,21.35l-1.45,-1.32C5.4,15.36 …`, and Home's brand mark uses the Lucide `heart`. The design calls its path "the app mark", but the only literal path it gives is the one above.

### Globe
Lucide, `design/lucide/globe.svg`, viewBox 24:
- `<circle cx="12" cy="12" r="10"/>`, which becomes the arc path `M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0`
- `M12 2a14.5 14.5 0 0 0 0 20 14.5 14.5 0 0 0 0-20`
- `M2 12h20`

### User-round
Lucide; ⚠ **not vendored** in design/lucide, so verify against the Lucide source:
- `<circle cx="12" cy="8" r="5"/>`, which becomes `M7 8a5 5 0 1 0 10 0a5 5 0 1 0 -10 0`
- `M20 21a8 8 0 0 0-16 0`

### WhatsApp glyph
Already in the app as `LoveIcons.MessageCircle`.
- Stroked bubble `M7.9 20A9 9 0 1 0 4 16.1L2 22Z`, width 1.9, round caps and joins.
- Plus a filled Lucide `phone` path with `translate(6.5 6.5) scale(0.46)`.

## j. Tray icon, pack naming, lettering, fit and composite (NOTES checked)

### Tray icon
96 × 96 PNG, ≤ 50 KB, transparent. Also drawn at 28 dp in the reveal header with the same proportions.

- **Heart:** the path above scaled to the full box (viewBox 24, ×4), fill **#C23359**, no outline.
- **Initials:**
  - First grapheme of *your* name, then the first grapheme of *theirs* ("you ❤ them" order). With your name skipped there is one initial.
  - Upper case where the script has case; the prototype uses `a[0].toUpperCase()`, which is a UTF-16 unit, so use a BreakIterator.
  - Scripts without case stay as typed.
  - The font is chosen from the *initials string* with the lettering rule.
  - White, weight 800, letter-spacing −0.03em, line height 1.
  - Size: **0.36 × box = 34.56 ≈ 35 px for two initials**, and **0.46 × box = 44.16 ≈ 44 px for one**. At 28 dp that is 10.1 and 12.9 sp.
  - Text box = the full width, from the top to **12% of the size above the bottom** (11.52 px at 96). So the text centre is at 0.44 × size, **5.76 ≈ 6 px above the box centre** ("optical centre 6 px above the heart's centre").
  - The heart's own bounding-box centre is at x = 11/24, so the initials sit about 4 px right of the heart's visual centre at 96. This is what the prototype does; replicate it.
  - The fallback when both initials are empty is "♥". It is unreachable, because their name is required.
- Regenerate the tray when either name changes.

### Pack
- Identifier: `custom-{hash(names + lang)}`. The hash function is not specified.
  - It must pass `StickerPackValidator` and WhatsApp's identifier rules.
  - Because tone, character and relation aren't in the identifier, re-lettering (tone, re-cast or a family change) must produce a **new `image_data_version`**. The spec states this for tone.
- Name: `"{you} ❤ {love}"`, or `forName` "For {n}" (localized). At most 128 characters; "the ❤ is fine in the pack name".
- Publisher: as for library packs.
- 12 static stickers; the tray above; **emojis from the character JSON** per slot. All four JSONs have the same emojis.
- Also in the spec but out of scope for v1: a "Live" animated pack named "Aymen ❤ Sara · Live", shipped next to the static pack (§08 of the spec page). The repo draft defers animated packs to v2.

### Names
- Trim, collapse internal spaces, at most **14 grapheme clusters**. The prototype counts `.length` (UTF-16) and uses `maxLength=14`: use graphemes.
- **No `\p{Extended_Pictographic}`**. Letters, marks, digits, apostrophes, hyphens and dots are fine. The prototype rejects only Extended_Pictographic, so other punctuation passes.
- Inserted **verbatim**: never transliterated and never re-cased. This overrides the repo draft's "name_only uses locale-aware uppercase" and its "emoji too".
- Phrases fall back to English per key when a language lacks one.

### Phrases
- `phrases.json` holds `{lang: {sweet, flirty, family}}`, but only for **en, ar, fr and hi**. The other 15 languages must be translated, keeping to the 14-character name budget.
- `{n}` is their name and `{a}` is yours.
- `our_names` = `"{a} ❤ {n}"`. With your name empty, the sticker letters **only `{n}`**, which is the whole love name.
- Family: see §d, including the fallback bug.

### Template JSON
One file per character (`mango`, `pinky`, `bunny`, `capy`):
- Top level: `{character, canvas: 512, generated, notes, title?, stickers:[…]}`.
- Each sticker: `{file, slot, zone:{cx,cy,w,h,rotate}, emojis[2], ink, stroke, strokeColor?, surface}`.
- Ink: #FFFFFF on the red heart (`love_you`); #3B2114 on signs, bubbles and ribbons; #C23359 on the `our_names` banner and on the open-space slots (`good_night`, `kiss`, `hug`, which also have stroke 5 in #FFFFFF).
- `be_mine` has a rotation of +8 (Mango), +3 (Pinky and Capy) and +4 (Bunny), clockwise like its sign board. ⚠ Claude Design's files give these as negative numbers, which tilts the lettering uphill against the board (the prototype shows the same mismatch); the bundled copies flip the sign and keep the magnitudes. The boards measure about +7.5° (Mango), +9° (Pinky), +6.4° (Bunny) and +4.6° (Capy), so Claude Design may want to re-measure.

### Checking the NOTES against the design

| NOTES claim | Verdict |
|---|---|
| Fonts chosen from the **string** | ✔ Exact ranges: **Arabic** U+0600–06FF, 0750–077F, 08A0–08FF, FB50–FDFF, FE70–FEFF → Baloo Bhaijaan 2; **Hebrew** U+0590–05FF **or Cyrillic** U+0400–04FF → Rubik; everything else → Baloo 2 (Latin, Latin-ext, Devanagari). All at weight 800. About 600 KB bundled. Check the Pashto glyphs and fall back per glyph to Noto Sans Arabic ExtraBold. ⚠ zh, my and ha coverage matters if the app keeps its current language set |
| RTL "if the string contains Arabic or Hebrew" | ✔ Any character in the same Arabic or Hebrew ranges makes the text RTL. The text engine handles bidi; don't reorder. ⚠ This differs from the repo draft's FIRSTSTRONG_LTR. A mixed string like "Love you, سارة" goes RTL under the design rule |
| Fit start and floor | ✔ With a nuance: `size = floor(min(zone.h × 0.82, 128))`, then −2 per step while size ≥ 20. Line height 1.08 × size, tracking −1% (−0.01 em) |
| One line, else the best two-line split | ✔ With the loop order made explicit. **At each size:** (1) if the whole string fits `zone.w` on one line, return it. (2) Otherwise, if there is more than one word and 2 × line height ≤ zone.h, try every split on spaces and keep the one with the smallest longer line among those that fit; if one fits, return it **at this size**. So two lines at a larger size win over one line at a smaller size. Never three lines. Words never break inside, but a multi-word name *can* split at its own space. The prototype measures **without** the −1% tracking (slightly conservative) and measures ❤ as a font glyph. Suggest measuring the heart as 0.94 em (see below) |
| Floor: keep 20, clip nothing, show the limit note | ✔ Returns `{size 20, one line, ok=false}` and may overflow `zone.w`. Clipping is off (`white-space: nowrap`, no overflow hidden). The note is not wired in the prototype |
| Placement | ✔ The block is centred vertically in the zone and each line horizontally; the zone box is centred on (cx, cy); rotation is `zone.rotate` degrees around that centre. At display size S everything scales by S/512 (font size = fit.size × S/512) |
| Colour and stroke | ✔ Fill = `ink`. If `stroke > 0`, stroke width = `stroke` (5 at 512) in `strokeColor` (#FFFFFF), **painted before the fill** (CSS paint-order stroke fill, so about 2.5 px shows outside). On Android: draw with `Paint.Style.STROKE` first, then FILL |
| Heart in `our_names` | ✔ A vector heart, never a glyph or emoji. The box is **0.78 × font size** square, with **vertical-align −0.12 em** (box bottom 0.12 em below the baseline) and **0.08 em margins on both sides**. The spaces from the phrase stay. Colour: the spec says "same colour as the ink"; the prototype paints #C23359 unless ink is '#fff' (it compares against '#fff' while the JSON has '#FFFFFF', so always rose). They agree because `our_names` ink is #C23359 for all four characters. **Implement heart = ink.** The heart gets no stroke |
| Composite | ✔ Art (512) plus lettering, then an **8 px white die-cut outline around the union (round joins)** drawn beneath, then export **512 × 512 WebP ≤ 100 KB at quality 0.84**. ⚠ Correction: the spec's cache key (slot, tone, lang, names) must also include the **character**, and the family flag, because re-cast and family change the output |
| UI preview outline | The prototype approximates it with CSS: `k = max(1.5, S × 0.014)` white drop-shadows in 4 directions (210 → 2.9, 104 → 1.5) plus a shadow of 0, 2, 4 rgba(30,20,30,.12). The real outline is 8/512 × 210 = 3.3. Suggest drawing the real composite bitmap scaled, plus the soft shadow |
| Tray | ✔ With the details above: exact 34.56 / 44.16 px, −3% tracking, font chosen from the initials string |
| Pack | ✔ With the `image_data_version` note above |
| Names | ✔ (above) |
| Screens and motion numbers | ✔ All match the prototype. Corrections: on nameYou the order is title and helper, then "Who says it?" and the tiles, then the field. On the reveal the order is header, then meta and tones, then the character row, then the grid. Tiles pop from opacity 0 as well as scale 0.2. The real delays are 48–171 ms |
| Mom/Friend "hide Flirty" | ✔ Only the Flirty chip is removed; Sweet stays as a single chip. Plus the fallback bug (§d) |
| Assets | `{char}-{1..12}.webp` (512², text-free, no outline, 15–40 KB each; the spec says 14–33), `{char}.json`, `phrases.json`, `mango-wait.webp` (still). **`live/mango-wait.webp` (animated) is missing locally** |

## k. Settings, Home, Customization and Create in the design

- **Customization (theme picker):**
  - The prototype removed it from first run (the map no longer has "Pick themes").
  - It also removed Settings' "Edit themes" row: Preferences now contains **Language** and **Clear downloaded packs** only.
  - The Screens page dropped the Customization section.
  - ⚠ The Custom Stickers handoff §07 says "Customization and its Edit-themes path are untouched", and the Screens Settings caption still lists "Edit themes". Both are stale and contradict the page header ("gone from first run and from Settings; Home shows every theme").
  - **User decision (NOTES): remove it entirely.** That means the first-run step, Settings › Edit themes, the prefs and the theme chips' dependency on the picker.
- **Home:** after the flow, "All themes on, Trending selected".
  - The chip row is Trending, then ♥ Saved · n (only when there are favourites), then Animated, then every theme.
  - ⚠ The prototype's `defaultThemes()` omits **Anime** and **Family**, so their chips don't show. That contradicts "Home shows every theme"; show all 14. The After Dark 18+ theme appears only in 18+ builds.
  - The custom pack never lists on Home; Home shows catalog packs only.
  - The toast sits 84 above the bottom.
- **Settings › Language row:**
  - Unchanged: `languages` icon 20, label "Language", value = the current native name (13.5sp `Muted`), chevron 18.
  - It opens the full-screen list: native name over the English name, RTL badge, rose check on the current language, and the note "Changes the app. Pack names stay as published."
  - A tap saves the language, goes back, and toasts "Language: {native}".
  - It uses the same `langs` list as the sheet, so the set mismatch in §b applies here too.
  - Whether a language change re-letters an existing custom pack is not designed. The note implies it doesn't.
- **Create › "Name stickers":** named as the later entry point, **not designed**. There is no Create UI for it in the prototype.
- **Onboarding:** §a.

---

## Design frames for screenshot and QA parity
Prototype props are in brackets.

- **Main flow:**
  - onboarding
  - onboarding with the sheet [langSheet]
  - nameYou [youName Aymen]
  - nameLove [Aymen / Sara]
  - building [hold, 62%]
  - reveal [Aymen / Sara, Sweet]
  - home [toast added]
- **Variants:**
  - reveal Flirty
  - reveal with your name skipped [youName ""]: "For Sara", `our_names` = "Sara", one initial
  - at the limit [loveName "Anastasia-Mari"]
  - emoji [loveName "Sara 😍"]
  - no name [loveName "", csError]
- **Arabic RTL:**
  - onboarding
  - nameLove [أيمن / سارة]
  - building
  - reveal
  - home toast
- **Tweakable props:** character (mango, pinky, bunny, capy), tone, relation, lang (en, ar, fr, hi; only en and ar have UI copy, the others fall back to English UI with localized lettering).

## Open decisions and ambiguities
1. **Language set**: the design lists 19 languages that differ from the app's 19 (§b), and has no "System default" item.
2. **Building art** for Pinky, Bunny and Capy: there is no wait loop, and the prototype always shows Mango (§e). The animated `live/mango-wait.webp` is not downloaded.
3. **"Not now"**: save to My Packs or discard (§g).
4. **Back behaviour** on nameYou, building and reveal (§0).
5. **Emoji on nameYou**: the prototype lets Continue proceed. Disable it per the rules.
6. **Keyboard vs. the 210 preview**: not designed.
7. **Reduced motion**: the spec text refers to the removed filling heart. Interpretation in §e.
8. **Family fallback bug**: English leaks into other languages (§d).
9. **Heart colour**: spec (ink) vs. prototype (rose). The result is the same for the current JSONs.
10. **Cache key and `image_data_version`**: add the character and the family flag.
11. **Add-bar copy for a local pack**: "Download failed", and offline handling, are not designed.
12. **Render failure on building**: no design.
13. **Relation order per language**: none in the design. The Wife/Husband-first rule exists only in the repo draft.
14. **Reopening the flow** (Create › "Name stickers"): not designed.
15. **❤ in the UI pack name**: force text presentation.
16. **Arabic copy for shared keys differs** from the app's current `values-ar` (skip, start, obBody1, obBody2, addWA, add, retry, failedRetry, flirty). See `custom-stickers-copy.json › existingAndroidStringIds`.
17. **UI copy exists only for en and ar**. The other 17 languages need translation, and phrases exist only for en, ar, fr and hi.
18. **A gendered French family phrase**: fr `be_mine` "T'es la meilleure, {n}" is feminine, which is wrong for a male Friend. The other family phrases are neutral.
