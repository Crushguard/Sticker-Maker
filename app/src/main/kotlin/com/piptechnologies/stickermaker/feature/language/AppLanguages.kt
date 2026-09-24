package com.piptechnologies.stickermaker.feature.language

import com.piptechnologies.stickermaker.core.data.prefs.PrefsRepository

/**
 * One selectable app language: the BCP-47 [tag] persisted in prefs (or
 * [PrefsRepository.LANGUAGE_SYSTEM]), the [nativeName] shown as the row title
 * and the [englishName] under it, plus the RTL badge flag.
 */
internal data class AppLanguage(
    val tag: String,
    val nativeName: String,
    val englishName: String,
    val rtl: Boolean = false
)

/**
 * The language list from the design (Prototype `langs`), preceded by the
 * "System default" entry that maps to [PrefsRepository.LANGUAGE_SYSTEM].
 */
internal object AppLanguages {

    val entries: List<AppLanguage> = listOf(
        AppLanguage(PrefsRepository.LANGUAGE_SYSTEM, "System default", "Device language"),
        AppLanguage("en", "English", "English"),
        AppLanguage("ar", "العربية", "Arabic", rtl = true),
        AppLanguage("es", "Español", "Spanish"),
        AppLanguage("pt", "Português", "Portuguese"),
        AppLanguage("fr", "Français", "French"),
        AppLanguage("hi", "हिन्दी", "Hindi"),
        AppLanguage("id", "Bahasa Indonesia", "Indonesian"),
        AppLanguage("tr", "Türkçe", "Turkish"),
        AppLanguage("ur", "اردو", "Urdu", rtl = true),
        AppLanguage("de", "Deutsch", "German")
    )

    /** Native display name for [tag]; unknown tags read as the system entry. */
    fun nativeLabelOf(tag: String): String =
        entries.firstOrNull { it.tag == tag }?.nativeName ?: entries.first().nativeName
}
