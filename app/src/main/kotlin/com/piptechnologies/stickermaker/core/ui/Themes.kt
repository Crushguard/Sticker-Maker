package com.piptechnologies.stickermaker.core.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import com.piptechnologies.stickermaker.R
import com.piptechnologies.stickermaker.core.model.Category

/** Translated name of a catalog theme, or null for a theme the app does not know yet. */
@StringRes
fun themeNameRes(categoryId: String): Int? = when (categoryId) {
    "couples" -> R.string.theme_couples
    "cute" -> R.string.theme_cute
    "funny" -> R.string.theme_funny
    "anime" -> R.string.theme_anime
    "romantic" -> R.string.theme_romantic
    "flirty" -> R.string.theme_flirty
    "goodnight" -> R.string.theme_goodnight
    "distance" -> R.string.theme_distance
    else -> null
}

/**
 * A theme's name as UiText: the catalog's name in the reader's language, else the app's own translation of a
 * theme it knows, else the English name.
 */
fun Category.nameText(): UiText =
    UiText.Localized(names, themeNameRes(id)?.let { UiText.res(it) } ?: UiText.Raw(names["en"] ?: name))

/** A theme's name in the current language (see [nameText]). */
@Composable
@ReadOnlyComposable
fun Category.displayName(): String = nameText().asString()
