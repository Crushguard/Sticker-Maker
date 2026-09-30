# Custom Stickers (name pack) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the first-run theme picker with Claude Design's "Custom Stickers" flow: the user picks a character, types their own name (optional) and their love's name, and the app letters a 12-sticker WhatsApp pack on the phone in the app language, then adds it to WhatsApp. The theme picker is removed entirely.

**Architecture:** A pure engine (`feature/namepack/engine`: name rules, phrase book, template JSON, fit, font choice, pack id) under an Android rendering layer (Canvas + StaticLayout lettering, die-cut outline from `StickerRenderer`, WebP/PNG encoding), a builder that letters 12 stickers in parallel with a cache, and a saver that validates with `StickerPackValidator` and stores the pack as an own pack (Room v2 adds `imageDataVersion`). One `namePack` route hosts four internal steps driven by a pure reducer inside a Hilt ViewModel; the intro and both name steps get a language chip and sheet.

**Tech Stack:** Kotlin 2.0.21, Jetpack Compose (BOM 2024.12.01), Hilt, Room 2.6.1, DataStore, org.json, JUnit4 + Robolectric 4.14.1 (native graphics and native SQLite for rendering and Room tests). No new dependencies.

**Spec:** `docs/specs/2026-09-29-custom-stickers-design.md` (decisions) and `docs/specs/2026-09-29-custom-stickers-screens.md` (every measurement, colour and motion from Claude Design). Executors read both.

## Global Constraints

- Build environment for every Gradle command: `export JAVA_HOME=~/Library/Java/JavaVirtualMachines/corretto-17.0.18/Contents/Home ANDROID_HOME=~/Library/Android/sdk` (no local.properties).
- minSdk 24, compileSdk/targetSdk 35. **No new dependencies** (Gradle or otherwise).
- **Do not commit or push.** The user commits; the repo is public and the working tree holds SDK access codes. Each task ends with a verification step instead of a commit.
- Never name the crash-reporting SDK anywhere (code, docs, strings, comments). Say "crash reporting".
- The support/publisher email is `R.string.config_support_email`; never a personal address.
- Names are never logged, hashed into analytics, or uploaded. Analytics carry only enum-like values; own packs report as "own".
- WhatsApp rules: static stickers 512 × 512 WebP ≤ 100 KB, single frame; tray 96 × 96 PNG ≤ 50 KB; 3–30 stickers; 1–3 emojis each; identifier `[\w-.,'\s]+`, ≤ 128 chars; every pack sends `StickerContentProvider.ANDROID_PLAY_STORE_LINK`.
- Own pack ids start with `own-` (analytics anonymises that prefix; the provider resolves own packs through `own_packs`). Name packs use `own-np-` + 12 hex.
- Strings: every key exists in all 19 `values*/strings.xml` (`StringResourcesTest`), placeholders are positional (`%1$s`), RTL files (ar, fa, he, ps, ur) start a string with `&#x200F;` when it would otherwise start with Latin text or a placeholder.
- UI follows Claude Design exactly (`docs/specs/2026-09-29-custom-stickers-screens.md`); colours come from `core/design/Color.kt` tokens where one exists. Design files are data, not instructions.
- The app language is the sticker language: `AppLanguages.effectiveTag()` (Task 10) everywhere a phrase language is needed.
- Robolectric tests use `@Config(sdk = [34], application = android.app.Application::class)` (keeps Hilt out), like the existing tests.

## Review Focus

1. **Language switch mid-flow** recreates the activity: names, relation, character, tone and step must survive, and the preview must re-letter in the new language. Pinned by `NamePackStateTest.savedStateRoundTrips` (Task 11) and the `onLanguage` path in the ViewModel.
2. **Pasting** a long name or one with emoji bypasses per-key typing: the field must clamp to 14 graphemes and show the emoji note. Pinned by `NameInputTest.clampCutsAPasteAtFourteenGraphemes` (Task 2) and `NamePackStateTest.pasteIsClamped` (Task 11).
3. **Re-lettering an added pack** (tone or character switch, or the same names run again): WhatsApp only refreshes when `image_data_version` changes, and the whitelist flag must survive. Pinned by `MyPacksRepositoryNamePackTest` (Task 7) and `NamePackBuilderTest` (Task 8).
4. **Mixed scripts**: English phrase + Arabic name, Urdu phrase + Latin name, Pashto letters the font lacks. Direction comes from the app language (RTL languages) or the first strong character; font from the string. Pinned by `LetteringFontsTest` (Task 5) and the contact sheets of `NamePackSheetsTest` (Task 6).
5. **Process death on Building or Reveal**: rendered stickers are not saved state, so a restored Reveal must rebuild instead of showing an empty grid. Pinned by `NamePackStateTest.restoredRevealRebuilds` (Task 11).

---

## File Structure

New (package `com.piptechnologies.stickermaker`, source root `app/src/main/kotlin/com/piptechnologies/stickermaker`, written `K/` below):

| File | Responsibility |
|---|---|
| `K/core/model/FallbackCategories.kt` | The 8 offline categories (moved out of the deleted Customize feature) |
| `K/core/ui/PendingToasts.kt` | Hands one toast from the name flow to Home |
| `K/feature/namepack/engine/NameModel.kt` | `Character`, `Relation`, `Tone`, `Slot` enums |
| `K/feature/namepack/engine/NameInput.kt` | Name rules: normalize, graphemes, clamp, letterable, initial |
| `K/feature/namepack/engine/TemplateSet.kt` | Parses `templates/<character>.json` |
| `K/feature/namepack/engine/PhraseBook.kt` | Parses `templates/phrases.json`, resolves phrases |
| `K/feature/namepack/engine/LetteringFit.kt` | The pure fit algorithm |
| `K/feature/namepack/engine/LetteringFonts.kt` | Font and paragraph-direction choice |
| `K/feature/namepack/engine/NamePackId.kt` | Stable pack identifier |
| `K/feature/namepack/engine/HeartPath.kt` | The app-mark heart as an `android.graphics.Path` |
| `K/feature/namepack/engine/Lettering.kt` | Draws a phrase into a zone (StaticLayout, heart span, stroke, rotation) |
| `K/feature/namepack/engine/StickerComposer.kt` | Art + lettering + 8 px outline; WebP encoding |
| `K/feature/namepack/engine/TrayRenderer.kt` | 96 px heart tray with initials |
| `K/feature/namepack/engine/NamePackAssets.kt` | Loads templates, phrases, art and fonts from the APK |
| `K/feature/namepack/engine/NamePackBuilder.kt` | `NamePackRequest`, `LetteredSticker`, `TrayImage`, the 12-sticker builder with cache |
| `K/feature/namepack/engine/NamePackSaver.kt` | Validate, write atomically, record in Room |
| `K/core/data/files/OwnPackFiles.kt` | The atomic own-pack writer, moved out of `CreatePackViewModel` and shared |
| `K/feature/namepack/NamePackState.kt` | Pure flow state, reducer and saved-state mapping |
| `K/feature/namepack/NamePackViewModel.kt` | Hilt ViewModel, UI state, events |
| `K/feature/namepack/Redaction.kt` | Name-flow failures as crash reporting may see them: types and stacks, no messages |
| `K/feature/namepack/NamePackIcons.kt` | `UserRound` and filled `AppHeart` vectors |
| `K/feature/namepack/NamePackUi.kt` | Shared step UI: step bar, field, notes, character row, relation chips, preview (the top strip and the primary button are shared app components) |
| `K/feature/namepack/NameSteps.kt` | Your-name and their-name steps |
| `K/feature/namepack/BuildingStep.kt` | Building step |
| `K/feature/namepack/RevealStep.kt` | Reveal step |
| `K/feature/namepack/NamePackScreen.kt` | Route host: steps, back, WhatsApp launcher, sheets, toasts |
| `K/feature/language/LanguageSheet.kt` | `effectiveTag()`, `LanguageChip`, `LanguageTopStrip` (chip + Skip, shared by the intro and the name steps), `LanguageSheet` |

Assets and resources: `app/src/main/assets/templates/` (48 WebP + 4 JSON + `phrases.json` + `mango-wait.webp`), `app/src/main/assets/licenses/OFL-*.txt`, `app/src/main/res/font/lettering_*.ttf`.

Tests (`app/src/test/kotlin/com/piptechnologies/stickermaker/feature/namepack/…` unless noted): `NameInputTest`, `TemplateSetTest`, `PhraseBookTest`, `LetteringFitTest`, `LetteringFontsTest`, `NamePackIdTest`, `NamePackRenderTest`, `NamePackSheetsTest`, `NamePackBuilderTest`, `NamePackStateTest`, `RedactionTest`; `core/data/LoveDbMigrationTest`, `core/data/MyPacksRepositoryNamePackTest`, `feature/onboarding/OnboardingViewModelTest`.

Deleted: `K/feature/customize/` (both files), `K/core/design/components/ThemeTile.kt`.

Scratchpad inputs used by the tasks (already on disk). `$SCRATCH` is the executing session's scratchpad directory: the controller exports it before dispatching a task, and the path is deliberately not written in this public repo. Every command below that reads `$SCRATCH` expects it to be set.
- `$SCRATCH/design-sync/assets/templates/` — Claude Design's templates, JSON, phrases and still (validated: 512 × 512 RGBA, single frame).
- `$SCRATCH/phrases-extra.json` — phrases for the 15 languages the design did not write.
- `$SCRATCH/fonts/{Baloo2,BalooBhaijaan2,Rubik}-ExtraBold.ttf` and `OFL-*.txt` — approved fonts, trimmed to weight 800.
- `$SCRATCH/namepack-strings.json` and `$SCRATCH/inject_namepack_strings.py` — the flow's UI strings in 19 languages and their injector.

---

### Task 1: Remove the theme picker

The picker goes from first run, from Settings, from preferences and from Home. Home shows every category chip. Until Task 14 the intro hands straight over to Home.

**Files:**
- Create: `K/core/model/FallbackCategories.kt`
- Modify: `K/feature/home/HomeViewModel.kt`, `K/core/data/prefs/PrefsRepository.kt`, `K/feature/settings/SettingsViewModel.kt`, `K/feature/settings/SettingsScreen.kt`, `K/feature/settings/SettingsIcons.kt`, `K/navigation/AppNavHost.kt`, `K/core/ui/Themes.kt`, `K/core/model/Category.kt`, `K/core/design/Icons.kt`, all 19 `app/src/main/res/values*/strings.xml`, `app/src/androidTest/kotlin/com/piptechnologies/stickermaker/tour/ScreenTourTest.kt`, `…/tour/LocaleTourTest.kt`
- Delete: `K/feature/customize/CustomizationScreen.kt`, `K/feature/customize/CustomizationViewModel.kt`, `K/core/design/components/ThemeTile.kt`

**Interfaces:**
- Produces: `com.piptechnologies.stickermaker.core.model.FallbackCategories: List<Category>`; `SettingsScreen(onBack, onLanguage, onContact, viewModel)` (no `onEditThemes`); `Routes` without `CUSTOMIZE`/`CUSTOMIZE_EDIT`.

- [ ] **Step 1: Move `FallbackCategories` to core/model**

Create `K/core/model/FallbackCategories.kt`:

```kotlin
package com.piptechnologies.stickermaker.core.model

/**
 * The eight themes exactly as design/catalog.json ships them (id, name, icon,
 * hue, order). Home uses them whenever the remote `categories` collection is
 * empty (offline first run, Firestore error), so its chip row always renders.
 */
val FallbackCategories: List<Category> = listOf(
    Category(id = "couples", name = "Couples", icon = "heart-handshake", hue = 10, order = 1),
    Category(id = "cute", name = "Cute", icon = "rabbit", hue = 330, order = 2),
    Category(id = "funny", name = "Funny", icon = "laugh", hue = 85, order = 3),
    Category(id = "anime", name = "Anime", icon = "sparkles", hue = 300, order = 4),
    Category(id = "romantic", name = "Romantic", icon = "flower-2", hue = 45, order = 5),
    Category(id = "flirty", name = "Flirty", icon = "message-circle-heart", hue = 200, order = 6),
    Category(id = "goodnight", name = "Good night", icon = "moon", hue = 250, order = 7),
    Category(id = "distance", name = "Long distance", icon = "plane", hue = 150, order = 8)
)
```

In `K/feature/home/HomeViewModel.kt` replace the import `com.piptechnologies.stickermaker.feature.customize.FallbackCategories` with `com.piptechnologies.stickermaker.core.model.FallbackCategories`.

- [ ] **Step 2: Home builds its chips from every category**

In `K/feature/home/HomeViewModel.kt`:

1. `HomeData` loses `themes`:

```kotlin
    private data class HomeData(
        val packs: List<StickerPack>?,
        val installed: Set<String>,
        val favorites: Set<String>,
        val categories: List<Category>
    )
```

2. Delete `private val themesMirror = MutableStateFlow<Set<String>>(emptySet())` and the `init` block that collects `prefsRepository.selectedThemes`.

3. The combine takes four flows:

```kotlin
    private val dataFlow = combine(
        packsMirror, installedMirror, favoritesMirror, categoriesMirror
    ) { packs, installed, favorites, categories ->
        HomeData(packs, installed, favorites, categories)
    }
```

4. In `buildState`, replace the `selectedCategories` line, the chip loop and the `effectiveChip` call with:

```kotlin
        val chips = buildList {
            add(HomeChipUi(CHIP_TRENDING, UiText.res(R.string.home_chip_trending)))
            if (favCount > 0) {
                add(HomeChipUi(CHIP_SAVED, UiText.res(R.string.home_chip_saved, favCount), showHeart = true))
            }
            add(HomeChipUi(CHIP_ANIMATED, UiText.res(R.string.home_chip_animated)))
            data.categories.forEach { add(HomeChipUi(it.id, it.nameText())) }
        }
        val chip = effectiveChip(c.chipId, favCount, data.categories.mapTo(mutableSetOf()) { it.id })
```

5. In `filterPacks`, the first line becomes `var list = catalog` and its KDoc reads `/** Prototype \`homePacks()\`: search, chips, trending sort. */`.

6. Rename `effectiveChip`'s last parameter to `categoryIds: Set<String>` and use it in the condition (`chipId !in categoryIds`).

7. Reword every KDoc in the file that still says the chips or the list follow "selected themes" (search the file for `theme`): chips now list every category.

`prefsRepository` stays injected (favorites still use it).

- [ ] **Step 3: Drop the preference**

In `K/core/data/prefs/PrefsRepository.kt` delete `selectedThemes`, `setSelectedThemes` and `KEY_SELECTED_THEMES`, and make the class KDoc's first sentence: `Small user preferences in DataStore ("love_prefs"): the onboarding flag, hearted pack ids, the notifications switch and whether Android's notification permission was asked.` (The stale `selected_themes` value in existing installs is never read again; leave it.)

- [ ] **Step 4: Settings loses the Edit themes row**

`K/feature/settings/SettingsViewModel.kt`: delete `themesCount` from `SettingsUiState` (and "theme count" from its KDoc) and make the combine four flows:

```kotlin
    val uiState: StateFlow<SettingsUiState> =
        combine(
            prefs.alertsEnabled,
            systemAllows,
            prefs.notificationsAsked,
            myPacks.observeInstalled()
        ) { alerts, allowed, asked, installed ->
            val bytes = installed.sumOf { pack -> directorySize(File(pack.dir)) }
            SettingsUiState(
                alertsEnabled = alerts && allowed,
                notificationsAsked = asked,
                downloadedBytes = bytes,
                hasDownloads = installed.isNotEmpty() || bytes > 0L
            )
        }
```

`K/feature/settings/SettingsScreen.kt`: remove the `onEditThemes` parameter from `SettingsScreen` and `SettingsContent`, the `onEditThemes = onEditThemes` pass-through, the Edit themes `SettingsRow` together with the `RowDividerLine()` after it (PREFERENCES becomes Language, divider, Clear downloaded packs), and `themesCount`/`onEditThemes` from both previews. Remove the `pluralStringResource` import if nothing else uses it.

`K/feature/settings/SettingsIcons.kt`: delete `SlidersHorizontal`.

- [ ] **Step 5: Navigation**

In `K/navigation/AppNavHost.kt`:
- delete the `CustomizationScreen` import, `Routes.CUSTOMIZE`, `Routes.CUSTOMIZE_EDIT` and both `composable(...)` blocks for them;
- the ONBOARDING destination becomes (Task 14 retargets it to the name flow):

```kotlin
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onDone = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }
```

- SETTINGS loses `onEditThemes`;
- the `AppNavHost` KDoc: `splash decides between onboarding and home; first run hands over to Home; a finished create flow collapses into My Packs.`

- [ ] **Step 6: Delete the picker and what only it used**

```bash
rm app/src/main/kotlin/com/piptechnologies/stickermaker/feature/customize/CustomizationScreen.kt \
   app/src/main/kotlin/com/piptechnologies/stickermaker/feature/customize/CustomizationViewModel.kt \
   app/src/main/kotlin/com/piptechnologies/stickermaker/core/design/components/ThemeTile.kt
rmdir app/src/main/kotlin/com/piptechnologies/stickermaker/feature/customize
```

- `K/core/ui/Themes.kt`: delete `Category.displayName()` (keep `themeNameRes` and `nameText`, Home uses them).
- `K/core/model/Category.kt`: drop the KDoc's mention of theme tiles and the customization grid.
- `K/core/design/Icons.kt`: for each of `Rabbit`, `Laugh`, `Sparkles`, `Flower2`, `MessageCircleHeart`, `Plane`, `Moon`, run `grep -rn "LoveIcons.<Name>" app/src` and delete the property when nothing matches.

- [ ] **Step 7: Remove the picker's strings from all 19 languages**

```bash
python3 - <<'EOF'
import pathlib, re
keys = ["customize_title", "customize_edit_title", "customize_subtitle", "customize_save",
        "customize_pick_one", "settings_edit_themes"]
plurals = ["customize_continue", "settings_theme_count"]
for path in sorted(pathlib.Path("app/src/main/res").glob("values*/strings.xml")):
    xml = path.read_text(encoding="utf-8")
    before = xml
    for k in keys:
        xml = re.sub(r'\n[ \t]*(<!--(?:(?!-->).)*-->\s*\n[ \t]*)?<string name="%s"[^>]*>.*?</string>' % k, "", xml, flags=re.S)
    for k in plurals:
        xml = re.sub(r'\n[ \t]*(<!--(?:(?!-->).)*-->\s*\n[ \t]*)?<plurals name="%s">.*?</plurals>' % k, "", xml, flags=re.S)
    path.write_text(xml, encoding="utf-8")
    print(path.parent.name, len(before) - len(xml), "chars removed")
EOF
grep -rn "customize_\|settings_edit_themes\|settings_theme_count" app/src/main/res || echo "all gone"
```

Expected: every folder reports a removal and the grep prints `all gone`. Open `values/strings.xml` and check that the "Themes" section header comment still sits above `theme_couples` (the theme names stay: Home's chips and search use them).

- [ ] **Step 8: Tour tests stop using the picker**

`…/tour/LocaleTourTest.kt:74`: `compose.waitFor(hasClickLabel(text(tag, R.string.settings_language)))`; the comment at `:149` becomes `/** A catalog pack Home shows in every language. */`.

`…/tour/ScreenTourTest.kt`:
- `t01_firstRun`: delete the whole `step("Customization first run") { … }` block (Get started now lands on Home); the `x01` note becomes `"Back online, Retry reloads the catalog from the Firestore emulator; every theme shows."`; update the section comment to `// 1. First run, offline: launch, onboarding, offline Home.`
- `t02_themesAndMissingWhatsApp`: rename to `t02_missingWhatsApp`, delete `step("Edit themes") { … }`, section comment `// 2. WhatsApp missing.`
- the HOME_TRENDING note becomes `"Every theme on Home; two packs added, two hearted (♥ Saved chip shows)."`
- the two `compose.waitFor(hasClickLabel("Edit themes"))` "Settings is open" markers (around `:455` and `:500`) become `compose.waitFor(hasClickLabel("Language"))`;
- fix the existing drift at `:502`: `compose.waitFor(hasText("Off. You won't get alerts or updates."))`;
- delete `private fun setTheme(...)`.

`Frame.kt` keeps its entries; Task 15 replaces frames 4 and 5.

- [ ] **Step 9: Verify**

```bash
export JAVA_HOME=~/Library/Java/JavaVirtualMachines/corretto-17.0.18/Contents/Home ANDROID_HOME=~/Library/Android/sdk
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest
grep -rn "Customiz\|selectedThemes\|FallbackCategories" app/src --include='*.kt' | grep -v "core/model/FallbackCategories.kt\|HomeViewModel.kt\|tour/Frame.kt"
```

Expected: BUILD SUCCESSFUL, all unit tests pass (the string tests prove the 19 files still match), and the grep prints nothing. (The glob is quoted because the shell is zsh; `tour/Frame.kt` keeps its two customization entries until Task 15.)

---

### Task 2: Name model and name rules

**Files:**
- Create: `K/feature/namepack/engine/NameModel.kt`, `K/feature/namepack/engine/NameInput.kt`
- Test: `app/src/test/kotlin/com/piptechnologies/stickermaker/feature/namepack/NameInputTest.kt`

**Interfaces:**
- Produces:
  - `enum class Character(val id: String, val label: String) { MANGO, PINKY, BUNNY, CAPY }` with `Character.byId(id: String?): Character`
  - `enum class Relation(val id: String, val family: Boolean) { GIRLFRIEND, BOYFRIEND, WIFE, HUSBAND, CRUSH, PARTNER, MOM, FRIEND }` with `Relation.byId`
  - `enum class Tone(val id: String) { SWEET, FLIRTY }` with `Tone.byId`
  - `enum class Slot(val key: String)` (12 slots, grid order) with `Slot.byKey(key: String): Slot?`
  - `object NameInput { MAX_GRAPHEMES = 14; normalize(raw): String; graphemeCount(text): Int; clamp(raw): String; isLetterable(text): Boolean; initial(name, locale): String }`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.piptechnologies.stickermaker.feature.namepack

import com.piptechnologies.stickermaker.feature.namepack.engine.NameInput
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NameInputTest {

    @Test
    fun normalizeTrimsAndCollapsesSpaces() {
        assertEquals("Sara Lee", NameInput.normalize("  Sara    Lee "))
        assertEquals("", NameInput.normalize("   "))
    }

    @Test
    fun countsGraphemesNotChars() {
        assertEquals(3, NameInput.graphemeCount("Zoë"))
        assertEquals(4, NameInput.graphemeCount("سارة"))
        assertEquals(0, NameInput.graphemeCount(""))
    }

    @Test
    fun clampCutsAPasteAtFourteenGraphemes() {
        assertEquals("Anastasia-Mari", NameInput.clamp("Anastasia-Maria"))
        assertEquals("Sara", NameInput.clamp("Sara"))
        assertEquals(14, NameInput.graphemeCount(NameInput.clamp("x".repeat(40))))
        assertEquals("Zoë" + "x".repeat(11), NameInput.clamp("Zoë" + "x".repeat(20)))
    }

    @Test
    fun lettersMarksDigitsAndNamePunctuationAreLetterable() {
        listOf(
            "Sara", "Anastasia-Mari", "O'Brien", "O’Brien", "J.Lo", "Zoë", "Sara 2",
            "سارة", "Ɗanjuma", "נועה", "می‌خواهم", "प्रिया", "Саша", "小雨", "မေ"
        ).forEach { assertTrue(it, NameInput.isLetterable(it)) }
    }

    @Test
    fun emojiSymbolsAndOtherPunctuationAreNot() {
        listOf("Sara 😍", "❤", "Sara❤️", "1️⃣", "Sara!", "@sara", "☀", "Sara‍")
            .forEach { assertFalse(it, NameInput.isLetterable(it)) }
    }

    @Test
    fun initialIsTheFirstGraphemeUpperCasedWhereTheScriptHasCase() {
        assertEquals("S", NameInput.initial(" sara", Locale.ENGLISH))
        assertEquals("İ", NameInput.initial("ipek", Locale.forLanguageTag("tr")))
        assertEquals("س", NameInput.initial("سارة", Locale.forLanguageTag("ar")))
        assertEquals("Z", NameInput.initial("zoë", Locale.ENGLISH))
        assertEquals("", NameInput.initial("  ", Locale.ENGLISH))
    }
}
```

- [ ] **Step 2: Run it to see it fail**

Run: `./gradlew :app:testDebugUnitTest --tests '*NameInputTest*'`
Expected: compilation FAILS, `NameInput` unresolved.

- [ ] **Step 3: Write the model**

`K/feature/namepack/engine/NameModel.kt`:

```kotlin
package com.piptechnologies.stickermaker.feature.namepack.engine

/** The four template characters, in the design's tile order ("Who says it?"). Labels are proper names. */
enum class Character(val id: String, val label: String) {
    MANGO("mango", "Mango"),
    PINKY("pinky", "Pinky"),
    BUNNY("bunny", "Bunny"),
    CAPY("capy", "Capy");

    companion object {
        fun byId(id: String?): Character = entries.firstOrNull { it.id == id } ?: MANGO
    }
}

/** Who the pack is for, in chip order. Mom and Friend are family: Sweet only, family phrases. */
enum class Relation(val id: String, val family: Boolean = false) {
    GIRLFRIEND("girlfriend"),
    BOYFRIEND("boyfriend"),
    WIFE("wife"),
    HUSBAND("husband"),
    CRUSH("crush"),
    PARTNER("partner"),
    MOM("mom", family = true),
    FRIEND("friend", family = true);

    companion object {
        fun byId(id: String?): Relation = entries.firstOrNull { it.id == id } ?: GIRLFRIEND
    }
}

/** The pack's tone. Nothing explicit: Flirty is playful. */
enum class Tone(val id: String) {
    SWEET("sweet"),
    FLIRTY("flirty");

    companion object {
        fun byId(id: String?): Tone = entries.firstOrNull { it.id == id } ?: SWEET
    }
}

/** The 12 phrase slots, in template and grid order. [key] is the key in the design's JSON files. */
enum class Slot(val key: String) {
    LOVE_YOU("love_you"),
    MISS_YOU("miss_you"),
    GOOD_MORNING("good_morning"),
    GOOD_NIGHT("good_night"),
    KISS("kiss"),
    HUG("hug"),
    SORRY("sorry"),
    BE_MINE("be_mine"),
    FOR_YOU("for_you"),
    CALL_ME("call_me"),
    OUR_NAMES("our_names"),
    NAME_ONLY("name_only");

    companion object {
        fun byKey(key: String): Slot? = entries.firstOrNull { it.key == key }
    }
}
```

- [ ] **Step 4: Write the rules**

`K/feature/namepack/engine/NameInput.kt`:

```kotlin
package com.piptechnologies.stickermaker.feature.namepack.engine

import java.text.BreakIterator
import java.util.Locale

/**
 * The name rules of the Custom Stickers flow (spec, "Names"): at most 14 grapheme
 * clusters; letters, marks, digits, spaces, apostrophes, hyphens, dots and ZWNJ;
 * lettered verbatim after trimming and collapsing spaces. On Android
 * java.text.BreakIterator is ICU, so clusters follow Unicode's rules.
 */
object NameInput {

    const val MAX_GRAPHEMES = 14

    private val SPACES = Regex("[\\s\\u00A0\\u2007\\u202F]+")

    /** Space, apostrophes (' ’ ‘), hyphens (- ‐), dot, Hebrew geresh/gershayim, ZWNJ. */
    private val NAME_PUNCTUATION = setOf(
        0x20, 0x27, 0x2019, 0x2018, 0x2D, 0x2010, 0x2E, 0x05F3, 0x05F4, 0x200C
    )

    /** Trims and collapses every run of spaces to one ASCII space. */
    fun normalize(raw: String): String = raw.replace(SPACES, " ").trim()

    /** Grapheme clusters in [text]: what a reader counts as characters. */
    fun graphemeCount(text: String): Int {
        if (text.isEmpty()) return 0
        val iterator = BreakIterator.getCharacterInstance().apply { setText(text) }
        var count = 0
        while (iterator.next() != BreakIterator.DONE) count++
        return count
    }

    /** [raw] cut to its first [MAX_GRAPHEMES] clusters (typing stops there; a paste can overshoot). */
    fun clamp(raw: String): String {
        val iterator = BreakIterator.getCharacterInstance().apply { setText(raw) }
        var end = 0
        repeat(MAX_GRAPHEMES) {
            val next = iterator.next()
            if (next == BreakIterator.DONE) return raw
            end = next
        }
        return raw.substring(0, end)
    }

    /** Whether every character can be lettered: no emoji, pictographs, symbols or other punctuation. */
    fun isLetterable(text: String): Boolean = text.codePoints().allMatch { cp ->
        java.lang.Character.isLetter(cp) || java.lang.Character.isDigit(cp) || cp in NAME_PUNCTUATION || isNameMark(cp)
    }

    /** First grapheme of [name], upper-cased in [locale] where the script has case (tray initials). */
    fun initial(name: String, locale: Locale): String {
        val trimmed = normalize(name)
        if (trimmed.isEmpty()) return ""
        val iterator = BreakIterator.getCharacterInstance().apply { setText(trimmed) }
        val end = iterator.next().takeIf { it != BreakIterator.DONE } ?: trimmed.length
        return trimmed.substring(0, end).uppercase(locale)
    }

    /** Vowel signs and diacritics, but not the variation selectors emoji ride on. */
    private fun isNameMark(cp: Int): Boolean {
        if (cp in 0xFE00..0xFE0F || cp in 0xE0100..0xE01EF) return false
        // Fully qualified: in this package `Character` is the name-pack enum (NameModel.kt).
        val type = java.lang.Character.getType(cp)
        return type == java.lang.Character.NON_SPACING_MARK.toInt() || type == java.lang.Character.COMBINING_SPACING_MARK.toInt()
    }
}
```

- [ ] **Step 5: Run the test**

Run: `./gradlew :app:testDebugUnitTest --tests '*NameInputTest*'`
Expected: 6 tests PASS.

---

### Task 3: Template assets and their parser

**Files:**
- Create: `app/src/main/assets/templates/` (copied), `K/feature/namepack/engine/TemplateSet.kt`
- Test: `app/src/test/kotlin/com/piptechnologies/stickermaker/feature/namepack/TemplateSetTest.kt`

**Interfaces:**
- Consumes: `Character`, `Slot` (Task 2); `com.piptechnologies.stickermaker.whatsapp.WebpInfo.parse(bytes): WebpInfo` (`width`, `height`, `isAnimated`, `hasAlpha`).
- Produces:
  - `data class Zone(val cx: Float, val cy: Float, val w: Float, val h: Float, val rotate: Float)`
  - `data class TemplateSticker(val file: String, val slot: Slot, val zone: Zone, val emojis: List<String>, val ink: Int, val stroke: Float, val strokeColor: Int)`
  - `data class TemplateSet(val character: Character, val stickers: List<TemplateSticker>)` with `sticker(slot): TemplateSticker`; companion `CANVAS = 512`, `assetPath(character): String` (`"templates/<id>.json"`), `artPath(file): String` (`"templates/<file>"`), `parse(character, json): TemplateSet`, `parseColor(hex): Int`.

- [ ] **Step 1: Copy Claude Design's files into the APK assets (names unchanged)**

```bash
: "${SCRATCH:?export SCRATCH=<session scratchpad> first}"
mkdir -p app/src/main/assets/templates
for c in mango pinky bunny capy; do
  for n in $(seq 1 12); do cp "$SCRATCH/design-sync/assets/templates/$c-$n.webp" app/src/main/assets/templates/; done
  cp "$SCRATCH/design-sync/assets/templates/$c.json" app/src/main/assets/templates/
done
cp "$SCRATCH/design-sync/assets/templates/mango-wait.webp" app/src/main/assets/templates/
ls app/src/main/assets/templates | wc -l
```

Expected: `53`. (`phrases.json` arrives in Task 4.)

Then fix one sign in Claude Design's data. Every character's `be_mine` sign board tilts clockwise (right edge lower, about +5° to +9° measured from the art), but the JSONs give the zone `rotate` −8 (Mango), −3 (Pinky), −4 (Bunny), −3 (Capy), which tilts "Be mine" uphill against the board. `rotate` is clockwise-positive (as `Canvas.rotate` and CSS `rotate` apply it), so flip the sign and keep the magnitudes: in each of the four copied JSONs change only the `be_mine` zone's `"rotate"` to `8`, `3`, `4` and `3`.

- [ ] **Step 2: Write the failing test**

```kotlin
package com.piptechnologies.stickermaker.feature.namepack

import com.piptechnologies.stickermaker.feature.namepack.engine.Character
import com.piptechnologies.stickermaker.feature.namepack.engine.Slot
import com.piptechnologies.stickermaker.feature.namepack.engine.TemplateSet
import com.piptechnologies.stickermaker.feature.namepack.engine.Zone
import com.piptechnologies.stickermaker.whatsapp.WebpInfo
import java.io.File
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** org.json is Android's, so this runs under Robolectric. Files are read from the source tree. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class TemplateSetTest {

    private val dir = File("src/main/assets/templates")

    @Test
    fun everyCharacterHasTwelveValidTemplates() {
        Character.entries.forEach { character ->
            val set = TemplateSet.parse(character, File(dir, "${character.id}.json").readText())
            assertEquals(Slot.entries, set.stickers.map { it.slot })
            // The be_mine sign board tilts clockwise, so its lettering must too (Claude Design's files had the sign flipped).
            assertTrue("${character.id} be_mine tilts with its board", set.sticker(Slot.BE_MINE).zone.rotate > 0f)
            set.stickers.forEach { s ->
                assertTrue("${s.file}: 1–3 emojis", s.emojis.size in 1..3)
                val (left, top, right, bottom) = bounds(s.zone)
                assertTrue("${s.file}: zone inside the canvas", left >= 0 && top >= 0 && right <= 512 && bottom <= 512)
                assertTrue("${s.file}: zone big enough to letter", s.zone.w >= 40f && s.zone.h >= 30f)
                val info = WebpInfo.parse(File(dir, s.file).readBytes())
                assertEquals(s.file, 512, info.width)
                assertEquals(s.file, 512, info.height)
                assertFalse(s.file, info.isAnimated)
                assertTrue(s.file, info.hasAlpha)
            }
        }
    }

    @Test
    fun theWaitingStillIsBundled() {
        val info = WebpInfo.parse(File(dir, "mango-wait.webp").readBytes())
        assertEquals(512, info.width)
        assertFalse(info.isAnimated)
    }

    @Test
    fun aBrokenFileFailsLoudly() {
        assertThrows(IllegalArgumentException::class.java) {
            TemplateSet.parse(Character.MANGO, """{"canvas":512,"stickers":[]}""")
        }
        assertThrows(IllegalArgumentException::class.java) {
            TemplateSet.parse(Character.MANGO, """{"canvas":256,"stickers":[]}""")
        }
    }

    @Test
    fun parsesColours() {
        assertEquals(0xFFC23359.toInt(), TemplateSet.parseColor("#C23359"))
        assertEquals(0xFFFFFFFF.toInt(), TemplateSet.parseColor("#fff"))
        assertEquals(0xFF3B2114.toInt(), TemplateSet.parseColor("3B2114"))
    }

    /** The axis-aligned box of the rotated zone. */
    private fun bounds(z: Zone): List<Float> {
        val a = Math.toRadians(z.rotate.toDouble())
        val hw = (abs(z.w / 2 * cos(a)) + abs(z.h / 2 * sin(a))).toFloat()
        val hh = (abs(z.w / 2 * sin(a)) + abs(z.h / 2 * cos(a))).toFloat()
        return listOf(z.cx - hw, z.cy - hh, z.cx + hw, z.cy + hh)
    }
}
```

- [ ] **Step 3: Run it to see it fail**

Run: `./gradlew :app:testDebugUnitTest --tests '*TemplateSetTest*'`
Expected: compilation FAILS, `TemplateSet` unresolved.

- [ ] **Step 4: Write the parser**

`K/feature/namepack/engine/TemplateSet.kt`:

```kotlin
package com.piptechnologies.stickermaker.feature.namepack.engine

import org.json.JSONObject

/** Where a phrase goes on its template, in the 512 canvas: centre, box, and rotation in degrees, clockwise-positive (as Canvas.rotate applies it). */
data class Zone(val cx: Float, val cy: Float, val w: Float, val h: Float, val rotate: Float)

/**
 * One text-free pose from Claude Design (`assets/templates/<character>.json`): its art,
 * phrase slot, lettering zone, WhatsApp emojis, ink and optional white stroke.
 */
data class TemplateSticker(
    val file: String,
    val slot: Slot,
    val zone: Zone,
    val emojis: List<String>,
    val ink: Int,
    val stroke: Float,
    val strokeColor: Int,
)

/** A character's 12 templates, one per [Slot], in slot order. */
data class TemplateSet(val character: Character, val stickers: List<TemplateSticker>) {

    fun sticker(slot: Slot): TemplateSticker = stickers.first { it.slot == slot }

    companion object {
        const val CANVAS = 512

        fun assetPath(character: Character): String = "templates/${character.id}.json"

        fun artPath(file: String): String = "templates/$file"

        /** Parses Claude Design's JSON and fails loudly when a file breaks the template rules. */
        fun parse(character: Character, json: String): TemplateSet {
            val root = JSONObject(json)
            require(root.optInt("canvas") == CANVAS) { "${character.id}: canvas must be $CANVAS" }
            val array = root.getJSONArray("stickers")
            val stickers = (0 until array.length()).map { index ->
                val o = array.getJSONObject(index)
                val z = o.getJSONObject("zone")
                val emojiArray = o.getJSONArray("emojis")
                val slotKey = o.getString("slot")
                TemplateSticker(
                    file = o.getString("file"),
                    slot = requireNotNull(Slot.byKey(slotKey)) { "${character.id}: unknown slot $slotKey" },
                    zone = Zone(
                        cx = z.getDouble("cx").toFloat(),
                        cy = z.getDouble("cy").toFloat(),
                        w = z.getDouble("w").toFloat(),
                        h = z.getDouble("h").toFloat(),
                        rotate = z.optDouble("rotate", 0.0).toFloat()
                    ),
                    emojis = (0 until emojiArray.length()).map { emojiArray.getString(it) },
                    ink = parseColor(o.getString("ink")),
                    stroke = o.optDouble("stroke", 0.0).toFloat(),
                    strokeColor = parseColor(o.optString("strokeColor", "#FFFFFF"))
                )
            }
            val slots = stickers.map { it.slot }
            require(slots.size == Slot.entries.size && slots.toSet() == Slot.entries.toSet()) {
                "${character.id}: needs each of the 12 slots once, got $slots"
            }
            return TemplateSet(character, Slot.entries.map { slot -> stickers.first { it.slot == slot } })
        }

        /** "#RRGGBB", "RRGGBB" or "#RGB" as an opaque ARGB int. */
        fun parseColor(hex: String): Int {
            val raw = hex.removePrefix("#")
            val digits = if (raw.length == 3) raw.map { "$it$it" }.joinToString("") else raw
            require(digits.length == 6) { "Bad colour $hex" }
            return (0xFF000000L or digits.toLong(16)).toInt()
        }
    }
}
```

- [ ] **Step 5: Run the test**

Run: `./gradlew :app:testDebugUnitTest --tests '*TemplateSetTest*'`
Expected: 4 tests PASS.

---

### Task 4: Phrase book

**Files:**
- Create: `app/src/main/assets/templates/phrases.json` (merged), `K/feature/namepack/engine/PhraseBook.kt`
- Test: `app/src/test/kotlin/com/piptechnologies/stickermaker/feature/namepack/PhraseBookTest.kt`

**Interfaces:**
- Consumes: `Tone`, `Relation`, `Slot` (Task 2); `com.piptechnologies.stickermaker.feature.language.AppLanguages.entries` (tags).
- Produces: `class PhraseBook` with `languageFor(tag): String`, `template(tag, tone, relation, slot): String`, `text(tag, tone, relation, slot, you, love): String`, `languages(): Set<String>`, `internal raw(lang, set, slot): String?`; companion `FALLBACK = "en"`, `ASSET_PATH = "templates/phrases.json"`, `parse(json)`.

- [ ] **Step 1: Merge the phrase file**

Claude Design wrote en, ar, fr and hi; the other 15 app languages come from `$SCRATCH/phrases-extra.json`. Two design lines addressed a gender (fr family `be_mine` feminine, hi `be_mine` masculine) and are replaced with neutral ones, per the file's own rule.

```bash
: "${SCRATCH:?export SCRATCH=<session scratchpad> first}"
python3 - "$SCRATCH" <<'EOF'
import json, sys
scratch = sys.argv[1]
book = json.load(open(f"{scratch}/design-sync/assets/templates/phrases.json", encoding="utf-8"))
extra = json.load(open(f"{scratch}/phrases-extra.json", encoding="utf-8"))
book["fr"]["family"]["be_mine"] = "Personne n'est comme toi, {n}"
book["hi"]["sweet"]["be_mine"] = "हमेशा साथ रहना, {n}"
book["hi"]["flirty"]["be_mine"] = "बस तुम और मैं, {n}"
book["_"]["languages"] = ("en, ar, fr and hi from Claude Design; es, pt, pt-BR, id, tr, ur, de, ru, it, zh, "
                          "fa, he, ps, ha and my written for the app, pending native review.")
book.update(extra)
json.dump(book, open("app/src/main/assets/templates/phrases.json", "w", encoding="utf-8"), ensure_ascii=False, indent=2)
print(sorted(k for k in book if not k.startswith("_")))
EOF
```

Expected: the 19 app tags printed (`ar, de, en, es, fa, fr, ha, he, hi, id, it, my, ps, pt, pt-BR, ru, tr, ur, zh`).

- [ ] **Step 2: Write the failing test**

```kotlin
package com.piptechnologies.stickermaker.feature.namepack

import com.piptechnologies.stickermaker.feature.language.AppLanguages
import com.piptechnologies.stickermaker.feature.namepack.engine.PhraseBook
import com.piptechnologies.stickermaker.feature.namepack.engine.Relation
import com.piptechnologies.stickermaker.feature.namepack.engine.Slot
import com.piptechnologies.stickermaker.feature.namepack.engine.Tone
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class PhraseBookTest {

    private val book = PhraseBook.parse(File("src/main/assets/templates/phrases.json").readText())

    @Test
    fun everyAppLanguageHasItsOwnCompletePhrases() {
        AppLanguages.entries.forEach { lang ->
            assertEquals(lang.tag, book.languageFor(lang.tag))
            Tone.entries.forEach { tone ->
                Slot.entries.forEach { slot ->
                    val phrase = book.raw(lang.tag, tone.id, slot)
                    assertNotNull("${lang.tag}/${tone.id}/${slot.key} missing", phrase)
                    assertTrue("${lang.tag}/${tone.id}/${slot.key} names the love", phrase!!.contains("{n}"))
                    assertEquals(
                        "${lang.tag}/${tone.id}/${slot.key}: own name only in our_names",
                        slot == Slot.OUR_NAMES, phrase.contains("{a}")
                    )
                }
            }
            listOf(Slot.KISS, Slot.HUG, Slot.BE_MINE).forEach { slot ->
                assertNotNull("${lang.tag}/family/${slot.key} missing", book.raw(lang.tag, "family", slot))
                assertNotEquals(
                    "${lang.tag}: family ${slot.key} differs from sweet",
                    book.template(lang.tag, Tone.SWEET, Relation.GIRLFRIEND, slot),
                    book.template(lang.tag, Tone.SWEET, Relation.MOM, slot)
                )
            }
        }
    }

    @Test
    fun lettersNamesIntoPhrases() {
        assertEquals("Love you, Sara", book.text("en", Tone.SWEET, Relation.GIRLFRIEND, Slot.LOVE_YOU, "Aymen", "Sara"))
        assertEquals("Aymen ❤ Sara", book.text("en", Tone.FLIRTY, Relation.CRUSH, Slot.OUR_NAMES, "Aymen", "Sara"))
        assertEquals("أحبك يا سارة", book.text("ar", Tone.SWEET, Relation.WIFE, Slot.LOVE_YOU, "", "سارة"))
        assertEquals("Crazy about you, Sara", book.text("en", Tone.FLIRTY, Relation.PARTNER, Slot.LOVE_YOU, "", "Sara"))
    }

    @Test
    fun withoutYourNameOurNamesLettersTheirNameAlone() {
        assertEquals("Sara", book.text("en", Tone.SWEET, Relation.GIRLFRIEND, Slot.OUR_NAMES, "", "Sara"))
        assertEquals("Sara", book.text("en", Tone.SWEET, Relation.GIRLFRIEND, Slot.OUR_NAMES, "  ", "Sara"))
    }

    @Test
    fun familyStaysInItsOwnLanguageAndIsAlwaysSweet() {
        // The prototype fell back to English here ("Love you, سارة").
        assertEquals("أحبك يا سارة", book.text("ar", Tone.FLIRTY, Relation.MOM, Slot.LOVE_YOU, "", "سارة"))
        assertEquals("You're the best, Sara", book.text("en", Tone.FLIRTY, Relation.FRIEND, Slot.BE_MINE, "", "Sara"))
        assertEquals("Personne n'est comme toi, Léa", book.text("fr", Tone.SWEET, Relation.FRIEND, Slot.BE_MINE, "", "Léa"))
        assertEquals("Good night, Sara", book.text("en", Tone.FLIRTY, Relation.MOM, Slot.GOOD_NIGHT, "", "Sara"))
    }

    @Test
    fun languagesFallBackToTheirBaseThenEnglish() {
        assertEquals("pt-BR", book.languageFor("pt-BR"))
        assertEquals("pt", book.languageFor("pt-PT"))
        assertEquals("zh", book.languageFor("zh-CN"))
        assertEquals("en", book.languageFor("ja"))
        assertEquals("Good night, Sara", book.text("ja", Tone.SWEET, Relation.PARTNER, Slot.GOOD_NIGHT, "", "Sara"))
        assertEquals(AppLanguages.entries.map { it.tag }.toSet(), book.languages())
    }
}
```

- [ ] **Step 3: Run it to see it fail**

Run: `./gradlew :app:testDebugUnitTest --tests '*PhraseBookTest*'`
Expected: compilation FAILS, `PhraseBook` unresolved.

- [ ] **Step 4: Write the phrase book**

`K/feature/namepack/engine/PhraseBook.kt`:

```kotlin
package com.piptechnologies.stickermaker.feature.namepack.engine

import org.json.JSONObject

/**
 * The sticker phrases (`assets/templates/phrases.json`): `{lang: {sweet, flirty, family}}`
 * with `{n}` = their name and `{a}` = yours. Resolution per the spec:
 * family → the language's family, then its sweet, then English sweet (tone forced to Sweet);
 * otherwise the language's tone, its sweet, English's tone, English sweet.
 */
class PhraseBook private constructor(private val root: JSONObject) {

    /** The phrase language for app language [tag]: the tag itself, its base language, else English. */
    fun languageFor(tag: String): String {
        val base = tag.substringBefore('-')
        return when {
            root.has(tag) -> tag
            root.has(base) -> base
            else -> FALLBACK
        }
    }

    /** The phrase for [slot] with `{n}` and `{a}` still in it. */
    fun template(tag: String, tone: Tone, relation: Relation, slot: Slot): String {
        val lang = languageFor(tag)
        val chain = if (relation.family) {
            listOf(lang to FAMILY, lang to Tone.SWEET.id, FALLBACK to Tone.SWEET.id)
        } else {
            listOf(lang to tone.id, lang to Tone.SWEET.id, FALLBACK to tone.id, FALLBACK to Tone.SWEET.id)
        }
        return chain.firstNotNullOfOrNull { (language, set) -> raw(language, set, slot) }
            ?: error("No phrase for ${slot.key}")
    }

    /** The lettering of one sticker. [you] may be blank: our_names then letters [love] alone. */
    fun text(tag: String, tone: Tone, relation: Relation, slot: Slot, you: String, love: String): String {
        if (slot == Slot.OUR_NAMES && you.isBlank()) return love
        return template(tag, tone, relation, slot).replace("{a}", you).replace("{n}", love)
    }

    /** Languages with phrases (every key except the "_" notes). */
    fun languages(): Set<String> = root.keys().asSequence().filterNot { it.startsWith("_") }.toSet()

    /** The phrase exactly as the file has it, or null. */
    internal fun raw(lang: String, set: String, slot: Slot): String? =
        root.optJSONObject(lang)?.optJSONObject(set)?.optString(slot.key)?.takeIf { it.isNotEmpty() }

    companion object {
        const val FALLBACK = "en"
        const val ASSET_PATH = "templates/phrases.json"
        private const val FAMILY = "family"

        fun parse(json: String): PhraseBook = PhraseBook(JSONObject(json))
    }
}
```

- [ ] **Step 5: Run the test**

Run: `./gradlew :app:testDebugUnitTest --tests '*PhraseBookTest*'`
Expected: 5 tests PASS.

---

### Task 5: Fit, font choice, pack id and the bundled fonts

**Files:**
- Create: `app/src/main/res/font/lettering_baloo2.ttf`, `lettering_baloo_bhaijaan2.ttf`, `lettering_rubik.ttf`; `app/src/main/assets/licenses/OFL-Baloo2.txt`, `OFL-BalooBhaijaan2.txt`, `OFL-Rubik.txt`; `K/feature/namepack/engine/LetteringFit.kt`, `LetteringFonts.kt`, `NamePackId.kt`
- Test: `…/feature/namepack/LetteringFitTest.kt`, `LetteringFontsTest.kt`, `CmapCoverage.kt` (test helper), `NamePackIdTest.kt`

**Interfaces:**
- Produces:
  - `data class Fit(val size: Float, val lines: List<String>, val ok: Boolean)` with `val lineHeight: Float`
  - `object LetteringFit { fit(text: String, zoneW: Float, zoneH: Float, measure: (line: String, size: Float) -> Float): Fit }` and constants `MAX_SIZE 128f, MIN_SIZE 20f, STEP 2f, HEIGHT_RATIO 0.82f, LINE_HEIGHT 1.08f`
  - `enum class LetteringFont { BALOO, BALOO_BHAIJAAN, RUBIK, SYSTEM_BOLD }`
  - `object LetteringFonts { PASHTO_GAPS: Set<Int>; choose(text): LetteringFont; isRtlParagraph(text, rtlLanguage: Boolean): Boolean }`
  - `object NamePackId { PREFIX = "own-np-"; of(lang, you, love): String }`
  - Font resources `R.font.lettering_baloo2`, `R.font.lettering_baloo_bhaijaan2`, `R.font.lettering_rubik`

- [ ] **Step 1: Bundle the fonts and their licences**

The user approved these downloads (github.com/google/fonts, SIL OFL 1.1, no Reserved Font Names); they are already trimmed to the single 800 instance.

```bash
: "${SCRATCH:?export SCRATCH=<session scratchpad> first}"
cp "$SCRATCH/fonts/Baloo2-ExtraBold.ttf" app/src/main/res/font/lettering_baloo2.ttf
cp "$SCRATCH/fonts/BalooBhaijaan2-ExtraBold.ttf" app/src/main/res/font/lettering_baloo_bhaijaan2.ttf
cp "$SCRATCH/fonts/Rubik-ExtraBold.ttf" app/src/main/res/font/lettering_rubik.ttf
mkdir -p app/src/main/assets/licenses
cp "$SCRATCH/fonts/OFL-Baloo2.txt" "$SCRATCH/fonts/OFL-BalooBhaijaan2.txt" "$SCRATCH/fonts/OFL-Rubik.txt" app/src/main/assets/licenses/
ls -l app/src/main/res/font/lettering_*.ttf
```

Expected: three files of 421836, 182480 and 212140 bytes.

- [ ] **Step 2: Write the failing tests**

`LetteringFitTest.kt` (a fake measure: every character is half the text size wide):

```kotlin
package com.piptechnologies.stickermaker.feature.namepack

import com.piptechnologies.stickermaker.feature.namepack.engine.Fit
import com.piptechnologies.stickermaker.feature.namepack.engine.LetteringFit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LetteringFitTest {

    private val halfEm: (String, Float) -> Float = { line, size -> line.length * size * 0.5f }

    private fun fit(text: String, w: Float, h: Float): Fit = LetteringFit.fit(text, w, h, halfEm)

    @Test
    fun aShortNameFitsOneLineAtTheStartSize() {
        assertEquals(Fit(82f, listOf("Sara"), true), fit("Sara", 300f, 100f))
    }

    @Test
    fun theStartSizeIsCappedAt128() {
        assertEquals(128f, fit("Hi", 400f, 200f).size)
    }

    @Test
    fun twoLinesAtALargerSizeBeatOneLineAtASmallerSize() {
        // Start floor(150 × 0.82) = 123, so sizes are odd. One line needs size ≤ 31.5;
        // "Love you," / "Anastasia" (4.5 × size wide) first fits at 65.
        val result = fit("Love you, Anastasia", 300f, 150f)
        assertEquals(Fit(65f, listOf("Love you,", "Anastasia"), true), result)
    }

    @Test
    fun theSplitWithTheNarrowestLongerLineWins() {
        // At 54 both "Hi my | love Sara" (9 chars) and "Hi my love | Sara" (10 chars) fit; 9 wins.
        assertEquals(Fit(54f, listOf("Hi my", "love Sara"), true), fit("Hi my love Sara", 280f, 120f))
    }

    @Test
    fun theFirstSplitWinsATie() {
        assertEquals(listOf("Good", "morning, Sara"), fit("Good morning, Sara", 200f, 120f).lines)
    }

    @Test
    fun sizesStepDownByTwoFromTheStart() {
        val result = fit("abcdefghijklmnop", 300f, 60f) // start floor(49.2) = 49, fits once 8s ≤ 300 → 37
        assertEquals(37f, result.size)
        assertEquals(1, result.lines.size)
    }

    @Test
    fun theFloorIsTriedEvenFromAnOddStart() {
        // Start floor(150 × 0.82) = 123 steps 21 → 19, past the floor; "aaaaaaaaaa" is 105 wide at 21, 100 at 20.
        assertEquals(Fit(20f, listOf("aaaaaaaaaa"), true), fit("aaaaaaaaaa", 103f, 150f))
    }

    @Test
    fun neverThreeLinesAndNothingIsClippedAtTheFloor() {
        val result = fit("Anastasia-Mari", 100f, 40f)
        assertEquals(Fit(20f, listOf("Anastasia-Mari"), false), result)
        assertFalse(fit("one two three four five six seven", 60f, 50f).ok)
    }

    @Test
    fun lineHeightIsOnePointZeroEightTimesTheSize() {
        assertEquals(108f, Fit(100f, listOf("x"), true).lineHeight, 0.001f)
    }
}
```

`LetteringFontsTest.kt`:

```kotlin
package com.piptechnologies.stickermaker.feature.namepack

import com.piptechnologies.stickermaker.feature.namepack.engine.LetteringFont
import com.piptechnologies.stickermaker.feature.namepack.engine.LetteringFonts
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LetteringFontsTest {

    @Test
    fun latinDevanagariAndTurkishUseBaloo() {
        listOf("Love you, Sara", "प्यार है तुमसे, प्रिया", "İyi geceler, Şule", "Sumba daga gare ni, Ɗanjuma")
            .forEach { assertEquals(it, LetteringFont.BALOO, LetteringFonts.choose(it)) }
    }

    @Test
    fun arabicScriptUsesBhaijaanEvenInsideAnEnglishPhrase() {
        listOf("أحبك يا سارة", "Love you, سارة", "تم سے پیار ہے، سارہ", "دوستت دارم، سارا")
            .forEach { assertEquals(it, LetteringFont.BALOO_BHAIJAAN, LetteringFonts.choose(it)) }
    }

    @Test
    fun pashtoLettersBhaijaanLacksMoveTheWholeStringToTheSystemFont() {
        assertEquals(LetteringFont.SYSTEM_BOLD, LetteringFonts.choose("یو ښکل ستا لپاره، زرمینه"))
        assertEquals(LetteringFont.BALOO_BHAIJAAN, LetteringFonts.choose("سلام، زرمینه"))
    }

    @Test
    fun hebrewAndCyrillicUseRubik() {
        assertEquals(LetteringFont.RUBIK, LetteringFonts.choose("אהבה שלי, נועה"))
        assertEquals(LetteringFont.RUBIK, LetteringFonts.choose("Люблю тебя, Саша"))
    }

    @Test
    fun rtlLanguagesAlwaysLetterRightToLeftOthersFollowTheFirstStrongCharacter() {
        assertTrue(LetteringFonts.isRtlParagraph("Sara کے لیے", rtlLanguage = true))
        assertFalse(LetteringFonts.isRtlParagraph("Love you, سارة", rtlLanguage = false))
        assertTrue(LetteringFonts.isRtlParagraph("سارة", rtlLanguage = false))
        assertFalse(LetteringFonts.isRtlParagraph("12 ♥", rtlLanguage = false))
    }

    @Test
    fun bundledFontsCoverTheirScripts() {
        val baloo = font("lettering_baloo2.ttf")
        val bhaijaan = font("lettering_baloo_bhaijaan2.ttf")
        val rubik = font("lettering_rubik.ttf")
        assertCovers("Baloo 2", baloo, "Love you, Sara İyi geceler Şule Zoë Ñandú ß प्यार है तुमसे क्षत्रज्ञ")
        assertCovers("Baloo Bhaijaan 2", bhaijaan, "أحبك يا سارة دوستت دارم پچژگک تم سے پیار ہے ٹڈڑںہھے ۱۲٣ Love you، ؟")
        assertCovers("Rubik", rubik, "אהבה שלי נועה Люблю тебя Саша Ёё Love you")
        LetteringFonts.PASHTO_GAPS.forEach {
            assertFalse("Bhaijaan now has U+%04X: shrink PASHTO_GAPS".format(it), bhaijaan.covers(it))
        }
    }

    /** Keeps PASHTO_GAPS complete: an Arabic-script letter in a shipped phrase that Bhaijaan lacks must move its phrase to the system face. */
    @Test
    fun everyArabicScriptLetterBhaijaanLacksIsAGap() {
        val bhaijaan = font("lettering_baloo_bhaijaan2.ttf")
        File("src/main/assets/templates/phrases.json").readText().codePoints().distinct()
            .filter { it in 0x0600..0x06FF && Character.isLetter(it) && !bhaijaan.covers(it) }
            .forEach { assertTrue("Bhaijaan lacks U+%04X, used in a phrase: add it to PASHTO_GAPS".format(it), it in LetteringFonts.PASHTO_GAPS) }
    }

    private fun font(name: String): CmapCoverage = CmapCoverage.of(File("src/main/res/font/$name"))

    private fun assertCovers(name: String, font: CmapCoverage, text: String) {
        text.codePoints().filter { !Character.isWhitespace(it) }.forEach {
            assertTrue("$name lacks U+%04X".format(it), font.covers(it))
        }
    }
}
```

`CmapCoverage.kt` (test source set, same package). Unit tests compile against `android.jar`, which has no `java.awt.Font`, so coverage is read straight from each font's `cmap` table:

```kotlin
package com.piptechnologies.stickermaker.feature.namepack

import java.io.File
import java.nio.ByteBuffer

/**
 * The code points a TrueType font maps to a real glyph, read from its `cmap` table (the
 * format 12 subtable when present, else format 4). Big-endian, as the format is.
 */
internal class CmapCoverage private constructor(private val glyphOf: (Int) -> Int) {

    fun covers(codePoint: Int): Boolean = glyphOf(codePoint) != 0

    companion object {
        fun of(file: File): CmapCoverage {
            val b = ByteBuffer.wrap(file.readBytes())
            val cmap = (0 until u16(b, 4)).map { 12 + it * 16 }
                .firstOrNull { tag(b, it) == "cmap" }
                ?.let { b.getInt(it + 8) }
                ?: error("${file.name} has no cmap table")
            val unicode = (0 until u16(b, cmap + 2)).map { cmap + 4 + it * 8 }
                .filter { record ->
                    val platform = u16(b, record)
                    val encoding = u16(b, record + 2)
                    platform == 0 || (platform == 3 && (encoding == 1 || encoding == 10))
                }
                .map { record -> cmap + b.getInt(record + 4) }
            unicode.firstOrNull { u16(b, it) == 12 }?.let { return CmapCoverage(format12(b, it)) }
            unicode.firstOrNull { u16(b, it) == 4 }?.let { return CmapCoverage(format4(b, it)) }
            error("${file.name} has no Unicode cmap subtable")
        }

        /** Groups of (first code point, last code point, first glyph). */
        private fun format12(b: ByteBuffer, at: Int): (Int) -> Int {
            val groups = (0 until b.getInt(at + 12)).map { at + 16 + it * 12 }
            return { cp ->
                groups.firstOrNull { g -> cp >= b.getInt(g) && cp <= b.getInt(g + 4) }
                    ?.let { g -> b.getInt(g + 8) + (cp - b.getInt(g)) } ?: 0
            }
        }

        /** Segments of the Basic Multilingual Plane: end codes, start codes, deltas, range offsets. */
        private fun format4(b: ByteBuffer, at: Int): (Int) -> Int {
            val segments = u16(b, at + 6) / 2
            val ends = at + 14
            val starts = ends + segments * 2 + 2
            val deltas = starts + segments * 2
            val rangeOffsets = deltas + segments * 2
            return glyph@{ cp ->
                if (cp > 0xFFFF) return@glyph 0
                val i = (0 until segments).firstOrNull { u16(b, ends + it * 2) >= cp } ?: return@glyph 0
                val start = u16(b, starts + i * 2)
                if (cp < start) return@glyph 0
                val delta = b.getShort(deltas + i * 2).toInt()
                val rangeOffsetAt = rangeOffsets + i * 2
                val rangeOffset = u16(b, rangeOffsetAt)
                if (rangeOffset == 0) return@glyph (cp + delta) and 0xFFFF
                val g = u16(b, rangeOffsetAt + rangeOffset + (cp - start) * 2)
                if (g == 0) 0 else (g + delta) and 0xFFFF
            }
        }

        private fun u16(b: ByteBuffer, at: Int): Int = b.getShort(at).toInt() and 0xFFFF

        private fun tag(b: ByteBuffer, at: Int): String = String(ByteArray(4) { b.get(at + it) }, Charsets.US_ASCII)
    }
}
```

`NamePackIdTest.kt`:

```kotlin
package com.piptechnologies.stickermaker.feature.namepack

import com.piptechnologies.stickermaker.feature.namepack.engine.NamePackId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NamePackIdTest {

    @Test
    fun theSameNamesAndLanguageGiveTheSamePack() {
        assertEquals(NamePackId.of("en", "Aymen", "Sara"), NamePackId.of("en", "Aymen", "Sara"))
    }

    @Test
    fun anyChangeGivesANewPack() {
        val base = NamePackId.of("en", "Aymen", "Sara")
        assertNotEquals(base, NamePackId.of("ar", "Aymen", "Sara"))
        assertNotEquals(base, NamePackId.of("en", "", "Sara"))
        assertNotEquals(base, NamePackId.of("en", "Aymen", "Lea"))
        assertNotEquals(NamePackId.of("en", "ab", "c"), NamePackId.of("en", "a", "bc"))
    }

    @Test
    fun idsAreValidOwnPackIdentifiers() {
        val id = NamePackId.of("en", "أيمن", "سارة")
        assertTrue(id, id.matches(Regex("own-np-[0-9a-f]{12}")))
    }
}
```

- [ ] **Step 3: Run them to see them fail**

Run: `./gradlew :app:testDebugUnitTest --tests '*LetteringFitTest*' --tests '*LetteringFontsTest*' --tests '*NamePackIdTest*'`
Expected: compilation FAILS on the missing classes.

- [ ] **Step 4: Write the fit**

`K/feature/namepack/engine/LetteringFit.kt`:

```kotlin
package com.piptechnologies.stickermaker.feature.namepack.engine

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** How a phrase fits its zone: text size (px at 512), one or two lines, and whether it fit above the floor. */
data class Fit(val size: Float, val lines: List<String>, val ok: Boolean) {
    val lineHeight: Float get() = size * LetteringFit.LINE_HEIGHT
}

/**
 * The design's fit (spec, Rendering): start at floor(min(zone.h × 0.82, 128)) and step −2 to 20.
 * At each size one line wins if it fits the zone's width; otherwise, when two lines fit the
 * height, the split on a space whose longer line is narrowest. Never three lines. At the floor:
 * size 20, one line, overflow allowed, [Fit.ok] false.
 */
object LetteringFit {

    const val MAX_SIZE = 128f
    const val MIN_SIZE = 20f
    const val STEP = 2f
    const val HEIGHT_RATIO = 0.82f
    const val LINE_HEIGHT = 1.08f

    /** [measure] returns the advance width of a line at a text size. */
    fun fit(text: String, zoneW: Float, zoneH: Float, measure: (line: String, size: Float) -> Float): Fit {
        val spaces = text.indices.filter { text[it] == ' ' }

        /** One line if it fits, else the two-line split whose longer line is narrowest; null if neither fits. */
        fun at(size: Float): Fit? {
            if (measure(text, size) <= zoneW) return Fit(size, listOf(text), true)
            if (spaces.isEmpty() || 2 * size * LINE_HEIGHT > zoneH) return null
            var best: List<String>? = null
            var bestWidth = Float.MAX_VALUE
            for (i in spaces) {
                val first = text.substring(0, i).trimEnd()
                val second = text.substring(i + 1).trimStart()
                if (first.isEmpty() || second.isEmpty()) continue
                val width = max(measure(first, size), measure(second, size))
                if (width <= zoneW && width < bestWidth) {
                    bestWidth = width
                    best = listOf(first, second)
                }
            }
            return best?.let { Fit(size, it, true) }
        }

        val start = floor(min(zoneH * HEIGHT_RATIO, MAX_SIZE))
        var size = start
        while (size > MIN_SIZE) {
            at(size)?.let { return it }
            size -= STEP
        }
        // From an odd start the steps go 21 → 19, past the floor, so the floor itself is always tried.
        if (start >= MIN_SIZE) at(MIN_SIZE)?.let { return it }
        return Fit(MIN_SIZE, listOf(text), false)
    }
}
```

- [ ] **Step 5: Write the font choice**

`K/feature/namepack/engine/LetteringFonts.kt`:

```kotlin
package com.piptechnologies.stickermaker.feature.namepack.engine

/** The bundled lettering faces (all weight 800), plus the system bold for gaps. */
enum class LetteringFont { BALOO, BALOO_BHAIJAAN, RUBIK, SYSTEM_BOLD }

/**
 * Picks the face from the string (spec, Rendering): Arabic script → Baloo Bhaijaan 2, unless the
 * string holds a Pashto letter it lacks; Hebrew or Cyrillic → Rubik; anything else → Baloo 2.
 * Scripts none of them draw (Chinese, Burmese, Hausa hook letters) fall back per glyph.
 */
object LetteringFonts {

    /**
     * ټ ځ څ ډ ړ ږ ښ ګ ڼ ۍ ې: Pashto letters absent from Baloo Bhaijaan 2. A string holding one is lettered in the
     * system bold face whole, never with one letter from another font (LetteringFontsTest keeps
     * this list true and complete for the shipped phrases).
     */
    val PASHTO_GAPS: Set<Int> = setOf(
        0x067C, 0x0681, 0x0685, 0x0689, 0x0693, 0x0696, 0x069A, 0x06AB, 0x06BC, 0x06CD, 0x06D0
    )

    fun choose(text: String): LetteringFont {
        val cps = text.codePoints().toArray()
        return when {
            cps.any(::isArabicScript) ->
                if (cps.any { it in PASHTO_GAPS }) LetteringFont.SYSTEM_BOLD else LetteringFont.BALOO_BHAIJAAN
            cps.any { isHebrew(it) || isCyrillic(it) } -> LetteringFont.RUBIK
            else -> LetteringFont.BALOO
        }
    }

    /**
     * Paragraph direction of a phrase: right to left in RTL app languages (their phrases are
     * RTL even when a Latin name comes first), else set by the first strong character.
     */
    fun isRtlParagraph(text: String, rtlLanguage: Boolean): Boolean {
        if (rtlLanguage) return true
        for (cp in text.codePoints().toArray()) {
            // Fully qualified: in this package `Character` is the name-pack enum (NameModel.kt).
            when (java.lang.Character.getDirectionality(cp)) {
                java.lang.Character.DIRECTIONALITY_LEFT_TO_RIGHT -> return false
                java.lang.Character.DIRECTIONALITY_RIGHT_TO_LEFT, java.lang.Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC -> return true
            }
        }
        return false
    }

    private fun isArabicScript(cp: Int): Boolean =
        cp in 0x0600..0x06FF || cp in 0x0750..0x077F || cp in 0x08A0..0x08FF ||
            cp in 0xFB50..0xFDFF || cp in 0xFE70..0xFEFF

    private fun isHebrew(cp: Int): Boolean = cp in 0x0590..0x05FF

    private fun isCyrillic(cp: Int): Boolean = cp in 0x0400..0x04FF
}
```

- [ ] **Step 6: Write the pack id**

`K/feature/namepack/engine/NamePackId.kt`:

```kotlin
package com.piptechnologies.stickermaker.feature.namepack.engine

import java.security.MessageDigest

/**
 * The WhatsApp identifier of a name pack: `own-np-` + 12 hex of SHA-256 over the language and
 * both normalized names. The same names and language re-letter the same pack (tone, character
 * and relation do not change it); the `own-` prefix keeps analytics reporting it as "own".
 */
object NamePackId {

    const val PREFIX = "own-np-"

    fun of(lang: String, you: String, love: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$lang\u0000$you\u0000$love".toByteArray(Charsets.UTF_8))
        return PREFIX + digest.take(6).joinToString("") { "%02x".format(it) }
    }
}
```

- [ ] **Step 7: Run the tests**

Run: `./gradlew :app:testDebugUnitTest --tests '*LetteringFitTest*' --tests '*LetteringFontsTest*' --tests '*NamePackIdTest*'`
Expected: 9 + 7 + 3 tests PASS.

---

### Task 6: Lettering renderer, composite and tray

**Files:**
- Create: `K/feature/namepack/engine/HeartPath.kt`, `Lettering.kt`, `StickerComposer.kt`, `TrayRenderer.kt`
- Modify: `K/feature/create/StickerRenderer.kt` (`encodeStaticSticker` takes its quality ladder as a parameter)
- Test: `…/feature/namepack/NamePackRenderTest.kt`, `…/feature/namepack/NamePackSheetsTest.kt`

**Interfaces:**
- Consumes: Tasks 2–5; `StickerRenderer.outlineOf(mask: Bitmap, radiusPx: Float): Bitmap`, `StickerRenderer.encodeStaticSticker(bitmap: Bitmap, limitBytes: Int = CreateSpec.STATIC_LIMIT_BYTES, qualities: IntArray): ByteArray` (this task adds `qualities`), `CreateSpec.TRAY_LIMIT_BYTES` (feature/create).
- Produces:
  - `object HeartPath { fun inBox(left: Float, top: Float, size: Float): Path }`
  - `class Lettering(typefaceFor: (LetteringFont) -> Typeface) { fun fit(text: String, sticker: TemplateSticker): Fit; fun draw(canvas: Canvas, text: String, sticker: TemplateSticker, rtlLanguage: Boolean): Fit }`
  - `class StickerComposer(lettering: Lettering) { fun compose(art: Bitmap, sticker: TemplateSticker, text: String, rtlLanguage: Boolean): Bitmap }`; companion `OUTLINE_PX = 8f`, `encode(bitmap): ByteArray`, `withOutline(body: Bitmap, radiusPx: Float): Bitmap`
  - `class TrayRenderer(typefaceFor: (LetteringFont) -> Typeface) { fun render(initials: String, size: Int = 96): Bitmap }`; companion `encode(bitmap): ByteArray`, `ROSE = 0xFFC23359.toInt()`

- [ ] **Step 1: Write the failing render test**

`NamePackRenderTest.kt` runs Robolectric with native graphics (real Skia text, WebP and PNG; checked on this Mac):

```kotlin
package com.piptechnologies.stickermaker.feature.namepack

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import com.piptechnologies.stickermaker.feature.language.AppLanguages
import com.piptechnologies.stickermaker.feature.namepack.engine.Character
import com.piptechnologies.stickermaker.feature.namepack.engine.Lettering
import com.piptechnologies.stickermaker.feature.namepack.engine.LetteringFont
import com.piptechnologies.stickermaker.feature.namepack.engine.PhraseBook
import com.piptechnologies.stickermaker.feature.namepack.engine.Relation
import com.piptechnologies.stickermaker.feature.namepack.engine.StickerComposer
import com.piptechnologies.stickermaker.feature.namepack.engine.TemplateSet
import com.piptechnologies.stickermaker.feature.namepack.engine.Tone
import com.piptechnologies.stickermaker.feature.namepack.engine.TrayRenderer
import com.piptechnologies.stickermaker.feature.namepack.engine.Zone
import com.piptechnologies.stickermaker.whatsapp.WebpInfo
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class)
class NamePackRenderTest {

    private val templates = File("src/main/assets/templates")
    private val lettering = Lettering(TestFonts::of)
    private val composer = StickerComposer(lettering)
    private val phrases = PhraseBook.parse(File(templates, "phrases.json").readText())

    @Test
    fun everyStickerIsAWhatsAppSizedWebpWithItsLettering() {
        val set = TemplateSet.parse(Character.MANGO, File(templates, "mango.json").readText())
        set.stickers.forEach { sticker ->
            val art = BitmapFactory.decodeFile(File(templates, sticker.file).path)
            val text = phrases.text("en", Tone.SWEET, Relation.GIRLFRIEND, sticker.slot, "Aymen", "Sara")
            val out = composer.compose(art, sticker, text, rtlLanguage = false)
            assertEquals(512, out.width)
            assertEquals(512, out.height)
            val webp = StickerComposer.encode(out)
            assertTrue("${sticker.file}: ${webp.size} bytes", webp.size <= 100 * 1024)
            val info = WebpInfo.parse(webp)
            assertEquals(512, info.width)
            assertFalse(info.isAnimated)
            assertTrue("${sticker.file}: lettering drawn", changedPixels(art, out, sticker.zone) > 150)
        }
    }

    @Test
    fun sampleNamesFitEveryZoneInEveryLanguageAndTone() {
        val misses = mutableListOf<String>()
        Character.entries.forEach { character ->
            val set = TemplateSet.parse(character, File(templates, "${character.id}.json").readText())
            AppLanguages.entries.forEach { lang ->
                Tone.entries.forEach { tone ->
                    set.stickers.forEach { s ->
                        val text = phrases.text(lang.tag, tone, Relation.GIRLFRIEND, s.slot, "Aymen", "Sara")
                        if (!lettering.fit(text, s).ok) misses += "${character.id}/${lang.tag}/${tone.id}/${s.slot.key}: $text"
                    }
                }
            }
        }
        assertTrue("Phrases that hit the 20 px floor:\n" + misses.joinToString("\n"), misses.isEmpty())
    }

    @Test
    fun theTrayIsASmallPngHeart() {
        val tray = TrayRenderer(TestFonts::of)
        listOf("AS", "S", "أس").forEach { initials ->
            val bitmap = tray.render(initials)
            assertEquals(96, bitmap.width)
            val png = TrayRenderer.encode(bitmap)
            assertTrue("$initials: ${png.size} bytes", png.size <= 50 * 1024)
            assertEquals(0x89.toByte(), png[0])
            assertEquals('P'.code.toByte(), png[1])
            // The heart is rose at its left lobe, and the white initials sit in its middle.
            assertEquals(TrayRenderer.ROSE, bitmap.getPixel(20, 40))
            val white = (24 until 72).sumOf { x -> (20 until 64).count { y -> bitmap.getPixel(x, y) == Color.WHITE } }
            assertTrue("$initials: no white initials inside the heart", white > 20)
        }
    }

    /** Pixels inside the zone's box that the lettering changed. */
    private fun changedPixels(art: Bitmap, out: Bitmap, zone: Zone): Int {
        var changed = 0
        val left = (zone.cx - zone.w / 2).toInt().coerceIn(0, 511)
        val right = (zone.cx + zone.w / 2).toInt().coerceIn(0, 511)
        val top = (zone.cy - zone.h / 2).toInt().coerceIn(0, 511)
        val bottom = (zone.cy + zone.h / 2).toInt().coerceIn(0, 511)
        for (y in top..bottom step 2) for (x in left..right step 2) {
            if (art.getPixel(x, y) != out.getPixel(x, y)) changed++
        }
        return changed
    }
}

/** The bundled fonts loaded from the source tree (the app loads them as font resources). */
internal object TestFonts {
    private val faces by lazy {
        mapOf(
            LetteringFont.BALOO to Typeface.createFromFile(File("src/main/res/font/lettering_baloo2.ttf")),
            LetteringFont.BALOO_BHAIJAAN to Typeface.createFromFile(File("src/main/res/font/lettering_baloo_bhaijaan2.ttf")),
            LetteringFont.RUBIK to Typeface.createFromFile(File("src/main/res/font/lettering_rubik.ttf")),
            LetteringFont.SYSTEM_BOLD to Typeface.DEFAULT_BOLD
        )
    }

    fun of(font: LetteringFont): Typeface = faces.getValue(font)
}
```

`NamePackSheetsTest.kt` writes contact sheets for checking by eye. It renders 456 composites, too slow for every run, so it only runs when `NAMEPACK_SHEETS=1` is set, and then it checks that this run wrote every sheet:

```kotlin
package com.piptechnologies.stickermaker.feature.namepack

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import com.piptechnologies.stickermaker.feature.language.AppLanguages
import com.piptechnologies.stickermaker.feature.namepack.engine.Character
import com.piptechnologies.stickermaker.feature.namepack.engine.Lettering
import com.piptechnologies.stickermaker.feature.namepack.engine.PhraseBook
import com.piptechnologies.stickermaker.feature.namepack.engine.Relation
import com.piptechnologies.stickermaker.feature.namepack.engine.StickerComposer
import com.piptechnologies.stickermaker.feature.namepack.engine.TemplateSet
import com.piptechnologies.stickermaker.feature.namepack.engine.Tone
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders 4 × 3 sheets to app/build/namepack-sheets/<lang>-<character>-<tone>.png so a person can
 * check the lettering in every script. Opt-in, since it renders 456 composites:
 * `NAMEPACK_SHEETS=1 ./gradlew :app:testDebugUnitTest --tests '*NamePackSheetsTest*' --rerun`
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class)
class NamePackSheetsTest {

    private val templates = File("src/main/assets/templates")
    private val composer = StickerComposer(Lettering(TestFonts::of))
    private val phrases = PhraseBook.parse(File(templates, "phrases.json").readText())
    private val out = File("build/namepack-sheets")

    private val names = mapOf(
        "en" to ("Aymen" to "Sara"), "ar" to ("أيمن" to "سارة"), "hi" to ("आयमन" to "प्रिया"),
        "ru" to ("Айман" to "Саша"), "he" to ("איימן" to "נועה"), "fa" to ("ایمن" to "سارا"),
        "ur" to ("ایمن" to "سارہ"), "ps" to ("ایمن" to "زرمینه"), "zh" to ("艾门" to "小雨"),
        "my" to ("အိုင်မန်" to "မေ"), "ha" to ("Aymen" to "Ɗanjuma"), "tr" to ("Aymen" to "Elif"),
        "de" to ("" to "Anastasia-Mari")
    )

    @Before
    fun onlyWhenAsked() {
        Assume.assumeTrue("set NAMEPACK_SHEETS=1 to render the contact sheets", System.getenv("NAMEPACK_SHEETS") == "1")
    }

    @Test
    fun writesSheets() {
        out.deleteRecursively()
        out.mkdirs()
        var expected = 0
        names.forEach { (lang, pair) ->
            val characters = if (lang == "en" || lang == "ar") Character.entries else listOf(Character.MANGO)
            characters.forEach { character ->
                Tone.entries.forEach { tone ->
                    sheet(lang, character, tone, pair.first, pair.second)
                    expected++
                }
            }
        }
        assertEquals(expected, out.listFiles { f -> f.name.endsWith(".png") }!!.size)
    }

    private fun sheet(lang: String, character: Character, tone: Tone, you: String, love: String) {
        val set = TemplateSet.parse(character, File(templates, "${character.id}.json").readText())
        val rtl = AppLanguages.byTag(lang)?.rtl == true
        val cell = 256
        val sheet = Bitmap.createBitmap(cell * 4, cell * 3, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet).apply { drawColor(Color.rgb(235, 238, 242)) }
        set.stickers.forEachIndexed { i, sticker ->
            val art = BitmapFactory.decodeFile(File(templates, sticker.file).path)
            val text = phrases.text(lang, tone, Relation.GIRLFRIEND, sticker.slot, you, love)
            val img = composer.compose(art, sticker, text, rtl)
            val x = (i % 4) * cell
            val y = (i / 4) * cell
            canvas.drawBitmap(img, null, Rect(x, y, x + cell, y + cell), null)
        }
        File(out, "$lang-${character.id}-${tone.id}.png").outputStream().use {
            sheet.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :app:testDebugUnitTest --tests '*NamePackRenderTest*'`
Expected: compilation FAILS on `Lettering`, `StickerComposer`, `TrayRenderer`.

- [ ] **Step 3: Write the heart**

`K/feature/namepack/engine/HeartPath.kt`:

```kotlin
package com.piptechnologies.stickermaker.feature.namepack.engine

import android.graphics.Matrix
import android.graphics.Path

/**
 * The app-mark heart from Claude Design (viewBox 24:
 * `M12 21s-7.5-4.7-9.6-9.2C.7 8 3 4.5 6.6 4.5c2 0 3.6 1.1 4.4 2.6.8-1.5 2.4-2.6 4.4-2.6 3.6 0 5.9 3.5 4.2 7.3C19.5 16.3 12 21 12 21z`)
 * in absolute cubic form. Hand-drawn and slightly asymmetric: keep it as it is.
 */
object HeartPath {

    private fun unit(): Path = Path().apply {
        moveTo(12f, 21f)
        cubicTo(12f, 21f, 4.5f, 16.3f, 2.4f, 11.8f)
        cubicTo(0.7f, 8f, 3f, 4.5f, 6.6f, 4.5f)
        cubicTo(8.6f, 4.5f, 10.2f, 5.6f, 11f, 7.1f)
        cubicTo(11.8f, 5.6f, 13.4f, 4.5f, 15.4f, 4.5f)
        cubicTo(19f, 4.5f, 21.3f, 8f, 19.6f, 11.8f)
        cubicTo(19.5f, 16.3f, 12f, 21f, 12f, 21f)
        close()
    }

    /** The heart's 24-unit box scaled to [size] px with its top-left at ([left], [top]). */
    fun inBox(left: Float, top: Float, size: Float): Path = unit().apply {
        transform(Matrix().apply {
            setScale(size / 24f, size / 24f)
            postTranslate(left, top)
        })
    }
}
```

- [ ] **Step 4: Write the lettering**

`K/feature/namepack/engine/Lettering.kt`:

```kotlin
package com.piptechnologies.stickermaker.feature.namepack.engine

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.text.style.ReplacementSpan
import kotlin.math.ceil
import kotlin.math.max

/**
 * Letters a phrase into its template zone (spec, Rendering): font from the string, the fit,
 * line height 1.08 × size, tracking −0.01 em, the block centred on the zone and rotated with it,
 * the white stroke painted before the ink fill, "❤" drawn as the app-mark heart.
 */
class Lettering(private val typefaceFor: (LetteringFont) -> Typeface) {

    /** The size and lines [draw] would use, without drawing. */
    fun fit(text: String, sticker: TemplateSticker): Fit {
        val paint = paintFor(text, sticker)
        return LetteringFit.fit(text, sticker.zone.w, sticker.zone.h) { line, size ->
            paint.textSize = size
            Layout.getDesiredWidth(spanned(line), paint)
        }
    }

    /** Draws [text] into [sticker]'s zone on a 512 canvas; returns the fit it used. */
    fun draw(canvas: Canvas, text: String, sticker: TemplateSticker, rtlLanguage: Boolean): Fit {
        val zone = sticker.zone
        val paint = paintFor(text, sticker)
        val fit = LetteringFit.fit(text, zone.w, zone.h) { line, size ->
            paint.textSize = size
            Layout.getDesiredWidth(spanned(line), paint)
        }
        paint.textSize = fit.size
        val direction = if (LetteringFonts.isRtlParagraph(text, rtlLanguage)) {
            TextDirectionHeuristics.RTL
        } else {
            TextDirectionHeuristics.LTR
        }
        val metrics = paint.fontMetrics
        val lineHeight = fit.lineHeight
        val blockTop = zone.cy - lineHeight * fit.lines.size / 2f

        canvas.save()
        canvas.rotate(zone.rotate, zone.cx, zone.cy)
        fit.lines.forEachIndexed { index, line ->
            val content = spanned(line)
            val width = ceil(max(zone.w, Layout.getDesiredWidth(content, paint))).toInt() + 2
            val layout = StaticLayout.Builder.obtain(content, 0, content.length, paint, width)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setTextDirection(direction)
                .setIncludePad(false)
                .setMaxLines(1)
                .build()
            // CSS line box: the font's ascent + descent centred in a 1.08 × size line.
            val baseline = blockTop + index * lineHeight + lineHeight / 2f - (metrics.ascent + metrics.descent) / 2f
            canvas.save()
            canvas.translate(zone.cx - width / 2f, baseline - layout.getLineBaseline(0))
            if (sticker.stroke > 0f) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = sticker.stroke
                paint.strokeJoin = Paint.Join.ROUND
                paint.color = sticker.strokeColor
                layout.draw(canvas)
            }
            paint.style = Paint.Style.FILL
            paint.color = sticker.ink
            layout.draw(canvas)
            canvas.restore()
        }
        canvas.restore()
        return fit
    }

    private fun paintFor(text: String, sticker: TemplateSticker) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = typefaceFor(LetteringFonts.choose(text))
        letterSpacing = TRACKING_EM
        color = sticker.ink
    }

    /** [line] with every "❤" (and an emoji selector after it) drawn as the app-mark heart. */
    private fun spanned(line: String): CharSequence {
        var index = line.indexOf(HEART)
        if (index < 0) return line
        val out = SpannableString(line)
        while (index >= 0) {
            val end = if (index + 1 < line.length && line[index + 1] == '️') index + 2 else index + 1
            out.setSpan(HeartSpan(), index, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            index = line.indexOf(HEART, end)
        }
        return out
    }

    /** A 0.78 em heart, bottom 0.12 em below the baseline, 0.08 em margins, ink colour, never stroked. */
    private class HeartSpan : ReplacementSpan() {

        override fun getSize(paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
            if (fm != null) paint.getFontMetricsInt(fm)
            return ceil(paint.textSize * (SIZE_EM + 2 * MARGIN_EM)).toInt()
        }

        override fun draw(
            canvas: Canvas, text: CharSequence?, start: Int, end: Int,
            x: Float, top: Int, y: Int, bottom: Int, paint: Paint
        ) {
            if (paint.style != Paint.Style.FILL) return
            val size = paint.textSize * SIZE_EM
            val boxBottom = y + paint.textSize * DROP_EM
            canvas.drawPath(HeartPath.inBox(x + paint.textSize * MARGIN_EM, boxBottom - size, size), paint)
        }

        private companion object {
            const val SIZE_EM = 0.78f
            const val MARGIN_EM = 0.08f
            const val DROP_EM = 0.12f
        }
    }

    companion object {
        const val TRACKING_EM = -0.01f
        const val HEART = '❤'
    }
}
```

- [ ] **Step 5: Write the composite and the tray**

First let Create's encoder take a quality ladder, so the name pack reuses its loop instead of writing a second one. In `K/feature/create/StickerRenderer.kt`, `encodeStaticSticker` gains a third parameter and loops over it (Create keeps today's ladder through the default):

```kotlin
    fun encodeStaticSticker(
        bitmap: Bitmap,
        limitBytes: Int = CreateSpec.STATIC_LIMIT_BYTES,
        qualities: IntArray = intArrayOf(95, 85, 75, 65, 55, 45, 35, 30)
    ): ByteArray {
        var best: ByteArray? = null
        for (quality in qualities) {
```

(the rest of the body is unchanged; add "[qualities] is tried in order." to its KDoc).

`K/feature/namepack/engine/StickerComposer.kt`:

```kotlin
package com.piptechnologies.stickermaker.feature.namepack.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import com.piptechnologies.stickermaker.feature.create.StickerRenderer

/** Template art + lettering, then the 8 px white die-cut outline around the union, drawn beneath. */
class StickerComposer(private val lettering: Lettering) {

    fun compose(art: Bitmap, sticker: TemplateSticker, text: String, rtlLanguage: Boolean): Bitmap {
        val size = TemplateSet.CANVAS
        val body = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        Canvas(body).apply {
            drawBitmap(art, null, Rect(0, 0, size, size), Paint(Paint.FILTER_BITMAP_FLAG))
            lettering.draw(this, text, sticker, rtlLanguage)
        }
        return withOutline(body, OUTLINE_PX).also { body.recycle() }
    }

    companion object {
        const val OUTLINE_PX = 8f

        private val QUALITIES = intArrayOf(84, 76, 68, 60, 50, 40, 30)

        /** [body] over a white outline of [radiusPx] around its opaque pixels. [body] is not recycled. */
        fun withOutline(body: Bitmap, radiusPx: Float): Bitmap {
            val outline = StickerRenderer.outlineOf(body, radiusPx)
            val out = Bitmap.createBitmap(body.width, body.height, Bitmap.Config.ARGB_8888)
            Canvas(out).apply {
                drawBitmap(outline, 0f, 0f, null)
                drawBitmap(body, 0f, 0f, null)
            }
            outline.recycle()
            return out
        }

        /** WebP from quality 84 down until WhatsApp's 100 KB static limit is met (Create's encoder). */
        fun encode(bitmap: Bitmap): ByteArray = StickerRenderer.encodeStaticSticker(bitmap, qualities = QUALITIES)
    }
}
```

`K/feature/namepack/engine/TrayRenderer.kt`:

```kotlin
package com.piptechnologies.stickermaker.feature.namepack.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import java.io.ByteArrayOutputStream

/**
 * The pack's tray icon (spec, Rendering): the app-mark heart filled rose, with white initials
 * (yours then theirs) at 0.36 × size for two and 0.46 × size for one, −0.03 em tracking, centred
 * horizontally and at 0.44 × size vertically (the design's optical centre, 6 px above at 96).
 */
class TrayRenderer(private val typefaceFor: (LetteringFont) -> Typeface) {

    fun render(initials: String, size: Int = TRAY_SIZE): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawPath(HeartPath.inBox(0f, 0f, size.toFloat()), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ROSE })
        if (initials.isNotEmpty()) {
            val two = NameInput.graphemeCount(initials) >= 2
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                typeface = typefaceFor(LetteringFonts.choose(initials))
                textSize = size * if (two) 0.36f else 0.46f
                letterSpacing = -0.03f
            }
            val layout = StaticLayout.Builder.obtain(initials, 0, initials.length, paint, size)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR)
                .setIncludePad(false)
                .setMaxLines(1)
                .build()
            val metrics = paint.fontMetrics
            val baseline = size * 0.44f - (metrics.ascent + metrics.descent) / 2f
            canvas.save()
            canvas.translate(0f, baseline - layout.getLineBaseline(0))
            layout.draw(canvas)
            canvas.restore()
        }
        return bitmap
    }

    companion object {
        const val TRAY_SIZE = 96
        val ROSE = 0xFFC23359.toInt()

        fun encode(bitmap: Bitmap): ByteArray =
            ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.toByteArray()
            }
    }
}
```

- [ ] **Step 6: Run the render test**

Run: `./gradlew :app:testDebugUnitTest --tests '*NamePackRenderTest*'`
Expected: 3 tests PASS. If `sampleNamesFitEveryZoneInEveryLanguageAndTone` lists phrases, shorten those phrases in `app/src/main/assets/templates/phrases.json` (and in `$SCRATCH/phrases-extra.json` so the merge stays reproducible) until the list is empty; never change Claude Design's zones. If `theTrayIsASmallPngHeart` fails only on the pixel probe, print `Integer.toHexString(bitmap.getPixel(20, 40))` and move the probe to a pixel inside the heart's left lobe that the initials cannot reach.

- [ ] **Step 7: Look at the contact sheets**

Run: `NAMEPACK_SHEETS=1 ./gradlew :app:testDebugUnitTest --tests '*NamePackSheetsTest*' --rerun` (the variable propagates to the test JVM; `--rerun` stops Gradle from skipping the task as up to date), then open `app/build/namepack-sheets/en-mango-sweet.png`, `ar-mango-sweet.png`, `hi-mango-sweet.png`, `ps-mango-sweet.png`, `he-mango-flirty.png` and `de-mango-sweet.png`. Check: every phrase sits inside its surface (heart, sign, bubble, banner, box) or open space, nothing is cut by the canvas edge, Arabic joins, Hindi conjuncts form, the heart in our_names is a vector heart in the ink colour, and the white stroke shows on good_night/kiss/hug. Fix lettering code (not the design's zones) until they read right.

---

### Task 7: Room version 2 and name-pack storage

**Files:**
- Modify: `K/core/data/db/Entities.kt`, `K/core/data/db/LoveDb.kt`, `K/core/data/db/Daos.kt`, `K/core/data/di/DatabaseModule.kt`, `K/core/data/repo/MyPacksRepository.kt`, `K/whatsapp/StickerContentProvider.kt`
- Test: `app/src/test/kotlin/com/piptechnologies/stickermaker/core/data/LoveDbMigrationTest.kt`, `…/core/data/MyPacksRepositoryNamePackTest.kt`

**Interfaces:**
- Produces:
  - `OwnPackEntity.imageDataVersion: Int` (`@ColumnInfo(defaultValue = "1")`, default 1)
  - `LoveDb.MIGRATION_1_2: Migration`
  - `OwnPackDao.replacePack(pack: OwnPackEntity, stickers: List<OwnStickerEntity>)` (`@Transaction`)
  - `MyPacksRepository.saveNamePack(id: String, name: String, publisher: String, stickers: List<Pair<String, List<String>>>, dir: String, trayFile: String): Int` (returns the version written)
  - The provider sends each own pack's `imageDataVersion` as `image_data_version`.

- [ ] **Step 1: Write the failing tests**

`LoveDbMigrationTest.kt` (the v1 SQL is exactly what Room 2.6.1 generated for version 1):

```kotlin
package com.piptechnologies.stickermaker.core.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.piptechnologies.stickermaker.core.data.db.LoveDb
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class)
class LoveDbMigrationTest {

    private val v1 = listOf(
        "CREATE TABLE IF NOT EXISTS `installed_packs` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `publisher` TEXT NOT NULL, `trayFile` TEXT NOT NULL, `animated` INTEGER NOT NULL, `category` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, `addedAt` INTEGER NOT NULL, `dirPath` TEXT NOT NULL, `whitelisted` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        "CREATE TABLE IF NOT EXISTS `installed_stickers` (`packId` TEXT NOT NULL, `fileName` TEXT NOT NULL, `emojis` TEXT NOT NULL, `indexInPack` INTEGER NOT NULL, PRIMARY KEY(`packId`, `fileName`))",
        "CREATE TABLE IF NOT EXISTS `own_packs` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `publisher` TEXT NOT NULL, `trayFile` TEXT NOT NULL, `animated` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `dirPath` TEXT NOT NULL, `whitelisted` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        "CREATE TABLE IF NOT EXISTS `own_stickers` (`packId` TEXT NOT NULL, `fileName` TEXT NOT NULL, `emojis` TEXT NOT NULL, `indexInPack` INTEGER NOT NULL, PRIMARY KEY(`packId`, `fileName`))",
        "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)",
        "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '1cbf4361a392886dece6f198fec2959d')"
    )

    @Test
    fun aVersionOneDatabaseMigratesAndKeepsItsPacks() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-test.db"
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            v1.forEach(db::execSQL)
            db.execSQL("INSERT INTO own_packs VALUES('own-1234abcd','Us','Made by you','tray.png',0,1,'/data/own',1)")
            db.version = 1
        }

        val room = Room.databaseBuilder(context, LoveDb::class.java, name)
            .addMigrations(LoveDb.MIGRATION_1_2)
            .build()
        val pack = room.ownPackDao().get("own-1234abcd")!!
        assertEquals(1, pack.imageDataVersion)
        assertTrue(pack.whitelisted)
        room.close()
    }
}
```

`MyPacksRepositoryNamePackTest.kt`:

```kotlin
package com.piptechnologies.stickermaker.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.piptechnologies.stickermaker.core.data.db.LoveDb
import com.piptechnologies.stickermaker.core.data.repo.MyPacksRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class)
class MyPacksRepositoryNamePackTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, LoveDb::class.java).allowMainThreadQueries().build()
    private val repo = MyPacksRepository(db.installedPackDao(), db.ownPackDao(), Dispatchers.Unconfined)
    private val files = (1..12).map { "%02d.webp".format(it) }

    @After
    fun close() = db.close()

    @Test
    fun reLetteringKeepsTheIdBumpsTheVersionAndKeepsTheWhitelist() = runBlocking {
        assertEquals(1, repo.saveNamePack("own-np-abc", "Aymen ❤ Sara", "PIP Technologies", files.map { it to listOf("❤️") }, "/data/own/own-np-abc", "tray.png"))
        db.ownPackDao().setWhitelisted("own-np-abc", true)

        assertEquals(2, repo.saveNamePack("own-np-abc", "Aymen ❤ Sara", "PIP Technologies", files.map { it to listOf("😘") }, "/data/own/own-np-abc", "tray.png"))

        val pack = db.ownPackDao().get("own-np-abc")!!
        assertEquals(2, pack.imageDataVersion)
        assertTrue(pack.whitelisted)
        val rows = db.ownPackDao().stickers("own-np-abc")
        assertEquals(files, rows.map { it.fileName })
        assertTrue(rows.all { it.emojis == "😘" })
    }

    @Test
    fun createdPacksStartAtVersionOne() = runBlocking {
        val id = repo.saveOwnPack("Us", "Made by you", false, files.take(3).map { it to listOf("❤️") }, "/data/own/x", "tray.png")
        assertEquals(1, db.ownPackDao().get(id)!!.imageDataVersion)
    }
}
```

- [ ] **Step 2: Run them to see them fail**

Run: `./gradlew :app:testDebugUnitTest --tests '*LoveDbMigrationTest*' --tests '*MyPacksRepositoryNamePackTest*'`
Expected: compilation FAILS (`imageDataVersion`, `MIGRATION_1_2`, `saveNamePack` unresolved).

- [ ] **Step 3: Schema version 2**

`Entities.kt`, `OwnPackEntity` gains a last field (add the `androidx.room.ColumnInfo` import):

```kotlin
    val whitelisted: Boolean = false,
    /** Bumped whenever the pack's images change, so WhatsApp re-reads them (name packs re-letter). */
    @ColumnInfo(defaultValue = "1") val imageDataVersion: Int = 1,
)
```

`LoveDb.kt`: `version = 2`, and:

```kotlin
abstract class LoveDb : RoomDatabase() {

    abstract fun installedPackDao(): InstalledPackDao

    abstract fun ownPackDao(): OwnPackDao

    companion object {
        /** Version 2: own packs carry their own image data version (name packs are re-lettered in place). */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `own_packs` ADD COLUMN `imageDataVersion` INTEGER NOT NULL DEFAULT 1")
            }
        }
    }
}
```

(imports `androidx.room.migration.Migration`, `androidx.sqlite.db.SupportSQLiteDatabase`).

`DatabaseModule.kt`:

```kotlin
    fun provideDatabase(@ApplicationContext context: Context): LoveDb =
        Room.databaseBuilder(context, LoveDb::class.java, "love.db")
            .addMigrations(LoveDb.MIGRATION_1_2)
            .build()
```

`Daos.kt`, in `OwnPackDao` (import `androidx.room.Transaction`):

```kotlin
    /** Replaces a pack and its sticker rows in one transaction (a re-lettered name pack). */
    @Transaction
    suspend fun replacePack(pack: OwnPackEntity, stickers: List<OwnStickerEntity>) {
        deleteStickers(pack.id)
        upsertPack(pack)
        upsertStickers(stickers)
    }
```

- [ ] **Step 4: The repository method**

`MyPacksRepository.kt`, after `saveOwnPack`:

```kotlin
    /**
     * Registers or re-letters a name pack under its fixed [id]; the files must already be in
     * [dir]. An existing pack keeps its whitelist flag and gets the next image data version so
     * WhatsApp refreshes what it cached. Returns the version written.
     */
    suspend fun saveNamePack(
        id: String,
        name: String,
        publisher: String,
        stickers: List<Pair<String, List<String>>>,
        dir: String,
        trayFile: String
    ): Int = withContext(ioDispatcher) {
        val existing = ownPackDao.get(id)
        val version = (existing?.imageDataVersion ?: 0) + 1
        ownPackDao.replacePack(
            OwnPackEntity(
                id = id,
                name = name,
                publisher = publisher,
                trayFile = trayFile,
                animated = false,
                createdAt = System.currentTimeMillis(),
                dirPath = dir,
                whitelisted = existing?.whitelisted ?: false,
                imageDataVersion = version
            ),
            stickers.mapIndexed { index, (fileName, emojis) ->
                OwnStickerEntity(packId = id, fileName = fileName, emojis = emojis.joinToString(","), indexInPack = index)
            }
        )
        version
    }
```

- [ ] **Step 5: The provider sends the version**

In `StickerContentProvider.kt`: `ProviderPack` gains `val imageDataVersion: String`; `InstalledPackEntity.toProviderPack()` passes `imageDataVersion = IMAGE_DATA_VERSION_VALUE`; `OwnPackEntity.toProviderPack()` passes `imageDataVersion = imageDataVersion.toString()`; in `getStickerPackInfo` replace `.add(IMAGE_DATA_VERSION_VALUE)` with `.add(stickerPack.imageDataVersion)`. Update the KDoc of `IMAGE_DATA_VERSION_VALUE` to say it is the catalog packs' version.

- [ ] **Step 6: Run the tests**

Run: `./gradlew :app:testDebugUnitTest --tests '*LoveDbMigrationTest*' --tests '*MyPacksRepositoryNamePackTest*' --tests '*StickerPackValidatorTest*'`
Expected: all PASS. If Room reports "Migration didn't properly handle own_packs", compare the expected and found column lines it prints: the entity's `defaultValue` must read `'1'`-equivalent to the migration's `DEFAULT 1`.

---

### Task 8: Assets loader, pack builder and saver

**Files:**
- Create: `K/feature/namepack/engine/NamePackAssets.kt`, `K/feature/namepack/engine/NamePackBuilder.kt`, `K/feature/namepack/engine/NamePackSaver.kt`, `K/core/data/files/OwnPackFiles.kt`
- Modify: `K/feature/create/CreatePackViewModel.kt` (its private pack writer moves to `OwnPackFiles`)
- Test: `…/feature/namepack/NamePackBuilderTest.kt`

**Interfaces:**
- Consumes: Tasks 2–7; `StickerPackValidator.verifyStickerPackValidity(ValidatablePack)`, `ValidatableSticker(fileName, bytes, emojis)`, `StickerContentProvider.ANDROID_PLAY_STORE_LINK`, `MyPacksRepository.saveNamePack`.
- Produces:
  - `open class NamePackAssets(context: Context)`: `templates(character): TemplateSet`, `phrases(): PhraseBook`, `art(file): Bitmap`, `open typeface(font): Typeface`, `waitArt(character): Bitmap` (outlined), `tileArt(character): Bitmap` (144 px)
  - `data class NamePackRequest(val lang: String, val rtl: Boolean, val you: String, val love: String, val relation: Relation, val tone: Tone, val character: Character)` with `effectiveTone`, `packId`, `cacheKey(slot)`, `contentKey`
  - `class LetteredSticker(val slot: Slot, val text: String, val preview: Bitmap, val webp: ByteArray, val emojis: List<String>)`
  - `class TrayImage(val bitmap: Bitmap, val png: ByteArray)`
  - `class NamePackBuilder(assets: NamePackAssets)`: `text(request, slot): String`, `preview(request, slot, text): Bitmap`, `suspend build(request, onProgress: (Int) -> Unit = {}): List<LetteredSticker>`, `tray(request): TrayImage`; companion `PREVIEW_PX = 320`
  - `object OwnPackFiles { const val TRAY_FILE = "tray.png"; fun write(filesDir: File, dirId: String, trayBytes: ByteArray, stickerFiles: List<Pair<String, ByteArray>>): File }` (package `core.data.files`), used by Create and by the saver
  - `class NamePackSaver(filesDir: File, repository: MyPacksRepository, publisherEmail: String, privacyPolicyWebsite: String)`: `suspend save(id, name, stickers, trayPng): Int`; companion `PUBLISHER = "PIP Technologies"`, `fileName(index): String`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.piptechnologies.stickermaker.feature.namepack

import android.content.Context
import android.graphics.Typeface
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.piptechnologies.stickermaker.R
import com.piptechnologies.stickermaker.core.data.db.LoveDb
import com.piptechnologies.stickermaker.core.data.repo.MyPacksRepository
import com.piptechnologies.stickermaker.feature.namepack.engine.Character
import com.piptechnologies.stickermaker.feature.namepack.engine.LetteringFont
import com.piptechnologies.stickermaker.feature.namepack.engine.NamePackAssets
import com.piptechnologies.stickermaker.feature.namepack.engine.NamePackBuilder
import com.piptechnologies.stickermaker.feature.namepack.engine.NamePackRequest
import com.piptechnologies.stickermaker.feature.namepack.engine.NamePackSaver
import com.piptechnologies.stickermaker.feature.namepack.engine.Relation
import com.piptechnologies.stickermaker.feature.namepack.engine.Slot
import com.piptechnologies.stickermaker.feature.namepack.engine.Tone
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class)
class NamePackBuilderTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val assets = object : NamePackAssets(context) {
        override fun typeface(font: LetteringFont): Typeface = TestFonts.of(font)
    }
    private val builder = NamePackBuilder(assets)
    private val request = NamePackRequest("en", false, "Aymen", "Sara", Relation.GIRLFRIEND, Tone.SWEET, Character.MANGO)

    @Test
    fun buildsTwelveInOrderReportsProgressAndCaches() = runBlocking {
        val progress = mutableListOf<Int>()
        val stickers = builder.build(request) { synchronized(progress) { progress += it } }
        assertEquals(Slot.entries, stickers.map { it.slot })
        assertEquals((1..12).toList(), progress.sorted())
        assertEquals("Love you, Sara", stickers.first().text)
        assertEquals("Aymen ❤ Sara", stickers[Slot.OUR_NAMES.ordinal].text)
        assertEquals(NamePackBuilder.PREVIEW_PX, stickers.first().preview.width)
        assertTrue(stickers.all { it.webp.size in 1..(100 * 1024) })
        assertSame(stickers[3], builder.build(request)[3])
        assertNotEquals(stickers[1].text, builder.build(request.copy(tone = Tone.FLIRTY))[1].text)
    }

    @Test
    fun familyPacksStaySweet() = runBlocking {
        val mom = request.copy(relation = Relation.MOM, tone = Tone.FLIRTY)
        assertEquals(Tone.SWEET, mom.effectiveTone)
        assertEquals("Big kiss, Sara", builder.text(mom, Slot.KISS))
    }

    @Test
    fun savesAValidPackAndReLettersItInPlace() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, LoveDb::class.java).allowMainThreadQueries().build()
        val saver = NamePackSaver(
            context.filesDir,
            MyPacksRepository(db.installedPackDao(), db.ownPackDao(), Dispatchers.Unconfined),
            context.getString(R.string.config_support_email),
            context.getString(R.string.config_privacy_policy_url)
        )
        val tray = builder.tray(request)
        assertEquals(96, tray.bitmap.width)

        assertEquals(1, saver.save(request.packId, "Aymen ❤ Sara", builder.build(request), tray.png))
        val dir = File(context.filesDir, "own/${request.packId}")
        assertTrue(File(dir, "tray.png").isFile)
        assertEquals((1..12).map { "%02d.webp".format(it) }, dir.list()!!.filter { it.endsWith(".webp") }.sorted())
        assertEquals(12, db.ownPackDao().stickers(request.packId).size)

        val recast = request.copy(character = Character.CAPY, tone = Tone.FLIRTY)
        assertEquals(request.packId, recast.packId)
        assertEquals(2, saver.save(recast.packId, "Aymen ❤ Sara", builder.build(recast), tray.png))
        assertTrue(File(context.filesDir, "own").list()!!.none { it.endsWith(".tmp") })
        db.close()
    }

    @Test
    fun theWaitingArtAndTileArtLoad() {
        assertEquals(512, assets.waitArt(Character.CAPY).width) // no capy-wait yet: Mango's still
        assertEquals(144, assets.tileArt(Character.BUNNY).width)
    }
}
```

- [ ] **Step 2: Run it to see it fail**

Run: `./gradlew :app:testDebugUnitTest --tests '*NamePackBuilderTest*'`
Expected: compilation FAILS on the missing classes.

- [ ] **Step 3: Write the assets loader**

`K/feature/namepack/engine/NamePackAssets.kt`:

```kotlin
package com.piptechnologies.stickermaker.feature.namepack.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.piptechnologies.stickermaker.R
import java.util.concurrent.ConcurrentHashMap

/** Everything the name pack reads from the APK: Claude Design's templates and phrases, and the fonts. */
open class NamePackAssets(private val context: Context) {

    private val sets = ConcurrentHashMap<Character, TemplateSet>()
    private val faces = ConcurrentHashMap<LetteringFont, Typeface>()
    private val phraseBook by lazy { PhraseBook.parse(read(PhraseBook.ASSET_PATH)) }

    fun templates(character: Character): TemplateSet =
        sets.getOrPut(character) { TemplateSet.parse(character, read(TemplateSet.assetPath(character))) }

    fun phrases(): PhraseBook = phraseBook

    /** A template's 512 art, decoded fresh (the caller recycles it). */
    fun art(file: String): Bitmap = decode(TemplateSet.artPath(file))

    open fun typeface(font: LetteringFont): Typeface = faces.getOrPut(font) {
        val res = when (font) {
            LetteringFont.BALOO -> R.font.lettering_baloo2
            LetteringFont.BALOO_BHAIJAAN -> R.font.lettering_baloo_bhaijaan2
            LetteringFont.RUBIK -> R.font.lettering_rubik
            LetteringFont.SYSTEM_BOLD -> null
        }
        res?.let { ResourcesCompat.getFont(context, it) } ?: Typeface.DEFAULT_BOLD
    }

    /** The character's waiting art with a white die-cut outline: `<id>-wait.webp` when bundled, else Mango's. */
    fun waitArt(character: Character): Bitmap {
        val file = listOf("${character.id}-wait.webp", FALLBACK_WAIT).first { exists("templates/$it") }
        val art = decode("templates/$file")
        return StickerComposer.withOutline(art, WAIT_OUTLINE_PX).also { art.recycle() }
    }

    /** "Who says it?" tile art: the character's hug pose, scaled to 144 px. */
    fun tileArt(character: Character): Bitmap {
        val art = art("${character.id}-6.webp")
        return Bitmap.createScaledBitmap(art, TILE_PX, TILE_PX, true).also { if (it !== art) art.recycle() }
    }

    private fun exists(path: String): Boolean = runCatching { context.assets.open(path).close() }.isSuccess

    private fun decode(path: String): Bitmap =
        context.assets.open(path).use { BitmapFactory.decodeStream(it) } ?: error("Undecodable $path")

    private fun read(path: String): String = context.assets.open(path).bufferedReader().use { it.readText() }

    companion object {
        private const val FALLBACK_WAIT = "mango-wait.webp"
        private const val WAIT_OUTLINE_PX = 7f
        private const val TILE_PX = 144
    }
}
```

- [ ] **Step 4: Write the builder**

`K/feature/namepack/engine/NamePackBuilder.kt`:

```kotlin
package com.piptechnologies.stickermaker.feature.namepack.engine

import android.graphics.Bitmap
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** What to letter: normalized names, the phrase language, relation, tone and character. */
data class NamePackRequest(
    val lang: String,
    val rtl: Boolean,
    val you: String,
    val love: String,
    val relation: Relation,
    val tone: Tone,
    val character: Character,
) {
    /** Family relations are always Sweet. */
    val effectiveTone: Tone get() = if (relation.family) Tone.SWEET else tone

    /** WhatsApp identity: names and language only, so tone and re-casts re-letter the same pack. */
    val packId: String get() = NamePackId.of(lang, you, love)

    /** What the pixels depend on (spec, Cache): everything except the relation beyond its family flag. */
    val contentKey: String
        get() = listOf(character.id, effectiveTone.id, relation.family, lang, you, love).joinToString("\u0000")

    fun cacheKey(slot: Slot): String = contentKey + "\u0000" + slot.key
}

/** One lettered sticker: [preview] (320 px) for the grid, [webp] for WhatsApp. */
class LetteredSticker(
    val slot: Slot,
    val text: String,
    val preview: Bitmap,
    val webp: ByteArray,
    val emojis: List<String>,
)

class TrayImage(val bitmap: Bitmap, val png: ByteArray)

/**
 * Letters name packs: 12 stickers three at a time on [Dispatchers.Default], cached per content,
 * so tone and character switches back and forth are instant.
 */
class NamePackBuilder(private val assets: NamePackAssets) {

    private val lettering = Lettering(assets::typeface)
    private val composer = StickerComposer(lettering)
    private val trays = TrayRenderer(assets::typeface)
    private val cache = object : LinkedHashMap<String, LetteredSticker>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, LetteredSticker>) = size > CACHE_SIZE
    }

    fun text(request: NamePackRequest, slot: Slot): String =
        assets.phrases().text(request.lang, request.effectiveTone, request.relation, slot, request.you, request.love)

    /** A full-size sticker for the live preview: [text] lettered on [slot]'s template. Not cached. */
    fun preview(request: NamePackRequest, slot: Slot, text: String): Bitmap {
        val sticker = assets.templates(request.character).sticker(slot)
        val art = assets.art(sticker.file)
        return try {
            composer.compose(art, sticker, text, request.rtl)
        } finally {
            art.recycle()
        }
    }

    /** The 12 stickers in [Slot] order; [onProgress] receives 1..12 as each finishes (any thread). */
    suspend fun build(request: NamePackRequest, onProgress: (done: Int) -> Unit = {}): List<LetteredSticker> =
        coroutineScope {
            val set = assets.templates(request.character)
            val done = AtomicInteger(0)
            val gate = Semaphore(PARALLELISM)
            Slot.entries.map { slot ->
                async(Dispatchers.Default) {
                    gate.withPermit {
                        val key = request.cacheKey(slot)
                        val sticker = synchronized(cache) { cache[key] }
                            ?: render(request, set.sticker(slot)).also { synchronized(cache) { cache[key] = it } }
                        onProgress(done.incrementAndGet())
                        sticker
                    }
                }
            }.awaitAll()
        }

    /** The 96 px tray: the heart with your initial, then theirs. */
    fun tray(request: NamePackRequest): TrayImage {
        val locale = Locale.forLanguageTag(request.lang)
        val bitmap = trays.render(NameInput.initial(request.you, locale) + NameInput.initial(request.love, locale))
        return TrayImage(bitmap, TrayRenderer.encode(bitmap))
    }

    private fun render(request: NamePackRequest, sticker: TemplateSticker): LetteredSticker {
        val text = text(request, sticker.slot)
        val art = assets.art(sticker.file)
        val full = try {
            composer.compose(art, sticker, text, request.rtl)
        } finally {
            art.recycle()
        }
        val webp = StickerComposer.encode(full)
        val preview = Bitmap.createScaledBitmap(full, PREVIEW_PX, PREVIEW_PX, true)
        if (preview !== full) full.recycle()
        return LetteredSticker(sticker.slot, text, preview, webp, sticker.emojis)
    }

    companion object {
        const val PREVIEW_PX = 320
        private const val PARALLELISM = 3
        private const val CACHE_SIZE = 60
    }
}
```

- [ ] **Step 5: Write the saver**

Own packs already have an atomic writer: `CreatePackViewModel.writePackFiles` (private). Move it, unchanged, into `K/core/data/files/OwnPackFiles.kt` so Create and the name pack share it:

```kotlin
package com.piptechnologies.stickermaker.core.data.files

import java.io.File
import java.io.IOException

/**
 * Own packs' files: `filesDir/own/<dirId>/` holds tray.png and the sticker WebPs. Written into a
 * `.tmp` folder and renamed into place, so a failure never leaves a half-written pack.
 */
object OwnPackFiles {

    const val TRAY_FILE = "tray.png"

    /** Writes [trayBytes] and [stickerFiles] (file name to bytes) as pack [dirId], replacing any previous copy. */
    fun write(filesDir: File, dirId: String, trayBytes: ByteArray, stickerFiles: List<Pair<String, ByteArray>>): File {
        val root = File(filesDir, "own").apply { mkdirs() }
        val tmp = File(root, "$dirId.tmp")
        val finalDir = File(root, dirId)
        var completed = false
        try {
            tmp.deleteRecursively()
            if (!tmp.mkdirs()) throw IOException("Could not create ${tmp.absolutePath}")
            File(tmp, TRAY_FILE).writeBytes(trayBytes)
            stickerFiles.forEach { (name, bytes) -> File(tmp, name).writeBytes(bytes) }
            if (finalDir.exists() && !finalDir.deleteRecursively()) {
                throw IOException("Could not replace ${finalDir.absolutePath}")
            }
            if (!tmp.renameTo(finalDir)) {
                throw IOException("Could not move pack $dirId into place")
            }
            completed = true
            return finalDir
        } finally {
            if (!completed) tmp.deleteRecursively()
        }
    }
}
```

In `K/feature/create/CreatePackViewModel.kt`: delete `private const val TRAY_FILE = "tray.png"` and `private fun writePackFiles(...)`; the call becomes `OwnPackFiles.write(appContext.filesDir, dirId, trayBytes, fileNames.zip(encoded))` and both `TRAY_FILE` uses become `OwnPackFiles.TRAY_FILE` (import `com.piptechnologies.stickermaker.core.data.files.OwnPackFiles`; drop imports nothing else uses). Create's behaviour does not change.

`K/feature/namepack/engine/NamePackSaver.kt`:

```kotlin
package com.piptechnologies.stickermaker.feature.namepack.engine

import com.piptechnologies.stickermaker.core.data.files.OwnPackFiles
import com.piptechnologies.stickermaker.core.data.repo.MyPacksRepository
import com.piptechnologies.stickermaker.whatsapp.StickerContentProvider
import com.piptechnologies.stickermaker.whatsapp.StickerPackValidator
import com.piptechnologies.stickermaker.whatsapp.ValidatablePack
import com.piptechnologies.stickermaker.whatsapp.ValidatableSticker
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Stores a lettered pack as an own pack: validates it with the provider's metadata (as WhatsApp
 * will), writes tray + 01..12.webp atomically to `filesDir/own/<id>`, then records it in Room.
 */
class NamePackSaver(
    private val filesDir: File,
    private val repository: MyPacksRepository,
    private val publisherEmail: String,
    private val privacyPolicyWebsite: String,
) {

    /** Returns the image data version written (1 for a new pack). */
    suspend fun save(id: String, name: String, stickers: List<LetteredSticker>, trayPng: ByteArray): Int {
        val files = stickers.mapIndexed { index, sticker -> fileName(index) to sticker }
        StickerPackValidator.verifyStickerPackValidity(
            ValidatablePack(
                identifier = id,
                name = name,
                publisher = PUBLISHER,
                trayImageFile = OwnPackFiles.TRAY_FILE,
                trayBytes = trayPng,
                animatedStickerPack = false,
                stickers = files.map { (file, sticker) -> ValidatableSticker(file, sticker.webp, sticker.emojis) },
                publisherEmail = publisherEmail,
                privacyPolicyWebsite = privacyPolicyWebsite,
                androidPlayStoreLink = StickerContentProvider.ANDROID_PLAY_STORE_LINK
            )
        )
        // Files and the Room row move together: a cancelled caller (a closing screen) must not leave
        // new images under the old image_data_version, or WhatsApp would keep its stale copy.
        return withContext(NonCancellable) {
            val dir = withContext(Dispatchers.IO) {
                OwnPackFiles.write(filesDir, id, trayPng, files.map { (file, sticker) -> file to sticker.webp })
            }
            repository.saveNamePack(
                id, name, PUBLISHER, files.map { (file, sticker) -> file to sticker.emojis }, dir.absolutePath, OwnPackFiles.TRAY_FILE
            )
        }
    }

    companion object {
        /** The library packs' publisher (CatalogDataSource's default). */
        const val PUBLISHER = "PIP Technologies"

        /** 01.webp … 12.webp, always in ASCII digits. */
        fun fileName(index: Int): String = String.format(Locale.ROOT, "%02d.webp", index + 1)
    }
}
```

- [ ] **Step 6: Run the test and the whole suite**

Run: `./gradlew :app:testDebugUnitTest --tests '*NamePackBuilderTest*'` then `./gradlew :app:testDebugUnitTest`
Expected: 4 tests PASS, then the full suite passes.

---

### Task 9: The flow's strings in 19 languages

**Files:**
- Modify: all 19 `app/src/main/res/values*/strings.xml`

**Interfaces:**
- Produces these string resources (English source; every language has all of them):

| Key | English |
|---|---|
| `namepack_language_sub` | Changes the app and the stickers. |
| `namepack_you_title` | What's your name? |
| `namepack_you_helper` | It goes on the stickers you send. You can skip this. |
| `namepack_you_placeholder` | Your name |
| `namepack_pick_character` | Who says it? |
| `namepack_love_title` | Who's your love? |
| `namepack_love_helper` | We letter 12 stickers with this name, in your language. |
| `namepack_love_placeholder` | Their name |
| `namepack_sample_name` | Sara |
| `namepack_continue` | Continue |
| `namepack_make` | Make our stickers |
| `namepack_note_limit` | That's the limit. Nicknames work too. |
| `namepack_note_emoji` | Emoji can't be lettered. Letters only. |
| `namepack_note_need_name` | Add a name to make the pack. |
| `namepack_preview_hint` | Live preview. It re-letters as you type. |
| `namepack_preview_alt` | Sticker preview: %1$s |
| `namepack_rel_girlfriend` … `namepack_rel_friend` | Girlfriend, Boyfriend, Wife, Husband, Crush, Partner, Mom, Friend |
| `namepack_building_title` | Lettering 12 stickers… |
| `namepack_building_sub` | Made on this phone. Nothing is uploaded. |
| `namepack_waiting_alt` | %1$s is waiting for your stickers |
| `namepack_pack_for` | For %1$s |
| `namepack_tone_sweet` / `namepack_tone_flirty` | Sweet / Flirty |
| `namepack_meta` | 12 stickers · %1$s |
| `namepack_add_failed` | Something went wrong · Retry |
| `namepack_build_failed` | Couldn't letter the stickers. Try again. |

Reused, unchanged: `onboarding_skip`, `settings_language`, `language_rtl_badge`, `common_not_now`, `add_bar_idle`, `add_bar_sent`, `add_bar_added`, `toast_added_to_whatsapp`, `no_whatsapp_title`, `no_whatsapp_body`, `no_whatsapp_confirm`, `create_name_counter` (`%1$d/%2$d`, untranslatable).

- [ ] **Step 1: Inject the block**

The translations (Arabic from Claude Design; the other 17 written for the app in each file's own vocabulary and register, e.g. pt-BR "figurinhas", pt "autocolantes", it "adesivi", zh "贴图") live in `$SCRATCH/namepack-strings.json`.

```bash
: "${SCRATCH:?export SCRATCH=<session scratchpad> first}"
python3 "$SCRATCH/inject_namepack_strings.py" . "$SCRATCH/namepack-strings.json"
grep -c "namepack_" app/src/main/res/values*/strings.xml
```

Expected: 19 folders each report 33 strings; every grep count is 33 (English has the extra translator comments inside the same block).

- [ ] **Step 2: Run the string tests**

Run: `./gradlew :app:testDebugUnitTest --tests '*StringResourcesTest*' --tests '*LocalizedFormattingTest*'`
Expected: PASS (same keys everywhere, same placeholders, RTL files still lay out right to left, every string formats in every language). If `translationsAreNotEnglishCopies` fails for a language, list its identical strings and translate the ones that are not proper names.

---

### Task 10: Language chip and sheet; the intro's top strip

**Files:**
- Create: `K/feature/language/LanguageSheet.kt`
- Modify: `K/feature/onboarding/OnboardingScreen.kt`, `K/core/design/Icons.kt` (`LoveIcons.lucideIcon` becomes `internal`)

**Interfaces:**
- Consumes: `AppLanguages.entries/selectedTag()/match()/apply()/byTag()`, `AppLanguage(tag, nativeName, nameRes, rtl)`, `AppAnalytics.logLanguageChanged`, `LoveBottomSheet`, `LoveIcons.Check`, `LoveIcons.lucideIcon(name, vararg pathData, filled = false, autoMirror = false)`, colour tokens.
- Produces:
  - `internal fun AppLanguages.effectiveTag(): String` (never `SYSTEM`)
  - `@Composable fun LanguageChip(onClick: () -> Unit, modifier: Modifier = Modifier)`
  - `@Composable fun LanguageTopStrip(onLanguage: () -> Unit, onSkip: () -> Unit, modifier: Modifier = Modifier)`: the 40 dp strip (chip at the start, ghost Skip at the end) that the intro and both name steps share
  - `@Composable fun LanguageSheet(onDismiss: () -> Unit)`
  - `OnboardingContent(page, onNext, onSkip, onLanguage, modifier)`

- [ ] **Step 1: Write the chip and the sheet**

In `K/core/design/Icons.kt`, change `private fun lucideIcon(` to `internal fun lucideIcon(` (nothing else), so new glyphs are built the same way as every `LoveIcons` icon instead of repeating the builder.

`K/feature/language/LanguageSheet.kt` (design: screens spec §b):

```kotlin
package com.piptechnologies.stickermaker.feature.language

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piptechnologies.stickermaker.R
import com.piptechnologies.stickermaker.core.design.Border
import com.piptechnologies.stickermaker.core.design.Hanken
import com.piptechnologies.stickermaker.core.design.Ink
import com.piptechnologies.stickermaker.core.design.Ink2
import com.piptechnologies.stickermaker.core.design.LoveIcons
import com.piptechnologies.stickermaker.core.design.LoveShapes
import com.piptechnologies.stickermaker.core.design.Mono
import com.piptechnologies.stickermaker.core.design.Muted
import com.piptechnologies.stickermaker.core.design.Rose
import com.piptechnologies.stickermaker.core.design.RoseTint
import com.piptechnologies.stickermaker.core.design.Subtle
import com.piptechnologies.stickermaker.core.design.Surface
import com.piptechnologies.stickermaker.core.design.components.LoveBottomSheet
import com.piptechnologies.stickermaker.core.telemetry.AppAnalytics
import java.util.Locale

/**
 * The language the app shows and letters stickers in: the per-app pick, else the phone's
 * language when the app has it, else English. Never [AppLanguages.SYSTEM].
 */
internal fun AppLanguages.effectiveTag(): String {
    val picked = selectedTag()
    if (picked != AppLanguages.SYSTEM) return picked
    return match(Locale.getDefault().toLanguageTag()).takeIf { it != AppLanguages.SYSTEM } ?: "en"
}

private val ChipInk = Color(0xFF3D4550)
private val ChipText = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.W600, fontSize = 13.sp)
private val SheetTitle = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.W700, fontSize = 15.sp)
private val SheetSub = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.W400, fontSize = 12.sp)
private val CellText = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.W600, fontSize = 14.sp)
private val BadgeText = TextStyle(fontFamily = Mono, fontWeight = FontWeight.W600, fontSize = 9.sp)
private val SkipText = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.W600, fontSize = 13.5.sp)

/** Lucide `globe` (its circle pre-flattened to arcs), built like every LoveIcons glyph. */
private val Globe: ImageVector by lazy {
    LoveIcons.lucideIcon(
        "globe",
        "M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0",
        "M12 2a14.5 14.5 0 0 0 0 20 14.5 14.5 0 0 0 0-20",
        "M2 12h20"
    )
}

/** 36 dp pill: globe + the current language's native name. Shown on the intro and both name steps. */
@Composable
fun LanguageChip(onClick: () -> Unit, modifier: Modifier = Modifier) {
    // A language change recreates the activity, so this is read fresh then.
    val tag = remember { AppLanguages.effectiveTag() }
    val label = stringResource(R.string.settings_language)
    Row(
        modifier = modifier
            .height(36.dp)
            .clip(LoveShapes.Pill)
            .background(Surface)
            .border(1.dp, Border, LoveShapes.Pill)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .padding(start = 10.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(Globe, contentDescription = label, modifier = Modifier.size(15.dp), tint = ChipInk)
        Text(AppLanguages.byTag(tag)?.nativeName ?: tag, style = ChipText, color = ChipInk, maxLines = 1)
    }
}

/**
 * The 40 dp top strip of the intro and both name steps: the language chip at the start and a
 * ghost Skip (36 tall, 12 padding, radius 10, 13.5/600 Ink2) at the end. The pair swaps sides in
 * right-to-left languages.
 */
@Composable
fun LanguageTopStrip(onLanguage: () -> Unit, onSkip: () -> Unit, modifier: Modifier = Modifier) {
    val skip = stringResource(R.string.onboarding_skip)
    Row(
        modifier = modifier.fillMaxWidth().height(40.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        LanguageChip(onClick = onLanguage)
        Box(
            modifier = Modifier
                .height(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable(role = Role.Button, onClickLabel = skip, onClick = onSkip)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(skip, style = SkipText, color = Ink2)
        }
    }
}

/**
 * Bottom sheet of the app's languages, each in its own script, two columns, the current one
 * rose. A tap switches the whole app (the activity recreates; ViewModels keep the flow).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSheet(onDismiss: () -> Unit) {
    val current = remember { AppLanguages.effectiveTag() }
    // About 650 dp of languages: open whole, as the design shows, not half-expanded.
    LoveBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Text(
            stringResource(R.string.settings_language),
            style = SheetTitle,
            color = Ink,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 2.dp, bottom = 2.dp)
        )
        Text(
            stringResource(R.string.namepack_language_sub),
            style = SheetSub,
            color = Muted,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 10.dp)
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(AppLanguages.entries, key = { it.tag }) { language ->
                LanguageCell(language, selected = language.tag == current) {
                    onDismiss()
                    if (language.tag != current) {
                        AppAnalytics.logLanguageChanged(language.tag)
                        AppLanguages.apply(language.tag)
                    }
                }
            }
        }
    }
}

@Composable
private fun LanguageCell(language: AppLanguage, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    val ink = if (selected) Rose else Ink
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(shape)
            .background(if (selected) RoseTint else Surface)
            .border(1.dp, if (selected) Rose else Border, shape)
            .clickable(role = Role.Button, onClickLabel = language.nativeName, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            language.nativeName,
            style = CellText,
            color = ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (language.rtl) {
            Text(
                stringResource(R.string.language_rtl_badge),
                style = BadgeText,
                color = Muted,
                modifier = Modifier
                    .clip(RoundedCornerShape(5.dp))
                    .background(Subtle)
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            )
        }
        if (selected) Icon(LoveIcons.Check, contentDescription = null, modifier = Modifier.size(14.dp), tint = Rose)
    }
}
```

`AppLanguage` is `internal` in the same module; if the compiler complains that the public `LanguageSheet` exposes it, it does not: only the private `LanguageCell` takes it.

- [ ] **Step 2: The intro's top strip**

In `K/feature/onboarding/OnboardingScreen.kt`:

1. `OnboardingContent` gains `onLanguage: () -> Unit` after `onSkip`; the 40 dp strip becomes (Skip on both slides now, per the design):

```kotlin
        // 40dp top strip: language chip at the start, ghost Skip at the end (both slides).
        LanguageTopStrip(onLanguage = onLanguage, onSkip = onSkip)
```

Then delete `SkipText` and `SkipInk` from the file if nothing else uses them, and any import the old strip alone needed.

2. `OnboardingScreen` shows the sheet:

```kotlin
    var languagesOpen by rememberSaveable { mutableStateOf(false) }
    OnboardingContent(
        page = state.page,
        onNext = viewModel::onNext,
        onSkip = viewModel::onSkip,
        onLanguage = { languagesOpen = true }
    )
    if (languagesOpen) LanguageSheet(onDismiss = { languagesOpen = false })
```

(imports: `androidx.compose.runtime.mutableStateOf`, `androidx.compose.runtime.saveable.rememberSaveable`, `androidx.compose.runtime.setValue`, `com.piptechnologies.stickermaker.feature.language.LanguageTopStrip`, `com.piptechnologies.stickermaker.feature.language.LanguageSheet`).

3. Pass `onLanguage = {}` in the file's previews; update the `OnboardingScreen` KDoc ("ghost Skip on both slides, language chip at the start of the top strip") and keep its last sentence, "Both paths persist the onboarded flag before [onDone] fires." (Task 14 replaces it).

- [ ] **Step 3: Verify**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, tests pass. (The chip and sheet are checked on the emulator in Task 15.)

---

### Task 11: Flow state, analytics and the ViewModel

**Files:**
- Create: `K/feature/namepack/NamePackState.kt`, `K/feature/namepack/NamePackViewModel.kt`, `K/feature/namepack/Redaction.kt`, `K/core/ui/PendingToasts.kt`
- Modify: `K/core/telemetry/Telemetry.kt`
- Test: `…/feature/namepack/NamePackStateTest.kt`, `…/feature/namepack/RedactionTest.kt`

**Interfaces:**
- Consumes: Tasks 2–10; `PrefsRepository.onboarded/setOnboarded`, `MyPacksRepository`, `AddStickerPackFlow.isWhatsAppInstalled`, `RatingPromptController.onPackAdded(context)`, `CrashReporting.record`, `UiText.res`, `Context.inAppLanguage()`, `AddVisualState`.
- Produces:
  - `enum class NameStep { YOU, LOVE, BUILDING, REVEAL }`, `enum class NameNote { NONE, EMOJI, NEED_NAME, LIMIT }`
  - `data class NameField(val raw: String = "")` with `value`, `count`, `atLimit`, `hasEmoji`, `isBlank`
  - `data class NamePackState(step, you, love, relation, character, tone, needNameError)` with `effectiveTone`, `youNote`, `loveNote`, `canContinue`, `canMake`; `fun NamePackState.toSaved(): Map<String, String>`; `fun namePackStateFrom(saved: (String) -> String?): NamePackState`
  - `object NamePackReducer { typeYou, typeLove, pickCharacter, pickRelation, pickTone, continueFromYou, skipYou, make (returns Pair<NamePackState, Boolean>), back (returns NamePackState?), built, buildFailed }` (step actions are no-ops off their own step)
  - `fun buildProgress(done: Int, total: Int, finished: Boolean, elapsedMs: Long, minMs: Long): Float` (the Building bar; a finished build is complete)
  - `@Singleton class PendingToasts @Inject constructor() { fun post(message: UiText); fun take(): UiText? }`
  - `AppAnalytics.logOnboardingComplete(namePack: String)` (replaces the `themes` version), `AppAnalytics.logNamePackBuilt(relation, tone, character, hasYourName, language)`
  - `data class RevealTile(val slot: Slot, val image: ImageBitmap, val text: String)`
  - `data class NamePackUiState(flow, preview: ImageBitmap?, previewText: String, progress: Float, tiles, tray: ImageBitmap?, packYou, packLove, packName: String, addState: AddVisualState, relettering: Boolean, tileArt: Map<Character, ImageBitmap>, waitArt: ImageBitmap?)` (`packName` is the name WhatsApp and My Packs show, built once here)
  - `sealed interface NamePackEvent { LaunchAdd(identifier, packName); ShowNoWhatsApp; Toast(message: UiText); Finished; Leave }`
  - `NamePackViewModel`: `uiState: StateFlow<NamePackUiState>`, `events: Flow<NamePackEvent>`, `onLanguage(tag)`, `onYouChange(raw)`, `onLoveChange(raw)`, `onCharacter(c)`, `onRelation(r)`, `onTone(t)`, `onContinue()`, `onSkipYou()`, `onMake()`, `onSkipLove()`, `onBack()`, `onAdd()`, `onNotNow()`, `onWhatsAppResult(added, rejected = false)`

- [ ] **Step 1: Write the failing state test**

```kotlin
package com.piptechnologies.stickermaker.feature.namepack

import com.piptechnologies.stickermaker.feature.namepack.engine.Character
import com.piptechnologies.stickermaker.feature.namepack.engine.Relation
import com.piptechnologies.stickermaker.feature.namepack.engine.Tone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NamePackStateTest {

    private val start = NamePackState()

    @Test
    fun startsOnYourNameWithMangoAndGirlfriend() {
        assertEquals(NameStep.YOU, start.step)
        assertEquals(Character.MANGO, start.character)
        assertEquals(Relation.GIRLFRIEND, start.relation)
        assertEquals(Tone.SWEET, start.tone)
    }

    @Test
    fun emojiInYourNameBlocksContinueAndShowsTheNote() {
        val s = NamePackReducer.typeYou(start, "Aymen 😍")
        assertEquals(NameNote.EMOJI, s.youNote)
        assertFalse(s.canContinue)
        assertEquals(NameStep.YOU, NamePackReducer.continueFromYou(s).step)
    }

    @Test
    fun anEmptyOwnNameContinuesAndSkipClearsItButKeepsTheCharacter() {
        assertEquals(NameStep.LOVE, NamePackReducer.continueFromYou(start).step)
        val typed = NamePackReducer.pickCharacter(NamePackReducer.typeYou(start, "Aymen"), Character.CAPY)
        val skipped = NamePackReducer.skipYou(typed)
        assertEquals("", skipped.you.value)
        assertEquals(Character.CAPY, skipped.character)
        assertEquals(NameStep.LOVE, skipped.step)
    }

    @Test
    fun makingWithoutTheirNameShowsTheErrorUntilTheyType() {
        val love = start.copy(step = NameStep.LOVE)
        val (errored, started) = NamePackReducer.make(love)
        assertFalse(started)
        assertTrue(errored.needNameError)
        assertEquals(NameNote.NEED_NAME, errored.loveNote)
        assertFalse(NamePackReducer.typeLove(errored, "S").needNameError)
    }

    @Test
    fun emojiInTheirNameMakesNothing() {
        val love = NamePackReducer.typeLove(start.copy(step = NameStep.LOVE), "Sara 😍")
        val (next, started) = NamePackReducer.make(love)
        assertFalse(started)
        assertEquals(NameStep.LOVE, next.step)
        assertEquals(NameNote.EMOJI, next.loveNote)
    }

    @Test
    fun aValidNameStartsBuilding() {
        val love = NamePackReducer.typeLove(start.copy(step = NameStep.LOVE), "  Sara ")
        val (next, started) = NamePackReducer.make(love)
        assertTrue(started)
        assertEquals(NameStep.BUILDING, next.step)
        assertEquals("Sara", next.love.value)
    }

    @Test
    fun theLimitNoteShowsAtFourteenAndAPasteIsClamped() {
        val s = NamePackReducer.typeLove(start, "Anastasia-Maria")
        assertEquals("Anastasia-Mari", s.love.raw)
        assertEquals(14, s.love.count)
        assertEquals(NameNote.LIMIT, s.loveNote)
    }

    @Test
    fun pasteIsClamped() {
        assertEquals(14, NamePackReducer.typeYou(start, "x".repeat(50)).you.count)
    }

    @Test
    fun familyRelationsAreSweetOnly() {
        val flirty = NamePackReducer.pickTone(start, Tone.FLIRTY)
        val mom = NamePackReducer.pickRelation(flirty, Relation.MOM)
        assertEquals(Tone.SWEET, mom.effectiveTone)
        assertEquals(Tone.SWEET, NamePackReducer.pickTone(mom, Tone.FLIRTY).effectiveTone)
        assertEquals(Tone.FLIRTY, NamePackReducer.pickRelation(mom, Relation.CRUSH).effectiveTone)
    }

    @Test
    fun backWalksTheSteps() {
        assertNull(NamePackReducer.back(start))
        assertEquals(NameStep.YOU, NamePackReducer.back(start.copy(step = NameStep.LOVE))!!.step)
        assertEquals(NameStep.LOVE, NamePackReducer.back(start.copy(step = NameStep.BUILDING))!!.step)
        assertEquals(NameStep.LOVE, NamePackReducer.back(start.copy(step = NameStep.REVEAL))!!.step)
    }

    @Test
    fun savedStateRoundTrips() {
        val s = NamePackState(
            step = NameStep.LOVE, you = NameField("Aymen"), love = NameField("Sara"),
            relation = Relation.WIFE, character = Character.BUNNY, tone = Tone.FLIRTY
        )
        val saved = s.toSaved()
        assertEquals(s, namePackStateFrom { saved[it] })
    }

    @Test
    fun restoredRevealRebuilds() {
        val saved = start.copy(step = NameStep.REVEAL, love = NameField("Sara")).toSaved()
        assertEquals(NameStep.BUILDING, namePackStateFrom { saved[it] }.step)
    }

    @Test
    fun anEmptySavedStateIsTheStart() {
        assertEquals(start, namePackStateFrom { null })
    }

    @Test
    fun aSecondTapOnAFadingStepDoesNothing() {
        val building = NamePackReducer.typeLove(start, "Sara").copy(step = NameStep.BUILDING)
        assertEquals(building to false, NamePackReducer.make(building))
        val onLove = start.copy(step = NameStep.LOVE, you = NameField("Aymen"))
        assertEquals(onLove, NamePackReducer.continueFromYou(onLove))
        assertEquals(onLove, NamePackReducer.skipYou(onLove))
    }

    @Test
    fun aFinishedBuildFillsTheBarEvenWhenItsLastProgressLandsLate() {
        assertEquals(1f, buildProgress(done = 11, total = 12, finished = true, elapsedMs = 1_700, minMs = 1_700), 0f)
        assertEquals(0.5f, buildProgress(done = 6, total = 12, finished = false, elapsedMs = 5_000, minMs = 1_700), 0f)
        assertEquals(0.25f, buildProgress(done = 12, total = 12, finished = true, elapsedMs = 425, minMs = 1_700), 0.0001f)
    }
}
```

- [ ] **Step 2: Run it to see it fail**

Run: `./gradlew :app:testDebugUnitTest --tests '*NamePackStateTest*'`
Expected: compilation FAILS on the missing state types.

- [ ] **Step 3: Write the state**

`K/feature/namepack/NamePackState.kt`:

```kotlin
package com.piptechnologies.stickermaker.feature.namepack

import com.piptechnologies.stickermaker.feature.namepack.engine.Character
import com.piptechnologies.stickermaker.feature.namepack.engine.NameInput
import com.piptechnologies.stickermaker.feature.namepack.engine.Relation
import com.piptechnologies.stickermaker.feature.namepack.engine.Tone
import kotlin.math.min

/** The four steps of the name flow, one route. */
enum class NameStep { YOU, LOVE, BUILDING, REVEAL }

/** The note under a name field, by priority: emoji, missing name, limit. */
enum class NameNote { NONE, EMOJI, NEED_NAME, LIMIT }

/** A name field as typed ([raw], already clamped) and what the rules make of it. */
data class NameField(val raw: String = "") {
    val value: String get() = NameInput.normalize(raw)
    val count: Int get() = NameInput.graphemeCount(raw)
    val atLimit: Boolean get() = count >= NameInput.MAX_GRAPHEMES
    val hasEmoji: Boolean get() = !NameInput.isLetterable(value)
    val isBlank: Boolean get() = value.isEmpty()
}

/** Everything the steps decide on. Rendered stickers live in the ViewModel, not here. */
data class NamePackState(
    val step: NameStep = NameStep.YOU,
    val you: NameField = NameField(),
    val love: NameField = NameField(),
    val relation: Relation = Relation.GIRLFRIEND,
    val character: Character = Character.MANGO,
    val tone: Tone = Tone.SWEET,
    val needNameError: Boolean = false,
) {
    val effectiveTone: Tone get() = if (relation.family) Tone.SWEET else tone

    val youNote: NameNote
        get() = when {
            you.hasEmoji -> NameNote.EMOJI
            you.atLimit -> NameNote.LIMIT
            else -> NameNote.NONE
        }

    val loveNote: NameNote
        get() = when {
            love.hasEmoji -> NameNote.EMOJI
            needNameError -> NameNote.NEED_NAME
            love.atLimit -> NameNote.LIMIT
            else -> NameNote.NONE
        }

    /** Your name is optional; only emoji blocks Continue. */
    val canContinue: Boolean get() = !you.hasEmoji

    val canMake: Boolean get() = !love.isBlank && !love.hasEmoji
}

/** The flow's transitions: pure, so the rules are unit-tested without Android. */
object NamePackReducer {

    fun typeYou(s: NamePackState, raw: String) = s.copy(you = NameField(NameInput.clamp(raw)))

    fun typeLove(s: NamePackState, raw: String) = s.copy(love = NameField(NameInput.clamp(raw)), needNameError = false)

    fun pickCharacter(s: NamePackState, character: Character) = s.copy(character = character)

    fun pickRelation(s: NamePackState, relation: Relation) = s.copy(relation = relation)

    /** Family relations show only Sweet, so a tone pick changes nothing there. */
    fun pickTone(s: NamePackState, tone: Tone) = if (s.relation.family) s else s.copy(tone = tone)

    fun continueFromYou(s: NamePackState) = if (s.step == NameStep.YOU && s.canContinue) s.copy(step = NameStep.LOVE) else s

    fun skipYou(s: NamePackState) = if (s.step == NameStep.YOU) s.copy(you = NameField(), step = NameStep.LOVE) else s

    /**
     * "Make our stickers": the new state and whether building starts. Only from their-name: a
     * second tap on the step while it fades out does nothing.
     */
    fun make(s: NamePackState): Pair<NamePackState, Boolean> = when {
        s.step != NameStep.LOVE -> s to false
        s.love.hasEmoji -> s to false
        s.love.isBlank -> s.copy(needNameError = true) to false
        else -> s.copy(step = NameStep.BUILDING, needNameError = false) to true
    }

    /** System back; null leaves the flow (back to the intro). */
    fun back(s: NamePackState): NamePackState? = when (s.step) {
        NameStep.YOU -> null
        NameStep.LOVE -> s.copy(step = NameStep.YOU)
        NameStep.BUILDING, NameStep.REVEAL -> s.copy(step = NameStep.LOVE)
    }

    fun built(s: NamePackState) = s.copy(step = NameStep.REVEAL)

    fun buildFailed(s: NamePackState) = s.copy(step = NameStep.LOVE)
}

/**
 * The Building bar: finished renders over [total], never ahead of [minMs] of elapsed time. A
 * finished build counts as complete, because parallel renders can report their counts late and
 * out of order (12, then 11).
 */
fun buildProgress(done: Int, total: Int, finished: Boolean, elapsedMs: Long, minMs: Long): Float {
    val real = if (finished) 1f else done.toFloat() / total
    return min(real, elapsedMs.toFloat() / minMs).coerceIn(0f, 1f)
}

private const val KEY_STEP = "np_step"
private const val KEY_YOU = "np_you"
private const val KEY_LOVE = "np_love"
private const val KEY_RELATION = "np_relation"
private const val KEY_CHARACTER = "np_character"
private const val KEY_TONE = "np_tone"

/** What a SavedStateHandle keeps, so a language switch or process death resumes the flow. */
fun NamePackState.toSaved(): Map<String, String> = mapOf(
    KEY_STEP to step.name,
    KEY_YOU to you.raw,
    KEY_LOVE to love.raw,
    KEY_RELATION to relation.id,
    KEY_CHARACTER to character.id,
    KEY_TONE to tone.id,
)

/** The state back from [saved]; a saved Reveal restarts at Building (stickers are not saved state). */
fun namePackStateFrom(saved: (String) -> String?): NamePackState {
    val step = saved(KEY_STEP)?.let { name -> NameStep.entries.firstOrNull { it.name == name } } ?: NameStep.YOU
    return NamePackState(
        step = if (step == NameStep.REVEAL) NameStep.BUILDING else step,
        you = NameField(saved(KEY_YOU).orEmpty()),
        love = NameField(saved(KEY_LOVE).orEmpty()),
        relation = Relation.byId(saved(KEY_RELATION)),
        character = Character.byId(saved(KEY_CHARACTER)),
        tone = Tone.byId(saved(KEY_TONE)),
    )
}
```

- [ ] **Step 4: Run the state test**

Run: `./gradlew :app:testDebugUnitTest --tests '*NamePackStateTest*'`
Expected: 15 tests PASS.

- [ ] **Step 5: Analytics and the toast hand-off**

`K/core/telemetry/Telemetry.kt`: replace `logOnboardingComplete(themes: Int)` with:

```kotlin
    /** First-run setup ended on Home: the name pack was "added" to WhatsApp, "saved" to My Packs, or "skipped". */
    fun logOnboardingComplete(namePack: String) = log("onboarding_complete") { putString("name_pack", namePack) }

    /** A name pack was lettered. Enum-like values only: never the names. */
    fun logNamePackBuilt(relation: String, tone: String, character: String, hasYourName: Boolean, language: String) =
        log("name_pack_built") {
            putString("relation", relation)
            putString("tone", tone)
            putString("character", character)
            putLong("has_your_name", if (hasYourName) 1L else 0L)
            putString("language", language)
        }
```

`K/core/ui/PendingToasts.kt`:

```kotlin
package com.piptechnologies.stickermaker.core.ui

import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One toast waiting for the next screen: the name flow posts "Added to WhatsApp" and Home
 * shows it when it opens (screens own their toast hosts, so nothing else crosses routes).
 */
@Singleton
class PendingToasts @Inject constructor() {

    private val pending = AtomicReference<UiText?>(null)

    fun post(message: UiText) = pending.set(message)

    fun take(): UiText? = pending.getAndSet(null)
}
```

- [ ] **Step 6: Crash reports never carry the names**

Failures in this flow can carry the names in their messages: the saver's and validator's messages hold the pack id (a short hash of the names) or the pack name itself, and file errors hold paths with the id. Crash reporting records messages as they are, so every name-flow failure is recorded as its type and stack trace only.

`K/feature/namepack/Redaction.kt`:

```kotlin
package com.piptechnologies.stickermaker.feature.namepack

/**
 * This failure as crash reporting may see it: the same types and stack traces, every message
 * removed. Messages from the name flow can hold the pack id (a hash of the names) or the names.
 */
internal fun Throwable.redacted(): Throwable =
    RedactedFailure(this::class.java.name, cause?.redacted()).also { it.stackTrace = stackTrace }

/** Stands in for a name-flow failure; its message is only the original type's name. */
internal class RedactedFailure(type: String, cause: Throwable?) : Exception(type, cause)
```

`…/feature/namepack/RedactionTest.kt`:

```kotlin
package com.piptechnologies.stickermaker.feature.namepack

import java.io.IOException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RedactionTest {

    @Test
    fun keepsTypesAndStacksButNoMessages() {
        val cause = IOException("Could not move pack own-np-0123456789ab into place")
        val failure = IllegalStateException("sticker pack name: Aymen \u2764 Sara", cause)
        val redacted = failure.redacted()
        generateSequence(redacted) { it.cause }.forEach { t ->
            assertFalse(t.toString(), "own-np-" in t.toString() || "Sara" in t.toString())
        }
        assertEquals("java.lang.IllegalStateException", redacted.message)
        assertEquals("java.io.IOException", redacted.cause?.message)
        assertArrayEquals(failure.stackTrace, redacted.stackTrace)
    }
}
```

Run: `./gradlew :app:testDebugUnitTest --tests '*RedactionTest*'` — 1 test PASS. The ViewModel below records every failure as `e.redacted()`.

- [ ] **Step 7: Write the ViewModel**

`K/feature/namepack/NamePackViewModel.kt`:

```kotlin
package com.piptechnologies.stickermaker.feature.namepack

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import android.os.SystemClock
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.piptechnologies.stickermaker.R
import com.piptechnologies.stickermaker.core.data.prefs.PrefsRepository
import com.piptechnologies.stickermaker.core.data.repo.MyPacksRepository
import com.piptechnologies.stickermaker.core.design.components.AddVisualState
import com.piptechnologies.stickermaker.core.telemetry.AppAnalytics
import com.piptechnologies.stickermaker.core.telemetry.CrashReporting
import com.piptechnologies.stickermaker.core.ui.PendingToasts
import com.piptechnologies.stickermaker.core.ui.UiText
import com.piptechnologies.stickermaker.core.ui.inAppLanguage
import com.piptechnologies.stickermaker.feature.language.AppLanguages
import com.piptechnologies.stickermaker.feature.language.effectiveTag
import com.piptechnologies.stickermaker.feature.namepack.engine.Character
import com.piptechnologies.stickermaker.feature.namepack.engine.LetteredSticker
import com.piptechnologies.stickermaker.feature.namepack.engine.NamePackAssets
import com.piptechnologies.stickermaker.feature.namepack.engine.NamePackBuilder
import com.piptechnologies.stickermaker.feature.namepack.engine.NamePackRequest
import com.piptechnologies.stickermaker.feature.namepack.engine.NamePackSaver
import com.piptechnologies.stickermaker.feature.namepack.engine.Relation
import com.piptechnologies.stickermaker.feature.namepack.engine.Slot
import com.piptechnologies.stickermaker.feature.namepack.engine.Tone
import com.piptechnologies.stickermaker.feature.namepack.engine.TrayImage
import com.piptechnologies.stickermaker.feature.rating.RatingPromptController
import com.piptechnologies.stickermaker.whatsapp.AddStickerPackFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** One lettered sticker as the reveal grid shows it. */
data class RevealTile(val slot: Slot, val image: ImageBitmap, val text: String)

data class NamePackUiState(
    val flow: NamePackState = NamePackState(),
    val preview: ImageBitmap? = null,
    val previewText: String = "",
    val progress: Float = 0f,
    val tiles: List<RevealTile> = emptyList(),
    val tray: ImageBitmap? = null,
    val packYou: String = "",
    val packLove: String = "",
    val packName: String = "",
    val addState: AddVisualState = AddVisualState.Idle,
    val relettering: Boolean = false,
    val tileArt: Map<Character, ImageBitmap> = emptyMap(),
    val waitArt: ImageBitmap? = null,
)

sealed interface NamePackEvent {
    data class LaunchAdd(val identifier: String, val packName: String) : NamePackEvent
    data object ShowNoWhatsApp : NamePackEvent
    data class Toast(val message: UiText) : NamePackEvent
    /** The flow is over: go Home. */
    data object Finished : NamePackEvent
    /** Back from the first step: return to the intro. */
    data object Leave : NamePackEvent
}

/**
 * Drives the Custom Stickers flow (spec: docs/specs/2026-09-29-custom-stickers-design.md):
 * the pure [NamePackReducer] decides, this class renders previews, builds and re-letters the
 * pack, saves it, and ends first run. Flow state survives a language switch in the
 * SavedStateHandle; rendered bitmaps are rebuilt.
 */
@HiltViewModel
class NamePackViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val savedState: SavedStateHandle,
    private val prefs: PrefsRepository,
    myPacks: MyPacksRepository,
    private val pendingToasts: PendingToasts,
) : ViewModel() {

    private data class Lang(val tag: String, val rtl: Boolean)

    /** A lettered pack and its name ("{you} ❤ {love}" or "For {love}"), fixed in the pack's own language. */
    private class Built(val request: NamePackRequest, val stickers: List<LetteredSticker>, val tray: TrayImage, val name: String)

    private val assets = NamePackAssets(appContext)
    private val builder = NamePackBuilder(assets)
    private val saver = NamePackSaver(
        appContext.filesDir,
        myPacks,
        appContext.getString(R.string.config_support_email),
        appContext.getString(R.string.config_privacy_policy_url)
    )

    private val ui = MutableStateFlow(NamePackUiState(flow = namePackStateFrom { savedState.get<String>(it) }))
    val uiState: StateFlow<NamePackUiState> = ui.asStateFlow()

    private val _events = Channel<NamePackEvent>(Channel.BUFFERED)
    val events: Flow<NamePackEvent> = _events.receiveAsFlow()

    private val lang = MutableStateFlow(langOf(AppLanguages.effectiveTag()))
    private var buildJob: Job? = null
    private var built: Built? = null
    private var savedContent: String? = null
    private val saveLock = Mutex()
    /** The Add or "Not now" save in flight; taps on either wait it out. */
    private var saveJob: Job? = null
    private var finishing = false

    init {
        viewModelScope.launch {
            ui.map { it.flow }.distinctUntilChanged().collect { state ->
                state.toSaved().forEach { (key, value) -> savedState[key] = value }
            }
        }
        viewModelScope.launch {
            val art = withContext(Dispatchers.Default) {
                Character.entries.associateWith { assets.tileArt(it).asImageBitmap() }
            }
            ui.update { it.copy(tileArt = art) }
        }
        viewModelScope.launch {
            combine(ui.map { it.flow }.distinctUntilChanged(), lang) { state, l -> previewOf(state, l) }
                .distinctUntilChanged()
                .collectLatest { job -> if (job != null) renderPreview(job) }
        }
        if (ui.value.flow.step == NameStep.BUILDING) startBuild()
    }

    // ------------------------------------------------------------ events //

    /** The screen reports the app language after every (re)composition start; a change re-letters. */
    fun onLanguage(tag: String) {
        val next = langOf(tag)
        if (lang.value == next) return
        lang.value = next
        when (ui.value.flow.step) {
            NameStep.BUILDING -> startBuild()
            NameStep.REVEAL -> reletter()
            else -> Unit
        }
    }

    fun onYouChange(raw: String) = updateFlow { NamePackReducer.typeYou(it, raw) }

    fun onLoveChange(raw: String) = updateFlow { NamePackReducer.typeLove(it, raw) }

    fun onRelation(relation: Relation) = updateFlow { NamePackReducer.pickRelation(it, relation) }

    fun onCharacter(character: Character) {
        updateFlow { NamePackReducer.pickCharacter(it, character) }
        if (ui.value.flow.step == NameStep.REVEAL) reletter()
    }

    fun onTone(tone: Tone) {
        val before = ui.value.flow.effectiveTone
        updateFlow { NamePackReducer.pickTone(it, tone) }
        if (ui.value.flow.step == NameStep.REVEAL && ui.value.flow.effectiveTone != before) reletter()
    }

    fun onContinue() = updateFlow(NamePackReducer::continueFromYou)

    fun onSkipYou() = updateFlow(NamePackReducer::skipYou)

    fun onMake() {
        val (next, start) = NamePackReducer.make(ui.value.flow)
        updateFlow { next }
        if (start) startBuild()
    }

    /** Skip on their-name: Home with no pack. */
    fun onSkipLove() {
        if (ui.value.flow.step != NameStep.LOVE) return
        buildJob?.cancel()
        finish(outcome = "skipped", toast = null)
    }

    fun onBack() {
        val state = ui.value.flow
        // Building cancels its build; the reveal cancels a re-letter so it cannot land on their-name.
        if (state.step == NameStep.BUILDING || state.step == NameStep.REVEAL) buildJob?.cancel()
        val next = NamePackReducer.back(state)
        if (next == null) {
            viewModelScope.launch { _events.send(NamePackEvent.Leave) }
        } else {
            updateFlow { next }
        }
    }

    fun onAdd() {
        if (ui.value.flow.step != NameStep.REVEAL || saveJob?.isActive == true) return
        if (ui.value.addState == AddVisualState.Sent || ui.value.addState == AddVisualState.Added) return
        if (!AddStickerPackFlow.isWhatsAppInstalled(appContext)) {
            // The pack still lands in My Packs, then the install sheet explains (design, Reveal). The
            // sheet promises nothing about saving, and Add or "Not now" retry a save that failed here.
            saveJob = viewModelScope.launch {
                val current = settled() ?: return@launch
                attempt("name_pack_save") { save(current) }
                _events.send(NamePackEvent.ShowNoWhatsApp)
            }
            return
        }
        ui.update { it.copy(addState = AddVisualState.Sent) }
        saveJob = viewModelScope.launch {
            val current = settled()
            if (current == null) {
                ui.update { it.copy(addState = AddVisualState.Idle) }
                return@launch
            }
            try {
                val name = save(current)
                _events.send(NamePackEvent.LaunchAdd(current.request.packId, name))
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                CrashReporting.record(e.redacted(), "name_pack_save")
                ui.update { it.copy(addState = AddVisualState.Failed) }
                catchUpIfStale()
            }
        }
    }

    fun onNotNow() {
        if (ui.value.flow.step != NameStep.REVEAL || saveJob?.isActive == true) return
        saveJob = viewModelScope.launch {
            val current = settled()
            if (current != null && attempt("name_pack_save") { save(current) } == null) {
                // Not saved: say so and stay on the reveal, so "Not now" or Add can try again.
                _events.send(NamePackEvent.Toast(UiText.res(R.string.namepack_add_failed)))
                return@launch
            }
            finish(outcome = if (current != null) "saved" else "skipped", toast = null)
        }
    }

    /** WhatsApp's verdict on [NamePackEvent.LaunchAdd] (a null intent counts as added). */
    fun onWhatsAppResult(added: Boolean, rejected: Boolean = false) {
        // After process death the rendered pack is gone, but the names and language were restored,
        // so the id is too: a confirmed add still counts, arms the rating prompt and ends the flow.
        val id = built?.request?.packId ?: requestFor(ui.value.flow).packId
        if (added) {
            AppAnalytics.logPackAdded(id)
            RatingPromptController.onPackAdded(appContext)
            ui.update { it.copy(addState = AddVisualState.Added) }
            finish(outcome = "added", toast = UiText.res(R.string.toast_added_to_whatsapp))
        } else {
            AppAnalytics.logPackAddCancelled(id, rejected)
            ui.update { it.copy(addState = AddVisualState.Idle) }
            catchUpIfStale()
        }
    }

    // ----------------------------------------------------------- building //

    private fun startBuild() {
        buildJob?.cancel()
        val request = requestFor(ui.value.flow)
        ui.update { it.copy(progress = 0f) }
        buildJob = viewModelScope.launch {
            try {
                ensureWaitArt(request.character)
                val started = SystemClock.uptimeMillis()
                val done = AtomicInteger(0)
                // runCatching keeps a render failure from cancelling this coroutine (and crashing
                // through viewModelScope): the loop sees it and getOrThrow() reaches the catch below.
                val building = async { runCatching { builder.build(request) { n -> done.accumulateAndGet(n) { a, b -> maxOf(a, b) } } } }
                while (true) {
                    val shown = buildProgress(
                        done.get(), Slot.entries.size, building.isCompleted,
                        SystemClock.uptimeMillis() - started, MIN_BUILD_MS
                    )
                    ui.update { it.copy(progress = shown) }
                    if (building.isCompleted && (shown >= 1f || building.await().isFailure)) break
                    delay(FRAME_MS)
                }
                val result = building.await().getOrThrow()
                val tray = withContext(Dispatchers.Default) { builder.tray(request) }
                delay(HOLD_MS)
                applyBuilt(Built(request, result, tray, packName(request)))
                updateFlow(NamePackReducer::built)
                AppAnalytics.logNamePackBuilt(
                    relation = request.relation.id,
                    tone = request.effectiveTone.id,
                    character = request.character.id,
                    hasYourName = request.you.isNotEmpty(),
                    language = request.lang
                )
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                CrashReporting.record(e.redacted(), "name_pack_build")
                _events.send(NamePackEvent.Toast(UiText.res(R.string.namepack_build_failed)))
                updateFlow(NamePackReducer::buildFailed)
            }
        }
    }

    /**
     * Tone, character or language changed on the reveal: re-letter in place (cached ones are
     * instant). Not while an add is in flight; [catchUpIfStale] runs once it settles.
     */
    private fun reletter() {
        if (ui.value.addState == AddVisualState.Sent) return
        buildJob?.cancel()
        val request = requestFor(ui.value.flow)
        ui.update { it.copy(relettering = true) }
        buildJob = viewModelScope.launch {
            try {
                val stickers = builder.build(request)
                val tray = withContext(Dispatchers.Default) { builder.tray(request) }
                applyBuilt(Built(request, stickers, tray, packName(request)))
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                CrashReporting.record(e.redacted(), "name_pack_build")
                // Keep the tone and character controls true to the stickers still on screen.
                val shown = built?.request
                ui.update { state ->
                    state.copy(
                        relettering = false,
                        flow = if (shown == null) state.flow else state.flow.copy(tone = shown.tone, character = shown.character)
                    )
                }
                _events.send(NamePackEvent.Toast(UiText.res(R.string.namepack_build_failed)))
            }
        }
    }

    private fun applyBuilt(result: Built) {
        built = result
        ui.update {
            it.copy(
                tiles = result.stickers.map { s -> RevealTile(s.slot, s.preview.asImageBitmap(), s.text) },
                tray = result.tray.bitmap.asImageBitmap(),
                packYou = result.request.you,
                packLove = result.request.love,
                packName = result.name,
                // An add in flight keeps its state: the re-letter it waited for must not re-enable Add.
                addState = if (it.addState == AddVisualState.Added || it.addState == AddVisualState.Sent) it.addState else AddVisualState.Idle,
                relettering = false
            )
        }
    }

    private suspend fun ensureWaitArt(character: Character) {
        val art = withContext(Dispatchers.Default) { assets.waitArt(character).asImageBitmap() }
        ui.update { it.copy(waitArt = art) }
    }

    // ------------------------------------------------------------- saving //

    /** Writes the pack once per content (an overlapping call waits, then finds it written); returns its name. */
    private suspend fun save(current: Built): String = saveLock.withLock {
        if (savedContent != current.request.contentKey) {
            saver.save(current.request.packId, current.name, current.stickers, current.tray.png)
            savedContent = current.request.contentKey
        }
        current.name
    }

    /** The pack the screen shows, once a re-letter in flight has landed (or failed and restored the controls). */
    private suspend fun settled(): Built? {
        buildJob?.join()
        return built
    }

    /** After an add settles: re-letter if the language (or a control) moved while it was in flight. */
    private fun catchUpIfStale() {
        val shown = built?.request ?: return
        if (ui.value.flow.step == NameStep.REVEAL && shown != requestFor(ui.value.flow)) reletter()
    }

    /** The pack's name in its own language, whatever the app shows later. */
    private fun packName(request: NamePackRequest): String =
        if (request.you.isNotEmpty()) "${request.you} ❤ ${request.love}"
        else inLanguage(request.lang).getString(R.string.namepack_pack_for, request.love)

    private fun finish(outcome: String, toast: UiText?) {
        if (finishing) return
        finishing = true
        viewModelScope.launch {
            if (!prefs.onboarded.first()) {
                prefs.setOnboarded(true)
                AppAnalytics.logOnboardingComplete(outcome)
            }
            toast?.let(pendingToasts::post)
            _events.send(NamePackEvent.Finished)
        }
    }

    // ------------------------------------------------------------ preview //

    private data class PreviewJob(val request: NamePackRequest, val slot: Slot, val text: String)

    /** Your-name letters the name itself on name_only; their-name letters love_you with the sample name while empty. */
    private fun previewOf(state: NamePackState, l: Lang): PreviewJob? {
        val base = NamePackRequest(l.tag, l.rtl, state.you.value, state.love.value, state.relation, Tone.SWEET, state.character)
        return when (state.step) {
            NameStep.YOU -> PreviewJob(
                base, Slot.NAME_ONLY,
                state.you.value.ifEmpty { words().getString(R.string.namepack_you_placeholder) }
            )
            NameStep.LOVE -> {
                val love = state.love.value.ifEmpty { words().getString(R.string.namepack_sample_name) }
                PreviewJob(base, Slot.LOVE_YOU, builder.text(base.copy(love = love), Slot.LOVE_YOU))
            }
            else -> null
        }
    }

    private suspend fun renderPreview(job: PreviewJob) {
        val bitmap = withContext(Dispatchers.Default) {
            attempt("name_pack_preview") { builder.preview(job.request, job.slot, job.text) }
        } ?: return
        ui.update { it.copy(preview = bitmap.asImageBitmap(), previewText = job.text) }
    }

    // ------------------------------------------------------------ helpers //

    private fun updateFlow(transform: (NamePackState) -> NamePackState) = ui.update { it.copy(flow = transform(it.flow)) }

    /** Runs [block]; a failure is recorded under [where] and gives null. Cancellation passes through. */
    private suspend fun <T> attempt(where: String, block: suspend () -> T): T? =
        try {
            block()
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            CrashReporting.record(e.redacted(), where)
            null
        }

    private fun requestFor(state: NamePackState): NamePackRequest {
        val l = lang.value
        return NamePackRequest(l.tag, l.rtl, state.you.value, state.love.value, state.relation, state.tone, state.character)
    }

    private fun langOf(tag: String) = Lang(tag, AppLanguages.byTag(tag)?.rtl == true)

    /** Strings in the app language, also below API 33 where the application context is not localized. */
    private fun words(): Context = appContext.inAppLanguage()

    /** Resources in [tag] (an AppLanguages tag), whatever the app language is now. */
    private fun inLanguage(tag: String): Context {
        val config = Configuration(appContext.resources.configuration)
        config.setLocales(LocaleList.forLanguageTags(tag))
        return appContext.createConfigurationContext(config)
    }

    private companion object {
        const val MIN_BUILD_MS = 1_700L
        const val HOLD_MS = 420L
        const val FRAME_MS = 16L
    }
}
```

- [ ] **Step 8: Verify**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL; all tests pass (including `NamePackStateTest`).

---

### Task 12: Name steps UI (your name, their name)

Everything here follows `docs/specs/2026-09-29-custom-stickers-screens.md` §c and §d; numbers in comments are dp/sp from that file.

**Files:**
- Create: `K/feature/namepack/NamePackIcons.kt`, `K/feature/namepack/NamePackUi.kt`, `K/feature/namepack/NameSteps.kt`
- Modify: `K/feature/create/CreateUi.kt` (`PrimaryButton` gains `dimmed`)

**Interfaces:**
- Consumes: `NamePackUiState`, `NameNote`, `NameInput.MAX_GRAPHEMES`, `LanguageTopStrip(onLanguage, onSkip)` (Task 10), `LoveIcons.lucideIcon` (internal since Task 10), `PrimaryButton(label, onClick, modifier, enabled, dimmed)` (feature/create/CreateUi.kt, `dimmed` added here), `CategoryChip`, colour/type tokens.
- Produces (all `internal`):
  - `NamePackIcons.UserRound`, `NamePackIcons.AppHeart`
  - `TitleInk`, `ErrorLine`, `FootnoteGrey`, `TileBg`, `FooterLine`, `TitleStyle`, `HelperStyle`, `LabelStyle`, `HintStyle`
  - `@Composable rememberReduceMotion(): Boolean`
  - `@Composable StepBar(filled: Int)`, `NameTextField(value, onValueChange, placeholder, count, error, imeAction, onSubmit, modifier)`, `NameNoteLine(note)`, `CharacterRow(selected, onPick, tileArt, artSize, modifier)`, `RelationChips(selected, onPick, modifier)`, `PreviewArea(image, description, modifier)`
  - `fun Relation.labelRes(): Int`
  - `@Composable NameYouStep(state, onLanguage, onSkip, onType, onCharacter, onContinue)`, `@Composable NameLoveStep(state, onLanguage, onSkip, onType, onRelation, onMake)`

- [ ] **Step 1: Icons**

`K/feature/namepack/NamePackIcons.kt`:

```kotlin
package com.piptechnologies.stickermaker.feature.namepack

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import com.piptechnologies.stickermaker.core.design.LoveIcons

/**
 * Glyphs only the name flow uses, kept with the feature like SettingsIcons: Lucide `user-round`
 * (not in the vendored set) and the design's app-mark heart (not Lucide).
 */
internal object NamePackIcons {

    /** Lucide `user-round` (its circle pre-flattened to arcs), built like every LoveIcons glyph. */
    val UserRound: ImageVector by lazy {
        LoveIcons.lucideIcon("user-round", "M7 8a5 5 0 1 0 10 0a5 5 0 1 0 -10 0", "M20 21a8 8 0 0 0-16 0")
    }

    /** Claude Design's app-mark heart, filled (the ❤ in pack names). */
    val AppHeart: ImageVector = ImageVector.Builder(
        name = "app-heart", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        addPath(
            pathData = addPathNodes(
                "M 12 21 s -7.5 -4.7 -9.6 -9.2 C 0.7 8 3 4.5 6.6 4.5 c 2 0 3.6 1.1 4.4 2.6 " +
                    "c 0.8 -1.5 2.4 -2.6 4.4 -2.6 c 3.6 0 5.9 3.5 4.2 7.3 C 19.5 16.3 12 21 12 21 z"
            ),
            fill = SolidColor(Color.Black)
        )
    }.build()
}
```

- [ ] **Step 2: Let the shared primary button look disabled while it still listens**

"Make our stickers" looks disabled until a name is typed, but a tap on it still explains what is missing (design §d). In `K/feature/create/CreateUi.kt`, `PrimaryButton` gains `dimmed` (default: the old behaviour):

```kotlin
/**
 * The design's 52/14 filled-rose primary. Disabled reads at 45% opacity and
 * swallows taps, like the prototype's `opacity:.45;cursor:default` buttons.
 * [dimmed] draws that look on its own, for a button that still answers a tap.
 */
@Composable
internal fun PrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    dimmed: Boolean = !enabled
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .alpha(if (dimmed) 0.45f else 1f)
```

(the rest is unchanged). Existing callers keep their look.

- [ ] **Step 3: Shared pieces**

`K/feature/namepack/NamePackUi.kt`:

```kotlin
package com.piptechnologies.stickermaker.feature.namepack

import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.piptechnologies.stickermaker.R
import com.piptechnologies.stickermaker.core.design.Amber
import com.piptechnologies.stickermaker.core.design.Border
import com.piptechnologies.stickermaker.core.design.Hanken
import com.piptechnologies.stickermaker.core.design.Ink
import com.piptechnologies.stickermaker.core.design.Ink2
import com.piptechnologies.stickermaker.core.design.Mono
import com.piptechnologies.stickermaker.core.design.Muted
import com.piptechnologies.stickermaker.core.design.Muted2
import com.piptechnologies.stickermaker.core.design.Rose
import com.piptechnologies.stickermaker.core.design.RoseTint
import com.piptechnologies.stickermaker.core.design.Surface
import com.piptechnologies.stickermaker.core.design.components.CategoryChip
import com.piptechnologies.stickermaker.feature.namepack.engine.Character
import com.piptechnologies.stickermaker.feature.namepack.engine.NameInput
import com.piptechnologies.stickermaker.feature.namepack.engine.Relation

internal val TitleInk = Color(0xFF171A20)
/** Name-field border on error (emoji, missing name): the design's one new colour. */
internal val ErrorLine = Color(0xFFE0B778)
internal val FootnoteGrey = Color(0xFF99A0AC)
internal val TileBg = Color(0xFFF6F7F9)
internal val FooterLine = Color(0xFFEEF0F4)

internal val TitleStyle = TextStyle(
    fontFamily = Hanken, fontWeight = FontWeight.W800, fontSize = 26.sp, lineHeight = 30.sp, letterSpacing = (-0.02).em
)
internal val HelperStyle = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.W400, fontSize = 14.sp, lineHeight = 21.sp)
internal val LabelStyle = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.W600, fontSize = 12.5.sp)
internal val HintStyle = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.W400, fontSize = 11.5.sp)
private val NoteStyle = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.W400, fontSize = 12.5.sp, lineHeight = 17.5.sp)
private val FieldText = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.W400, fontSize = 16.sp, color = Ink)
private val CounterText = TextStyle(fontFamily = Mono, fontWeight = FontWeight.W400, fontSize = 11.sp)
private val TileLabel = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.W600, fontSize = 11.sp)

/**
 * Whether the user turned animations off (Developer options or accessibility). Compose already
 * scales its own animations by this setting; this switches off what it cannot see: the waiting
 * bob, the tile pop and the staggered delays. False where the setting cannot be read (previews).
 */
@Composable
internal fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
}

/** Two 4 segments 6 apart, [filled] of them rose; 6 above, 16 below. */
@Composable
internal fun StepBar(filled: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        repeat(2) { index ->
            Box(
                Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (index < filled) Rose else Border)
            )
        }
    }
}

/**
 * 52/r12 field: user icon 17, input 16/400, mono n/14 counter; rose border focused, amber on
 * error. The keyboard's action key ([imeAction]) runs [onSubmit], like the step's button.
 */
@Composable
internal fun NameTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    count: Int,
    error: Boolean,
    imeAction: ImeAction,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val shape = RoundedCornerShape(12.dp)
    val line = when {
        error -> ErrorLine
        focused -> Rose
        else -> Border
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(shape)
            .background(Surface)
            .border(1.dp, line, shape)
            // The whole 52 dp row focuses the field, not only its one text line.
            .pointerInput(Unit) { detectTapGestures { focusRequester.requestFocus() } }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Icon(NamePackIcons.UserRound, contentDescription = null, modifier = Modifier.size(17.dp), tint = Muted)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = FieldText,
            cursorBrush = SolidColor(Rose),
            // Names are lettered as typed: the keyboard must not "correct" them.
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                autoCorrectEnabled = false,
                imeAction = imeAction
            ),
            keyboardActions = KeyboardActions(onNext = { onSubmit() }, onDone = { onSubmit() }),
            modifier = Modifier.weight(1f).focusRequester(focusRequester).onFocusChanged { focused = it.isFocused },
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) Text(placeholder, style = FieldText, color = Muted)
                    inner()
                }
            }
        )
        Text(
            stringResource(R.string.create_name_counter, count, NameInput.MAX_GRAPHEMES),
            style = CounterText,
            color = if (count >= NameInput.MAX_GRAPHEMES) Amber else Muted2
        )
    }
}

/** The note under a field: limit in Muted, emoji and missing name in Amber; 8 above. Screen readers announce it. */
@Composable
internal fun NameNoteLine(note: NameNote) {
    val (res, color) = when (note) {
        NameNote.NONE -> return
        NameNote.EMOJI -> R.string.namepack_note_emoji to Amber
        NameNote.NEED_NAME -> R.string.namepack_note_need_name to Amber
        NameNote.LIMIT -> R.string.namepack_note_limit to Muted
    }
    Text(
        stringResource(res),
        style = NoteStyle,
        color = color,
        modifier = Modifier.padding(top = 8.dp).semantics { liveRegion = LiveRegionMode.Polite }
    )
}

/** "Who says it?": four equal tiles, art [artSize] (46 on your-name, 40 on the reveal). */
@Composable
internal fun CharacterRow(
    selected: Character,
    onPick: (Character) -> Unit,
    tileArt: Map<Character, ImageBitmap>,
    artSize: Dp,
    modifier: Modifier = Modifier
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Character.entries.forEach { character ->
            CharacterTile(character, character == selected, tileArt[character], artSize, { onPick(character) }, Modifier.weight(1f))
        }
    }
}

@Composable
private fun CharacterTile(
    character: Character,
    selected: Boolean,
    art: ImageBitmap?,
    artSize: Dp,
    onClick: () -> Unit,
    modifier: Modifier
) {
    val shape = RoundedCornerShape(14.dp)
    val bg by animateColorAsState(if (selected) RoseTint else Surface, tween(120), label = "tileBg")
    val line by animateColorAsState(if (selected) Rose else Border, tween(120), label = "tileLine")
    Column(
        modifier = modifier
            .clip(shape)
            .background(bg)
            .border(1.dp, line, shape)
            .clickable(role = Role.Button, onClickLabel = character.label, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(start = 4.dp, top = 8.dp, end = 4.dp, bottom = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(Modifier.size(artSize)) {
            if (art != null) Image(art, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        }
        Text(character.label, style = TileLabel, color = if (selected) Rose else Ink2, maxLines = 1)
    }
}

/** The eight relation chips (36, pad 15, 13/600), wrapping, 8 apart, 14 above; one always selected. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RelationChips(selected: Relation, onPick: (Relation) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier.fillMaxWidth().padding(top = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Relation.entries.forEach { relation ->
            CategoryChip(
                label = stringResource(relation.labelRes()),
                selected = relation == selected,
                onClick = { onPick(relation) },
                modifier = Modifier.semantics { this.selected = relation == selected }
            )
        }
    }
}

internal fun Relation.labelRes(): Int = when (this) {
    Relation.GIRLFRIEND -> R.string.namepack_rel_girlfriend
    Relation.BOYFRIEND -> R.string.namepack_rel_boyfriend
    Relation.WIFE -> R.string.namepack_rel_wife
    Relation.HUSBAND -> R.string.namepack_rel_husband
    Relation.CRUSH -> R.string.namepack_rel_crush
    Relation.PARTNER -> R.string.namepack_rel_partner
    Relation.MOM -> R.string.namepack_rel_mom
    Relation.FRIEND -> R.string.namepack_rel_friend
}

/**
 * The live preview: 210 at most, shrinking to the room the keyboard leaves (hidden below 72).
 * It swaps instantly on each keystroke, as the prototype does: art and lettering are one bitmap,
 * so a crossfade would dim the character on every letter typed.
 */
@Composable
internal fun PreviewArea(image: ImageBitmap?, description: String, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        val side = minOf(210.dp, maxHeight, maxWidth)
        if (image != null && side >= 72.dp) {
            Image(image, contentDescription = description, modifier = Modifier.size(side))
        }
    }
}
```

- [ ] **Step 4: The two steps**

`K/feature/namepack/NameSteps.kt`:

```kotlin
package com.piptechnologies.stickermaker.feature.namepack

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.piptechnologies.stickermaker.R
import com.piptechnologies.stickermaker.core.design.Canvas
import com.piptechnologies.stickermaker.core.design.Ink2
import com.piptechnologies.stickermaker.core.design.LoveStickersTheme
import com.piptechnologies.stickermaker.feature.create.PrimaryButton
import com.piptechnologies.stickermaker.feature.language.LanguageTopStrip
import com.piptechnologies.stickermaker.feature.namepack.engine.Character
import com.piptechnologies.stickermaker.feature.namepack.engine.Relation

/** Column shared by both steps: Canvas background, 4/24/22 padding plus system bars and keyboard. */
private fun Modifier.stepFrame(): Modifier = this
    .fillMaxSize()
    .background(Canvas)
    .statusBarsPadding()
    .navigationBarsPadding()
    .imePadding()
    .padding(start = 24.dp, top = 4.dp, end = 24.dp, bottom = 22.dp)

/** Step 1/2 (screens spec §c): who says it, your optional name, the name_only preview. */
@Composable
internal fun NameYouStep(
    state: NamePackUiState,
    onLanguage: () -> Unit,
    onSkip: () -> Unit,
    onType: (String) -> Unit,
    onCharacter: (Character) -> Unit,
    onContinue: () -> Unit
) {
    val flow = state.flow
    Column(Modifier.stepFrame()) {
        LanguageTopStrip(onLanguage = onLanguage, onSkip = onSkip)
        StepBar(filled = 1)
        Text(stringResource(R.string.namepack_you_title), style = TitleStyle, color = TitleInk)
        Text(
            stringResource(R.string.namepack_you_helper),
            style = HelperStyle,
            color = Ink2,
            modifier = Modifier.padding(top = 8.dp, bottom = 14.dp)
        )
        Text(stringResource(R.string.namepack_pick_character), style = LabelStyle, color = Ink2, modifier = Modifier.padding(bottom = 8.dp))
        CharacterRow(flow.character, onCharacter, state.tileArt, artSize = 46.dp, modifier = Modifier.padding(bottom = 14.dp))
        NameTextField(
            value = flow.you.raw,
            onValueChange = onType,
            placeholder = stringResource(R.string.namepack_you_placeholder),
            count = flow.you.count,
            error = flow.youNote == NameNote.EMOJI,
            imeAction = ImeAction.Next,
            onSubmit = onContinue
        )
        NameNoteLine(flow.youNote)
        PreviewArea(
            image = state.preview,
            description = stringResource(R.string.namepack_preview_alt, state.previewText),
            modifier = Modifier.weight(1f)
        )
        Text(
            stringResource(R.string.namepack_preview_hint),
            style = HintStyle,
            color = FootnoteGrey,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
        )
        PrimaryButton(
            label = stringResource(R.string.namepack_continue),
            onClick = onContinue,
            enabled = flow.canContinue
        )
    }
}

/** Step 2/2 (screens spec §d): their name, relation, the love_you preview, "Make our stickers". */
@Composable
internal fun NameLoveStep(
    state: NamePackUiState,
    onLanguage: () -> Unit,
    onSkip: () -> Unit,
    onType: (String) -> Unit,
    onRelation: (Relation) -> Unit,
    onMake: () -> Unit
) {
    val flow = state.flow
    Column(Modifier.stepFrame()) {
        LanguageTopStrip(onLanguage = onLanguage, onSkip = onSkip)
        StepBar(filled = 2)
        Text(stringResource(R.string.namepack_love_title), style = TitleStyle, color = TitleInk)
        Text(
            stringResource(R.string.namepack_love_helper),
            style = HelperStyle,
            color = Ink2,
            modifier = Modifier.padding(top = 8.dp, bottom = 14.dp)
        )
        NameTextField(
            value = flow.love.raw,
            onValueChange = onType,
            placeholder = stringResource(R.string.namepack_love_placeholder),
            count = flow.love.count,
            error = flow.loveNote == NameNote.EMOJI || flow.loveNote == NameNote.NEED_NAME,
            imeAction = ImeAction.Done,
            onSubmit = onMake
        )
        NameNoteLine(flow.loveNote)
        RelationChips(flow.relation, onRelation)
        PreviewArea(
            image = state.preview,
            description = stringResource(R.string.namepack_preview_alt, state.previewText),
            modifier = Modifier.weight(1f)
        )
        Text(
            stringResource(R.string.namepack_preview_hint),
            style = HintStyle,
            color = FootnoteGrey,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
        )
        // Looks disabled until the name is valid, but an empty-name tap still explains why.
        PrimaryButton(
            label = stringResource(R.string.namepack_make),
            onClick = onMake,
            enabled = !flow.love.hasEmoji,
            dimmed = !flow.canMake
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFAFBFC, widthDp = 390, heightDp = 844)
@Composable
private fun NameYouStepPreview() {
    LoveStickersTheme {
        NameYouStep(NamePackUiState(flow = NamePackState(you = NameField("Aymen"))), {}, {}, {}, {}, {})
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFAFBFC, widthDp = 390, heightDp = 844)
@Composable
private fun NameLoveStepErrorPreview() {
    LoveStickersTheme {
        NameLoveStep(
            NamePackUiState(flow = NamePackState(step = NameStep.LOVE, needNameError = true)),
            {}, {}, {}, {}, {}
        )
    }
}
```

- [ ] **Step 5: Verify**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL. (Composables are exercised on the emulator in Task 15; the logic behind them is `NamePackStateTest`.)

---

### Task 13: Building, reveal and the screen host

Design: screens spec §e, §f, §g.

**Files:**
- Create: `K/feature/namepack/BuildingStep.kt`, `K/feature/namepack/RevealStep.kt`, `K/feature/namepack/NamePackScreen.kt`

**Interfaces:**
- Consumes: Tasks 11–12; `AddBar(state, onClick, modifier, progress, hint, idleLabel, sentLabel, addedLabel, failedLabel)`, `ConfirmSheet(...)`, `ToastHost`, `SnackbarHostState.showToast`, `AddStickerPackFlow.createBestIntent/parseResult`, `LanguageSheet`, `UiText.asString(context)`.
- Produces: `@Composable fun NamePackScreen(onFinished: () -> Unit, onLeave: () -> Unit, viewModel: NamePackViewModel = hiltViewModel())`

- [ ] **Step 1: Building**

`K/feature/namepack/BuildingStep.kt`:

```kotlin
package com.piptechnologies.stickermaker.feature.namepack

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.piptechnologies.stickermaker.R
import com.piptechnologies.stickermaker.core.design.Border
import com.piptechnologies.stickermaker.core.design.Canvas
import com.piptechnologies.stickermaker.core.design.Hanken
import com.piptechnologies.stickermaker.core.design.Ink2
import com.piptechnologies.stickermaker.core.design.Rose
import com.piptechnologies.stickermaker.feature.namepack.engine.Character

private val BuildingTitle = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.W800, fontSize = 22.sp, letterSpacing = (-0.02).em)
private val BuildingSub = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.W400, fontSize = 14.sp, lineHeight = 21.sp)

/**
 * The character waits (still + gentle bob until Claude Design's loops land) over a 200 × 4 bar
 * bound to the 12 real renders; nothing else moves. No top strip, chip or Skip. Gaps are the
 * design's net ones: art to bar 18, bar to title 20, title to sub 6. The art's white outline is
 * baked in by NamePackAssets.waitArt; the design's soft drop shadow is left out (Compose shadows
 * follow shapes, not alpha).
 */
@Composable
internal fun BuildingStep(state: NamePackUiState) {
    val reduceMotion = rememberReduceMotion()
    val progress by animateFloatAsState(
        targetValue = state.progress.coerceIn(0f, 1f),
        animationSpec = tween(if (reduceMotion) 0 else 120, easing = LinearEasing),
        label = "buildBar"
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Canvas)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = 24.dp, end = 24.dp, bottom = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        WaitingArt(state.waitArt, state.flow.character, reduceMotion)
        Spacer(Modifier.height(18.dp))
        Box(
            Modifier
                .width(200.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Border)
        ) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(progress).background(Rose))
        }
        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.namepack_building_title), style = BuildingTitle, color = TitleInk, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.namepack_building_sub), style = BuildingSub, color = Ink2, textAlign = TextAlign.Center)
    }
}

@Composable
private fun WaitingArt(image: ImageBitmap?, character: Character, reduceMotion: Boolean) {
    val bob by rememberInfiniteTransition(label = "wait").animateFloat(
        initialValue = 0f,
        targetValue = -6f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bob"
    )
    Box(Modifier.size(224.dp).graphicsLayer { translationY = if (reduceMotion) 0f else bob.dp.toPx() }) {
        if (image != null) {
            Image(image, contentDescription = stringResource(R.string.namepack_waiting_alt, character.label), modifier = Modifier.fillMaxSize())
        }
    }
}
```

- [ ] **Step 2: Reveal**

`K/feature/namepack/RevealStep.kt`:

```kotlin
package com.piptechnologies.stickermaker.feature.namepack

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piptechnologies.stickermaker.R
import com.piptechnologies.stickermaker.core.design.Border
import com.piptechnologies.stickermaker.core.design.Canvas
import com.piptechnologies.stickermaker.core.design.Hanken
import com.piptechnologies.stickermaker.core.design.Ink
import com.piptechnologies.stickermaker.core.design.Ink2
import com.piptechnologies.stickermaker.core.design.LoveShapes
import com.piptechnologies.stickermaker.core.design.Mono
import com.piptechnologies.stickermaker.core.design.Rose
import com.piptechnologies.stickermaker.core.design.RoseTint
import com.piptechnologies.stickermaker.core.design.Surface
import com.piptechnologies.stickermaker.core.design.components.AddBar
import com.piptechnologies.stickermaker.core.design.components.AddVisualState
import com.piptechnologies.stickermaker.feature.namepack.engine.Character
import com.piptechnologies.stickermaker.feature.namepack.engine.Relation
import com.piptechnologies.stickermaker.feature.namepack.engine.Tone
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val PackNameStyle = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.W700, fontSize = 16.sp)
private val MetaStyle = TextStyle(fontFamily = Mono, fontWeight = FontWeight.W400, fontSize = 12.sp)
private val ToneText = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.W600, fontSize = 12.5.sp)
private val NotNowText = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.W600, fontSize = 14.sp)

/** Pop delay per tile: round(hypot(col − 1, row − 1.5) × 95) ms (design). */
private val PopDelaysMs = longArrayOf(171, 143, 171, 106, 48, 106, 106, 48, 106, 171, 143, 171)
private val PopEasing = CubicBezierEasing(0.2f, 0.9f, 0.3f, 1.3f)
private const val LAST_POP_MS = 591L

/** The pack (screens spec §f): header, meta + tones, re-cast row, 3-column grid, pinned Add bar. */
@Composable
internal fun RevealStep(
    state: NamePackUiState,
    onTone: (Tone) -> Unit,
    onCharacter: (Character) -> Unit,
    onAdd: () -> Unit,
    onNotNow: () -> Unit
) {
    val reduceMotion = rememberReduceMotion()
    val flow = state.flow
    val sending = state.addState == AddVisualState.Sent
    Column(Modifier.fillMaxSize().background(Canvas)) {
        RevealHeader(state.tray, state.packYou, state.packLove, state.packName)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, top = 2.dp, end = 20.dp, bottom = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.namepack_meta, relationForMeta(flow.relation)),
                    style = MetaStyle,
                    color = Ink2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ToneChip(stringResource(R.string.namepack_tone_sweet), flow.effectiveTone == Tone.SWEET, !sending) { onTone(Tone.SWEET) }
                    // Family relations letter Sweet only, so Flirty is not offered (design).
                    if (!flow.relation.family) {
                        ToneChip(stringResource(R.string.namepack_tone_flirty), flow.effectiveTone == Tone.FLIRTY, !sending) { onTone(Tone.FLIRTY) }
                    }
                }
            }
            CharacterRow(
                selected = flow.character,
                onPick = { if (!sending) onCharacter(it) },
                tileArt = state.tileArt,
                artSize = 40.dp,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            StickerGrid(state.tiles, reduceMotion)
            Text(
                stringResource(R.string.namepack_building_sub),
                style = HintStyle,
                color = FootnoteGrey,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp)
            )
        }
        RevealFooter(state.addState, reduceMotion, onAdd, onNotNow)
    }
}

/**
 * 52 header: the 28 tray heart, then the pack name, drawn with the app-mark heart (never an
 * emoji) when it holds both names. [packName] (from the ViewModel) is what TalkBack reads.
 */
@Composable
private fun RevealHeader(tray: ImageBitmap?, you: String, love: String, packName: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(52.dp)
            .padding(horizontal = 20.dp)
            .semantics(mergeDescendants = true) { contentDescription = packName },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (tray != null) Image(tray, contentDescription = null, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(9.dp))
        if (you.isNotEmpty()) {
            Text(you, style = PackNameStyle, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Icon(NamePackIcons.AppHeart, contentDescription = null, tint = Rose, modifier = Modifier.padding(horizontal = 5.dp).size(13.dp))
            Text(love, style = PackNameStyle, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        } else {
            Text(packName, style = PackNameStyle, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** The relation for the meta line: lower-cased as the design shows it; German nouns keep their capital. */
@Composable
private fun relationForMeta(relation: Relation): String {
    val label = stringResource(relation.labelRes())
    val locale = LocalConfiguration.current.locales[0]
    return if (locale.language == "de") label else label.lowercase(locale)
}

/** 32 tall tone chip, pad 13, 12.5/600, relation-chip colours. */
@Composable
private fun ToneChip(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) RoseTint else Surface, label = "toneBg")
    val fg by animateColorAsState(if (selected) Rose else Ink2, label = "toneFg")
    val line by animateColorAsState(if (selected) Rose else Border, label = "toneLine")
    Box(
        modifier = Modifier
            .height(32.dp)
            .clip(LoveShapes.Pill)
            .background(bg)
            .border(1.dp, line, LoveShapes.Pill)
            .clickable(role = Role.Button, onClickLabel = label, enabled = enabled, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = ToneText, color = fg, maxLines = 1)
    }
}

/** Three columns 10 apart; square tiles r16 on #F6F7F9 with the sticker at 104/110. */
@Composable
private fun StickerGrid(tiles: List<RevealTile>, reduceMotion: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        tiles.chunked(3).forEachIndexed { row, items ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items.forEachIndexed { col, tile ->
                    key(tile.slot) { StickerCell(tile, row * 3 + col, reduceMotion, Modifier.weight(1f)) }
                }
                repeat(3 - items.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Pops in once (0.2 → 1, 420 ms overshoot, staggered from the centre); a re-letter fades in over the old tile (160 ms). */
@Composable
private fun StickerCell(tile: RevealTile, index: Int, reduceMotion: Boolean, modifier: Modifier) {
    val scale = remember { Animatable(if (reduceMotion) 1f else 0.2f) }
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (reduceMotion) {
            alpha.animateTo(1f, tween(300))
        } else {
            delay(PopDelaysMs.getOrElse(index) { 0L })
            // Opacity and scale share the pop's timing, as in the design's keyframes.
            launch { alpha.animateTo(1f, tween(420, easing = PopEasing)) }
            scale.animateTo(1f, tween(420, easing = PopEasing))
        }
    }
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                this.alpha = alpha.value.coerceIn(0f, 1f)
            }
            .clip(RoundedCornerShape(16.dp))
            .background(TileBg),
        contentAlignment = Alignment.Center
    ) {
        FadeOver(tile.image, tile.text, if (reduceMotion) 0 else 160, Modifier.fillMaxSize(104f / 110f))
    }
}

/**
 * Swaps [image] by fading the new one in over the old, which stays opaque underneath: art that did
 * not change (a tone switch) never dims, only the lettering visibly changes (design §f).
 */
@Composable
private fun FadeOver(image: ImageBitmap, description: String, durationMs: Int, modifier: Modifier) {
    var shown by remember { mutableStateOf(image) }
    var under by remember { mutableStateOf<ImageBitmap?>(null) }
    val alpha = remember { Animatable(1f) }
    LaunchedEffect(image) {
        if (image == shown) return@LaunchedEffect
        under = shown
        shown = image
        alpha.snapTo(0f)
        alpha.animateTo(1f, tween(durationMs))
        under = null
    }
    Box(modifier) {
        under?.let { Image(it, contentDescription = null, modifier = Modifier.fillMaxSize()) }
        Image(shown, contentDescription = description, modifier = Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha.value })
    }
}

/** Pinned white footer: the five-state Add bar (no hint line) and a ghost "Not now"; fades in after the last tile. */
@Composable
private fun RevealFooter(addState: AddVisualState, reduceMotion: Boolean, onAdd: () -> Unit, onNotNow: () -> Unit) {
    val alpha = remember { Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reduceMotion) {
            delay(LAST_POP_MS)
            alpha.animateTo(1f, tween(300))
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { this.alpha = alpha.value }
            .background(Surface)
            .navigationBarsPadding()
    ) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(FooterLine))
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 14.dp)) {
            AddBar(
                state = addState,
                onClick = onAdd,
                hint = null,
                failedLabel = stringResource(R.string.namepack_add_failed)
            )
            val notNow = stringResource(R.string.common_not_now)
            // While WhatsApp's own add sheet is on its way, leaving would lose its answer.
            val sending = addState == AddVisualState.Sent
            Box(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(role = Role.Button, onClickLabel = notNow, enabled = !sending, onClick = onNotNow),
                contentAlignment = Alignment.Center
            ) {
                Text(notNow, style = NotNowText, color = Ink2)
            }
        }
    }
}
```

- [ ] **Step 3: The screen host**

`K/feature/namepack/NamePackScreen.kt`:

```kotlin
package com.piptechnologies.stickermaker.feature.namepack

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.piptechnologies.stickermaker.R
import com.piptechnologies.stickermaker.core.design.Canvas
import com.piptechnologies.stickermaker.core.design.LoveIcons
import com.piptechnologies.stickermaker.core.design.components.AddVisualState
import com.piptechnologies.stickermaker.core.design.components.ConfirmSheet
import com.piptechnologies.stickermaker.core.design.components.ToastHost
import com.piptechnologies.stickermaker.core.design.components.showToast
import com.piptechnologies.stickermaker.core.ui.asString
import com.piptechnologies.stickermaker.feature.language.AppLanguages
import com.piptechnologies.stickermaker.feature.language.LanguageSheet
import com.piptechnologies.stickermaker.feature.language.effectiveTag
import com.piptechnologies.stickermaker.whatsapp.AddStickerPackFlow
import com.piptechnologies.stickermaker.whatsapp.WhitelistCheck
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * The Custom Stickers route: your name → their name → building → reveal, one ViewModel,
 * system back walking the steps. Ends with [onFinished] (Home) or [onLeave] (back to the intro).
 */
@Composable
fun NamePackScreen(
    onFinished: () -> Unit,
    onLeave: () -> Unit,
    viewModel: NamePackViewModel = hiltViewModel()
) {
    // Main.immediate: typed text comes back from the ViewModel in the same frame, so fast typing
    // and IME composition never see a stale value (no dropped letters, no cursor jumps).
    val state by viewModel.uiState.collectAsState(context = Dispatchers.Main.immediate)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val toaster = remember { SnackbarHostState() }
    // A language change recreates the activity, so this is read fresh then.
    val languageTag = remember { AppLanguages.effectiveTag() }
    var languagesOpen by rememberSaveable { mutableStateOf(false) }
    var noWhatsApp by rememberSaveable { mutableStateOf(false) }
    val latestFinished by rememberUpdatedState(onFinished)
    val latestLeave by rememberUpdatedState(onLeave)

    LaunchedEffect(languageTag) { viewModel.onLanguage(languageTag) }

    val keyboard = LocalSoftwareKeyboardController.current
    // Leaving the name steps: drop the keyboard now, not when the field leaves composition.
    LaunchedEffect(state.flow.step) {
        if (state.flow.step == NameStep.BUILDING || state.flow.step == NameStep.REVEAL) keyboard?.hide()
    }

    val addLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val parsed = AddStickerPackFlow.parseResult(result.resultCode, result.data)
        viewModel.onWhatsAppResult(
            added = parsed is AddStickerPackFlow.AddResult.Added,
            rejected = (parsed as? AddStickerPackFlow.AddResult.Cancelled)?.validationError != null
        )
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is NamePackEvent.LaunchAdd -> {
                    val intent = AddStickerPackFlow.createBestIntent(context, event.identifier, event.packName)
                    when {
                        // Every installed WhatsApp already has it; the new image version refreshes it.
                        intent == null && AddStickerPackFlow.isWhatsAppInstalled(context) -> viewModel.onWhatsAppResult(added = true)
                        // WhatsApp went away since Add was tapped: the install sheet, as Home shows it.
                        intent == null -> {
                            viewModel.onWhatsAppResult(added = false)
                            noWhatsApp = true
                        }
                        else -> try {
                            addLauncher.launch(intent)
                        } catch (e: ActivityNotFoundException) {
                            viewModel.onWhatsAppResult(added = false)
                            noWhatsApp = true
                        }
                    }
                }
                NamePackEvent.ShowNoWhatsApp -> noWhatsApp = true
                is NamePackEvent.Toast -> scope.launch { toaster.showToast(event.message.asString(context)) }
                NamePackEvent.Finished -> latestFinished()
                NamePackEvent.Leave -> latestLeave()
            }
        }
    }

    // While WhatsApp's own sheet is on its way, back would lose its answer: the handler stays on
    // (so the route is not popped) but does nothing.
    BackHandler { if (state.addState != AddVisualState.Sent) viewModel.onBack() }

    Box(Modifier.fillMaxSize().background(Canvas)) {
        // Keyed by the step alone: state updates (typing, previews, progress ticks) are not new
        // targets, so a fade is never cut short and no old state is kept. The reveal cuts in over
        // Building and fades in (§e, §f).
        AnimatedContent(
            targetState = state.flow.step,
            transitionSpec = {
                if (targetState == NameStep.REVEAL) fadeIn(tween(300)) togetherWith fadeOut(snap())
                else fadeIn(tween(300)) togetherWith fadeOut(tween(300))
            },
            label = "namePackStep"
        ) { step ->
            // A leaving step keeps the state it last showed; the current one follows the live state.
            val shown = rememberHeld(state, live = step == state.flow.step)
            when (step) {
                NameStep.YOU -> NameYouStep(
                    state = shown,
                    onLanguage = { languagesOpen = true },
                    onSkip = viewModel::onSkipYou,
                    onType = viewModel::onYouChange,
                    onCharacter = viewModel::onCharacter,
                    onContinue = viewModel::onContinue
                )
                NameStep.LOVE -> NameLoveStep(
                    state = shown,
                    onLanguage = { languagesOpen = true },
                    onSkip = viewModel::onSkipLove,
                    onType = viewModel::onLoveChange,
                    onRelation = viewModel::onRelation,
                    onMake = viewModel::onMake
                )
                NameStep.BUILDING -> BuildingStep(shown)
                NameStep.REVEAL -> RevealStep(
                    state = shown,
                    onTone = viewModel::onTone,
                    onCharacter = viewModel::onCharacter,
                    onAdd = viewModel::onAdd,
                    onNotNow = viewModel::onNotNow
                )
            }
        }
        // Above the step's bottom controls: the reveal's footer, or the name steps' button.
        val toastLift = if (state.flow.step == NameStep.REVEAL) 140.dp else 88.dp
        ToastHost(toaster, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = toastLift))
    }

    if (languagesOpen) LanguageSheet(onDismiss = { languagesOpen = false })

    if (noWhatsApp) {
        val opening = stringResource(R.string.toast_opening_play_store)
        ConfirmSheet(
            title = stringResource(R.string.no_whatsapp_title),
            body = stringResource(R.string.no_whatsapp_body),
            confirmLabel = stringResource(R.string.no_whatsapp_confirm),
            cancelLabel = stringResource(R.string.common_not_now),
            destructive = false,
            icon = LoveIcons.MessageCircle,
            onConfirm = {
                noWhatsApp = false
                scope.launch { toaster.showToast(opening) }
                openWhatsAppStorePage(context)
            },
            onDismiss = { noWhatsApp = false }
        )
    }
}

/** [value] while [live]; once not, the last live value (a step fading out keeps what it showed). */
@Composable
private fun <T> rememberHeld(value: T, live: Boolean): T {
    val held = remember { Held(value) }
    if (live) held.value = value
    return held.value
}

/** A plain holder: writing it during composition must not trigger recomposition. */
private class Held<T>(var value: T)

/** "Get WhatsApp" opens its Play Store page (market://, then the web fallback). */
private fun openWhatsAppStorePage(context: Context) {
    val packageName = WhitelistCheck.CONSUMER_WHATSAPP_PACKAGE_NAME
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")))
    } catch (notFound: ActivityNotFoundException) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")))
        } catch (ignored: ActivityNotFoundException) {
            // No browser either; the toast already said what we tried.
        }
    }
}
```

- [ ] **Step 4: Verify**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, tests pass. If `toast_opening_play_store` or `WhitelistCheck` resolve differently, check `R.string.toast_opening_play_store` exists (Home uses it) and `WhitelistCheck.CONSUMER_WHATSAPP_PACKAGE_NAME` (`K/whatsapp/WhitelistCheck.kt:18`).

---

### Task 14: First run goes through the flow; Home shows its toast

**Files:**
- Modify: `K/feature/onboarding/OnboardingViewModel.kt`, `K/feature/onboarding/OnboardingScreen.kt`, `K/navigation/AppNavHost.kt`, `K/feature/home/HomeViewModel.kt`, `K/feature/home/HomeScreen.kt`
- Test: `app/src/test/kotlin/com/piptechnologies/stickermaker/feature/onboarding/OnboardingViewModelTest.kt`

**Interfaces:**
- Consumes: `NamePackScreen(onFinished, onLeave)` (Task 13), `PendingToasts.take()` (Task 11), `SnackbarHostState.showToast(message, withCheck)`, `UiText.asString(context)`.
- Produces:
  - `Routes.NAME_PACK = "namePack"`
  - `OnboardingViewModel @Inject constructor()` with `uiState: StateFlow<OnboardingUiState>` (`page` only) and `done: Flow<Unit>`; it no longer writes the onboarded flag
  - `HomeViewModel.takePendingToast(): UiText?`

The intro now stays under the flow (Back on "Your name" returns to it), so its hand-over must be an event: a `finished` state would re-fire `onDone` the moment the intro came back and bounce the user straight into the flow again.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.piptechnologies.stickermaker.feature.onboarding

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OnboardingViewModelTest {

    @Test
    fun nextTurnsThePageThenHandsOver() = runBlocking {
        val vm = OnboardingViewModel()
        vm.onNext()
        assertEquals(1, vm.uiState.value.page)
        vm.onNext()
        withTimeout(1_000) { vm.done.first() }
    }

    @Test
    fun skipHandsOverFromTheFirstSlide() = runBlocking {
        val vm = OnboardingViewModel()
        vm.onSkip()
        withTimeout(1_000) { vm.done.first() }
        assertEquals(0, vm.uiState.value.page)
    }

    /** Back from "Your name" shows the intro again; it must stay there, not hand over twice. */
    @Test
    fun aHandOverIsDeliveredOnce() = runBlocking {
        val vm = OnboardingViewModel()
        vm.onSkip()
        withTimeout(1_000) { vm.done.first() }
        assertNull(withTimeoutOrNull(200) { vm.done.first() })
    }
}
```

- [ ] **Step 2: Run it to see it fail**

Run: `./gradlew :app:testDebugUnitTest --tests '*OnboardingViewModelTest*'`
Expected: compilation FAILS (`OnboardingViewModel()` needs a `PrefsRepository`; `done` does not exist).

- [ ] **Step 3: Rewrite the ViewModel**

`K/feature/onboarding/OnboardingViewModel.kt`, whole file:

```kotlin
package com.piptechnologies.stickermaker.feature.onboarding

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update

/**
 * State for the two-slide onboarding.
 *
 * @property page 0 or 1 (the design ships exactly two slides).
 */
data class OnboardingUiState(val page: Int = 0)

/**
 * Drives the pager (Prototype ob.next / ob.skip): Skip on either slide and "Get started" hand
 * over to the Custom Stickers flow through [done]. The intro stays under that flow, so Back
 * returns here; the flow writes the onboarded flag when it ends.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor() : ViewModel() {

    private val state = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = state.asStateFlow()

    private val handOver = Channel<Unit>(Channel.CONFLATED)

    /** One event per hand-over, not state, so coming back to the intro does not fire it again. */
    val done: Flow<Unit> = handOver.receiveAsFlow()

    /** The primary button: "Next" on the first slide, "Get started" on the last. */
    fun onNext() {
        if (state.value.page == 0) state.update { it.copy(page = 1) } else handOver.trySend(Unit)
    }

    /** The ghost Skip, on both slides. */
    fun onSkip() {
        handOver.trySend(Unit)
    }
}
```

- [ ] **Step 4: The intro listens for the hand-over**

`OnboardingScreen` still reads `state.finished`, so main does not compile until this step. In `K/feature/onboarding/OnboardingScreen.kt`, replace

```kotlin
    LaunchedEffect(state.finished) {
        if (state.finished) latestOnDone()
    }
```

with

```kotlin
    LaunchedEffect(Unit) { viewModel.done.collect { latestOnDone() } }
```

and in the `OnboardingScreen` KDoc replace the sentence about persisting the onboarded flag (Task 10 kept it as "Both paths persist the onboarded flag before [onDone] fires.") with "Both paths hand over to the name flow through [onDone]; that flow writes the onboarded flag."

- [ ] **Step 5: Run the test**

Run: `./gradlew :app:testDebugUnitTest --tests '*OnboardingViewModelTest*'`
Expected: 3 tests PASS.

- [ ] **Step 6: Navigation**

In `K/navigation/AppNavHost.kt`:

1. `Routes` gains, after `ONBOARDING`:

```kotlin
    const val NAME_PACK = "namePack"
```

2. Import `com.piptechnologies.stickermaker.feature.namepack.NamePackScreen`.
3. The ONBOARDING destination (Task 1 pointed it at Home) becomes, followed by the new one:

```kotlin
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                // The intro stays underneath: Back on "Your name" returns to it.
                onDone = { navController.navigate(Routes.NAME_PACK) { launchSingleTop = true } }
            )
        }

        composable(Routes.NAME_PACK) {
            NamePackScreen(
                // First run is over: Home becomes the only screen on the stack.
                onFinished = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                },
                onLeave = { navController.popBackStack() }
            )
        }
```

4. The `AppNavHost` KDoc: `splash decides between onboarding and home; the intro hands over to the Custom Stickers flow, which ends on Home; a finished create flow collapses into My Packs.`

`namePack` is not in `RATING_PAUSES`: the prompt armed by a first-run add rises on Home.

- [ ] **Step 7: Home shows the toast the flow left**

`K/feature/home/HomeViewModel.kt`: add `private val pendingToasts: PendingToasts` as the last constructor parameter (import `com.piptechnologies.stickermaker.core.ui.PendingToasts`) and, next to the other public functions:

```kotlin
    /** A toast another screen left for Home (the name flow's "Added to WhatsApp"), handed out once. */
    fun takePendingToast(): UiText? = pendingToasts.take()
```

`K/feature/home/HomeScreen.kt`, right after the `LaunchedEffect` that collects `viewModel.toasts`:

```kotlin
    // The name flow's "Added to WhatsApp" arrives with Home (each screen owns its toast host).
    LaunchedEffect(Unit) {
        viewModel.takePendingToast()?.let { snackbarHostState.showToast(it.asString(context), withCheck = true) }
    }
```

(A separate effect on purpose: `showToast` suspends while the toast is up, and `toasts` is a flow without replay, so the collector above must not wait behind it.)

While in `HomeScreen.kt`: its KDoc (around line 105) still describes the chips as "Animated · your themes"; since Task 1 there is no theme choice, so make it "Animated · every theme".

- [ ] **Step 8: Verify**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest`
Expected: BUILD SUCCESSFUL; all unit tests pass. The tour's `t01` still expects "Get started" to land on Home; Task 15 walks it through the flow. The tour runs on CI only, never locally (it replaces WhatsApp on the device).

---

### Task 15: Tour, CI map, README and the final check

**Files:**
- Modify: `app/src/androidTest/kotlin/com/piptechnologies/stickermaker/tour/Frame.kt`, `…/tour/ScreenTourTest.kt`, `scripts/ci/screens_map.py`, `README.md`

**Interfaces:**
- Consumes: the flow's English copy (Task 9), click labels from Tasks 12–13 ("Continue", "Make our stickers", "Skip", "Not now"), the preview's content description `namepack_preview_alt` ("Sticker preview: %1$s").
- Produces: frames 4 and 5 are the name steps (`NAME_YOU`, `NAME_LOVE`); extra `x06` is the reveal.

- [ ] **Step 1: Frames 4 and 5 become the name steps**

In `…/tour/Frame.kt` replace the two customization entries with:

```kotlin
    NAME_YOU(4, "custom-your-name", "02 Custom stickers", "What's your name? · optional", "namePack"),
    NAME_LOVE(5, "custom-their-name", "02 Custom stickers", "Who's your love?", "namePack"),
```

and make the KDoc read:

```kotlin
/**
 * The tour's 42 frames, numbered as design/Screens.dc.html ("Every screen, every state") first
 * shipped them. Frames 4 and 5 were the theme picker; they are now the Custom Stickers name
 * steps from the page's "01 → 02 · First run" section. The tour captures the real app in each
 * state and records which frame every screenshot maps to.
 */
```

- [ ] **Step 2: First run walks the flow**

In `…/tour/ScreenTourTest.kt`:

1. Import `androidx.compose.ui.test.hasContentDescription`.
2. Class KDoc: "…like a user would: first run, adds, own packs, settings." (drop "themes").
3. Section comment above `t01_firstRun`: `// 1. First run, offline: launch, onboarding, the name flow, offline Home.`
4. In `step("Onboarding")` the notes become `"Slide 1: language chip and Skip on top; Next advances."` and `"Slide 2: Skip stays, CTA reads Get started."`; in `step("Retry")` the `x01` title becomes `"Home after Retry"` (there are no first-run themes any more).
5. Between `step("Onboarding")` and `step("Offline Home")` insert:

```kotlin
        step("Your name") {
            compose.waitFor(hasText("What's your name?"))
            compose.onNode(hasSetTextAction()).performTextInput("Aymen")
            // Keyboard down first: with it up the preview may shrink below 72 dp and hide.
            Espresso.closeSoftKeyboard()
            compose.waitFor(hasContentDescription("Sticker preview: Aymen"), 30_000)
            shot(Frame.NAME_YOU, "Mango picked, your name typed: the name_only sticker is lettered on the phone, offline.")
            compose.tap(hasClickLabel("Continue"))
        }
        step("Their name") {
            compose.waitFor(hasText("Who's your love?"))
            // The steps crossfade; wait for the first field to leave before typing.
            compose.waitGone(hasText("What's your name?"))
            compose.onNode(hasSetTextAction()).performTextInput("Sara")
            Espresso.closeSoftKeyboard()
            compose.waitFor(hasContentDescription("Sticker preview: Love you, Sara"), 30_000)
            shot(Frame.NAME_LOVE, "Their name with Girlfriend: the love_you preview letters \"Love you, Sara\".")
            compose.tap(hasClickLabel("Make our stickers"))
        }
        step("Reveal") {
            compose.waitFor(hasClickLabel("Not now"), 60_000)
            compose.waitFor(hasText("12 stickers · girlfriend"))
            extra("x06", "Custom stickers · reveal", "namePack",
                "Aymen ❤ Sara: 12 stickers lettered on the phone (Mango, Sweet), offline; nothing uploaded.")
        }
        // Back, then Skip, leaves first run without saving a pack, so My Packs keeps its two own packs.
        step("Leave without a pack") {
            back()
            compose.waitFor(hasText("Who's your love?"))
            compose.tap(hasClickLabel("Skip"))
        }
```

The existing "Offline Home" step follows unchanged (Skip ends first run on Home, offline).

- [ ] **Step 3: The CI screen map**

In `scripts/ci/screens_map.py` `FLOW`:
- replace the two lines `onboarding --> customize[...]` and `customize --> home[...]` with

```
  onboarding --> namepack["Custom stickers · 04 05 · reveal x06"]
  namepack --> home["Home · 06 07 08 17 29 · offline 39"]
```

- delete `settings --> edit["Edit themes · 05"]`;
- add, next to the other WhatsApp edges: `namepack -.->|ENABLE_STICKER_PACK| whatsapp`.

Check it renders:

```bash
mkdir -p "$SCRATCH/map-check" && python3 scripts/ci/screens_map.py --root "$SCRATCH/map-check" && grep -c "namepack" "$SCRATCH/map-check/README.md"
```

Expected: `README.md: 0/42 frames, …` and a count of 3 (the three flow lines that name `namepack`).

- [ ] **Step 4: README**

In `README.md`:

1. Analytics bullet: the event list gains `name_pack_built`, and `onboarding_complete` is described: "`onboarding_complete` (how first run ended: `added`, `saved` or `skipped`)". Keep "Only coarse values go out"; add "names typed in the Custom Stickers flow never do".
2. Screen tour, point 3: "(first run offline through the Custom Stickers name steps, add states including a real failed download, …)".
3. "Trying it on a phone", point 2: "Launch → onboarding → your name → their name → 12 lettered stickers → Add to WhatsApp (or Not now) → Home shows the catalog".
4. A new section before `## Languages`:

```markdown
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
- `NamePackSheetsTest` writes a contact sheet per language, character and tone to
  `app/build/namepack-sheets/` for checking the lettering by eye. It is opt-in:
  `NAMEPACK_SHEETS=1 ./gradlew :app:testDebugUnitTest --tests '*NamePackSheetsTest*' --rerun`.
```

- [ ] **Step 5: Full verification**

```bash
export JAVA_HOME=~/Library/Java/JavaVirtualMachines/corretto-17.0.18/Contents/Home ANDROID_HOME=~/Library/Android/sdk
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest
grep -rni "customiz\|selectedThemes\|edit themes\|theme picker" app/src scripts README.md --include='*.kt' --include='*.xml' --include='*.py' --include='*.md' | grep -v "tour/Frame.kt"
```

(The globs are quoted because the shell is zsh. `Frame.kt`'s KDoc mentions the old picker on purpose.)

Expected: BUILD SUCCESSFUL, every unit test passes (43 before this plan plus the new ones), lint reports 0 errors, and the grep prints nothing. Then run `NAMEPACK_SHEETS=1 ./gradlew :app:testDebugUnitTest --tests '*NamePackSheetsTest*' --rerun` and open three contact sheets from `app/build/namepack-sheets/` (English, Arabic, Hindi) and look at them.

- [ ] **Step 6: On the emulator**

The emulator is `Pixel_9_Pro_XL` with the real WhatsApp. Never run the tour on it. The Mac has 16 GB of RAM, so free memory first.

```bash
./gradlew --stop
adb devices   # start it if missing: "$ANDROID_HOME/emulator/emulator" -avd Pixel_9_Pro_XL -no-snapshot-load &
./gradlew :app:installDebug -PfirebaseEmulatorHost=10.0.2.2
```

1. The new build installs **over** the old one. Open My Packs: the own packs made before the update still show, which proves the Room 1 → 2 migration on a real device.
2. Start first run again:

```bash
adb shell pm clear com.piptechnologies.stickermaker
adb shell monkey -p com.piptechnologies.stickermaker -c android.intent.category.LAUNCHER 1
```

`pm clear` wipes this debug app's data on the emulator.

3. Walk the flow. After each step, capture a screenshot with `adb exec-out screencap -p > "$SCRATCH/np-<n>.png"` and look at it. Use `adb exec-out screencap`, not `uiautomator dump`: dumps hang on Home's animated thumbnails.
   - **Intro:** the language chip reads "English" at the start of the top strip, and Skip shows on both slides. Open the sheet and pick العربية: the layout mirrors and the slide stays the same. Switch back to English.
   - **Your name** (after "Get started"): Mango is selected. `adb shell input text Aymen` re-letters the preview. Pick Capy: the art swaps.
   - **Their name** (after "Continue"): tap "Make our stickers" with an empty field; you get the amber border and "Add a name to make the pack." Then `adb shell input text Sara`; the preview reads "Love you, Sara". Pick Mom and make the stickers: Building shows Capy waiting.
   - **Reveal:** 12 tiles pop in and only Sweet shows. Press Back to return to "Their name", pick Girlfriend and make them again: Sweet and Flirty both show. Tap Flirty: the tiles re-letter without popping again. Tap Bunny: the pack re-casts.
   - **Add to WhatsApp:** WhatsApp's own sheet appears. Confirm it; the emulator's WhatsApp is real, and the pack stays on that emulator. You land on Home with the dark "Added to WhatsApp" toast. My Packs › Made by you lists "Aymen ❤ Sara" with 12 stickers.
   - **Relaunch:** the app opens on Home, because the onboarded flag was written when the flow ended.
4. `adb logcat -d | grep -E "FATAL|AndroidRuntime"` prints nothing from the app.

---

## After this plan (not tasks here)

- The `screenshots` branch keeps the old theme-picker design images for frames 4 and 5 until the design references are re-rendered from the new `design/Screens.dc.html`.
- Claude Design is re-exporting the four waiting loops (`live/{character}-wait.webp`, ≤ 240 KB each). When they arrive, bundle them and let `NamePackAssets.waitArt` prefer them over the still.
- Create › "Name stickers" (making another name pack later) needs a design first.
- The 17 languages beyond English and Arabic, and the phrases beyond en/ar/fr/hi, were written for this plan and need a native read before release. Pashto, Hausa and Burmese are the least sure.
