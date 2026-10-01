package com.piptechnologies.stickermaker.core.ui

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Whether the user turned animations off (Developer options or accessibility): the animator
 * duration scale is 0. False where the setting can't be read.
 */
fun isReduceMotionOn(context: Context): Boolean = runCatching {
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
}.getOrDefault(false)

/**
 * [isReduceMotionOn], read once while the caller stays composed. Compose already scales its own
 * animations by this setting; this switches off what it cannot see: frame-clock loops, the
 * waiting bob, the tile pop and staggered delays. False where the setting cannot be read (previews).
 */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) { isReduceMotionOn(context) }
}
