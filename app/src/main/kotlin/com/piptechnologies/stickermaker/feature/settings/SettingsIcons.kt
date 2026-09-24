package com.piptechnologies.stickermaker.feature.settings

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Lucide glyphs the Settings suite needs that core/design/Icons.kt does not
 * carry (that file is frozen). Vendored in the same style as LoveIcons:
 * 24x24, 2px round stroke, ISC-licensed Lucide path data, tinted through
 * [androidx.compose.material3.Icon].
 */
internal object SettingsIcons {

    private fun lucideIcon(name: String, vararg pathData: String, autoMirror: Boolean = false): ImageVector {
        val builder = ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
            autoMirror = autoMirror
        )
        for (d in pathData) {
            builder.addPath(
                pathData = addPathNodes(d),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
        }
        return builder.build()
    }

    /** Off state of the "New pack alerts" hero card. */
    val BellOff: ImageVector by lazy {
        lucideIcon(
            "bell-off",
            "M10.268 21a2 2 0 0 0 3.464 0",
            "M17.658 17H4a1 1 0 0 1-.74-1.673C4.59 13.956 6 12.499 6 8a6 6 0 0 1 .258-1.742",
            "m2 2 20 20",
            "M8.668 3.01A6 6 0 0 1 18 8c0 2.687.77 4.653 1.707 6.05"
        )
    }

    /** "Edit themes" row (the prototype's `sliders-horizontal`). */
    val SlidersHorizontal: ImageVector by lazy {
        lucideIcon(
            "sliders-horizontal",
            "M21 4h-7",
            "M10 4H3",
            "M21 12h-9",
            "M8 12H3",
            "M21 20h-5",
            "M12 20H3",
            "M14 2v4",
            "M8 10v4",
            "M16 18v4"
        )
    }

    /** "Language" row. */
    val Languages: ImageVector by lazy {
        lucideIcon(
            "languages",
            "m5 8 6 6",
            "m4 14 6-6 2-3",
            "M2 5h12",
            "M7 2h1",
            "m22 22-5-10-5 10",
            "M14 18h6"
        )
    }

    /** "More apps" row (the prototype's `layout-grid`). */
    val LayoutGrid: ImageVector by lazy {
        lucideIcon(
            "layout-grid",
            "M3 4a1 1 0 0 1 1-1h5a1 1 0 0 1 1 1v5a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1z",
            "M14 4a1 1 0 0 1 1-1h5a1 1 0 0 1 1 1v5a1 1 0 0 1-1 1h-5a1 1 0 0 1-1-1z",
            "M14 15a1 1 0 0 1 1-1h5a1 1 0 0 1 1 1v5a1 1 0 0 1-1 1h-5a1 1 0 0 1-1-1z",
            "M3 15a1 1 0 0 1 1-1h5a1 1 0 0 1 1 1v5a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1z"
        )
    }

    /** "Privacy policy" row (the prototype's `shield-check`). */
    val ShieldCheck: ImageVector by lazy {
        lucideIcon(
            "shield-check",
            "M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z",
            "m9 12 2 2 4-4"
        )
    }

    /** Trailing arrow on rows that leave the app (More apps, Privacy policy). */
    val ArrowUpRight: ImageVector by lazy {
        lucideIcon(
            "arrow-up-right",
            "M7 7h10v10",
            "M7 17 17 7"
        )
    }

    /** Five-star celebration icon of the rating sheet's Google Play stage. */
    val PartyPopper: ImageVector by lazy {
        lucideIcon(
            "party-popper",
            "M5.8 11.3 2 22l10.7-3.79",
            "M4 3h.01",
            "M22 8h.01",
            "M15 2h.01",
            "M22 20h.01",
            "m22 2-2.24.75a2.9 2.9 0 0 0-1.96 3.12c.1.86-.57 1.63-1.45 1.63h-.38c-.86 0-1.6.6-1.76 1.44L14 10",
            "m22 13-.82-.33c-.86-.34-1.82.2-1.98 1.11c-.11.7-.72 1.22-1.43 1.22H17",
            "m11 2 .33.82c.34.86-.2 1.82-1.11 1.98C9.52 4.9 9 5.52 9 6.23V7",
            "M11 13c1.93 1.93 2.83 4.17 2 5-.83.83-3.07-.07-5-2-1.93-1.93-2.83-4.17-2-5 .83-.83 3.07.07 5 2Z"
        )
    }

    /** Icon of the rating sheet's private-feedback stage. */
    val MessageSquareText: ImageVector by lazy {
        lucideIcon(
            "message-square-text",
            "M22 17a2 2 0 0 1-2 2H6.828a2 2 0 0 0-1.414.586l-2.202 2.202A.71.71 0 0 1 2 21.286V5a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2z",
            "M7 8h10",
            "M7 12h8"
        )
    }

    /** Paper plane on the "Send feedback" button. */
    val Send: ImageVector by lazy {
        lucideIcon(
            "send",
            "M14.536 21.686a.5.5 0 0 0 .937-.024l6.5-19a.496.496 0 0 0-.635-.635l-19 6.5a.5.5 0 0 0-.024.937l7.93 3.18a2 2 0 0 1 1.112 1.11z",
            "m21.854 2.147-10.94 10.939",
            autoMirror = true
        )
    }
}
