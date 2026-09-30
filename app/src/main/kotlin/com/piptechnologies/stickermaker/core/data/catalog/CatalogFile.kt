package com.piptechnologies.stickermaker.core.data.catalog

import com.piptechnologies.stickermaker.core.model.Category
import com.piptechnologies.stickermaker.core.model.StickerPack
import org.json.JSONArray
import org.json.JSONObject

/** One published catalog: categories in chip order, packs in rank order. */
data class Catalog(
    val version: Int,
    val categories: List<Category>,
    val packs: List<StickerPack>,
)

/**
 * Reads the catalog file (public/catalog/v<k>.json.gz, schema 1) the stickermaker functions publish. Unknown
 * fields are ignored; a pack without id, name or zip is skipped. Throws on JSON that is not a catalog.
 *
 * The words people type for each pack tag and lettering language, by app language ("tags" and "languages"), come in
 * their own file, named by its content in the catalog's "words" (they change far less often than the packs); older
 * catalogs carried them inline. Each pack's keywords get the words of its own, so search needs nothing else.
 */
object CatalogFile {

    private const val DEFAULT_PUBLISHER = "PIP Technologies"
    private const val DEFAULT_HUE = 340

    /** The path of the catalog's words file, or null for a catalog that carries its words inline (or none). */
    fun wordsPath(json: String): String? = JSONObject(json).optJSONObject("words")?.optString("path")?.ifBlank { null }

    /** [words] is the content of the catalog's words file ([wordsPath]); without it, words inline in [json] count. */
    fun parse(json: String, urls: CatalogUrls, words: String? = null): Catalog {
        val root = JSONObject(json)
        val categories = root.optJSONArray("categories").objects().mapNotNull(::category)
        val hues = categories.associate { it.id to it.hue }
        val dictionaries = words?.let(::JSONObject) ?: root
        val searchWords = SearchWords(
            dictionaries.optJSONObject("tags").wordsById(),
            dictionaries.optJSONObject("languages").wordsById(),
        )
        val packs = root.optJSONArray("packs").objects()
            .mapNotNull { pack(it, urls, hues, searchWords) }
            .mapIndexed { index, pack -> pack.copy(order = index) }
        return Catalog(version = root.optInt("version", 0), categories = categories, packs = packs)
    }

    private fun category(o: JSONObject): Category? {
        val id = o.optString("id").ifBlank { return null }
        val names = o.optJSONObject("names").strings()
        return Category(
            id = id,
            name = names["en"] ?: id,
            icon = o.optString("icon", "heart"),
            hue = o.optInt("hue", DEFAULT_HUE),
            order = o.optInt("order", Int.MAX_VALUE),
            names = names,
            keywords = o.optJSONObject("keywords").stringLists(),
            packCount = o.optInt("packs", -1),
        )
    }

    /** Words people type, by pack tag and by lettering language, every app language together. */
    private class SearchWords(val tags: Map<String, List<String>>, val languages: Map<String, List<String>>)

    private fun pack(o: JSONObject, urls: CatalogUrls, hues: Map<String, Int>, words: SearchWords): StickerPack? {
        val id = o.optString("id").ifBlank { return null }
        val name = o.optString("name").ifBlank { return null }
        val zip = o.optJSONObject("zip") ?: return null
        val zipPath = zip.optString("path").ifBlank { return null }
        val cover = o.optJSONObject("cover")
        val category = o.optString("category")
        // Catalogs published before languages were lists have one "lang".
        val langs = o.optJSONArray("langs").strings().ifEmpty { listOf(o.optString("lang", "en").ifBlank { "en" }) }
        val keywords = o.optJSONArray("keywords").strings() +
            o.optJSONArray("tags").strings().flatMap { words.tags[it].orEmpty() } +
            // A language code without words of its own takes its primary language's ("pt-BR" → "pt").
            langs.flatMap { (words.languages[it] ?: words.languages[it.substringBefore('-')]).orEmpty() }
        return StickerPack(
            id = id,
            name = name,
            publisher = DEFAULT_PUBLISHER,
            category = category,
            animated = o.optBoolean("animated", false),
            order = 0,
            downloads = o.optLong("adds", 0),
            hue = hues[category] ?: DEFAULT_HUE,
            stickerCount = o.optInt("count", 0),
            version = o.optInt("version", 1),
            names = o.optJSONObject("names").strings(),
            alsoIn = o.optJSONArray("alsoIn").strings(),
            langs = langs,
            keywords = keywords.distinct(),
            coverSmallUrl = cover?.optString("s")?.ifBlank { null }?.let(urls::url),
            coverLargeUrl = cover?.optString("l")?.ifBlank { null }?.let(urls::url),
            coverTiles = cover?.optInt("tiles", 0) ?: 0,
            zipUrl = urls.url(zipPath),
            zipBytes = zip.optLong("bytes", 0),
        )
    }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

    private fun JSONArray?.strings(): List<String> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optString(it).ifBlank { null } }

    private fun JSONObject?.strings(): Map<String, String> {
        if (this == null) return emptyMap()
        return keys().asSequence().mapNotNull { key -> optString(key).ifBlank { null }?.let { key to it } }.toMap()
    }

    /** { id: { lang: [words] } } → { id: [every word, all languages] }. */
    private fun JSONObject?.wordsById(): Map<String, List<String>> {
        if (this == null) return emptyMap()
        return keys().asSequence().associateWith { id -> optJSONObject(id).stringLists().values.flatten().distinct() }
    }

    private fun JSONObject?.stringLists(): Map<String, List<String>> {
        if (this == null) return emptyMap()
        return keys().asSequence().map { key -> key to optJSONArray(key).strings() }.filter { it.second.isNotEmpty() }.toMap()
    }
}
