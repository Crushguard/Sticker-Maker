package com.piptechnologies.stickermaker.core.design

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.piptechnologies.stickermaker.R

/** Hanken Grotesk — the app face (bundled, no network font provider). */
val Hanken = FontFamily(
    Font(R.font.hg_regular, FontWeight.W400),
    Font(R.font.hg_medium, FontWeight.W500),
    Font(R.font.hg_semibold, FontWeight.W600),
    Font(R.font.hg_bold, FontWeight.W700),
    Font(R.font.hg_extrabold, FontWeight.W800)
)

/** JetBrains Mono — counts and meta lines. */
val Mono = FontFamily(
    Font(R.font.jb_regular, FontWeight.W400),
    Font(R.font.jb_medium, FontWeight.W500),
    Font(R.font.jb_semibold, FontWeight.W600)
)

/**
 * Type ramp from the design system:
 * Display 26/800 · Title 16/700 · Title-2 15/600 · Body 15/400 ·
 * Body-2 13.5/400 · Button 14/700 · Label 12/600 · Meta 11.5 mono/500.
 */
val LoveTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = Hanken,
        fontWeight = FontWeight.W800,
        fontSize = 26.sp,
        lineHeight = 31.sp,
        letterSpacing = (-0.02).em
    ),
    titleLarge = TextStyle(
        fontFamily = Hanken,
        fontWeight = FontWeight.W700,
        fontSize = 16.sp,
        lineHeight = 21.sp,
        letterSpacing = (-0.01).em
    ),
    titleMedium = TextStyle(
        fontFamily = Hanken,
        fontWeight = FontWeight.W600,
        fontSize = 15.sp,
        lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = Hanken,
        fontWeight = FontWeight.W400,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = Hanken,
        fontWeight = FontWeight.W400,
        fontSize = 13.5.sp,
        lineHeight = 20.sp
    ),
    labelLarge = TextStyle(
        // Buttons.
        fontFamily = Hanken,
        fontWeight = FontWeight.W700,
        fontSize = 14.sp,
        lineHeight = 18.sp
    ),
    labelMedium = TextStyle(
        fontFamily = Hanken,
        fontWeight = FontWeight.W600,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        // Meta: "24 stickers · 42.5K downloads".
        fontFamily = Mono,
        fontWeight = FontWeight.W500,
        fontSize = 11.5.sp,
        lineHeight = 16.sp
    )
)
