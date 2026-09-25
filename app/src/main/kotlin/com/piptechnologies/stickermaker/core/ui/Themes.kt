package com.piptechnologies.stickermaker.core.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
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

/** A theme's name as UiText: translated when known, the published name otherwise. */
fun Category.nameText(): UiText = themeNameRes(id)?.let { UiText.res(it) } ?: UiText.Raw(name)

/** A theme's name in the current language (see [themeNameRes]). */
@Composable
@ReadOnlyComposable
fun Category.displayName(): String = themeNameRes(id)?.let { stringResource(it) } ?: name
