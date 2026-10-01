# Create › Cut out: Add, Draw and Animate Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend the Create › Cut out editor per Claude Design's "Decorate and Animate" page:
- a new tool bar, with Zoom moved onto the canvas card;
- layers (text, emoji, stickers, drawings) that can be selected, moved, resized, rotated, flipped, duplicated, sent behind and deleted;
- an Add sheet with Text, Emoji and Stickers tabs;
- Draw, a marker;
- Animate, with nine whole-sticker presets;
- outline thickness and colour;
- one undo stack per sticker;
- animated export;
- Settings › Licences.

**Architecture:** A new package `feature/create/decor/` holds the parts that don't need Android:
- data catalogs parsed from `app/src/main/assets`;
- motion math;
- the layer model with its editor and undo history.

The Android-side renderer (fonts, text painter, asset bitmaps, a layer cache and a scene renderer) is shared by the live canvas and the exporter, so what you see is what exports. `CreatePackViewModel` owns one `DecorEditor` per sticker and exposes the editor state to a split editor UI: the tool bar, the canvas with its overlay, the rows under the bar, and the Add sheet.

**Tech Stack:**
- Kotlin, Jetpack Compose (Material 3), Hilt, Coil 2.7.
- `android.graphics` (Canvas, StaticLayout, Bitmap).
- org.json, DataStore (prefs), `HttpURLConnection`.
- JUnit 4 and Robolectric 4.14.1 with `@GraphicsMode(NATIVE)`.

**Spec:** `docs/specs/2026-09-30-create-decorate-animate-design.md` (read it first; section numbers below refer to it). The design files are in `$SCRATCH/design-sync/`: `Prototype.dc.html`, `Decorate and Animate.dc.html` and `Screens.dc.html`.

## Global Constraints

**Build**
- minSdk 24, compileSdk/targetSdk 35. No new dependencies: `libs.versions.toml` and `app/build.gradle.kts` stay unchanged.
- Build from the worktree with `export JAVA_HOME=~/Library/Java/JavaVirtualMachines/corretto-17.0.18/Contents/Home ANDROID_HOME=~/Library/Android/sdk` and `./gradlew --offline …`. The worktree has no `local.properties`; that is expected.
- Never touch the main checkout (`…/Sticker-Maker/` outside `.claude/worktrees/`).

**Git**
- Commit on this branch only. Never push.
- End every commit message with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Never name the crash-reporting SDK in code, comments, docs or commit messages.
- Never use a personal email address anywhere.

**Product rules**
- On-device only: no AI and no server rendering. The only network call is the skin-tone download from `raw.githubusercontent.com/microsoft/fluentui-emoji` (spec §2).
- WhatsApp limits (spec §7):
  - 512 × 512 WebP; static ≤ 100 KB; animated ≤ 500 KB and ≤ 10 s, every frame ≥ 8 ms.
  - Frame 0 is the rest pose.
  - A pack is all static or all animated; a still sticker in an animated pack is **2 identical frames**.
  - 1–3 emoji tags per sticker; tray 96 × 96 PNG ≤ 50 KB.

**Geometry**
- All geometry is in 512-px canvas space (`CreateSpec.CANVAS_SIZE`). Export fits 512 into 460 (`CreateSpec.EXPORT_CONTENT`).

**UI rules**
- Every user-facing string is a resource in all 19 locales (`values` plus ar, de, es, fa, fr, ha, hi, in, it, iw, my, ps, pt, pt-rBR, ru, tr, ur, zh).
- Layout uses logical start/end, so RTL mirrors.
- Touch targets are at least 44 dp.
- Reduced motion (`Settings.Global.ANIMATOR_DURATION_SCALE == 0`) is respected everywhere (spec §6, §8).

**Code style**
- Match the file you're in: KDoc on public types, `// ----` section banners in long screens, no wildcard imports.
- Colours come from `core/design/Color.kt` tokens where one exists.

## Review Focus

1. **Long or mixed-script text** (30 characters, a CJK string with no spaces, "love you سارة"). The layer must keep its box inside the canvas and letter in the right font. The test is in Task 5 (`TextLayerPainterTest.longAndMixedScripts`).
2. **Leaving Draw halfway through a stroke** (switching tool, rail sticker or Next while a finger is down). The strokes must become one layer and no live stroke may be left behind. The test is in Task 3 (`DecorEditorTest.flushWhileStrokeInProgress`).
3. **Auto re-run with layers present.** Layers, outline and preset must survive, the mask strokes leave the undo stack, and undo then steps through the decor changes. The test is in Task 3 (`DecorEditorTest.clearMaskHistoryKeepsDecorSteps`).
4. **Skin tones while offline.** The download fails: the popover shows the failed cells disabled, a toast explains, and the default tone still adds. The test is in Task 6 (`EmojiTonesTest.failureReturnsNullAndCachesNothing`).
5. **Busy photo with a 16-frame preset.** The export must still land at ≤ 500 KB by lowering quality, then halving frames. The test is in Task 7 (`StickerExporterTest.noisyPresetStaysUnderLimit`).

---

## File Map

`K` = `app/src/main/kotlin/com/piptechnologies/stickermaker`, `T` = `app/src/test/kotlin/com/piptechnologies/stickermaker`.

| File | Responsibility | Task |
|---|---|---|
| `K/feature/create/decor/DecorData.kt` | Parse `decor.json`, `subset.json`, `text-styles.json`, `quick-phrases.json`, `presets.json` | 1 |
| `K/feature/create/decor/Motion.kt` | Easing, keyframe sampling, frame transform (`Affine`), frame timing, particles | 2 |
| `K/feature/create/decor/DecorModel.kt` | Layer model, outline style, geometry types, `DecorSpec` constants | 3 |
| `K/feature/create/decor/EditHistory.kt` | Undo steps | 3 |
| `K/feature/create/decor/DecorEditor.kt` | All layer edits, gestures, text sessions, draw strokes, undo, hit testing, emoji tags | 3 |
| `app/src/main/res/values*/strings.xml`, `assets/text/quick-phrases.json` | New strings × 19 locales, phrases × 19 | 4 |
| `K/feature/create/decor/DecorFonts.kt` | Font per (style, mood, script) | 5 |
| `K/feature/create/decor/TextLayerPainter.kt` | Draw text layers in the four styles | 5 |
| `K/feature/create/decor/DecorAssets.kt` | Emoji/decor bitmaps (assets, tone cache) | 5 |
| `K/feature/create/decor/LayerRenderCache.kt` | Per-layer bitmap and silhouette at the current size; base sizes | 5 |
| `K/feature/create/decor/SceneRenderer.kt` | Draw a sticker (outline union, z-order), still/animated frames, particles | 5 |
| `K/feature/create/decor/EmojiTones.kt` | Download and cache skin-tone PNGs | 6 |
| `K/core/data/prefs/PrefsRepository.kt` | Emoji recents | 6 |
| `K/feature/create/CreateSession.kt`, `CreatePackViewModel.kt`, `StickerRenderer.kt` | Editor state and actions, scene for the canvas | 6 |
| `K/feature/create/decor/StickerExporter.kt` | Static, preset, still-in-animated, clip and tray bytes | 7 |
| `K/core/ui/ReduceMotion.kt` | `rememberReduceMotion()` moved from namepack | 8 |
| `K/feature/create/editor/CreateEditorScreen.kt` | Screen layout and wiring | 8, 9, 10 |
| `K/feature/create/editor/EditorToolbar.kt` | Six tools with the label fallback | 8 |
| `K/feature/create/editor/EditorCanvas.kt` | Canvas card, zoom button, drawing, animation clock, gestures | 8 |
| `K/feature/create/editor/LayerOverlay.kt` | Selection box, handles, guides, action pill | 8 |
| `K/feature/create/editor/EditorRows.kt` | Draw row, Animate strip, Outline row, rail tile | 9 |
| `K/feature/create/editor/AddSheet.kt` | Sheet, Text/Emoji/Stickers tabs, skin popover | 10 |
| `K/feature/create/details/CreatePackDetailsScreen.kt` | Animated info line | 11 |
| `K/feature/settings/LicencesScreen.kt`, `SettingsScreen.kt`, `K/navigation/AppNavHost.kt` | Licences | 11 |
| `app/src/androidTest/.../tour/ScreenTourTest.kt` | Tour follows Add instead of Text | 12 |

Assets are already committed (`7460c3f`):
- `assets/decor/*.webp`, `decor.json`;
- `assets/emoji/*.webp`, `subset.json` (files rewritten to `.webp`);
- `assets/motion/presets.json`;
- `assets/text/text-styles.json` and `quick-phrases.json` (en, ar, fr, hi);
- `assets/fonts/lettering_caveat.ttf`;
- `res/font/lettering_{mirza,kalam,amaticsc,lilitaone,ruslandisplay,lalezar,yatraone,karantina}.ttf`;
- `assets/licenses/*`;
- the ten new `LoveIcons`: SmilePlus, PenLine, CirclePlay, ZoomOut, Copy, FlipHorizontal, SendToBack, BringToFront, Scaling, Scale.

---

### Task 1: Decor data catalogs

**Files:**
- Create: `K/feature/create/decor/DecorData.kt`
- Test: `T/feature/create/decor/DecorDataTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - Enums: `Script`, `TextStyleId`, `FontMood`, `OutlineThickness`, `MarkerSize`, `SkinTone`, `Easing`, `ParticleKind`.
  - Data classes: `DecorPiece`, `EmojiItem`, `ShadowSpec`, `BubbleSpec`, `TextStyleSpec`, `NamedColour`, `FontRef`, `Keyframe`, `ParticleSpec`, `MotionPreset`.
  - Classes: `DecorCatalog`, `EmojiCatalog`, `TextStyleBook`, `QuickPhrases`, `MotionBook`, `DecorData` (with `load(AssetManager)`).

- [ ] **Step 1: Write the failing test**

org.json only works under Robolectric in this project: plain JUnit gets Android's stubs, which return defaults. Use the house runner:

```kotlin
package com.piptechnologies.stickermaker.feature.create.decor

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class DecorDataTest {

    private fun asset(path: String) = File("src/main/assets/$path").readText()

    @Test
    fun decorCatalogHas54PiecesAndEveryFileShips() {
        val catalog = DecorCatalog.parse(asset("decor/decor.json"))
        assertEquals(54, catalog.pieces.size)
        assertEquals(30, catalog.set(DecorCatalog.DOODLES).size)
        assertEquals(24, catalog.set(DecorCatalog.PROPS).size)
        catalog.pieces.forEach { piece ->
            assertTrue(piece.file, File("src/main/assets/decor/${piece.file}").isFile)
            assertTrue(piece.file, piece.defaultWidth > 0f && piece.defaultWidth <= 1f)
            assertTrue(piece.file, piece.emojis.isNotEmpty())
        }
        val crown = catalog.byFile("doodles-1.webp")!!
        assertEquals("Crown", crown.label)
        assertTrue(crown.headTop)
        assertEquals(0.34f, crown.defaultWidth, 1e-6f)
    }

    @Test
    fun emojiSubsetHasSevenTabsAnd327BundledFiles() {
        val catalog = EmojiCatalog.parse(asset("emoji/subset.json"))
        assertEquals(listOf("love", "smileys", "hearts", "hands", "animals", "food", "symbols"), catalog.tabs.keys.toList())
        assertEquals(listOf(22, 84, 24, 48, 54, 46, 80), catalog.tabs.values.map { it.size })
        assertEquals(327, catalog.files.size)
        catalog.files.forEach { assertTrue(it, File("src/main/assets/emoji/$it").isFile) }
        val toned = catalog.tabs.values.flatten().filter { it.skinTones }.distinctBy { it.file }
        assertEquals(42, toned.size)
        assertTrue(toned.all { item -> catalog.tab("hands").any { it.file == item.file } })
    }

    @Test
    fun toneUrlFollowsTheFluentLayout() {
        val wave = EmojiItem("Waving hand", "1F44B", "👋", "waving_hand.webp", skinTones = true)
        assertEquals(
            "https://raw.githubusercontent.com/microsoft/fluentui-emoji/main/assets/Waving%20hand/Medium-Light/3D/waving_hand_3d_medium-light.png",
            EmojiCatalog.toneUrl(wave, SkinTone.MediumLight)
        )
    }

    @Test
    fun textStylesColoursFontsOutlineAndMarker() {
        val book = TextStyleBook.parse(asset("text/text-styles.json"))
        assertEquals(TextStyleId.entries.toSet(), book.styles.keys)
        val sticker = book.styles.getValue(TextStyleId.Sticker)
        assertNull(sticker.fill)                              // the chosen colour
        assertEquals(0.16f, sticker.strokeEm, 1e-6f)
        assertEquals(0xFFFFFFFF.toInt(), sticker.strokeColour)
        val stroke = book.styles.getValue(TextStyleId.Stroke)
        assertEquals(0xFFFFFFFF.toInt(), stroke.fill)
        assertNull(stroke.strokeColour)                       // the chosen colour
        assertTrue(book.styles.getValue(TextStyleId.Classic).uiFont)
        val bubble = book.styles.getValue(TextStyleId.Bubble).bubble!!
        assertEquals(28f, bubble.radius, 0f)
        assertEquals(26f, bubble.tailW, 0f)
        assertEquals(8, book.colours.size)
        assertEquals(0xFFC23359.toInt(), book.colours.first { it.id == "rose" }.argb)
        assertEquals(FontRef("Mirza", 700), book.moods.getValue(FontMood.Hand).getValue(Script.Arabic))
        assertEquals(FontRef("Baloo Bhaijaan 2", 800), book.moods.getValue(FontMood.Round).getValue(Script.Arabic))
        assertEquals(mapOf(OutlineThickness.Thin to 5f, OutlineThickness.Medium to 8f, OutlineThickness.Thick to 12f), book.outlinePx)
        assertEquals(5, book.outlineColours.size)
        assertEquals(mapOf(MarkerSize.S to 8f, MarkerSize.M to 14f, MarkerSize.L to 22f), book.markerPx)
        assertEquals(3f, book.markerEdgeExtra, 0f)
    }

    @Test
    fun parseColourHandlesHexRgbaAndPlaceholders() {
        assertEquals(0xFFC23359.toInt(), TextStyleBook.parseColour("#C23359"))
        assertEquals(0xFFFFFFFF.toInt(), TextStyleBook.parseColour("#fff"))
        assertEquals(0xFFF5C542.toInt(), TextStyleBook.parseColour("#F5C542 with #fff core"))
        assertEquals(0x59000000, TextStyleBook.parseColour("rgba(0,0,0,.35)"))
        assertNull(TextStyleBook.parseColour("{colour}"))
    }

    @Test
    fun quickPhrasesResolveLanguageThenEnglish() {
        val phrases = QuickPhrases.parse("""{"_":"x","en":["a"],"pt":["p"],"pt-BR":["b"],"in":["i"],"iw":["h"]}""")
        assertEquals(listOf("a"), phrases.forLocale("de", null))
        assertEquals(listOf("p"), phrases.forLocale("pt", "PT"))
        assertEquals(listOf("b"), phrases.forLocale("pt", "BR"))
        assertEquals(listOf("i"), phrases.forLocale("id", null))
        assertEquals(listOf("h"), phrases.forLocale("he", null))
        val shipped = QuickPhrases.parse(asset("text/quick-phrases.json"))
        listOf("en", "ar", "fr", "hi").forEach { assertEquals(it, 14, shipped.forLocale(it, null).size) }
    }

    @Test
    fun motionPresetsLoopAndStartAtRest() {
        val book = MotionBook.parse(asset("motion/presets.json"))
        assertEquals(12, book.fps)
        assertEquals(
            listOf("none", "heartbeat", "wiggle", "bounce", "float", "jelly", "shake", "hearts", "sparkle"),
            book.presets.map { it.id }
        )
        book.presets.forEach { p ->
            val first = p.keyframes.first()
            val last = p.keyframes.last()
            assertEquals(p.id, 0f, first.t, 0f)
            listOf(first, last).forEach { k ->
                assertEquals(p.id, 1f, k.scaleX, 0f); assertEquals(p.id, 1f, k.scaleY, 0f)
                assertEquals(p.id, 0f, k.rotate, 0f); assertEquals(p.id, 0f, k.dx, 0f); assertEquals(p.id, 0f, k.dy, 0f)
            }
            if (!p.isNone) {
                assertEquals(p.id, 1f, last.t, 0f)
                assertTrue(p.id, p.frames in 12..16)
                assertTrue(p.id, p.baseScale in 0.8f..1f)
            }
        }
        assertEquals(ParticleKind.Hearts, book.byId("hearts").particles!!.kind)
        assertEquals(6, book.byId("hearts").particles!!.count)
        assertEquals(700, book.byId("sparkle").particles!!.lifetimeMs)
        assertEquals(Easing.EaseInOut, book.byId("heartbeat").keyframes[0].easing)
        assertEquals("none", book.byId("does-not-exist").id)
    }
}
```

- [ ] **Step 2: Run it and see it fail**

Run: `./gradlew --offline -q :app:testDebugUnitTest --tests 'com.piptechnologies.stickermaker.feature.create.decor.DecorDataTest'`
Expected: compilation fails (unresolved `DecorCatalog`, …).

- [ ] **Step 3: Implement `DecorData.kt`**

```kotlin
package com.piptechnologies.stickermaker.feature.create.decor

import android.content.res.AssetManager
import kotlin.math.roundToInt
import org.json.JSONArray
import org.json.JSONObject

/** Scripts that choose a lettering font. The font follows the string, never the UI language. */
enum class Script(val key: String) { Latin("latin"), Cyrillic("cyrillic"), Arabic("arabic"), Devanagari("devanagari"), Hebrew("hebrew") }

enum class TextStyleId(val key: String) {
    Classic("classic"), Sticker("sticker"), Stroke("stroke"), Bubble("bubble");
    companion object { fun of(key: String) = entries.first { it.key == key } }
}

enum class FontMood(val key: String) {
    Round("round"), Hand("hand"), Display("display");
    companion object { fun of(key: String) = entries.first { it.key == key } }
}

enum class OutlineThickness(val key: String) {
    Thin("thin"), Medium("medium"), Thick("thick");
    companion object { fun of(key: String) = entries.first { it.key == key } }
}

enum class MarkerSize(val key: String) {
    S("s"), M("m"), L("l");
    companion object { fun of(key: String) = entries.first { it.key == key } }
}

/** Fluent Emoji tone folders; the bundled file is always [Default]. */
enum class SkinTone(val folder: String) {
    Default("Default"), Light("Light"), MediumLight("Medium-Light"), Medium("Medium"), MediumDark("Medium-Dark"), Dark("Dark");
    val fileSuffix: String get() = folder.lowercase()
}

// ------------------------------------------------------------------ decor

/** One decoration piece from `assets/decor/decor.json`. */
data class DecorPiece(
    val file: String,
    val set: String,
    val label: String,
    val headTop: Boolean,
    val defaultWidth: Float,
    val emojis: List<String>
)

class DecorCatalog(val pieces: List<DecorPiece>) {
    fun set(set: String): List<DecorPiece> = pieces.filter { it.set == set }
    fun byFile(file: String): DecorPiece? = pieces.firstOrNull { it.file == file }

    companion object {
        const val DOODLES = "doodles"
        const val PROPS = "props"

        fun parse(json: String): DecorCatalog {
            val arr = JSONObject(json).getJSONArray("pieces")
            return DecorCatalog(List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                DecorPiece(
                    file = o.getString("file"),
                    set = o.getString("set"),
                    label = o.getString("label"),
                    headTop = o.getString("anchor") == "head-top",
                    defaultWidth = o.getDouble("defaultWidth").toFloat(),
                    emojis = o.getJSONArray("emojis").strings()
                )
            })
        }
    }
}

// ------------------------------------------------------------------ emoji

/** One bundled Fluent Emoji 3D item from `assets/emoji/subset.json`. */
data class EmojiItem(val name: String, val cp: String, val glyph: String, val file: String, val skinTones: Boolean)

/** The bundled emoji by tab, in display order (Love first). */
class EmojiCatalog(val tabs: Map<String, List<EmojiItem>>) {
    val love: List<EmojiItem> get() = tab(LOVE)
    fun tab(id: String): List<EmojiItem> = tabs[id].orEmpty()
    fun byFile(file: String): EmojiItem? = tabs.values.asSequence().flatten().firstOrNull { it.file == file }
    val files: List<String> get() = tabs.values.flatten().map { it.file }.distinct()

    companion object {
        const val LOVE = "love"
        val CATEGORIES = listOf("smileys", "hearts", "hands", "animals", "food", "symbols")
        private const val FLUENT = "https://raw.githubusercontent.com/microsoft/fluentui-emoji/main/assets/"

        fun parse(json: String): EmojiCatalog {
            val tabs = JSONObject(json).getJSONArray("tabs")
            val map = LinkedHashMap<String, List<EmojiItem>>()
            for (i in 0 until tabs.length()) {
                val t = tabs.getJSONObject(i)
                val items = t.getJSONArray("items")
                map[t.getString("id")] = List(items.length()) { j ->
                    val o = items.getJSONObject(j)
                    EmojiItem(o.getString("name"), o.getString("cp"), o.getString("glyph"), o.getString("file"), o.optBoolean("skinTones", false))
                }
            }
            return EmojiCatalog(map)
        }

        /** Fluent Emoji URL of a non-default [tone] of [item] (spec §2). */
        fun toneUrl(item: EmojiItem, tone: SkinTone): String {
            require(tone != SkinTone.Default) { "the default tone is bundled" }
            val slug = item.name.lowercase().replace(' ', '_')
            return FLUENT + segment(item.name) + "/" + tone.folder + "/3D/" + segment(slug) + "_3d_" + tone.fileSuffix + ".png"
        }

        /** Percent-encodes one URL path segment (RFC 3986 unreserved characters stay). */
        internal fun segment(s: String): String = buildString {
            s.toByteArray(Charsets.UTF_8).forEach { b ->
                val c = b.toInt() and 0xFF
                if (c.toChar().isLetterOrDigit() && c < 0x80 || c.toChar() in "-._~") append(c.toChar())
                else append('%').append("%02X".format(c))
            }
        }
    }
}

// ------------------------------------------------------------ text styles

data class ShadowSpec(val dx: Float, val dy: Float, val blur: Float, val colour: Int)

data class BubbleSpec(
    val fill: Int, val border: Float, val borderColour: Int, val radius: Float,
    val padV: Float, val padH: Float, val tailW: Float, val tailH: Float
)

/**
 * One text style at the 52 px base size. A null [fill] or [strokeColour] means
 * the colour the user picked; [strokeEm] is the full stroke width in em (0 = none).
 */
data class TextStyleSpec(
    val id: TextStyleId,
    val fill: Int?,
    val strokeEm: Float,
    val strokeColour: Int?,
    val shadows: List<ShadowSpec>,
    val uiFont: Boolean,
    val bubble: BubbleSpec?
)

data class NamedColour(val id: String, val argb: Int)

/** "Baloo 2 800" → family "Baloo 2", weight 800. */
data class FontRef(val family: String, val weight: Int)

class TextStyleBook(
    val styles: Map<TextStyleId, TextStyleSpec>,
    val colours: List<NamedColour>,
    val moods: Map<FontMood, Map<Script, FontRef>>,
    val outlinePx: Map<OutlineThickness, Float>,
    val outlineColours: List<NamedColour>,
    val markerPx: Map<MarkerSize, Float>,
    val markerEdgeColour: Int,
    val markerEdgeExtra: Float
) {
    companion object {
        /** Default text size at 512 (text-styles.json note). */
        const val DEFAULT_TEXT_PX = 52f

        fun parse(json: String): TextStyleBook {
            val root = JSONObject(json)
            val styleArr = root.getJSONArray("styles")
            val styles = (0 until styleArr.length()).map { styleArr.getJSONObject(it) }.associate { o ->
                val id = TextStyleId.of(o.getString("id"))
                id to TextStyleSpec(
                    id = id,
                    fill = parseColour(o.getString("fill")),
                    strokeEm = o.optDouble("strokeWidth", 0.0).toFloat(),
                    strokeColour = o.optString("strokeColour", "").takeIf { it.isNotEmpty() }?.let(::parseColour),
                    shadows = listOf("shadow", "shadow2").mapNotNull { key ->
                        o.optJSONObject(key)?.let { s ->
                            ShadowSpec(s.getDouble("dx").toFloat(), s.getDouble("dy").toFloat(), s.getDouble("blur").toFloat(), parseColour(s.getString("colour")) ?: 0)
                        }
                    },
                    uiFont = o.getString("font") == "ui",
                    bubble = o.optJSONObject("bubble")?.let { b ->
                        val pad = b.getJSONArray("padding")
                        val tail = b.getJSONObject("tail")
                        BubbleSpec(
                            fill = requireNotNull(parseColour(b.getString("fill"))),
                            border = b.getDouble("border").toFloat(),
                            borderColour = requireNotNull(parseColour(b.getString("borderColour"))),
                            radius = b.getDouble("radius").toFloat(),
                            padV = pad.getDouble(0).toFloat(),
                            padH = pad.getDouble(1).toFloat(),
                            tailW = tail.getDouble("w").toFloat(),
                            tailH = tail.getDouble("h").toFloat()
                        )
                    }
                )
            }
            val moodArr = root.getJSONObject("fonts").getJSONArray("moods")
            val moods = (0 until moodArr.length()).associate { i ->
                val m = moodArr.getJSONObject(i)
                val by = m.getJSONObject("byScript")
                FontMood.of(m.getString("id")) to Script.entries.associateWith { parseFontRef(by.getString(it.key)) }
            }
            val outline = root.getJSONObject("outline")
            val thickness = outline.getJSONObject("thickness")
            val marker = root.getJSONObject("marker")
            val widths = marker.getJSONObject("widths")
            val edge = marker.getJSONObject("edge")
            return TextStyleBook(
                styles = styles,
                colours = root.getJSONArray("colours").namedColours(),
                moods = moods,
                outlinePx = OutlineThickness.entries.associateWith { thickness.getDouble(it.key).toFloat() },
                outlineColours = outline.getJSONArray("colours").namedColours(),
                markerPx = MarkerSize.entries.associateWith { widths.getDouble(it.key).toFloat() },
                markerEdgeColour = requireNotNull(parseColour(edge.getString("colour"))),
                markerEdgeExtra = edge.getDouble("extra").toFloat()
            )
        }

        /** `#RRGGBB`, `#RGB` (trailing words ignored) or `rgba(r,g,b,a)` → ARGB; `{colour}` → null. */
        fun parseColour(s: String): Int? {
            val v = s.trim()
            if (v.startsWith("{")) return null
            if (v.startsWith("#")) {
                val hex = v.substring(1).takeWhile { it.isLetterOrDigit() }
                val full = if (hex.length == 3) hex.map { "$it$it" }.joinToString("") else hex.take(6)
                return (0xFF000000L or full.toLong(16)).toInt()
            }
            val m = Regex("""rgba?\(\s*([\d.]+)\s*,\s*([\d.]+)\s*,\s*([\d.]+)\s*(?:,\s*([\d.]+))?\s*\)""").find(v)
                ?: error("unknown colour: $s")
            val (r, g, b) = (1..3).map { m.groupValues[it].toFloat().toInt() }
            val a = m.groupValues[4].takeIf { it.isNotEmpty() }?.toFloat() ?: 1f
            return ((a * 255f).roundToInt() shl 24) or (r shl 16) or (g shl 8) or b
        }

        private fun parseFontRef(s: String): FontRef {
            val parts = s.trim().split(' ')
            return FontRef(parts.dropLast(1).joinToString(" "), parts.last().toInt())
        }

        private fun JSONArray.namedColours(): List<NamedColour> = List(length()) { i ->
            val o = getJSONObject(i)
            NamedColour(o.getString("id"), requireNotNull(parseColour(o.getString("hex"))))
        }
    }
}

// ----------------------------------------------------------- quick phrases

/** Text-tab chips by locale (Android-legacy codes: `in`, `iw`; Brazil is `pt-BR`). */
class QuickPhrases(private val byLang: Map<String, List<String>>) {
    fun forLocale(language: String, region: String?): List<String> {
        val lang = when (language) { "id" -> "in"; "he" -> "iw"; else -> language }
        if (lang == "pt" && region.equals("BR", ignoreCase = true)) byLang["pt-BR"]?.let { return it }
        return byLang[lang] ?: byLang["en"].orEmpty()
    }

    companion object {
        fun parse(json: String): QuickPhrases {
            val root = JSONObject(json)
            return QuickPhrases(root.keys().asSequence().filterNot { it.startsWith("_") }.associateWith { root.getJSONArray(it).strings() })
        }
    }
}

// ----------------------------------------------------------------- motion

enum class Easing {
    Linear, EaseIn, EaseOut, EaseInOut;
    companion object {
        fun of(s: String?): Easing = when (s) { "ease-in" -> EaseIn; "ease-out" -> EaseOut; "ease-in-out" -> EaseInOut; else -> Linear }
    }
}

data class Keyframe(val t: Float, val scaleX: Float, val scaleY: Float, val rotate: Float, val dx: Float, val dy: Float, val easing: Easing)

enum class ParticleKind { Hearts, Sparkle }

data class ParticleSpec(val kind: ParticleKind, val count: Int, val colour: Int, val sizeMin: Float, val sizeMax: Float, val lifetimeMs: Int)

/** One whole-sticker motion preset (spec §7). */
data class MotionPreset(
    val id: String,
    val durationMs: Int,
    val frames: Int,
    val baseScale: Float,
    val pivotX: Float,
    val pivotY: Float,
    val keyframes: List<Keyframe>,
    val particles: ParticleSpec?
) {
    val isNone: Boolean get() = id == NONE
    companion object { const val NONE = "none" }
}

class MotionBook(val fps: Int, val presets: List<MotionPreset>) {
    fun byId(id: String): MotionPreset = presets.firstOrNull { it.id == id } ?: presets.first { it.isNone }

    companion object {
        fun parse(json: String): MotionBook {
            val root = JSONObject(json)
            val arr = root.getJSONArray("presets")
            return MotionBook(root.getInt("fps"), List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                val pivot = o.getJSONObject("pivot")
                val ks = o.getJSONArray("keyframes")
                MotionPreset(
                    id = o.getString("id"),
                    durationMs = o.getInt("durationMs"),
                    frames = o.getInt("frames"),
                    baseScale = o.getDouble("baseScale").toFloat(),
                    pivotX = pivot.getDouble("x").toFloat(),
                    pivotY = pivot.getDouble("y").toFloat(),
                    keyframes = List(ks.length()) { k ->
                        val kf = ks.getJSONObject(k)
                        Keyframe(
                            kf.getDouble("t").toFloat(), kf.getDouble("scaleX").toFloat(), kf.getDouble("scaleY").toFloat(),
                            kf.getDouble("rotate").toFloat(), kf.getDouble("dx").toFloat(), kf.getDouble("dy").toFloat(),
                            Easing.of(kf.optString("easing", ""))
                        )
                    },
                    particles = o.optJSONObject("particles")?.let { p ->
                        val size = p.getJSONArray("size")
                        ParticleSpec(
                            kind = if (p.getString("shape") == "app-heart") ParticleKind.Hearts else ParticleKind.Sparkle,
                            count = p.getInt("count"),
                            colour = TextStyleBook.parseColour(p.getString("colour")) ?: 0,
                            sizeMin = size.getDouble(0).toFloat(),
                            sizeMax = size.getDouble(1).toFloat(),
                            lifetimeMs = p.getInt("lifetimeMs")
                        )
                    }
                )
            })
        }
    }
}

// ------------------------------------------------------------------ bundle

/** The editor's data, read once from app assets (spec §2). */
class DecorData(
    val decor: DecorCatalog,
    val emoji: EmojiCatalog,
    val styles: TextStyleBook,
    val phrases: QuickPhrases,
    val motion: MotionBook
) {
    companion object {
        fun load(assets: AssetManager): DecorData = DecorData(
            decor = DecorCatalog.parse(assets.text("decor/decor.json")),
            emoji = EmojiCatalog.parse(assets.text("emoji/subset.json")),
            styles = TextStyleBook.parse(assets.text("text/text-styles.json")),
            phrases = QuickPhrases.parse(assets.text("text/quick-phrases.json")),
            motion = MotionBook.parse(assets.text("motion/presets.json"))
        )

        private fun AssetManager.text(path: String) = open(path).bufferedReader().use { it.readText() }
    }
}

internal fun JSONArray.strings(): List<String> = List(length()) { getString(it) }
```

- [ ] **Step 4: Run the test and see it pass**

Run the Step 2 command. Expected: 7 tests pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/piptechnologies/stickermaker/feature/create/decor/DecorData.kt app/src/test/kotlin/com/piptechnologies/stickermaker/feature/create/decor/DecorDataTest.kt
git commit -m "Parse the decor, emoji, text-style, phrase and motion data

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Motion math

**Files:**
- Create: `K/feature/create/decor/Motion.kt`
- Test: `T/feature/create/decor/MotionMathTest.kt` (plain JUnit; no Android types)

**Interfaces:**
- Consumes: `MotionPreset`, `Keyframe`, `Easing`, `ParticleKind`, `ParticleSpec` (Task 1).
- Produces:
  - `object Easings { fun ease(e: Easing, u: Float): Float; fun cubicBezier(x1, y1, x2, y2, x: Float): Float }`
  - `data class Pose(scaleX, scaleY, rotate, dx, dy)`
  - `data class Affine(a, b, c, d, e, f)` with `times`, `map(x, y): FloatArray`, `matrixValues(): FloatArray` (for `android.graphics.Matrix.setValues`), and `translate`/`scale`/`rotate`/`IDENTITY`
  - `data class ParticleState(kind, x, y, size, scale, rotation, alpha, colour)`
  - `object MotionMath { fun pose(p, t): Pose; fun transform(p, t, canvas = 512f): Affine; fun frameDurations(p): List<Int>; fun particles(p, timeMs: Float, canvas = 512f): List<ParticleState>; const val MIN_FRAME_MS = 8 }`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.piptechnologies.stickermaker.feature.create.decor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionMathTest {

    private fun k(t: Float, sx: Float = 1f, sy: Float = sx, rot: Float = 0f, dx: Float = 0f, dy: Float = 0f, e: Easing = Easing.Linear) =
        Keyframe(t, sx, sy, rot, dx, dy, e)

    private val heartbeat = MotionPreset("heartbeat", 1000, 12, 0.88f, 0.5f, 0.5f,
        listOf(k(0f, e = Easing.EaseInOut), k(0.14f, 1.12f, e = Easing.EaseInOut), k(0.28f, e = Easing.EaseInOut),
            k(0.42f, 1.08f, e = Easing.EaseInOut), k(0.6f), k(1f)), null)
    private val bounce = MotionPreset("bounce", 1000, 12, 0.86f, 0.5f, 1f,
        listOf(k(0f, e = Easing.EaseOut), k(0.35f, 0.96f, 1.04f, dy = -0.07f, e = Easing.EaseIn), k(0.7f, 1.06f, 0.94f, e = Easing.EaseOut), k(1f)), null)
    private val hearts = MotionPreset("hearts", 1800, 16, 0.9f, 0.5f, 0.5f, listOf(k(0f), k(1f)),
        ParticleSpec(ParticleKind.Hearts, 6, 0xFFC23359.toInt(), 0.06f, 0.1f, 1800))
    private val sparkle = MotionPreset("sparkle", 1500, 16, 0.9f, 0.5f, 0.5f, listOf(k(0f), k(1f)),
        ParticleSpec(ParticleKind.Sparkle, 6, 0xFFF5C542.toInt(), 0.05f, 0.09f, 700))

    @Test
    fun cubicBezierMatchesCssEndpointsAndSymmetry() {
        assertEquals(0f, Easings.ease(Easing.EaseInOut, 0f), 0f)
        assertEquals(1f, Easings.ease(Easing.EaseInOut, 1f), 0f)
        assertEquals(0.5f, Easings.ease(Easing.EaseInOut, 0.5f), 1e-3f)
        assertTrue(Easings.ease(Easing.EaseOut, 0.5f) > 0.5f)
        assertTrue(Easings.ease(Easing.EaseIn, 0.5f) < 0.5f)
        assertEquals(0.3f, Easings.ease(Easing.Linear, 0.3f), 0f)
    }

    @Test
    fun poseHitsKeyframesAndRestsAtZero() {
        assertEquals(Pose.REST, MotionMath.pose(heartbeat, 0f))
        assertEquals(1.12f, MotionMath.pose(heartbeat, 0.14f).scaleX, 1e-4f)
        assertEquals(1.08f, MotionMath.pose(heartbeat, 0.42f).scaleY, 1e-4f)
        assertEquals(Pose.REST, MotionMath.pose(heartbeat, 1f))
        assertEquals(-0.07f, MotionMath.pose(bounce, 0.35f).dy, 1e-4f)
    }

    @Test
    fun transformAppliesBaseScaleAboutTheCentreAndPoseAboutThePivot() {
        val rest = MotionMath.transform(heartbeat, 0f)
        rest.map(256f, 256f).let { assertEquals(256f, it[0], 1e-3f); assertEquals(256f, it[1], 1e-3f) }
        rest.map(0f, 0f).let { assertEquals(256f - 256f * 0.88f, it[0], 1e-3f) }
        // Bounce at t = 0.35 lifts the bottom-centre pivot by 7 % of the canvas, then base-scales about the centre.
        val lifted = MotionMath.transform(bounce, 0.35f).map(256f, 512f)
        assertEquals(256f + 0.86f * ((512f - 0.07f * 512f) - 256f), lifted[1], 1e-2f)
    }

    @Test
    fun rotateIsClockwiseOnScreen() {
        val p = Affine.rotate(90f).map(1f, 0f)          // +x rotates to +y (down) with y pointing down
        assertEquals(0f, p[0], 1e-5f); assertEquals(1f, p[1], 1e-5f)
        val values = Affine.rotate(90f).matrixValues()  // android.graphics.Matrix order [scaleX, skewX, transX, skewY, scaleY, transY, …]
        assertEquals(0f, values[0], 1e-5f); assertEquals(-1f, values[1], 1e-5f); assertEquals(1f, values[3], 1e-5f)
    }

    @Test
    fun frameDurationsAddUpAndRespectTheMinimum() {
        val d = MotionMath.frameDurations(heartbeat)
        assertEquals(12, d.size)
        assertEquals(1000, d.sum())
        assertTrue(d.all { it >= MotionMath.MIN_FRAME_MS })
        assertEquals(List(16) { 125 }, MotionMath.frameDurations(hearts.copy(durationMs = 2000)))
    }

    @Test
    fun heartsRiseFadeAndLoopSeamlessly() {
        val at0 = MotionMath.particles(hearts, 0f)
        assertEquals(6, at0.size)
        assertEquals(0f, at0[0].alpha, 1e-4f)                      // heart 0 is just born
        assertEquals(0.88f * 512f, at0[0].y, 1e-2f)
        val later = MotionMath.particles(hearts, 540f)[0]           // phase 0.3: fully visible, higher up
        assertEquals(1f, later.alpha, 1e-4f)
        assertTrue(later.y < at0[0].y)
        val wrapped = MotionMath.particles(hearts, 1800f)
        at0.zip(wrapped).forEach { (a, b) -> assertEquals(a.y, b.y, 1e-2f); assertEquals(a.alpha, b.alpha, 1e-3f) }
    }

    @Test
    fun sparklesTwinkleWithinTheirLifetime() {
        val s0 = MotionMath.particles(sparkle, 0f)[0]
        assertEquals(0f, s0.scale, 1e-4f)
        val mid = MotionMath.particles(sparkle, 350f)[0]           // u = 0.5
        assertEquals(1f, mid.scale, 1e-4f)
        assertEquals(22.5f, mid.rotation, 1e-3f)
        val gone = MotionMath.particles(sparkle, 900f)[0]          // past its 700 ms life
        assertEquals(0f, gone.alpha, 0f)
    }

    @Test
    fun noneHasNoParticlesAndIdentityTransform() {
        val none = MotionPreset("none", 0, 1, 1f, 0.5f, 0.5f, listOf(k(0f)), null)
        assertEquals(emptyList<ParticleState>(), MotionMath.particles(none, 100f))
        assertEquals(Affine.IDENTITY, MotionMath.transform(none, 0.5f))
    }
}
```

- [ ] **Step 2: Run it and see it fail**

Run: `./gradlew --offline -q :app:testDebugUnitTest --tests 'com.piptechnologies.stickermaker.feature.create.decor.MotionMathTest'`. Expected: it doesn't compile.

- [ ] **Step 3: Implement `Motion.kt`**

```kotlin
package com.piptechnologies.stickermaker.feature.create.decor

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** CSS timing functions used by presets.json. */
object Easings {
    fun ease(easing: Easing, u: Float): Float = when (easing) {
        Easing.Linear -> u
        Easing.EaseIn -> cubicBezier(0.42f, 0f, 1f, 1f, u)
        Easing.EaseOut -> cubicBezier(0f, 0f, 0.58f, 1f, u)
        Easing.EaseInOut -> cubicBezier(0.42f, 0f, 0.58f, 1f, u)
    }

    /** `cubic-bezier(x1, y1, x2, y2)` at progress [x] in 0..1 (bisection on x, then y). */
    fun cubicBezier(x1: Float, y1: Float, x2: Float, y2: Float, x: Float): Float {
        if (x <= 0f) return 0f
        if (x >= 1f) return 1f
        fun bx(t: Float) = 3f * x1 * t * (1 - t) * (1 - t) + 3f * x2 * t * t * (1 - t) + t * t * t
        fun by(t: Float) = 3f * y1 * t * (1 - t) * (1 - t) + 3f * y2 * t * t * (1 - t) + t * t * t
        var lo = 0f
        var hi = 1f
        var t = x
        repeat(40) {
            val v = bx(t)
            if (abs(v - x) < 1e-6f) return by(t)
            if (v < x) lo = t else hi = t
            t = (lo + hi) / 2f
        }
        return by(t)
    }
}

/** The keyframe transform of one moment; canvas fractions for dx/dy, degrees for rotate. */
data class Pose(val scaleX: Float, val scaleY: Float, val rotate: Float, val dx: Float, val dy: Float) {
    companion object { val REST = Pose(1f, 1f, 0f, 0f, 0f) }
}

/**
 * 2-D affine map: x' = a·x + c·y + e, y' = b·x + d·y + f (y down, positive
 * angles clockwise on screen). [matrixValues] feeds android.graphics.Matrix.
 */
data class Affine(val a: Float, val b: Float, val c: Float, val d: Float, val e: Float, val f: Float) {
    operator fun times(o: Affine) = Affine(
        a = a * o.a + c * o.b, b = b * o.a + d * o.b,
        c = a * o.c + c * o.d, d = b * o.c + d * o.d,
        e = a * o.e + c * o.f + e, f = b * o.e + d * o.f + f
    )

    fun map(x: Float, y: Float) = floatArrayOf(a * x + c * y + e, b * x + d * y + f)

    fun matrixValues() = floatArrayOf(a, c, e, b, d, f, 0f, 0f, 1f)

    companion object {
        val IDENTITY = Affine(1f, 0f, 0f, 1f, 0f, 0f)
        fun translate(tx: Float, ty: Float) = Affine(1f, 0f, 0f, 1f, tx, ty)
        fun scale(sx: Float, sy: Float) = Affine(sx, 0f, 0f, sy, 0f, 0f)
        fun rotate(deg: Float): Affine {
            val r = deg * PI.toFloat() / 180f
            val cs = cos(r)
            val sn = sin(r)
            return Affine(cs, sn, -sn, cs, 0f, 0f)
        }
    }
}

/** One particle at one moment, in canvas px. [size] is the unscaled box edge. */
data class ParticleState(
    val kind: ParticleKind,
    val x: Float,
    val y: Float,
    val size: Float,
    val scale: Float,
    val rotation: Float,
    val alpha: Float,
    val colour: Int
)

/** Evaluates presets.json (spec §7). Pure; shared by the live canvas and the exporter. */
object MotionMath {
    const val CANVAS = 512f
    const val MIN_FRAME_MS = 8

    private val HEART_X = floatArrayOf(0.18f, 0.34f, 0.50f, 0.66f, 0.82f, 0.28f)
    private val SPARKLE_XY = arrayOf(0.16f to 0.20f, 0.82f to 0.16f, 0.88f to 0.60f, 0.12f to 0.66f, 0.50f to 0.08f, 0.70f to 0.88f)
    private const val SPARKLE_STAGGER_MS = 133f

    fun pose(preset: MotionPreset, t: Float): Pose {
        val ks = preset.keyframes
        if (preset.isNone || ks.size == 1) return ks.first().toPose()
        val tt = t.coerceIn(0f, 1f)
        val i = ks.indexOfLast { it.t <= tt }.coerceIn(0, ks.size - 2)
        val k0 = ks[i]
        val k1 = ks[i + 1]
        val span = k1.t - k0.t
        if (span <= 0f) return k1.toPose()
        val u = Easings.ease(k0.easing, ((tt - k0.t) / span).coerceIn(0f, 1f))
        return Pose(lerp(k0.scaleX, k1.scaleX, u), lerp(k0.scaleY, k1.scaleY, u), lerp(k0.rotate, k1.rotate, u), lerp(k0.dx, k1.dx, u), lerp(k0.dy, k1.dy, u))
    }

    /** `S_c(baseScale) · T(p + d) · R · S(scaleX, scaleY) · T(−p)` (spec §7). */
    fun transform(preset: MotionPreset, t: Float, canvas: Float = CANVAS): Affine {
        if (preset.isNone) return Affine.IDENTITY
        val p = pose(preset, t)
        val px = preset.pivotX * canvas
        val py = preset.pivotY * canvas
        val c = canvas / 2f
        val inner = Affine.translate(px + p.dx * canvas, py + p.dy * canvas) *
            Affine.rotate(p.rotate) * Affine.scale(p.scaleX, p.scaleY) * Affine.translate(-px, -py)
        val outer = Affine.translate(c, c) * Affine.scale(preset.baseScale, preset.baseScale) * Affine.translate(-c, -c)
        return outer * inner
    }

    /** Display time of each exported frame: sums to durationMs, none under [MIN_FRAME_MS]. */
    fun frameDurations(preset: MotionPreset): List<Int> {
        val n = preset.frames.coerceAtLeast(1)
        val d = preset.durationMs.toLong()
        return List(n) { i -> ((i + 1L) * d / n - i * d / n).toInt().coerceAtLeast(MIN_FRAME_MS) }
    }

    fun particles(preset: MotionPreset, timeMs: Float, canvas: Float = CANVAS): List<ParticleState> {
        val spec = preset.particles ?: return emptyList()
        return List(spec.count) { i ->
            val size = lerp(spec.sizeMin, spec.sizeMax, (i % 3) / 2f) * canvas
            when (spec.kind) {
                ParticleKind.Hearts -> {
                    val life = spec.lifetimeMs.toFloat()
                    val stagger = life / spec.count
                    val phase = floorMod(timeMs - i * stagger, life) / life
                    val alpha = when {
                        phase < 0.15f -> phase / 0.15f
                        phase < 0.6f -> 1f
                        else -> (1f - phase) / 0.4f
                    }
                    ParticleState(
                        kind = ParticleKind.Hearts,
                        x = (HEART_X[i % HEART_X.size] + 0.04f * sin(2f * PI.toFloat() * phase)) * canvas,
                        y = lerp(0.88f, 0.26f, phase) * canvas,
                        size = size,
                        scale = lerp(0.6f, 1f, phase),
                        rotation = 0f,
                        alpha = alpha.coerceIn(0f, 1f),
                        colour = spec.colour
                    )
                }
                ParticleKind.Sparkle -> {
                    val (sx, sy) = SPARKLE_XY[i % SPARKLE_XY.size]
                    val local = floorMod(timeMs - i * SPARKLE_STAGGER_MS, preset.durationMs.toFloat())
                    val life = spec.lifetimeMs.toFloat()
                    if (local >= life) {
                        ParticleState(ParticleKind.Sparkle, sx * canvas, sy * canvas, size, 0f, 0f, 0f, spec.colour)
                    } else {
                        val u = local / life
                        val s = if (u < 0.5f) u / 0.5f else (1f - u) / 0.5f
                        ParticleState(ParticleKind.Sparkle, sx * canvas, sy * canvas, size, s, 45f * u, 1f, spec.colour)
                    }
                }
            }
        }
    }

    private fun Keyframe.toPose() = Pose(scaleX, scaleY, rotate, dx, dy)
    private fun lerp(a: Float, b: Float, u: Float) = a + (b - a) * u
    private fun floorMod(x: Float, m: Float): Float = ((x % m) + m) % m
}
```

- [ ] **Step 4: Run the test and see it pass.** Expected: 8 tests pass.

- [ ] **Step 5: Commit** (`Add the motion preset math`, with the co-author line).

---

### Task 3: Layer model, undo history and the decor editor

**Files:**
- Create: `K/feature/create/decor/DecorModel.kt`, `K/feature/create/decor/EditHistory.kt`, `K/feature/create/decor/DecorEditor.kt`
- Test: `T/feature/create/decor/DecorEditorTest.kt` (plain JUnit)

**Interfaces:**
- Consumes: `TextStyleId`, `FontMood`, `OutlineThickness`, `MarkerSize`, `SkinTone`, `MotionPreset.NONE` (Task 1).
- Produces:
  - `DecorModel.kt`:
    - `data class Pt(x, y)`, `data class MarkerStroke(points: List<Pt>, colour: Int, size: MarkerSize)`
    - `sealed interface LayerContent` with `Text(text, style, colour, font)`, `Emoji(file, glyph, tone = Default)`, `Decor(file, emojis)`, `Drawing(strokes)` (points relative to the layer centre, at scale 1)
    - `data class Layer(id: Long, content, cx, cy, scale = 1f, rotation = 0f, flipped = false, behind = false)`
    - `data class OutlineStyle(on = true, thickness = Medium, colour = WHITE)`
    - `data class DecorState(layers = emptyList(), outline = OutlineStyle(), preset = "none")` with `animated` and `layer(id)`
    - `data class Size2(w, h)`, `data class Box(left, top, right, bottom)`, `data class TextDefaults(style = Sticker, colour = ROSE, font = Round)`, `data class Guides(x: Boolean, y: Boolean)`
    - `object DecorSpec` (constants below)
  - `EditHistory.kt`: `sealed interface EditStep { MaskStroke; LiveStroke; Decor(before: DecorState) }`, and `class EditHistory { push, pop, isEmpty, removeMaskStrokes(), removeLiveStrokes() }`
  - `DecorEditor.kt`: `class DecorEditor(sizeOf: (LayerContent) -> Size2, subjectBox: () -> Box?, newId: () -> Long)` with the public API in Step 3, `enum class UndoResult { Nothing, MaskStroke, Changed }`, and `fun emojiTags(state: DecorState, fallback: List<String>): List<String>`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.piptechnologies.stickermaker.feature.create.decor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DecorEditorTest {

    private var ids = 0L
    private var subject: Box? = Box(156f, 120f, 356f, 500f)
    private lateinit var editor: DecorEditor

    // Every layer is 100 × 50 at scale 1, except emoji (153.6 square).
    private val sizeOf: (LayerContent) -> Size2 = { c -> if (c is LayerContent.Emoji) Size2(153.6f, 153.6f) else Size2(100f, 50f) }

    @Before
    fun setUp() { editor = DecorEditor(sizeOf, { subject }, { ++ids }) }

    private fun text(t: String = "hi") = LayerContent.Text(t, TextStyleId.Sticker, DecorSpec.ROSE, FontMood.Round)
    private fun decor(file: String = "doodles-6.webp") = LayerContent.Decor(file, listOf("💕"))
    private fun emoji(glyph: String = "❤️") = LayerContent.Emoji("red_heart.webp", glyph)

    @Test
    fun firstTextGoesBottomCentreLaterTextCentre() {
        val a = editor.add(text())!!
        assertEquals(256f, editor.state.layer(a)!!.cx, 0f)
        assertEquals(0.87f * 512f, editor.state.layer(a)!!.cy, 1e-3f)
        val b = editor.add(text("b"))!!
        assertEquals(256f, editor.state.layer(b)!!.cy, 0f)
        assertEquals(b, editor.selectedId)
    }

    @Test
    fun headTopPiecesSitOnTheSubject() {
        val id = editor.add(decor("doodles-1.webp"), headTop = true)!!
        val layer = editor.state.layer(id)!!
        assertEquals(256f, layer.cx, 0f)                 // subject box centre
        assertEquals(120f - 50f / 4f, layer.cy, 1e-3f)   // a quarter of its height above the top
        subject = null
        val centred = editor.add(decor("doodles-2.webp"), headTop = true)!!
        assertEquals(256f, editor.state.layer(centred)!!.cy, 0f)
    }

    @Test
    fun ninthLayerIsRefused() {
        repeat(8) { assertNotNull(editor.add(decor())) }
        assertNull(editor.add(decor()))
        assertEquals(8, editor.state.layers.size)
        assertNull(editor.duplicate(editor.state.layers.first().id))
    }

    @Test
    fun duplicateFlipBehindDeleteAreOneUndoStepEach() {
        val id = editor.add(decor())!!
        val copy = editor.duplicate(id)!!
        assertEquals(256f + 0.08f * 512f, editor.state.layer(copy)!!.cx, 1e-3f)
        editor.flip(copy)
        assertTrue(editor.state.layer(copy)!!.flipped)
        editor.toggleBehind(copy)
        assertTrue(editor.state.layer(copy)!!.behind)
        editor.delete(copy)
        assertNull(editor.state.layer(copy))
        assertNull(editor.selectedId)
        assertEquals(UndoResult.Changed, editor.undo()); assertNotNull(editor.state.layer(copy))
        assertEquals(UndoResult.Changed, editor.undo()); assertFalse(editor.state.layer(copy)!!.behind)
        assertEquals(UndoResult.Changed, editor.undo()); assertFalse(editor.state.layer(copy)!!.flipped)
        assertEquals(UndoResult.Changed, editor.undo()); assertNull(editor.state.layer(copy))
        assertEquals(UndoResult.Changed, editor.undo()); assertTrue(editor.state.layers.isEmpty())
        assertEquals(UndoResult.Nothing, editor.undo())
    }

    @Test
    fun dragSnapsToTheCentreAndStaysInsideTheCanvas() {
        val id = editor.add(decor())!!
        editor.beginGesture(id)
        editor.drag(id, 40f, 0f)
        assertFalse(editor.guides.x)
        editor.drag(id, -37f, 0f)                           // raw 259: within 6 px of the centre
        assertEquals(256f, editor.state.layer(id)!!.cx, 0f)
        assertTrue(editor.guides.x)
        editor.drag(id, 5000f, 5000f)
        assertEquals(512f, editor.state.layer(id)!!.cx, 0f)  // the centre never leaves the canvas
        assertEquals(512f, editor.state.layer(id)!!.cy, 0f)
        editor.endGesture()
        assertFalse(editor.guides.x)
        assertEquals(UndoResult.Changed, editor.undo())      // the whole gesture is one step
        assertEquals(256f, editor.state.layer(id)!!.cx, 0f)
    }

    @Test
    fun pinchClampsScaleAndSnapsRotation() {
        val e = editor.add(emoji())!!
        editor.beginGesture(e)
        editor.pinch(e, zoom = 10f, rotationDeg = 3f)
        val layer = editor.state.layer(e)!!
        assertEquals(0.5f * 512f / 153.6f, layer.scale, 1e-4f)   // emoji cap: half the canvas
        assertEquals(0f, layer.rotation, 0f)                       // within ±4° snaps to 0
        editor.pinch(e, zoom = 0.001f, rotationDeg = 20f)
        assertEquals(0.1f * 512f / 153.6f, editor.state.layer(e)!!.scale, 1e-4f)
        assertEquals(23f, editor.state.layer(e)!!.rotation, 1e-3f)
        editor.endGesture()
        editor.setScaleRotation(e, scale = 1f, rotation = -2f, start = true)
        assertEquals(0f, editor.state.layer(e)!!.rotation, 0f)
    }

    @Test
    fun typingIsOneUndoStepAndEmptyTextIsDroppedOnClose() {
        assertTrue(editor.setText("m"))
        assertTrue(editor.setText("mi"))
        assertTrue(editor.setText("miss u"))
        val id = editor.selectedId!!
        assertEquals("miss u", (editor.state.layer(id)!!.content as LayerContent.Text).text)
        editor.setTextColour(0xFF000000.toInt())
        editor.setTextStyle(TextStyleId.Bubble)
        editor.endTextSession()
        assertEquals(UndoResult.Changed, editor.undo())
        assertTrue(editor.state.layers.isEmpty())           // one step removes typing and style edits together
        editor.setText("x"); editor.setText("")
        editor.endTextSession()
        assertTrue(editor.state.layers.isEmpty())
    }

    @Test
    fun styleWithoutATextLayerSetsTheDefaultsForTheNextOne() {
        editor.setTextStyle(TextStyleId.Classic)
        editor.setTextFont(FontMood.Hand)
        assertTrue(editor.state.layers.isEmpty())
        editor.setText("yo")
        val t = editor.state.layers.single().content as LayerContent.Text
        assertEquals(TextStyleId.Classic, t.style)
        assertEquals(FontMood.Hand, t.font)
    }

    @Test
    fun liveStrokesUndoOneByOneThenFlushIntoOneLayer() {
        assertTrue(editor.beginStroke(10f, 10f, DecorSpec.ROSE, MarkerSize.M))
        editor.extendStroke(30f, 10f); editor.endStroke()
        assertTrue(editor.beginStroke(10f, 50f, DecorSpec.ROSE, MarkerSize.L))
        editor.extendStroke(30f, 70f); editor.endStroke()
        assertEquals(2, editor.liveStrokes.size)
        assertEquals(UndoResult.Changed, editor.undo())
        assertEquals(1, editor.liveStrokes.size)
        val id = editor.flushLiveStrokes()!!
        val layer = editor.state.layer(id)!!
        assertEquals(20f, layer.cx, 1e-3f); assertEquals(10f, layer.cy, 1e-3f)
        assertTrue(editor.liveStrokes.isEmpty())
        assertEquals(UndoResult.Changed, editor.undo())
        assertTrue(editor.state.layers.isEmpty())
    }

    @Test
    fun flushWhileStrokeInProgress() {
        editor.beginStroke(10f, 10f, DecorSpec.ROSE, MarkerSize.S)
        editor.extendStroke(40f, 40f)                        // finger still down
        assertNotNull(editor.flushLiveStrokes())
        assertTrue(editor.liveStrokes.isEmpty())
        editor.extendStroke(60f, 60f)                        // stray move after the flush is ignored
        editor.endStroke()
        assertTrue(editor.liveStrokes.isEmpty())
        assertEquals(1, editor.state.layers.size)
    }

    @Test
    fun strokesAreRefusedAtTheLayerLimit() {
        repeat(8) { editor.add(decor()) }
        assertFalse(editor.beginStroke(0f, 0f, DecorSpec.ROSE, MarkerSize.M))
    }

    @Test
    fun clearMaskHistoryKeepsDecorSteps() {
        editor.recordMaskStroke()
        val id = editor.add(decor())!!
        editor.recordMaskStroke()
        editor.setOutlineThickness(OutlineThickness.Thick)
        editor.clearMaskHistory()                            // Auto re-ran the cut-out
        assertEquals(UndoResult.Changed, editor.undo())
        assertEquals(OutlineThickness.Medium, editor.state.outline.thickness)
        assertEquals(UndoResult.Changed, editor.undo())
        assertNull(editor.state.layer(id))
        assertEquals(UndoResult.Nothing, editor.undo())
    }

    @Test
    fun maskStrokesUndoThroughTheCaller() {
        editor.recordMaskStroke()
        assertTrue(editor.canUndo)
        assertEquals(UndoResult.MaskStroke, editor.undo())
        assertFalse(editor.canUndo)
    }

    @Test
    fun hitTestPrefersFrontLayersAndRespectsRotation() {
        val back = editor.add(decor())!!
        editor.toggleBehind(back)
        val front = editor.add(decor())!!
        assertEquals(front, editor.hitTest(256f, 256f))
        editor.delete(front)
        assertEquals(back, editor.hitTest(256f, 256f))
        editor.beginGesture(back); editor.pinch(back, 1f, 90f); editor.endGesture()
        assertEquals(back, editor.hitTest(256f, 256f + 45f))     // 100 × 50 turned upright
        assertNull(editor.hitTest(256f + 70f, 256f))
    }

    @Test
    fun presetAndOutlineChangesAreUndoable() {
        editor.setPreset("heartbeat")
        assertTrue(editor.state.animated)
        editor.setOutlineOn(false)
        editor.setOutlineColour(DecorSpec.ROSE)
        editor.undo(); editor.undo()
        assertTrue(editor.state.outline.on)
        editor.undo()
        assertFalse(editor.state.animated)
    }

    @Test
    fun emojiTagsPreferEmojiLayersThenDecorThenFallback() {
        assertEquals(listOf("❤️", "😊"), emojiTags(DecorState(), listOf("❤️", "😊")))
        editor.add(decor())
        editor.add(emoji("😍"))
        editor.add(emoji("🥺"))
        editor.add(emoji("😍"))
        assertEquals(listOf("😍", "🥺", "💕"), emojiTags(editor.state, listOf("❤️")))
    }
}
```

- [ ] **Step 2: Run it and see it fail** (`--tests '…decor.DecorEditorTest'`; compilation fails).

- [ ] **Step 3: Implement the three files**

`DecorModel.kt`:

```kotlin
package com.piptechnologies.stickermaker.feature.create.decor

/** A point in 512 canvas px. */
data class Pt(val x: Float, val y: Float)

/** One marker stroke; [points] are canvas px while live, and relative to the layer centre once flushed. */
data class MarkerStroke(val points: List<Pt>, val colour: Int, val size: MarkerSize)

sealed interface LayerContent {
    data class Text(val text: String, val style: TextStyleId, val colour: Int, val font: FontMood) : LayerContent
    data class Emoji(val file: String, val glyph: String, val tone: SkinTone = SkinTone.Default) : LayerContent
    data class Decor(val file: String, val emojis: List<String>) : LayerContent
    data class Drawing(val strokes: List<MarkerStroke>) : LayerContent
}

/** One layer; [scale] multiplies the content's base size (spec §3). */
data class Layer(
    val id: Long,
    val content: LayerContent,
    val cx: Float,
    val cy: Float,
    val scale: Float = 1f,
    val rotation: Float = 0f,
    val flipped: Boolean = false,
    val behind: Boolean = false
)

data class OutlineStyle(
    val on: Boolean = true,
    val thickness: OutlineThickness = OutlineThickness.Medium,
    val colour: Int = DecorSpec.WHITE
)

/** Everything decorating one sticker. Live Draw strokes are kept by [DecorEditor], outside the undo snapshots. */
data class DecorState(
    val layers: List<Layer> = emptyList(),
    val outline: OutlineStyle = OutlineStyle(),
    val preset: String = MotionPreset.NONE
) {
    val animated: Boolean get() = preset != MotionPreset.NONE
    fun layer(id: Long?): Layer? = layers.firstOrNull { it.id == id }
}

data class Size2(val w: Float, val h: Float)

data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val cx: Float get() = (left + right) / 2f
    val cy: Float get() = (top + bottom) / 2f
}

data class TextDefaults(
    val style: TextStyleId = TextStyleId.Sticker,
    val colour: Int = DecorSpec.ROSE,
    val font: FontMood = FontMood.Round
)

data class Guides(val x: Boolean, val y: Boolean) { companion object { val NONE = Guides(false, false) } }

/** Numbers from the handoff (spec §3). */
object DecorSpec {
    const val CANVAS = 512f
    const val MAX_LAYERS = 8
    const val MIN_WIDTH = 0.10f
    const val EMOJI_MAX_WIDTH = 0.5f
    const val EMOJI_WIDTH = 0.30f
    const val SNAP_PX = 6f
    const val ROTATION_SNAP_DEG = 4f
    const val DUPLICATE_OFFSET = 0.08f
    const val FIRST_TEXT_Y = 0.87f
    const val TEXT_MAX_CHARS = 30
    const val HIT_SLOP_PX = 12f
    const val ROSE = 0xFFC23359.toInt()
    const val WHITE = 0xFFFFFFFF.toInt()
}
```

`EditHistory.kt`:

```kotlin
package com.piptechnologies.stickermaker.feature.create.decor

/** One undoable touch (spec §4). */
sealed interface EditStep {
    /** A Brush or Erase stroke; the view model removes the item's last mask stroke. */
    data object MaskStroke : EditStep
    /** A Draw stroke that is still live. */
    data object LiveStroke : EditStep
    /** Any decor change; undo restores [before]. */
    data class Decor(val before: DecorState) : EditStep
}

/** One sticker's undo stack. */
class EditHistory {
    private val steps = ArrayDeque<EditStep>()
    val isEmpty: Boolean get() = steps.isEmpty()
    fun push(step: EditStep) = steps.addLast(step)
    fun pop(): EditStep? = steps.removeLastOrNull()
    /** The cut-out re-ran and reset its strokes. */
    fun removeMaskStrokes() { steps.removeAll { it == EditStep.MaskStroke } }
    /** Live strokes became a layer (one [EditStep.Decor] replaces them). */
    fun removeLiveStrokes() { steps.removeAll { it == EditStep.LiveStroke } }
}
```

`DecorEditor.kt`: implement exactly this API. Keep it free of Android types.

```kotlin
package com.piptechnologies.stickermaker.feature.create.decor

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

enum class UndoResult { Nothing, MaskStroke, Changed }

/**
 * Edits one sticker's decor (spec §3-§4): layers, gestures, text sessions, live
 * Draw strokes, outline, preset and the undo stack. [sizeOf] gives a content's base
 * size at scale 1; [subjectBox] is the cut-out's bounding box (null before the cut).
 */
class DecorEditor(
    private val sizeOf: (LayerContent) -> Size2,
    private val subjectBox: () -> Box?,
    private val newId: () -> Long
) {
    var state: DecorState = DecorState(); private set
    var selectedId: Long? = null; private set
    var liveStrokes: List<MarkerStroke> = emptyList(); private set
    var guides: Guides = Guides.NONE; private set
    var textDefaults: TextDefaults = TextDefaults(); private set
    private val history = EditHistory()
    val canUndo: Boolean get() = !history.isEmpty

    private var gesture: Gesture? = null
    private var textSession: Long? = null
    private var strokeDown = false

    private class Gesture(val id: Long, var rawX: Float, var rawY: Float, var rawScale: Float, var rawRotation: Float)

    // ---------------------------------------------------------------- queries

    fun baseSize(layer: Layer): Size2 = sizeOf(layer.content)
    /** Rendered size at the layer's scale. */
    fun renderedSize(layer: Layer): Size2 = baseSize(layer).let { Size2(it.w * layer.scale, it.h * layer.scale) }

    /** Top-most layer under a point: front layers first, then behind ones. */
    fun hitTest(x: Float, y: Float): Long? {
        val front = state.layers.filter { !it.behind }.asReversed()
        val back = state.layers.filter { it.behind }.asReversed()
        return (front + back).firstOrNull { contains(it, x, y, DecorSpec.HIT_SLOP_PX) }?.id
    }

    private fun contains(layer: Layer, x: Float, y: Float, slop: Float): Boolean {
        val s = renderedSize(layer)
        val r = Math.toRadians(-layer.rotation.toDouble())
        val dx = x - layer.cx
        val dy = y - layer.cy
        val lx = (dx * cos(r) - dy * sin(r)).toFloat()
        val ly = (dx * sin(r) + dy * cos(r)).toFloat()
        return abs(lx) <= s.w / 2f + slop && abs(ly) <= s.h / 2f + slop
    }

    // ------------------------------------------------------------- selection

    fun select(id: Long?) {
        if (id != textSession) endTextSession()
        selectedId = id?.takeIf { state.layer(it) != null }
    }

    // ---------------------------------------------------------------- adding

    /** Adds a layer at its spec position and selects it; null when 8 layers exist. */
    fun add(content: LayerContent, headTop: Boolean = false): Long? {
        if (state.layers.size >= DecorSpec.MAX_LAYERS) return null
        endTextSession()
        val before = state
        val id = place(content, headTop)
        history.push(EditStep.Decor(before))
        return id
    }

    private fun place(content: LayerContent, headTop: Boolean, at: Pt? = null): Long {
        val size = sizeOf(content)
        val c = DecorSpec.CANVAS
        val (cx, cy) = when {
            at != null -> at.x to at.y
            content is LayerContent.Text && state.layers.none { it.content is LayerContent.Text } -> c / 2f to DecorSpec.FIRST_TEXT_Y * c
            headTop -> subjectBox()?.let { box -> box.cx to max(box.top - size.h / 4f, size.h / 2f) } ?: (c / 2f to c / 2f)
            else -> c / 2f to c / 2f
        }
        val id = newId()
        val layer = Layer(id, content, cx.coerceIn(0f, c), cy.coerceIn(0f, c))
        state = state.copy(layers = state.layers + layer.copy(scale = clampScale(layer, 1f)))
        selectedId = id
        return id
    }

    // ------------------------------------------------------------ layer edits

    fun duplicate(id: Long): Long? {
        val src = state.layer(id) ?: return null
        if (state.layers.size >= DecorSpec.MAX_LAYERS) return null
        endTextSession()
        val before = state
        val off = DecorSpec.DUPLICATE_OFFSET * DecorSpec.CANVAS
        val copy = src.copy(id = newId(), cx = (src.cx + off).coerceIn(0f, DecorSpec.CANVAS), cy = (src.cy + off).coerceIn(0f, DecorSpec.CANVAS))
        state = state.copy(layers = state.layers + copy)
        selectedId = copy.id
        history.push(EditStep.Decor(before))
        return copy.id
    }

    fun flip(id: Long) = change(id) { it.copy(flipped = !it.flipped) }
    fun toggleBehind(id: Long) = change(id) { it.copy(behind = !it.behind) }

    fun delete(id: Long) {
        if (state.layer(id) == null) return
        endTextSession()
        history.push(EditStep.Decor(state))
        state = state.copy(layers = state.layers.filterNot { it.id == id })
        if (selectedId == id) selectedId = null
    }

    private fun change(id: Long, f: (Layer) -> Layer) {
        if (state.layer(id) == null) return
        endTextSession()
        history.push(EditStep.Decor(state))
        update(id, f)
    }

    private fun update(id: Long, f: (Layer) -> Layer) {
        state = state.copy(layers = state.layers.map { if (it.id == id) f(it) else it })
    }

    // -------------------------------------------------------------- gestures

    /** A drag, pinch or handle gesture starts on [id]: selects it and records one undo step. */
    fun beginGesture(id: Long) {
        val l = state.layer(id) ?: return
        if (id != textSession) endTextSession()
        selectedId = id
        history.push(EditStep.Decor(state))
        gesture = Gesture(id, l.cx, l.cy, l.scale, l.rotation)
    }

    /** Moves by a canvas-px delta, with centre snapping and the quarter-inside clamp. */
    fun drag(id: Long, dx: Float, dy: Float) {
        val g = gesture?.takeIf { it.id == id } ?: return
        g.rawX += dx
        g.rawY += dy
        val c = DecorSpec.CANVAS / 2f
        val snapX = abs(g.rawX - c) < DecorSpec.SNAP_PX
        val snapY = abs(g.rawY - c) < DecorSpec.SNAP_PX
        guides = Guides(snapX, snapY)
        update(id) { it.copy(cx = (if (snapX) c else g.rawX).coerceIn(0f, DecorSpec.CANVAS), cy = (if (snapY) c else g.rawY).coerceIn(0f, DecorSpec.CANVAS)) }
    }

    /** Multiplies the scale and adds rotation (two-finger pinch and twist). */
    fun pinch(id: Long, zoom: Float, rotationDeg: Float) {
        val g = gesture?.takeIf { it.id == id } ?: return
        g.rawScale *= zoom
        g.rawRotation += rotationDeg
        update(id) { it.copy(scale = clampScale(it, g.rawScale), rotation = snapRotation(g.rawRotation)) }
        g.rawScale = state.layer(id)!!.scale
    }

    /** Absolute scale and rotation from the corner handle; [start] records the undo step. */
    fun setScaleRotation(id: Long, scale: Float, rotation: Float, start: Boolean = false) {
        if (start) beginGesture(id)
        update(id) { it.copy(scale = clampScale(it, scale), rotation = snapRotation(rotation)) }
    }

    fun endGesture() {
        gesture = null
        guides = Guides.NONE
    }

    private fun clampScale(layer: Layer, scale: Float): Float {
        val base = baseSize(layer).w.coerceAtLeast(1f)
        val maxW = if (layer.content is LayerContent.Emoji) DecorSpec.EMOJI_MAX_WIDTH else 1f
        return scale.coerceIn(DecorSpec.MIN_WIDTH * DecorSpec.CANVAS / base, maxW * DecorSpec.CANVAS / base)
    }

    private fun snapRotation(deg: Float): Float {
        val n = ((deg % 360f) + 540f) % 360f - 180f
        return if (abs(n) < DecorSpec.ROTATION_SNAP_DEG) 0f else n
    }

    // ------------------------------------------------------------------ text

    /** Edits the selected text layer, or creates one on the first character. False when 8 layers exist. */
    fun setText(text: String): Boolean {
        val t = text.take(DecorSpec.TEXT_MAX_CHARS)
        val sel = selectedText()
        if (sel != null) {
            beginTextSession(sel.id)
            update(sel.id) { it.copy(content = (it.content as LayerContent.Text).copy(text = t)) }
            return true
        }
        if (t.isEmpty()) return true
        if (state.layers.size >= DecorSpec.MAX_LAYERS) return false
        val before = state
        val id = place(LayerContent.Text(t, textDefaults.style, textDefaults.colour, textDefaults.font), headTop = false)
        history.push(EditStep.Decor(before))
        textSession = id
        return true
    }

    fun setTextStyle(style: TextStyleId) = styleText({ it.copy(style = style) }) { textDefaults = textDefaults.copy(style = style) }
    fun setTextColour(colour: Int) = styleText({ it.copy(colour = colour) }) { textDefaults = textDefaults.copy(colour = colour) }
    fun setTextFont(font: FontMood) = styleText({ it.copy(font = font) }) { textDefaults = textDefaults.copy(font = font) }

    private fun styleText(f: (LayerContent.Text) -> LayerContent.Text, remember: () -> Unit) {
        remember()
        val sel = selectedText() ?: return
        beginTextSession(sel.id)
        update(sel.id) { it.copy(content = f(it.content as LayerContent.Text)) }
    }

    /** Opens a text layer for editing (edit handle or double tap). */
    fun editText(id: Long) {
        if (state.layer(id)?.content !is LayerContent.Text) return
        if (id != textSession) endTextSession()
        selectedId = id
    }

    /** Ends coalescing and removes empty text layers (the sheet closed or another layer was chosen). */
    fun endTextSession() {
        textSession = null
        val empty = state.layers.filter { (it.content as? LayerContent.Text)?.text?.isBlank() == true }.map { it.id }
        if (empty.isNotEmpty()) {
            state = state.copy(layers = state.layers.filterNot { it.id in empty })
            if (selectedId in empty) selectedId = null
        }
    }

    private fun beginTextSession(id: Long) {
        if (textSession != id) {
            history.push(EditStep.Decor(state))
            textSession = id
        }
    }

    private fun selectedText(): Layer? = state.layer(selectedId)?.takeIf { it.content is LayerContent.Text }

    // -------------------------------------------------------- outline, preset

    fun setOutlineOn(on: Boolean) = setOutline(state.outline.copy(on = on))
    fun setOutlineThickness(t: OutlineThickness) = setOutline(state.outline.copy(thickness = t))
    fun setOutlineColour(colour: Int) = setOutline(state.outline.copy(colour = colour))

    private fun setOutline(o: OutlineStyle) {
        if (o == state.outline) return
        endTextSession()
        history.push(EditStep.Decor(state))
        state = state.copy(outline = o)
    }

    fun setPreset(id: String) {
        if (id == state.preset) return
        endTextSession()
        history.push(EditStep.Decor(state))
        state = state.copy(preset = id)
    }

    // ------------------------------------------------------------------ draw

    /** Starts a marker stroke; false (nothing drawn) when the sticker already holds 8 layers. */
    fun beginStroke(x: Float, y: Float, colour: Int, size: MarkerSize): Boolean {
        if (state.layers.size >= DecorSpec.MAX_LAYERS) return false
        endTextSession()
        selectedId = null
        liveStrokes = liveStrokes + MarkerStroke(listOf(Pt(x, y)), colour, size)
        strokeDown = true
        history.push(EditStep.LiveStroke)
        return true
    }

    fun extendStroke(x: Float, y: Float) {
        if (!strokeDown || liveStrokes.isEmpty()) return
        val last = liveStrokes.last()
        liveStrokes = liveStrokes.dropLast(1) + last.copy(points = last.points + Pt(x, y))
    }

    fun endStroke() { strokeDown = false }

    /** Turns the live strokes into one Drawing layer (one undo step). */
    fun flushLiveStrokes(): Long? {
        strokeDown = false
        if (liveStrokes.isEmpty()) return null
        val pts = liveStrokes.flatMap { it.points }
        val cx = (pts.minOf { it.x } + pts.maxOf { it.x }) / 2f
        val cy = (pts.minOf { it.y } + pts.maxOf { it.y }) / 2f
        val rel = liveStrokes.map { s -> s.copy(points = s.points.map { Pt(it.x - cx, it.y - cy) }) }
        history.removeLiveStrokes()
        val before = state
        liveStrokes = emptyList()
        val id = place(LayerContent.Drawing(rel), headTop = false, at = Pt(cx, cy))
        selectedId = null
        history.push(EditStep.Decor(before))
        return id
    }

    // ------------------------------------------------------------ mask, undo

    fun recordMaskStroke() = history.push(EditStep.MaskStroke)
    fun clearMaskHistory() = history.removeMaskStrokes()

    fun undo(): UndoResult {
        val step = history.pop() ?: return UndoResult.Nothing
        textSession = null
        gesture = null
        guides = Guides.NONE
        return when (step) {
            EditStep.MaskStroke -> UndoResult.MaskStroke
            EditStep.LiveStroke -> { liveStrokes = liveStrokes.dropLast(1); strokeDown = false; UndoResult.Changed }
            is EditStep.Decor -> {
                state = step.before
                if (state.layer(selectedId) == null) selectedId = null
                UndoResult.Changed
            }
        }
    }
}

/** WhatsApp tags for one sticker (spec §3): emoji layers, then decor pieces, deduplicated, at most 3. */
fun emojiTags(state: DecorState, fallback: List<String>): List<String> {
    val tags = LinkedHashSet<String>()
    state.layers.forEach { (it.content as? LayerContent.Emoji)?.let { e -> tags += e.glyph } }
    state.layers.forEach { (it.content as? LayerContent.Decor)?.let { d -> tags += d.emojis } }
    return tags.filter { it.isNotBlank() }.take(3).ifEmpty { fallback }
}
```

Note on `flushLiveStrokes`: `place` clamps the centre, and the Drawing base size comes from `sizeOf`. With the test's fake size (100 × 50) the centre stays where the strokes are.

- [ ] **Step 4: Run the test and see it pass.** Expected: 16 tests pass.

- [ ] **Step 5: Commit** (`Add the layer model, undo history and decor editor`, with the co-author line).

---

### Task 4: Strings in 19 locales and quick phrases

**Files:**
- Modify: `app/src/main/res/values{,-ar,-de,-es,-fa,-fr,-ha,-hi,-in,-it,-iw,-my,-ps,-pt,-pt-rBR,-ru,-tr,-ur,-zh}/strings.xml`
- Modify: `app/src/main/assets/text/quick-phrases.json`
- Test: extend `T/feature/create/decor/DecorDataTest.kt`

**Interfaces:**
- Consumes: `$SCRATCH/i18n/source-strings.json` (en + ar + notes) and `$SCRATCH/i18n/translations.json` (`{"strings": {key: {lang: text}}, "quick_phrases": {lang: [14]}}`, 17 locales).
- Produces: the string resources below. Later tasks use them through `R.string.<key>`.

**New translatable keys:** every `key` in `source-strings.json`.
- `create_tool_{add,draw,animate}`
- `create_hint_{add,draw,animate,animate_clip}`
- `create_action_{duplicate,flip,behind,in_front,delete}`
- `create_handle_{edit,transform}`
- `create_toast_layer_limit`
- `create_add_tab_{text,emoji,stickers}`
- `create_text_placeholder`
- `create_text_style_{classic,sticker,stroke,bubble}`
- `create_text_font_{round,hand,display}`
- `create_colour_{white,ink,rose,peach,gold,mint,sky,violet}`
- `create_emoji_love`, `create_emoji_tab_{smileys,hearts,hands,animals,food,symbols,recent}`
- `create_skin_{default,light,medium_light,medium,medium_dark,dark}`
- `create_toast_skin_failed`
- `create_decor_{doodles,props}`
- `create_draw_{small,medium,large}`
- `create_animate_note{,_clip,_reduced}`
- `create_preset_{none,heartbeat,wiggle,bounce,float,jelly,shake,hearts,sparkle}`
- `create_outline_{thin,medium,thick}`
- `create_info_body_animated`
- `settings_licences`
- `licences_{intro,fluent_sub,fonts_sub,lucide_sub}`

**Existing keys whose value changes in every locale:** `create_outline_title` and `create_outline_body`.

**New untranslatable keys** (`translatable="false"`, in `values/strings.xml` only):
- `create_rail_anim` "ANIM"
- `create_text_style_sample` "Aa"
- `licences_fluent_name` "Fluent Emoji"
- `licences_fonts_name` "Baloo 2 · Baloo Bhaijaan 2 · Rubik · Caveat · Lilita One · Mirza · Kalam · Amatic SC · Lalezar · Yatra One · Ruslan Display · Karantina"
- `licences_lucide_name` "Lucide"
- `licences_tag_mit` "MIT", `licences_tag_ofl` "SIL OFL 1.1", `licences_tag_isc` "ISC"

**Keys to delete from every locale:** `create_tool_text`, `create_hint_text`, `create_caption_placeholder`. No code uses them after Tasks 8–10. Task 4 lands first, so its injector deletes them and Step 5 below removes the last references.

- [ ] **Step 1: Write the failing test** (append to `DecorDataTest`):

```kotlin
    @Test
    fun quickPhrasesShipForEveryAppLocale() {
        val shipped = QuickPhrases.parse(asset("text/quick-phrases.json"))
        listOf("en", "ar", "de", "es", "fa", "fr", "ha", "hi", "in", "it", "iw", "my", "ps", "pt", "ru", "tr", "ur", "zh").forEach {
            assertEquals(it, 14, shipped.forLocale(it, null).size)
        }
        assertEquals(14, shipped.forLocale("pt", "BR").size)
        assertTrue(shipped.forLocale("pt", "BR") !== shipped.forLocale("pt", null))
    }
```

- [ ] **Step 2: Run it and see it fail** (the phrases file has only 4 locales).

- [ ] **Step 3: Write the injector** at `$SCRATCH/i18n/inject_decor_strings.py` (not committed; the repo is public), then run it from the worktree root:

```python
#!/usr/bin/env python3
"""Adds the Decorate & Animate strings to all 19 strings.xml files and the phrases to quick-phrases.json."""
import json, os, re, sys

SCR = sys.argv[1]
src = json.load(open(os.path.join(SCR, 'i18n/source-strings.json')))
tr = json.load(open(os.path.join(SCR, 'i18n/translations.json')))
LOCALES = ['ar', 'de', 'es', 'fa', 'fr', 'ha', 'hi', 'in', 'it', 'iw', 'my', 'ps', 'pt', 'pt-rBR', 'ru', 'tr', 'ur', 'zh']
RTL = {'ar', 'fa', 'iw', 'ps', 'ur'}
DROP = ['create_tool_text', 'create_hint_text', 'create_caption_placeholder']
REPLACE = {'create_outline_title', 'create_outline_body'}
UNTRANSLATABLE = [
    ('create_rail_anim', 'ANIM'), ('create_text_style_sample', 'Aa'),
    ('licences_fluent_name', 'Fluent Emoji'),
    ('licences_fonts_name', 'Baloo 2 · Baloo Bhaijaan 2 · Rubik · Caveat · Lilita One · Mirza · Kalam · Amatic SC · Lalezar · Yatra One · Ruslan Display · Karantina'),
    ('licences_lucide_name', 'Lucide'), ('licences_tag_mit', 'MIT'), ('licences_tag_ofl', 'SIL OFL 1.1'), ('licences_tag_isc', 'ISC'),
]

def esc(s):
    s = s.replace('\\', '\\\\').replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;')
    s = s.replace("'", "\\'").replace('"', '\\"').replace('\n', '\\n')
    if s.startswith('@') or s.startswith('?'):
        s = '\\' + s
    return s

def value(key, lang):
    item = next(i for i in src['strings'] if i['key'] == key)
    if lang == 'en': return item['en']
    if lang == 'ar': return item['ar']
    return tr['strings'][key][lang]

def rtl_mark(text, lang):
    return ('&#x200F;' + text) if lang in RTL and re.match(r'[A-Za-z%]', text) else text

for lang in ['en'] + LOCALES:
    path = 'app/src/main/res/values%s/strings.xml' % ('' if lang == 'en' else '-' + lang)
    xml = open(path, encoding='utf-8').read()
    for key in DROP:
        xml = re.sub(r'\n[ \t]*<string name="%s"[^>]*>.*?</string>' % key, '', xml, flags=re.S)
    block = []
    for item in src['strings']:
        key = item['key']
        text = rtl_mark(esc(value(key, lang)), lang)
        line = '    <string name="%s">%s</string>' % (key, text)
        if key in REPLACE:
            xml, n = re.subn(r'<string name="%s">.*?</string>' % key, line.strip(), xml, flags=re.S)
            assert n == 1, (path, key)
            continue
        assert 'name="%s"' % key not in xml, (path, key)
        if '%1$d' in item['en']:
            block.append('    <!-- %1$d is always 8. -->' if key == 'create_toast_layer_limit' else '')
        block.append(line)
    if lang == 'en':
        block += ['    <string name="%s" translatable="false">%s</string>' % (k, esc(v)) for k, v in UNTRANSLATABLE]
    anchor = '</resources>'
    xml = xml.replace(anchor, '\n    <!-- Create › Cut out: Add, Draw, Animate; Settings › Licences -->\n' + '\n'.join(b for b in block if b) + '\n' + anchor)
    open(path, 'w', encoding='utf-8').write(xml)
    print('ok', path)

qp_path = 'app/src/main/assets/text/quick-phrases.json'
qp = json.load(open(qp_path, encoding='utf-8'))
for lang, phrases in tr['quick_phrases'].items():
    assert len(phrases) == 14, lang
    qp['pt-BR' if lang == 'pt-rBR' else lang] = phrases
json.dump(qp, open(qp_path, 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
print('ok', qp_path, sorted(k for k in qp if not k.startswith('_')))
```

Run: `python3 "$SCRATCH/i18n/inject_decor_strings.py" "$SCRATCH"`
Expected: 19 `ok …strings.xml` lines, then `ok …quick-phrases.json` listing 19 locale keys (`pt-BR` included).

- [ ] **Step 4: Remove the last uses of the deleted keys** so the app compiles:
  - In `CreateEditorScreen.kt`, the Text tool button (`R.string.create_tool_text`), `CaptionField` (`R.string.create_caption_placeholder`) and the `EditorTool.Text` hint (`R.string.create_hint_text`) are replaced in Tasks 8–10.
  - For now, delete `CaptionField`, the Text `ToolButton` and the `tool == EditorTool.Text` hint branch, and keep `EditorTool.Text` in the enum until Task 6. Build: `./gradlew --offline -q :app:compileDebugKotlin`.

- [ ] **Step 5: Run the string tests and the new phrase test**

Run: `./gradlew --offline -q :app:testDebugUnitTest --tests 'com.piptechnologies.stickermaker.l10n.*' --tests 'com.piptechnologies.stickermaker.feature.create.decor.DecorDataTest'`
Expected:
- `StringResourcesTest`: equal key sets, placeholders preserved, fewer than 20% identical to English.
- `LocalizedFormattingTest`.
- `DecorDataTest`.
All pass. If `StringResourcesTest` flags a locale whose translations are mostly identical to English, fix the offending strings in `translations.json` and rerun Step 3 on a clean checkout of the strings files (`git checkout -- app/src/main/res`).

- [ ] **Step 6: Commit** (`Translate the Decorate & Animate strings into all 19 languages`, with the co-author line).

---

### Task 5: Rendering engine

**Files:**
- Create: `K/feature/create/decor/DecorFonts.kt`, `TextLayerPainter.kt`, `DecorAssets.kt`, `LayerRenderCache.kt`, `SceneRenderer.kt`
- Modify: `K/feature/create/StickerRenderer.kt` (add `maskedSubject`)
- Test: `T/feature/create/decor/TextLayerPainterTest.kt`, `T/feature/create/decor/SceneRendererTest.kt`

**Interfaces:**
- Consumes: Tasks 1–3; `LetteringFonts.choose` and `LetteringFonts.isRtlParagraph` (`feature/namepack/engine/LetteringFonts.kt`); `HeartPath.inBox(left, top, size): Path` (`feature/namepack/engine/HeartPath.kt`); `StickerRenderer.outlineOf(bitmap, radiusPx)`.
- Produces:
  - `enum class FontFile { UI, BALOO, BALOO_BHAIJAAN, RUBIK, SYSTEM_BOLD, CAVEAT, MIRZA, KALAM, AMATIC, LILITA, RUSLAN, LALEZAR, YATRA, KARANTINA }`
  - `data class FontFace(typeface: Typeface, fakeBold: Boolean = false)`
  - `class DecorFonts(book: TextStyleBook, load: (FontFile) -> FontFace)`:
    - members: `face(file)`, `forText(text, uiFont, mood): FontFace`;
    - companion: `fileFor(text, uiFont, mood, book): FontFile`, `scriptOf(text): Script`, `android(context, book): DecorFonts`.
  - `class TextLayerPainter(fonts: DecorFonts, book: TextStyleBook, rtlLanguage: () -> Boolean)`: `baseSize(t: LayerContent.Text): Size2`, `render(t, scale): Bitmap`.
  - `class DecorAssets(openAsset: (String) -> InputStream, toneDir: File)`:
    - members: `emoji(file, tone): Bitmap?`, `decor(file): Bitmap?`, `aspect(path): Float`;
    - companion: `toneFileName(file, tone): String`, `android(context): DecorAssets`.
  - `class RenderedLayer(bitmap: Bitmap, silhouette: Bitmap?, renderScale: Float)`
  - `class LayerRenderCache(assets, painter, data: DecorData)`: `baseSize(content): Size2`, `render(layer, outlineRadius: Float?): RenderedLayer`, `clear()`.
  - `class SceneRenderer(cache: LayerRenderCache, data: DecorData)`:
    - nested `class Scene(subject: Bitmap?, subjectSilhouette: Bitmap?, decor: DecorState, liveStrokes: List<MarkerStroke>)`;
    - members: `outlineRadius(t: OutlineThickness): Float`, `drawSticker(canvas, scene, dimLayers = false)`, `renderStill(scene): Bitmap`, `drawParticles(canvas, particles)`, `drawStrokes(canvas, strokes)`, `renderFrame(rest: Bitmap, preset: MotionPreset, index: Int): Bitmap`.
  - `StickerRenderer.maskedSubject(source: Bitmap, mask: Bitmap): Bitmap`

**Behaviour (spec §5)**
- **Fonts:**
  - `fileFor` returns `UI` for Classic.
  - For Rounded it maps `LetteringFonts.choose(text)`: BALOO → BALOO, BALOO_BHAIJAAN → BALOO_BHAIJAAN, RUBIK → RUBIK, SYSTEM_BOLD → SYSTEM_BOLD. That keeps the Custom Stickers Pashto rule.
  - For Hand and Display it looks up `book.moods[mood][scriptOf(text)].family` through `{"Caveat": CAVEAT, "Mirza": MIRZA, "Kalam": KALAM, "Amatic SC": AMATIC, "Lilita One": LILITA, "Ruslan Display": RUSLAN, "Lalezar": LALEZAR, "Yatra One": YATRA, "Karantina": KARANTINA}`.
  - `scriptOf` checks any Arabic-script char (U+0600–06FF, 0750–077F, 08A0–08FF, FB50–FDFF, FE70–FEFF) → Arabic. Otherwise Hebrew (0590–05FF), then Cyrillic (0400–04FF), then Devanagari (0900–097F), else Latin.
  - `android()` loads `R.font.hg_extrabold` for UI and `R.font.lettering_*` for the rest with `ResourcesCompat.getFont`, and SYSTEM_BOLD = `Typeface.DEFAULT_BOLD`.
  - CAVEAT on API ≥ 26 is `Typeface.Builder(context.assets, "fonts/lettering_caveat.ttf").setFontVariationSettings("'wght' 700").build()`; otherwise `Typeface.createFromAsset(...)` with `fakeBold = true`.
- **Text painter:**
  - Size is `52 × scale`; Bubble text is `0.78 ×` that size.
  - Lines are chosen once at scale 1 with a 480 px limit (§5 fit). A line that fits stays one line. Otherwise split at the space whose longer half is narrowest. With no space, keep one line.
  - Line height is 1.05 × text size (1.1 for Bubble). Letter spacing is −0.01 em for Classic and Sticker.
  - Direction comes from `LetteringFonts.isRtlParagraph(text, rtlLanguage())`, applied with one `StaticLayout` per line (`TextDirectionHeuristics.RTL` or `LTR`, `ALIGN_CENTER`).
  - Passes (colour = the layer's colour; the `k = size / 52` factor scales every px value):
    - **Classic:** first the copy in #1E2128 at `dy = 3k` with `setShadowLayer(12k × 0.87, 0, 0, 0x59000000)`; then the fill in the colour.
    - **Sticker:** first a STROKE pass, width `0.16 × textSize`, white, `Join.ROUND`, `setShadowLayer(3k × 0.87, 0, 3k, 0x2E000000)`; then the fill in the colour.
    - **Bold stroke:** first a STROKE pass, width `0.2 × textSize`, in the colour; then a white fill.
    - **Bubble:**
      - the path is a round rect (radius 28k) around the block, padded 12k vertically and 22k horizontally, `Path.op UNION` with the tail triangle. The tail is 26k wide and 20k tall; its base starts 0.5 em in from the start edge (the right edge for RTL text) on the bottom edge, and its tip sits 20k below;
      - fill it white, then stroke 5k in #1E2128;
      - then the text fill in #1E2128.
  - The bitmap is padded by the stroke half-width, the largest shadow reach (|dy| + blur) and the bubble border plus tail, plus 2 px. `baseSize` returns that bitmap's size at scale 1 without drawing.
- **Assets:**
  - `emoji(file, Default)` decodes `emoji/$file` from assets. Other tones decode `toneDir/<file without .webp>_<tone.fileSuffix>.png` and fall back to the default asset when that file is missing.
  - `decor(file)` decodes `decor/$file`.
  - Keep an `LruCache<String, Bitmap>` of 24 MB sized by `byteCount`.
  - `aspect(path)` = height / width from a bounds-only decode.
- **Layer cache:**
  - Base sizes:
    - Text: `painter.baseSize`.
    - Emoji: `w = 0.30 × 512`, `h = w × aspect`.
    - Decor: `w = piece.defaultWidth × 512`, `h = w × aspect`.
    - Drawing: the strokes' bounding box plus the largest `markerPx + 2 × edge`.
  - `render` quantises the scale to 1/64. The key is `(content, scaleBucket, radius)`; keep at most 48 entries with LRU eviction.
  - Text renders at the bucket scale. Images draw the source scaled to `(w, h)` into a fresh bitmap with filtering. Drawings draw the strokes relative to the bitmap centre: the ink edge first (width `markerPx + 2 × edge`, #1E2128), then the colour (width `markerPx`); quadratic smoothing through midpoints; round cap and join.
  - Silhouette: pad the rendered bitmap by `radius + 2` on every side, then `StickerRenderer.outlineOf(padded, radius)`. The result is white and centred on the same point as the content.
- **Scene renderer (`drawSticker` order, all in 512 space):**
  1. If the outline is on, one tint `Paint` (`PorterDuffColorFilter(colour, SRC_IN)`, alpha 102 for layers when `dimLayers`) draws: the subject silhouette at (0, 0); each layer's silhouette under the layer matrix; the live strokes stroked at `markerPx + 2 × edge + 2 × radius` in the outline colour.
  2. Behind layers.
  3. The subject.
  4. Front layers.
  5. Live strokes: the ink edge, then the colour.
  - The layer matrix is `translate(cx, cy) · rotate(rotation) · scale(±s, s) · translate(−bitmapW / 2, −bitmapH / 2)`, where `s = layer.scale / renderScale` and the sign is − when flipped.
  - `outlineRadius(t)` = `book.outlinePx[t] × 512 / 460`.
  - `renderStill` draws into a new transparent 512 ARGB bitmap.
  - `drawParticles`:
    - Hearts: `HeartPath.inBox(-size / 2, -size / 2, size)`.
    - Sparkle: the four-point star `M12 1c.7 6 5 10.3 11 11-6 .7-10.3 5-11 11-.7-6-5-10.3-11-11 6-.7 10.3-5 11-11z` in a 24 box, parsed with `androidx.core.graphics.PathParser.createPathFromPathData` and scaled to `size`. Colour #F5C542, then the same star at 40% size in white on top.
    - Each particle is translated to (x, y), scaled by `scale`, rotated by `rotation`, and drawn with alpha `alpha`.
  - `renderFrame` returns a new 512 bitmap: `concat(Matrix().apply { setValues(MotionMath.transform(preset, index / frames).matrixValues()) })`, draw `rest` with filtering, restore, then `drawParticles(MotionMath.particles(preset, index × durationMs / frames))`.
- **`StickerRenderer.maskedSubject(source, mask)`:** a new 512 bitmap holding `source` with `mask` applied through DST_IN, the same way as `drawComposite` today.

- [ ] **Step 1: Write the failing tests**

The house pattern is `@RunWith(RobolectricTestRunner::class) @GraphicsMode(GraphicsMode.Mode.NATIVE) @Config(sdk = [34], application = android.app.Application::class)`. Fonts come from `File("src/main/res/font/…")` and `File("src/main/assets/fonts/lettering_caveat.ttf")` through `Typeface.createFromFile`, as `NamePackRenderTest.TestFonts` does. Assets come from `File("src/main/assets/$path").inputStream()`.

`TextLayerPainterTest` must cover:
- `scriptOfPicksTheStrongestScript`: "love you" → Latin, "love you سارة" → Arabic, "люблю" → Cyrillic, "प्यार" → Devanagari, "אוהב" → Hebrew.
- `fontsFollowMoodAndScript`: Classic → UI; Round/"أحبك" → BALOO_BHAIJAAN; Round with a Pashto letter "ځ" → SYSTEM_BOLD; Hand/"love" → CAVEAT; Hand/"أحبك" → MIRZA; Display/"प्यार" → YATRA; Display/"אוהב" → KARANTINA.
- `sizeGrowsWithTextAndWrapsToTwoLines`:
  - `baseSize("hi").w < baseSize("hello there").w`;
  - a 30-character phrase with spaces is taller than a one-word text (two lines);
  - `render(t, 2f).width` is 2 × `render(t, 1f).width` within 2 px.
- `longAndMixedScripts`:
  - "我爱你我爱你我爱你我爱你我爱你我爱你我爱你我爱你我爱你我爱你" (30 characters, no spaces) renders one line with more than 200 non-transparent pixels;
  - "love you سارة" in Sticker style renders with rose pixels (colour distance < 40 from #C23359) and white pixels.
- `bubbleHasWhiteFillAndInkBorder`: a Bubble render has an opaque white pixel near the centre-left of the bubble body and an ink (#1E2128 ± 30) pixel on its outer border.
- `stickerStyleHasAWhiteEdge`: the Sticker style has white pixels outside the rose glyph pixels (scan one row through the middle: white appears before rose).

`SceneRendererTest` covers the following; synthetic subject: a 200 px rose disc centred at (256, 256) as `subject`, `outlineOf(disc, r)` as its silhouette.
- `outlineWrapsLayersToo`: an emoji layer at (80, 80) scale 0.5; outline Thick, Ink. There are ink pixels just outside the emoji bounds (e.g. 2 px left of its left edge on the centre row); with the outline off, those pixels are transparent.
- `behindLayersHideUnderTheSubject`: a decor layer centred at (256, 256) with `behind = true`: the centre pixel equals the disc colour. With `behind = false` it differs.
- `dimmedLayersAreFortyPercent`: with `dimLayers = true`, an opaque layer pixel over transparent space has alpha 102 ± 3.
- `flippedLayerMirrors`: the decor `props-7.webp` (asymmetric arrow) rendered flipped equals the unflipped render mirrored about the layer centre (compare alpha sums of the left and right halves swapped, ± 5%).
- `liveStrokesHaveAnInkEdge`: a horizontal live stroke (Rose, M) from (100, 400) to (400, 400): the pixel at y = 400 is rose, and the pixel at y = 400 − 7 − 2 is ink.
- `framesMoveAndParticlesShow`:
  - `renderFrame(rest, heartbeat, 0)` has the same non-transparent bounding box as `rest` scaled by 0.88 about the centre (± 3 px);
  - frame at index ≈ 0.14 × 12 is wider than frame 0;
  - `renderFrame(rest, hearts, 5)` has rose pixels below y = 0.7 × 512 outside the disc.

- [ ] **Step 2: Run them and see them fail** (`--tests '…decor.TextLayerPainterTest' --tests '…decor.SceneRendererTest'`).

- [ ] **Step 3: Implement the five files and `maskedSubject`,** following the behaviour list above. Keep every class Android-light (Canvas, Bitmap, Paint, StaticLayout only) and free of Compose.

- [ ] **Step 4: Run the tests and see them pass.** Then `./gradlew --offline -q :app:testDebugUnitTest --tests 'com.piptechnologies.stickermaker.feature.*'` to confirm nothing else broke.

- [ ] **Step 5: Commit** (`Render layers, outlines, frames and particles for Create`, with the co-author line).

---

### Task 6: View-model editing integration

**Files:**
- Create: `K/feature/create/decor/EmojiTones.kt`
- Test: `T/feature/create/decor/EmojiTonesTest.kt`
- Modify: `K/feature/create/CreateSession.kt`, `K/feature/create/CreatePackViewModel.kt`, `K/feature/create/StickerRenderer.kt` (delete `drawCaption` and the `text`/`typeface` parameters of `drawComposite`/`renderComposite` once nothing calls them), `K/core/data/prefs/PrefsRepository.kt`

**Interfaces:**
- Consumes: Tasks 1–5.
- Produces:
  - **CreateSession.kt**
    - `enum class EditorTool { Auto, Brush, Erase, Add, Draw, Animate }` (Text removed).
    - `enum class AddTab { Text, Emoji, Stickers }`, `enum class LayerKind { Text, Emoji, Decor, Drawing }`, `enum class ToneState { Ready, Loading, Failed }`.
    - `@Immutable data class LayerUi(id: Long, kind: LayerKind, cx: Float, cy: Float, width: Float, height: Float, rotation: Float, flipped: Boolean, behind: Boolean)`
    - `@Immutable data class ToneCellUi(tone: SkinTone, state: ToneState, model: Any?)`
    - `@Immutable data class SkinPopoverUi(file: String, cells: List<ToneCellUi>)`
    - `CreateItemUi` gains `animated: Boolean`.
    - `CreateUiState` loses `activeText` and `activeOutlineOn`, and gains `addTab: AddTab = Text`, `emojiTab: String = "smileys"`, `recents: List<String> = emptyList()`, `skinPopover: SkinPopoverUi? = null`, `layers: List<LayerUi> = emptyList()`, `selectedLayerId: Long? = null`, `guideX: Boolean = false`, `guideY: Boolean = false`, `textValue: String = ""`, `textStyle: TextStyleId = Sticker`, `textColour: Int = DecorSpec.ROSE`, `textFont: FontMood = Round`, `drawColour: Int = DecorSpec.ROSE`, `drawSize: MarkerSize = M`, `preset: String = "none"`, `activeIsVideo: Boolean = false`, `outline: OutlineStyle = OutlineStyle()`, `playing: Boolean = false`, `dataReady: Boolean = false`.
    - `animatedPack` becomes `selected.any { it.isVideo || it.editor.state.animated }`.
  - **CreatePackViewModel.kt** public API for the UI:
    - `val data: DecorData?` (loaded on IO in `init`) and `val renderer: SceneRenderer?`
    - `fun activeScene(): SceneRenderer.Scene?`
    - Tools and view: `selectTool(EditorTool)`, `toggleZoom()`, `setBrush(Int)`
    - Canvas (canvas px): `canvasTap(x, y)`, `canvasDoubleTap(x, y)`, `beginLayerGesture(x, y): Boolean`, `layerDrag(dx, dy)`, `layerPinch(zoom, rotationDeg)`, `endLayerGesture()`, `beginHandleGesture(id: Long)`, `handleGesture(scale: Float, rotation: Float)`
    - Layer actions: `deleteLayer(id: Long)`, `duplicateSelected()`, `flipSelected()`, `toggleBehindSelected()`, `editTextLayer(id: Long)`
    - Add sheet: `setAddTab(AddTab)`, `setEmojiTab(String)`, `closeAddSheet()`, `setText(String)`, `setTextStyle(TextStyleId)`, `setTextColour(Int)`, `setTextFont(FontMood)`, `addEmoji(file: String, tone: SkinTone = Default)`, `openSkinTones(file: String)`, `closeSkinTones()`, `addDecor(file: String)`
    - Draw: `setDrawColour(Int)`, `setDrawSize(MarkerSize)`, `beginMarker(x, y)`, `extendMarker(x, y)`, `endMarker()`
    - Animate: `setPreset(String)`, `togglePlaying()`, `stopPlaying()`
    - Outline: `setOutlineOn(Boolean)`, `setOutlineThickness(OutlineThickness)`, `setOutlineColour(Int)`
    - Kept: `undo()`, `selectSticker(Int)`, and the brush-stroke functions.
  - **PrefsRepository**: `val emojiRecents: Flow<List<String>>` and `suspend fun pushEmojiRecent(file: String)`. Most recent first, deduplicated, at most 18, stored as a `\n`-joined `stringPreferencesKey("emoji_recents")`.
  - **EmojiTones**: `class EmojiTones(dir: File, fetch: (String) -> ByteArray? = ::httpGet)` with `fun cached(item, tone): File?` and `suspend fun ensure(item, tone): File?` (runs on the caller's dispatcher; the VM calls it on IO).

**Wiring rules** (spec §4, §8, §9):
- **MediaItem:**
  - Gets `val editor: DecorEditor`, built with `sizeOf = { cache.baseSize(it) }`, `subjectBox = { maskBounds(mask) }` (alpha > 128 bounding box in 512 space) and `newId` from a VM counter.
  - Gets `var subject: Bitmap?` and `var silhouette: Bitmap?`.
  - Loses `text`, `outlineOn` and `outline`.
- **`refreshDerived(item)`** computes on Default:
  - `subject = maskedSubject(source, mask)`;
  - `silhouette = outlineOf(mask, renderer.outlineRadius(state.outline.thickness))`;
  - `stickerThumb = thumbOf(renderer.renderStill(scene))`.
  It re-runs after mask edits, after thickness changes and after any decor change (debounced 150 ms, like today's text debounce).
- **`selectTool(t)`:**
  - Leaving Draw calls `flushLiveStrokes()` first.
  - Add while Add → `closeAddSheet()`.
  - Brush, Erase and Draw deselect.
  - Auto keeps today's re-run: `runCutout(item, resetStrokes = true)`, followed by `editor.clearMaskHistory()`.
- **Brush/Erase:** each ended mask stroke calls `editor.recordMaskStroke()`. `undo()` → `editor.undo()`; on `UndoResult.MaskStroke`, remove the last mask stroke and rebuild the mask as today; on `Nothing`, show the existing toast.
- **Canvas:**
  - `canvasTap`: no-op in Brush, Erase and Draw. In Animate with reduced motion (the UI passes that flag through `togglePlaying`), toggle playing. Otherwise `editor.select(editor.hitTest(x, y))`, then `endTextSession`.
  - `canvasDoubleTap` on a text layer → `editTextLayer(id)`.
  - `beginLayerGesture` hit-tests: on a hit it calls `editor.beginGesture(id)` and returns true; on a miss it returns false and the UI falls back to view pan while zoomed.
- **Text:** `editTextLayer(id)` sets the tool to Add, the tab to Text, and calls `editor.editText(id)`. `closeAddSheet()` calls `editor.endTextSession()` and sets the tool to Auto.
- **Emoji:** `addEmoji` adds `LayerContent.Emoji(file, glyph, tone)` using the catalog glyph, sets the tool to Auto, closes the skin popover and calls `pushEmojiRecent(file)`. At the layer limit, show the `create_toast_layer_limit` toast with 8.
- **Skin tones:** `openSkinTones(file)` shows six cells: Default Ready with the asset model `"file:///android_asset/emoji/$file"`; the other five Loading unless cached. Then it launches `ensure` for each missing tone on IO: Ready (model = the file) or Failed. The first Failed shows `create_toast_skin_failed` once per popover.
- **Decor:** `addDecor(file)` adds `LayerContent.Decor(file, piece.emojis)` with `headTop = piece.headTop`, then sets the tool to Auto.
- **Presets:** `setPreset` is ignored for clips (`isVideo`). Changing the preset resets `playing = false`.
- **Rail:** `selectSticker(i)` flushes live strokes on the item being left and deselects.
- **`push()`** maps the active editor into `layers` (`LayerUi` from `editor.renderedSize(layer)`), plus `selectedLayerId`, guides, text fields, `preset`, `outline`, `canUndo = editor.canUndo`, and `CreateItemUi.animated`.

- [ ] **Step 1: Write the failing `EmojiTonesTest`** (plain JUnit with a temp dir and a fake `fetch`):

```kotlin
package com.piptechnologies.stickermaker.feature.create.decor

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EmojiTonesTest {
    private val wave = EmojiItem("Waving hand", "1F44B", "👋", "waving_hand.webp", true)
    private val dir: File = Files.createTempDirectory("tones").toFile()

    @Test
    fun downloadsOnceThenServesTheCache() = runBlocking {
        var calls = 0
        val tones = EmojiTones(dir) { url -> calls++; assertEquals(EmojiCatalog.toneUrl(wave, SkinTone.Dark), url); byteArrayOf(1, 2, 3) }
        val f = tones.ensure(wave, SkinTone.Dark)!!
        assertEquals("waving_hand_dark.png", f.name)
        assertEquals(f, tones.ensure(wave, SkinTone.Dark))
        assertEquals(1, calls)
        assertEquals(f, tones.cached(wave, SkinTone.Dark))
    }

    @Test
    fun failureReturnsNullAndCachesNothing() = runBlocking {
        val tones = EmojiTones(dir) { null }
        assertNull(tones.ensure(wave, SkinTone.Light))
        assertNull(tones.cached(wave, SkinTone.Light))
        assertEquals(0, dir.listFiles()!!.size)
    }
}
```

`EmojiTones` writes to a `.part` file, then renames it. `httpGet` uses `HttpURLConnection` with 8 s connect and read timeouts, accepts only HTTP 200 and at most 512 KB, and returns null on any exception.

- [ ] **Step 2: Run it and see it fail;** implement `EmojiTones.kt`; see it pass.
- [ ] **Step 3: Add the PrefsRepository recents** (the pattern in the file: a key in the companion, a `Flow` with `.distinctUntilChanged()`, and a suspend setter with `dataStore.edit`).
- [ ] **Step 4: Rework `CreateSession.kt` and `CreatePackViewModel.kt`** per the rules above.
  - Inject `PrefsRepository` into the constructor.
  - Build `DecorData` with `DecorAssets.android`, `DecorFonts.android`, `TextLayerPainter` (`rtlLanguage = { AppLanguages.byTag(currentTag)?.rtl == true }`, with the tag read the same way `NamePackViewModel` reads it), `LayerRenderCache` and `SceneRenderer`, all on IO in `init`, then set `dataReady = true`.
  - Delete `setStickerText` and the old caption state.
- [ ] **Step 5: Make it compile and keep the old UI working minimally.** `CreateEditorScreen` still draws with the old `drawComposite`, so switch its draw call to `viewModel.renderer?.drawSticker(nc, scene)` and map `activeOutlineOn` to `state.outline.on`. Then run `./gradlew --offline -q :app:compileDebugKotlin :app:testDebugUnitTest`. Expected: builds, and all unit tests pass.
- [ ] **Step 6: Commit** (`Drive the Create editor's layers, tools and undo from the view model`, with the co-author line).

---

### Task 7: Export integration

**Files:**
- Create: `K/feature/create/decor/StickerExporter.kt`
- Test: `T/feature/create/decor/StickerExporterTest.kt`
- Modify: `K/feature/create/CreatePackViewModel.kt` (the `exportPack` / `encode*` functions)

**Interfaces:**
- Consumes: `SceneRenderer`, `MotionBook`, `MotionMath`, `StickerRenderer.{renderExportCanvas, encodeWebp, encodeStaticSticker, encodeTrayPng, thumbOf}`, `AnimatedWebpMuxer.mux`, `WebpInfo.parse`.
- Produces: `class StickerExporter(renderer: SceneRenderer)` with:
  - `fun staticSticker(scene): ByteArray`: `renderStill` → `renderExportCanvas` → `encodeStaticSticker`, ≤ 100 KB.
  - `fun presetSticker(scene, preset): ByteArray`:
    - The rest frame is `renderStill(scene)`; frames `i in 0 until preset.frames` come from `renderFrame(rest, preset, i)` → `renderExportCanvas`.
    - Durations are `MotionMath.frameDurations(preset)`.
    - Try qualities 60, 50, 42, 35, 30. If none fits 500 KB, take every other frame (durations summed in pairs, a last odd one added to the final pair) and retry. Stop at 4 frames and return the smallest attempt.
  - `fun stillInAnimatedPack(scene): ByteArray`: two identical frames of 500 ms, qualities 80 → 30 (today's rule).
  - `fun clipSticker(frames: List<SceneRenderer.Scene>, totalDurationMs: Long): ByteArray`: today's video path, each frame going through `renderStill`, trying frame counts `[n, 8, 6]` × qualities.
  - `fun tray(scene): ByteArray`: `encodeTrayPng(renderStill(scene))`.

**Rules to keep in `exportPack`:**
- `packAnimated = stickers.any { it.isVideo || it.editor.state.animated }`.
- Per sticker:
  - clip → `clipSticker`, with one scene per decoded frame: the subject masked by that frame's segmentation plus the item's mask strokes; the silhouette from that frame's mask; the item's `editor.state` layers;
  - else if packAnimated and `state.animated` → `presetSticker(scene, motion.byId(state.preset))`;
  - else if packAnimated → `stillInAnimatedPack`;
  - else → `staticSticker`.
- Emoji tags per sticker: `emojiTags(item.editor.state, DEFAULT_EMOJIS)`, passed both to `ValidatableSticker` and to `saveOwnPack(stickers = fileNames.zip(tags))`.
- Tray: `tray(sceneOf(trayItem))` (frame 0 / rest).

- [ ] **Step 1: Write the failing test** (Robolectric NATIVE). Build a scene from a 400 px rose disc subject plus one decor layer:
  - `staticIsStillAndSmall`: `WebpInfo.parse(bytes)`: static, ≤ 100 KB, 512 × 512.
  - `presetHasItsFramesAndDuration`: heartbeat → `frameCount == 12`, `totalDurationMs == 1000`, ≤ 500 KB.
  - `stillInAnimatedPackIsTwoFrames`: `frameCount == 2`.
  - `noisyPresetStaysUnderLimit`: subject = 512 × 512 random-noise RGB (seeded `Random(7)`), fully opaque mask, sparkle (16 frames) → ≤ 500 KB and `frameCount >= 4`.
  - `trayIsA96PngUnder50Kb`.
- [ ] **Step 2: See it fail;** implement `StickerExporter`; see it pass.
- [ ] **Step 3: Route `exportPack` through `StickerExporter`** per the rules. Keep `StickerPackValidator.verifyStickerPackValidity` as today.
- [ ] **Step 4: Run** `./gradlew --offline -q :app:testDebugUnitTest`. All pass, including `StickerPackValidatorTest`.
- [ ] **Step 5: Commit** (`Export decorated and animated stickers`, with the co-author line).

---

### Task 8: Editor UI: tool bar, canvas, overlay, gestures

**Files:**
- Create: `K/core/ui/ReduceMotion.kt` (move `rememberReduceMotion` from `feature/namepack/NamePackUi.kt`; update its two call sites' imports), `K/feature/create/editor/EditorToolbar.kt`, `K/feature/create/editor/EditorCanvas.kt`, `K/feature/create/editor/LayerOverlay.kt`
- Modify: `K/feature/create/editor/CreateEditorScreen.kt`

**Interfaces:**
- Consumes: Task 6's VM API and state; `LoveIcons.{Wand2, Brush, Eraser, SmilePlus, PenLine, CirclePlay, ZoomIn, ZoomOut, Copy, FlipHorizontal, SendToBack, BringToFront, Trash2, X, Pencil?}`. There is no Pencil icon: use `LoveIcons.Type` for the edit handle, or add Lucide `pencil` from `design/lucide/pencil.svg` with the same `lucideIcon` pattern.
- Produces: the composables `EditorToolbar(tool, onTool)`, `EditorCanvasCard(state, viewModel)` and `LayerOverlay(...)`, used by `CreateEditorScreen`.

**Build to spec §8.** Key implementation points:

- **Tool bar label fallback:**

```kotlin
@Composable
internal fun EditorToolbar(tool: EditorTool, onTool: (EditorTool) -> Unit, modifier: Modifier = Modifier) {
    val tools = listOf(
        Triple(EditorTool.Auto, R.string.create_tool_auto, LoveIcons.Wand2),
        Triple(EditorTool.Brush, R.string.create_tool_brush, LoveIcons.Brush),
        Triple(EditorTool.Erase, R.string.create_tool_erase, LoveIcons.Eraser),
        Triple(EditorTool.Add, R.string.create_tool_add, LoveIcons.SmilePlus),
        Triple(EditorTool.Draw, R.string.create_tool_draw, LoveIcons.PenLine),
        Triple(EditorTool.Animate, R.string.create_tool_animate, LoveIcons.CirclePlay)
    )
    val labels = tools.map { stringResource(it.second) }
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Ink).padding(8.dp)) {
        val slotPx = with(LocalDensity.current) { ((maxWidth - 4.dp * 5) / 6 - 4.dp).toPx() }
        fun widest(sp: Float) = labels.maxOf {
            measurer.measure(it, TextStyle(fontFamily = Hanken, fontWeight = FontWeight.W600, fontSize = sp.sp), maxLines = 1, softWrap = false).size.width
        }
        val labelSp = remember(labels, slotPx) { when { widest(10f) <= slotPx -> 10f; widest(9.5f) <= slotPx -> 9.5f; else -> 0f } }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            tools.forEachIndexed { i, (t, _, icon) ->
                ToolButton(labels[i], icon, active = tool == t, labelSp = labelSp, modifier = Modifier.weight(1f)) { onTool(t) }
            }
        }
    }
}
```

`ToolButton` keeps today's look (54 dp, radius 11, Rose when active, #C3C9D2 idle, 20 dp icon, 3 dp gap). When `labelSp == 0f` it shows no label, but `onClickLabel` and the content description stay the label.

- **Canvas card (`EditorCanvasCard`):**
  - Today's card, with the zoom button (36 dp, `Modifier.align(Alignment.TopEnd).padding(top = 10.dp, end = 10.dp)`; `TopEnd` mirrors in RTL) → `viewModel.toggleZoom()`.
  - The hint line only when `state.selectedLayerId == null`. Otherwise the action pill at the bottom centre (spec §8 measurements) calls `duplicateSelected`, `flipSelected`, `toggleBehindSelected` and `deleteLayer(id)`.
  - Drawing in the `Canvas`:

```kotlin
drawIntoCanvas { c ->
    val nc = c.nativeCanvas
    nc.save(); nc.translate(panX, panY); nc.scale(scale, scale, edge / 2f, edge / 2f); nc.scale(s0, s0)
    val scene = viewModel.activeScene()
    if (scene != null && state.activeCut == CutStatus.Done) {
        drawChecker(nc, checkerPaint)
        val preset = viewModel.data?.motion?.byId(state.preset)
        val animating = preset != null && !preset.isNone && (!reduceMotion || state.playing)
        if (animating) { nc.save(); nc.concat(Matrix().apply { setValues(MotionMath.transform(preset!!, (timeMs % preset.durationMs) / preset.durationMs).matrixValues()) }) }
        viewModel.renderer!!.drawSticker(nc, scene, dimLayers = state.tool == EditorTool.Brush || state.tool == EditorTool.Erase)
        if (animating) { nc.restore(); viewModel.renderer!!.drawParticles(nc, MotionMath.particles(preset!!, timeMs % preset.durationMs)) }
    } else scene?.let { /* raw source while cutting, as today */ }
    nc.restore()
}
```

- **Animation clock:**

```kotlin
var timeMs by remember { mutableFloatStateOf(0f) }
val reduceMotion = rememberReduceMotion()
LaunchedEffect(state.preset, state.activeIndex, state.playing, reduceMotion) {
    timeMs = 0f
    val preset = viewModel.data?.motion?.byId(state.preset) ?: return@LaunchedEffect
    if (preset.isNone || (reduceMotion && !state.playing)) return@LaunchedEffect
    val start = withFrameMillis { it }
    while (true) {
        val elapsed = (withFrameMillis { it } - start).toFloat()
        if (reduceMotion && elapsed >= preset.durationMs) { timeMs = 0f; viewModel.stopPlaying(); break }
        timeMs = elapsed
    }
}
```

  Reading `timeMs` and `state.editorTick` inside the draw lambda invalidates the canvas.

- **Gestures** (one `pointerInput(state.tool, state.zoomed, state.activeIndex)` block, which replaces today's two):

```kotlin
awaitEachGesture {
    val down = awaitFirstDown(requireUnconsumed = false)
    val start = viewToImage(down.position)
    when (state.tool) {
        EditorTool.Brush, EditorTool.Erase -> brushGesture(down, start)          // today's begin/extend/end + tap-to-dab, moved here
        EditorTool.Draw -> {
            viewModel.beginMarker(start.x, start.y)
            drag(down.id) { change -> change.consume(); viewToImage(change.position).let { viewModel.extendMarker(it.x, it.y) } }
            viewModel.endMarker()
        }
        else -> {
            val onLayer = viewModel.beginLayerGesture(start.x, start.y)
            var moved = false
            var pointerCount = 1
            do {
                val event = awaitPointerEvent()
                pointerCount = event.changes.count { it.pressed }
                val pan = event.calculatePan()
                val zoom = event.calculateZoom()
                val rotation = event.calculateRotation()
                if (pan != Offset.Zero || zoom != 1f || rotation != 0f) moved = true
                if (onLayer) {
                    val k = 1f / (s0 * scale)                                   // view px → canvas px
                    viewModel.layerDrag(pan.x * k, pan.y * k)
                    if (pointerCount > 1) viewModel.layerPinch(zoom, rotation)
                    event.changes.forEach { it.consume() }
                } else if (state.zoomed) {
                    scale = (scale * zoom).coerceIn(1f, 4f)
                    val maxPan = (scale - 1f) * size.width / 2f
                    panX = (panX + pan.x).coerceIn(-maxPan, maxPan); panY = (panY + pan.y).coerceIn(-maxPan, maxPan)
                }
            } while (event.changes.any { it.pressed })
            if (onLayer) viewModel.endLayerGesture()
            if (!moved) {
                val now = SystemClock.uptimeMillis()
                if (now - lastTapAt < 300 && (lastTapPos - down.position).getDistance() < 24.dp.toPx()) viewModel.canvasDoubleTap(start.x, start.y)
                else viewModel.canvasTap(start.x, start.y)
                lastTapAt = now; lastTapPos = down.position
            }
        }
    }
}
```

  `lastTapAt` and `lastTapPos` are `remember`ed vars. `viewToImage` is today's helper.

- **`LayerOverlay`:**
  - Draws in view px: `canvasToView(p) = (p × s0 − centre) × scale + centre + pan`.
  - For the selected `LayerUi`, a `Canvas` draws the dashed Rose box: 2 dp stroke, dash 6 / 4, rotated by `rotation` about the centre. The box is `(width, height)` × `s0 × scale`, grown by 6 dp on each side, with 1.5 dp white hairlines inside and outside.
  - Guides: `state.guideX` draws the vertical 1.5 dp dashed Rose line at the canvas centre; `guideY` draws the horizontal one.
  - Handles are `Box(Modifier.offset { corner } .size(44.dp))` holding a 22 dp visual:
    - The corners are the rotated box corners.
    - Delete sits at top-start and edit (text only) at top-end, both calling `viewModel.deleteLayer(id)` / `viewModel.editTextLayer(id)`.
    - Resize-and-rotate sits at bottom-end, with its own `pointerInput`. On drag start it calls `beginHandleGesture(id)` and records `d0 = dist(finger, centre)`, `a0 = atan2(finger − centre)`, `s0 = layer scale` and `r0 = rotation`. On each move it calls `handleGesture(s0 × d / d0, r0 + deg(a − a0))`; on end, `endLayerGesture()`.
    - In RTL (`LocalLayoutDirection.current == Rtl`), start and end swap.
  - Content descriptions: `create_action_delete`, `create_handle_edit`, `create_handle_transform`.
  - The box and handles fade in over 120 ms (`AnimatedVisibility(fadeIn(tween(120)))`).

- **Hints:** Auto, Brush and Erase keep today's texts. Add → `create_hint_add`. Draw → `create_hint_draw`. Animate → `create_hint_animate`, or `create_hint_animate_clip` when `activeIsVideo`. Zoomed → `create_hint_zoomed`.

- [ ] **Step 1:** Move `rememberReduceMotion` to `core/ui/ReduceMotion.kt` (`internal` becomes `fun`). Build.
- [ ] **Step 2:** Write `EditorToolbar.kt`, `EditorCanvas.kt` and `LayerOverlay.kt`, and use them from `CreateEditorScreen` (the rows under the bar stay as they are until Task 9).
- [ ] **Step 3:** `./gradlew --offline -q :app:compileDebugKotlin :app:lintDebug`. Expected: builds, lint has 0 errors.
- [ ] **Step 4:** Run the whole unit suite (`:app:testDebugUnitTest`). Expected: passes.
- [ ] **Step 5: Commit** (`Add the six-tool bar, zoom button, layer overlay and canvas gestures`, with the co-author line).

---

### Task 9: Editor UI: Draw row, Animate strip, Outline row, rail

**Files:**
- Create: `K/feature/create/editor/EditorRows.kt`
- Modify: `K/feature/create/editor/CreateEditorScreen.kt` (rows under the bar, outline row, rail tile)

**Interfaces:**
- Consumes: the Task 6 state (`drawColour`, `drawSize`, `preset`, `activeIsVideo`, `playing`, `outline`, `items[].animated`), `viewModel.data` (`styles.colours`, `styles.outlineColours`, `motion.presets`), and `rememberReduceMotion()`.
- Produces: `DrawRow`, `AnimateStrip`, `OutlineRow` (replaces today's), `Swatch` (22/24 dp with the selected ring), and `RailTile` with the ANIM badge.

**Build to spec §8 "Rows under the bar", "Outline row" and "Rail"**, with the exact dp and sp values listed there.
- **Swatch accessibility labels:** `create_colour_{white,ink,rose,peach,gold,mint,sky,violet}`, matched on the id from `text-styles.json`. Outline colours reuse `white, ink, rose, gold, sky`.
- **Animate tiles:**
  - Each tile draws the active sticker's `stickerThumb` (`ImageBitmap`) inside a 44 dp box.
  - The box uses `graphicsLayer` with the preset's transform at the tile's own time: set `scaleX = pose.scaleX × base`, `scaleY = pose.scaleY × base`, `rotationZ = pose.rotate`, `translationX = pose.dx × size`, `translationY = pose.dy × size`, and `transformOrigin = TransformOrigin(pivotX, pivotY)`. `graphicsLayer` applies the base scale about the pivot rather than the centre, which is close enough for a 44 dp preview.
  - Particle presets draw their particles at 44 dp through `MotionMath.particles(..., canvas = sizePx)` in a small `Canvas`.
  - One shared clock drives all tiles; with reduced motion they are still.
  - Labels: `create_preset_<id>`.
  - On a clip the strip is at 40% opacity with click handling off, and the note is `create_animate_note_clip`.
  - Otherwise the note is `create_animate_note`, prefixed with `create_animate_note_reduced` + " " when motion is reduced.
- **Rail badge:** only when `item.animated || item.isVideo`, using `stringResource(R.string.create_rail_anim)`.
- **Outline row:** it replaces the old White outline row. The switch calls `setOutlineOn`; the thickness control and swatches call `setOutlineThickness` / `setOutlineColour`.

- [ ] **Step 1:** Implement `EditorRows.kt` and wire it into the screen (a `when (state.tool)` under the bar: Brush/Erase → the brush row, Draw → `DrawRow`, Animate → `AnimateStrip`).
- [ ] **Step 2:** `:app:compileDebugKotlin :app:lintDebug :app:testDebugUnitTest` all green.
- [ ] **Step 3: Commit** (`Add the Draw row, Animate presets strip, outline styles and rail badge`, with the co-author line).

---

### Task 10: Add sheet

**Files:**
- Create: `K/feature/create/editor/AddSheet.kt`
- Modify: `K/feature/create/editor/CreateEditorScreen.kt` (host the sheet over the footer; `BackHandler(enabled = state.tool == EditorTool.Add) { viewModel.closeAddSheet() }`)

**Interfaces:**
- Consumes: the Task 6 state (`addTab`, `emojiTab`, `recents`, `skinPopover`, `textValue`, `textStyle`, `textColour`, `textFont`); `viewModel.data` (`emoji`, `decor`, `styles`, `phrases`); `QuickPhrases.forLocale(LocalConfiguration.current.locales[0].language, …country)`; `TextLayerPainter` for the live "Aa" samples, rendered to an `ImageBitmap` of the sample in each style with the current colour and font (cache them with `remember(textColour, textFont)`).
- Produces: `AddSheet(state, viewModel, modifier)`.

**Build to spec §9.** Key points:
- **Container:**
  - `AnimatedVisibility(visible = state.tool == EditorTool.Add, enter = slideInVertically(tween(280, easing = CubicBezierEasing(.2f, .7f, .2f, 1f))) { it }, exit = slideOutVertically(tween(200, easing = FastOutLinearInEasing)) { it })`, aligned `BottomCenter` in the screen's root `Box`.
  - `Modifier.imePadding().heightIn(max = 442.dp)`, with no scrim.
  - The drag handle closes the sheet on a downward drag over 40 dp.
- **Tabs:** a row modelled on `core/design/components/SegmentedControl` (spec §9 measurements; reuse `SegmentedControl` if it matches them, else a local copy). Tab content crossfades over 120 ms.
- **Text tab:**
  - One `BasicTextField(value = state.textValue, onValueChange = viewModel::setText, maxLines = 2)` with the counter `stringResource(R.string.create_name_counter, len, 30)`, placeholder `create_text_placeholder`, `ImeAction.Done` → `LocalFocusManager.clearFocus()`.
  - Phrases: `LazyRow` chips.
  - Styles, swatches and fonts show only when `!WindowInsets.isImeVisible` (`@OptIn(ExperimentalLayoutApi::class)`).
  - Font chips render in their own face: `FontFamily(Typeface)` from `DecorFonts.face(...)` for BALOO, CAVEAT and LILITA.
- **Emoji tab:**
  - The Love `LazyRow` of 40 dp `AsyncImage("file:///android_asset/emoji/<file>")`.
  - A chip row: `EmojiCatalog.CATEGORIES`, plus `recent` last when `state.recents.isNotEmpty()`.
  - `LazyVerticalGrid(GridCells.Fixed(6))` inside `Modifier.heightIn(max = 240.dp)`. Items use `combinedClickable(onClick = { addEmoji(file) }, onLongClick = { if (item.skinTones) openSkinTones(file) }, onClickLabel = glyph)`.
  - The skin popover is an overlay `Box` 58 dp above the grid, start-aligned, with its 12 dp tail at the pressed item's x. It holds six 38 dp `AsyncImage`s (a `CircularProgressIndicator` of 14 dp while Loading, 40% alpha and disabled when Failed). Content descriptions are `create_skin_*`. A tap outside calls `closeSkinTones()`.
- **Stickers tab:** a `LazyVerticalGrid(GridCells.Fixed(4))` with full-span headers (`create_decor_doodles` and `create_decor_props`, uppercase mono) and tiles of `AsyncImage("file:///android_asset/decor/<file>")`. The content description is the piece `label`.

- [ ] **Step 1:** Implement `AddSheet.kt` and host it.
- [ ] **Step 2:** `:app:compileDebugKotlin :app:lintDebug :app:testDebugUnitTest` all green.
- [ ] **Step 3: Commit** (`Add the Text, Emoji and Stickers sheet`, with the co-author line).

---

### Task 11: Pack details line and Settings › Licences

**Files:**
- Modify: `K/feature/create/details/CreatePackDetailsScreen.kt` (`InfoCard`: `create_info_body_animated` when `animated`), `K/feature/settings/SettingsScreen.kt` (About row plus both previews), `K/navigation/AppNavHost.kt` (`Routes.LICENCES = "licences"`)
- Create: `K/feature/settings/LicencesScreen.kt`

**Interfaces:**
- Produces: `@Composable fun LicencesScreen(onBack: () -> Unit)`, a new `onLicences: () -> Unit` parameter on `SettingsScreen` and `SettingsContent`, and `SettingsIcons`-style use of `LoveIcons.Scale`.

**Build to spec §10–§11.**
- **Licences screen:** `LoveTopBar(title = stringResource(R.string.settings_licences), onBack, height = 52.dp)` + `windowInsetsPadding(WindowInsets.systemBars)` as `ContactScreen` does. Then the intro and one card with three rows built from the `licences_*` strings.
- **Settings row:** between More apps and Privacy policy: `SettingsRow(icon = LoveIcons.Scale, label = stringResource(R.string.settings_licences), onClick = onLicences)`.
- **Route:** `composable(Routes.LICENCES) { LicencesScreen(onBack = { navController.popBackStack() }) }`, and `onLicences = { navController.navigate(Routes.LICENCES) }` in the Settings entry.

- [ ] **Step 1:** Implement.
- [ ] **Step 2:** `:app:compileDebugKotlin :app:lintDebug :app:testDebugUnitTest` all green (`LocalizedFormattingTest` formats the new strings).
- [ ] **Step 3: Commit** (`Say 500 KB for animated packs and add Settings › Licences`, with the co-author line).

---

### Task 12: Screen tour and full verification

**Files:**
- Modify: `app/src/androidTest/kotlin/com/piptechnologies/stickermaker/tour/ScreenTourTest.kt` (`t06_create`)

`t06_create` currently taps `hasClickLabel("Text")` and then types into the only editable field. Change it to:
1. `tap(hasClickLabel("Add"))`.
2. Wait for the text "Add a word or two".
3. `onNode(hasSetTextAction()).performTextInput("miss u")`.
4. `tap(hasClickLabel("Add"))` again to close the sheet.
5. Continue to "Next · n stickers" as before.

Keep the Brush steps unchanged.

- [ ] **Step 1:** Update `t06_create`; compile androidTest: `./gradlew --offline -q :app:compileDebugAndroidTestKotlin`.
- [ ] **Step 2: Full verification**, each from the worktree:
  - `./gradlew --offline :app:testDebugUnitTest`: all green; record the test count.
  - `./gradlew --offline :app:lintDebug`: 0 errors.
  - `./gradlew --offline :app:assembleDebug`: the APK builds; record its size against the parent commit's APK.
- [ ] **Step 3: Commit** (`Follow the Add tool in the screen tour`, with the co-author line).

The device walk (spec §12, every frame and every button, stopping before WhatsApp's own sheet) is done by the controller after the final review, on the Pixel_9_Pro_XL AVD. Stop Gradle first (`./gradlew --stop`).
