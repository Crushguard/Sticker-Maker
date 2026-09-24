package com.piptechnologies.stickermaker.l10n

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/** One strings.xml: plain strings and plurals by name, plus the names marked untranslatable. */
internal data class StringsFile(
    val strings: Map<String, String>,
    val plurals: Map<String, Map<String, String>>,
    val untranslatable: Set<String>
)

/** Reads the app's string resources straight from src/main/res. */
internal object L10nFixtures {

    val resDir: File by lazy {
        // Gradle runs unit tests from the module directory; also accept the repository root.
        listOf(File("src/main/res"), File("app/src/main/res")).firstOrNull { it.isDirectory }
            ?: error("src/main/res not found from ${File(".").absolutePath}")
    }

    /** Resource folder qualifier for a BCP-47 tag: "id" -> "in", "he" -> "iw", "pt-BR" -> "pt-rBR". */
    fun folderFor(tag: String): String {
        val parts = tag.split('-')
        val language = when (parts[0]) {
            "id" -> "in"
            "he" -> "iw"
            else -> parts[0]
        }
        return if (parts.size > 1) "values-$language-r${parts[1]}" else "values-$language"
    }

    fun source(): StringsFile = parse(File(resDir, "values/strings.xml"))

    fun translation(tag: String): StringsFile = parse(File(resDir, "${folderFor(tag)}/strings.xml"))

    fun parse(file: File): StringsFile {
        check(file.isFile) { "missing ${file.path}" }
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
        val strings = linkedMapOf<String, String>()
        val plurals = linkedMapOf<String, Map<String, String>>()
        val untranslatable = mutableSetOf<String>()
        val nodes = root.childNodes
        for (i in 0 until nodes.length) {
            val node = nodes.item(i) as? Element ?: continue
            val name = node.getAttribute("name")
            if (node.getAttribute("translatable") == "false") untranslatable += name
            when (node.tagName) {
                "string" -> strings[name] = node.textContent
                "plurals" -> {
                    val items = node.getElementsByTagName("item")
                    plurals[name] = (0 until items.length).associate { j ->
                        val item = items.item(j) as Element
                        item.getAttribute("quantity") to item.textContent
                    }
                }
            }
        }
        return StringsFile(strings, plurals, untranslatable)
    }

    private val SPEC = Regex("""%(?:%|(\d+\$)?[-#+ 0,(]*\d*(?:\.\d+)?[a-zA-Z])""")

    /** Format specifiers in [text] (without %%); throws on a lone % that String.format would reject. */
    fun specs(text: String): List<String> {
        val found = mutableListOf<String>()
        var index = 0
        while (true) {
            val at = text.indexOf('%', index)
            if (at < 0) return found
            val match = SPEC.matchAt(text, at) ?: throw IllegalArgumentException("stray % in \"$text\"")
            if (match.value != "%%") found += match.value
            index = match.range.last + 1
        }
    }

    /**
     * Plural categories each language must provide for integers (CLDR, as Android's ICU uses
     * them); "many" in es/fr/it/pt only covers millions and is optional there.
     */
    val requiredPlurals: Map<String, Set<String>> = mapOf(
        "ar" to setOf("zero", "one", "two", "few", "many", "other"),
        "ru" to setOf("one", "few", "many", "other"),
        "he" to setOf("one", "two", "other"),
        "id" to setOf("other"),
        "my" to setOf("other"),
        "zh" to setOf("other")
    ).withDefault { setOf("one", "other") }

    val allowedPlurals: Map<String, Set<String>> = mapOf(
        "es" to setOf("one", "many", "other"),
        "fr" to setOf("one", "many", "other"),
        "it" to setOf("one", "many", "other"),
        "pt" to setOf("one", "many", "other"),
        "pt-BR" to setOf("one", "many", "other"),
        "he" to setOf("one", "two", "many", "other")
    )

    /** Categories that stand for a single number, so their text may spell it out instead of %d. */
    val exactPlurals: Map<String, Set<String>> = mapOf(
        "ar" to setOf("zero", "one", "two"),
        "he" to setOf("one", "two")
    )
}
