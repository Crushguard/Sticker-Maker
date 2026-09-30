package com.piptechnologies.stickermaker.feature.namepack

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import com.piptechnologies.stickermaker.core.design.LoveIcons

/**
 * Glyphs only the name flow uses, kept with the feature like SettingsIcons: Lucide `user-round`
 * (not in the vendored set) and the design's app-mark heart (not Lucide).
 */
internal object NamePackIcons {

    /** Lucide `user-round` (its circle pre-flattened to arcs), built like every LoveIcons glyph. */
    val UserRound: ImageVector by lazy {
        LoveIcons.lucideIcon("user-round", "M7 8a5 5 0 1 0 10 0a5 5 0 1 0 -10 0", "M20 21a8 8 0 0 0-16 0")
    }

    /** Claude Design's app-mark heart, filled (the ❤ in pack names). */
    val AppHeart: ImageVector = ImageVector.Builder(
        name = "app-heart", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        addPath(
            pathData = addPathNodes(
                "M 12 21 s -7.5 -4.7 -9.6 -9.2 C 0.7 8 3 4.5 6.6 4.5 c 2 0 3.6 1.1 4.4 2.6 " +
                    "c 0.8 -1.5 2.4 -2.6 4.4 -2.6 c 3.6 0 5.9 3.5 4.2 7.3 C 19.5 16.3 12 21 12 21 z"
            ),
            fill = SolidColor(Color.Black)
        )
    }.build()
}
