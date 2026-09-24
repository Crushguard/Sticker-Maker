package com.piptechnologies.stickermaker.core.ui

import android.content.Context
import android.icu.text.CompactDecimalFormat
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/**
 * Text a ViewModel hands to the UI unresolved. The screen resolves it in the
 * current app language, so a per-app language switch (which recreates the
 * Activity but keeps ViewModels) never leaves stale copy behind.
 *
 * Arguments may themselves be [UiText]; they are resolved first.
 */
sealed interface UiText {
    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText

    data class Plural(
        @PluralsRes val id: Int,
        val count: Int,
        val args: List<Any> = listOf(count)
    ) : UiText

    /** "96.4K"-style short number in the reader's locale. */
    data class Compact(val value: Long) : UiText

    /** Already-final text: pack names and anything else published as-is. */
    data class Raw(val text: String) : UiText

    companion object {
        fun res(@StringRes id: Int, vararg args: Any): UiText = Res(id, args.toList())
        fun plural(@PluralsRes id: Int, count: Int, vararg args: Any): UiText =
            Plural(id, count, if (args.isEmpty()) listOf(count) else args.toList())
    }
}

/** Resolves against [context]'s resources (use the Activity context, not the application's). */
fun UiText.asString(context: Context): String = when (this) {
    is UiText.Res ->
        if (args.isEmpty()) context.getString(id)
        else context.getString(id, *resolved(args, context))
    is UiText.Plural -> context.resources.getQuantityString(id, count, *resolved(args, context))
    is UiText.Compact -> compactNumber(value, context.resources.configuration.locales[0])
    is UiText.Raw -> text
}

@Composable
@ReadOnlyComposable
fun UiText.asString(): String {
    // Reading the configuration re-resolves the text when the locale changes.
    LocalConfiguration.current
    return asString(LocalContext.current)
}

private fun resolved(args: List<Any>, context: Context): Array<Any> =
    args.map { if (it is UiText) it.asString(context) else it }.toTypedArray()

/** "96.4K" in English, "96,4 k" in French, "9.6万" in Chinese; small counts stay whole. */
fun compactNumber(value: Long, locale: Locale): String =
    CompactDecimalFormat.getInstance(locale, CompactDecimalFormat.CompactStyle.SHORT)
        .apply { maximumSignificantDigits = 3 }
        .format(value)
