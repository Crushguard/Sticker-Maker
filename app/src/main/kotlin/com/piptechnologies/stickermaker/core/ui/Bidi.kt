package com.piptechnologies.stickermaker.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection

/**
 * [this] style with its paragraph direction pinned to the layout's. Compose
 * otherwise takes it from the text's first strong character, so a full-width
 * Latin pack name would align left inside a right-to-left screen.
 */
@Composable
@ReadOnlyComposable
fun TextStyle.inLayoutDirection(): TextStyle = copy(
    textDirection = if (LocalLayoutDirection.current == LayoutDirection.Rtl) TextDirection.Rtl else TextDirection.Ltr
)
