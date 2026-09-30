package com.piptechnologies.stickermaker.feature.create.decor

import android.content.res.AssetManager
import kotlin.math.roundToInt
import org.json.JSONArray
import org.json.JSONObject

/** Scripts that choose a lettering font. The font follows the string, never the UI language. */
enum class Script(val key: String) { Latin("latin"), Cyrillic("cyrillic"), Arabic("arabic"), Devanagari("devanagari"), Hebrew("hebrew") }

/** Text style presets for different lettering designs. */
enum class TextStyleId(val key: String) {
    Classic("classic"), Sticker("sticker"), Stroke("stroke"), Bubble("bubble");
    companion object { fun of(key: String) = entries.first { it.key == key } }
}

/** Emotional expression of typeface family selection. */
enum class FontMood(val key: String) {
    Round("round"), Hand("hand"), Display("display");
    companion object { fun of(key: String) = entries.first { it.key == key } }
}

/** Die-cut outline thickness (Thin, Medium, Thick = 5, 8, 12 px); the outline wraps the subject and every layer. */
enum class OutlineThickness(val key: String) {
    Thin("thin"), Medium("medium"), Thick("thick");
    companion object { fun of(key: String) = entries.first { it.key == key } }
}

/** Marker pen stroke width levels. */
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

/** Manages parsed decoration pieces from assets. */
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

/** Shadow parameters in px at 512 for the 52 px default text size. */
data class ShadowSpec(val dx: Float, val dy: Float, val blur: Float, val colour: Int)

/** Chat bubble parameters in px at 512 for the 52 px default text size. */
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

/** Named color palette entry. */
data class NamedColour(val id: String, val argb: Int)

/** "Baloo 2 800" → family "Baloo 2", weight 800. */
data class FontRef(val family: String, val weight: Int)

/** Complete text styling system with colors, fonts, and effects. */
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

/** Animation timing curve types. */
enum class Easing {
    Linear, EaseIn, EaseOut, EaseInOut;
    companion object {
        fun of(s: String?): Easing = when (s) { "ease-in" -> EaseIn; "ease-out" -> EaseOut; "ease-in-out" -> EaseInOut; else -> Linear }
    }
}

/** Animation frame: `t` is loop progress 0..1, `rotate` in degrees, `dx`/`dy` as canvas fractions, easing from previous frame. */
data class Keyframe(val t: Float, val scaleX: Float, val scaleY: Float, val rotate: Float, val dx: Float, val dy: Float, val easing: Easing)

/** Particle effect types for animations. */
enum class ParticleKind { Hearts, Sparkle }

/** Particle effect configuration. */
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

/** Motion presets catalog; unknown id returns fallback "none" preset. */
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
