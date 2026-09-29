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
 */
object CatalogFile {

    private const val DEFAULT_PUBLISHER = "PIP Technologies"
    private const val DEFAULT_HUE = 340

    fun parse(json: String, urls: CatalogUrls): Catalog {
        val root = JSONObject(json)
        val categories = root.optJSONArray("categories").objects().mapNotNull(::category)
        val hues = categories.associate { it.id to it.hue }
        val packs = root.optJSONArray("packs").objects()
            .mapNotNull { pack(it, urls, hues) }
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

    private fun pack(o: JSONObject, urls: CatalogUrls, hues: Map<String, Int>): StickerPack? {
        val id = o.optString("id").ifBlank { return null }
        val name = o.optString("name").ifBlank { return null }
        val zip = o.optJSONObject("zip") ?: return null
        val zipPath = zip.optString("path").ifBlank { return null }
        val cover = o.optJSONObject("cover")
        val category = o.optString("category")
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
            trayUrl = null,
            thumbUrls = emptyList(),
            stickerUrls = emptyList(),
            version = o.optInt("version", 1),
            names = o.optJSONObject("names").strings(),
            alsoIn = o.optJSONArray("alsoIn").strings(),
            lang = o.optString("lang", "en").ifBlank { "en" },
            keywords = o.optJSONArray("keywords").strings(),
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

    private fun JSONObject?.stringLists(): Map<String, List<String>> {
        if (this == null) return emptyMap()
        return keys().asSequence().map { key -> key to optJSONArray(key).strings() }.filter { it.second.isNotEmpty() }.toMap()
    }
}
