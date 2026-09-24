package com.piptechnologies.stickermaker.core.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Rose,
    onPrimary = Color.White,
    primaryContainer = RoseTint,
    onPrimaryContainer = RoseDark,
    secondary = Ink2,
    onSecondary = Color.White,
    secondaryContainer = Subtle,
    onSecondaryContainer = Ink,
    tertiary = Green,
    onTertiary = Color.White,
    tertiaryContainer = Subtle,
    onTertiaryContainer = Green,
    background = Canvas,
    onBackground = Ink,
    surface = Surface,
    onSurface = Ink,
    surfaceVariant = Subtle,
    onSurfaceVariant = Ink2,
    error = Destructive,
    onError = Color.White,
    errorContainer = DestructiveLine,
    onErrorContainer = Destructive,
    outline = Border,
    outlineVariant = BorderStrong,
    scrim = Ink
)

/** Corner radii from the design system: 10 · 14 · 20 · pill. */
object LoveShapes {
    val Small = RoundedCornerShape(10.dp)
    val Medium = RoundedCornerShape(14.dp)
    val Large = RoundedCornerShape(20.dp)
    val Pill = RoundedCornerShape(percent = 50)
}

/** 4-based spacing scale. */
@Immutable
data class Spacing(
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 20.dp,
    val xxl: Dp = 24.dp
)

val LocalSpacing = staticCompositionLocalOf { Spacing() }

/**
 * Light-only Material theme for Love Stickers. The app never follows the
 * system dark setting (see LoveStickersApp: MODE_NIGHT_NO).
 */
@Composable
fun LoveStickersTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalSpacing provides Spacing()) {
        MaterialTheme(
            colorScheme = LightColors,
            typography = LoveTypography,
            content = content
        )
    }
}
