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
