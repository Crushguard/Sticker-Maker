package com.piptechnologies.stickermaker.feature.language

import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.piptechnologies.stickermaker.R

/**
 * One app language: its BCP-47 [tag] (what AppCompat and locales_config.xml
 * use: "id" and "he", even though the resource folders are values-in and
 * values-iw), its [nativeName] as speakers write it (never translated), its
 * name in the current app language, and whether it lays out right to left.
 */
internal data class AppLanguage(
    val tag: String,
    val nativeName: String,
    @StringRes val nameRes: Int,
    val rtl: Boolean = false
)

/**
 * The languages Love Stickers ships, the same set as the RecoverMe and Status
 * Saver apps. The design's list comes first, in its order. The per-app
 * locale AppCompat stores (Android's own per-app language setting on 13+) is
 * the only record of the choice, so the system settings and this screen
 * never disagree.
 */
internal object AppLanguages {

    /** Selection meaning "follow the device language": no per-app locale. */
    const val SYSTEM = "system"

    val entries: List<AppLanguage> = listOf(
        AppLanguage("en", "English", R.string.language_name_en),
        AppLanguage("ar", "العربية", R.string.language_name_ar, rtl = true),
        AppLanguage("es", "Español", R.string.language_name_es),
        AppLanguage("pt", "Português", R.string.language_name_pt),
        AppLanguage("pt-BR", "Português (Brasil)", R.string.language_name_pt_br),
        AppLanguage("fr", "Français", R.string.language_name_fr),
        AppLanguage("hi", "हिन्दी", R.string.language_name_hi),
        AppLanguage("id", "Bahasa Indonesia", R.string.language_name_id),
        AppLanguage("tr", "Türkçe", R.string.language_name_tr),
        AppLanguage("ur", "اردو", R.string.language_name_ur, rtl = true),
        AppLanguage("de", "Deutsch", R.string.language_name_de),
        AppLanguage("ru", "Русский", R.string.language_name_ru),
        AppLanguage("it", "Italiano", R.string.language_name_it),
        AppLanguage("zh", "简体中文", R.string.language_name_zh),
        AppLanguage("fa", "فارسی", R.string.language_name_fa, rtl = true),
        AppLanguage("he", "עברית", R.string.language_name_he, rtl = true),
        AppLanguage("ps", "پښتو", R.string.language_name_ps, rtl = true),
        AppLanguage("ha", "Hausa", R.string.language_name_ha),
        AppLanguage("my", "မြန်မာ", R.string.language_name_my)
    )

    /** Legacy ISO codes Android still reports (and names folders) for two languages. */
    private val LEGACY_CODES = mapOf("in" to "id", "iw" to "he")

    /** The current selection: a tag from [entries], or [SYSTEM]. */
    fun selectedTag(): String {
        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.isEmpty) return SYSTEM
        return locales[0]?.let { match(it.toLanguageTag()) } ?: SYSTEM
    }

    /** Applies [tag] as the per-app locale; [SYSTEM] clears it. May recreate the activity. */
    fun apply(tag: String) {
        AppCompatDelegate.setApplicationLocales(
            if (tag == SYSTEM) LocaleListCompat.getEmptyLocaleList()
            else LocaleListCompat.forLanguageTags(tag)
        )
    }

    /**
     * The shipped language for any BCP-47 or Java tag: exact match first
     * ("pt-BR"), then language and region, then the language alone
     * ("pt-PT" -> "pt", "in" -> "id"); [SYSTEM] when nothing fits.
     */
    fun match(raw: String): String {
        val parts = raw.trim().replace('_', '-').split('-').filter { it.isNotEmpty() }
        if (parts.isEmpty()) return SYSTEM
        val language = parts[0].lowercase().let { LEGACY_CODES[it] ?: it }
        val region = parts.drop(1).firstOrNull { it.length == 2 && it.all(Char::isLetter) }?.uppercase()
        val tags = entries.map { it.tag }
        return when {
            region != null && "$language-$region" in tags -> "$language-$region"
            language in tags -> language
            else -> SYSTEM
        }
    }

    fun byTag(tag: String): AppLanguage? = entries.firstOrNull { it.tag == tag }
}
