package com.piptechnologies.stickermaker.core.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Lucide glyphs used by the design, vendored as Compose [ImageVector]s
 * (ported from the ISC-licensed Lucide SVGs kept in design/lucide/).
 * All icons are 24x24, 2px round stroke, and tint through [androidx.compose.material3.Icon].
 */
object LoveIcons {

    /** Builds a stroked 24x24 Lucide-style vector from raw SVG path data. */
    private fun lucideIcon(
        name: String,
        vararg pathData: String,
        filled: Boolean = false
    ): ImageVector {
        val builder = ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        )
        for (d in pathData) {
            builder.addPath(
                pathData = addPathNodes(d),
                fill = if (filled) SolidColor(Color.Black) else null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
        }
        return builder.build()
    }

    // Circles are pre-flattened to arc path data ("M(cx-r) cy a r r 0 1 0 2r 0 ...").

    val Heart: ImageVector by lazy {
        lucideIcon(
            "heart",
            "M2 9.5a5.5 5.5 0 0 1 9.591-3.676.56.56 0 0 0 .818 0A5.49 5.49 0 0 1 22 9.5c0 2.29-1.5 4-3 5.5l-5.492 5.313a2 2 0 0 1-3 .019L5 15c-1.5-1.5-3-3.2-3-5.5"
        )
    }

    val HeartFilled: ImageVector by lazy {
        lucideIcon(
            "heart-filled",
            "M2 9.5a5.5 5.5 0 0 1 9.591-3.676.56.56 0 0 0 .818 0A5.49 5.49 0 0 1 22 9.5c0 2.29-1.5 4-3 5.5l-5.492 5.313a2 2 0 0 1-3 .019L5 15c-1.5-1.5-3-3.2-3-5.5",
            filled = true
        )
    }

    val HeartHandshake: ImageVector by lazy {
        lucideIcon(
            "heart-handshake",
            "M19.414 14.414C21 12.828 22 11.5 22 9.5a5.5 5.5 0 0 0-9.591-3.676.6.6 0 0 1-.818.001A5.5 5.5 0 0 0 2 9.5c0 2.3 1.5 4 3 5.5l5.535 5.362a2 2 0 0 0 2.879.052 2.12 2.12 0 0 0-.004-3 2.124 2.124 0 1 0 3-3 2.124 2.124 0 0 0 3.004 0 2 2 0 0 0 0-2.828l-1.881-1.882a2.41 2.41 0 0 0-3.409 0l-1.71 1.71a2 2 0 0 1-2.828 0 2 2 0 0 1 0-2.828l2.823-2.762"
        )
    }

    val Rabbit: ImageVector by lazy {
        lucideIcon(
            "rabbit",
            "M13 16a3 3 0 0 1 2.24 5",
            "M18 12h.01",
            "M18 21h-8a4 4 0 0 1-4-4 7 7 0 0 1 7-7h.2L9.6 6.4a1 1 0 1 1 2.8-2.8L15.8 7h.2c3.3 0 6 2.7 6 6v1a2 2 0 0 1-2 2h-1a3 3 0 0 0-3 3",
            "M20 8.54V4a2 2 0 1 0-4 0v3",
            "M7.612 12.524a3 3 0 1 0-1.6 4.3"
        )
    }

    val Laugh: ImageVector by lazy {
        lucideIcon(
            "laugh",
            "M2 12a10 10 0 1 0 20 0a10 10 0 1 0-20 0",
            "M18 13a6 6 0 0 1-6 5 6 6 0 0 1-6-5h12Z",
            "M9 9h.01",
            "M15 9h.01"
        )
    }

    val Sparkles: ImageVector by lazy {
        lucideIcon(
            "sparkles",
            "M11.017 2.814a1 1 0 0 1 1.966 0l1.051 5.558a2 2 0 0 0 1.594 1.594l5.558 1.051a1 1 0 0 1 0 1.966l-5.558 1.051a2 2 0 0 0-1.594 1.594l-1.051 5.558a1 1 0 0 1-1.966 0l-1.051-5.558a2 2 0 0 0-1.594-1.594l-5.558-1.051a1 1 0 0 1 0-1.966l5.558-1.051a2 2 0 0 0 1.594-1.594z",
            "M20 2v4",
            "M22 4h-4",
            "M2 20a2 2 0 1 0 4 0a2 2 0 1 0-4 0"
        )
    }

    val Flower2: ImageVector by lazy {
        lucideIcon(
            "flower-2",
            "M12 5a3 3 0 1 1 3 3m-3-3a3 3 0 1 0-3 3m3-3v1M9 8a3 3 0 1 0 3 3M9 8h1m5 0a3 3 0 1 1-3 3m3-3h-1m-2 3v-1",
            "M10 8a2 2 0 1 0 4 0a2 2 0 1 0-4 0",
            "M12 10v12",
            "M12 22c4.2 0 7-1.667 7-5-4.2 0-7 1.667-7 5Z",
            "M12 22c-4.2 0-7-1.667-7-5 4.2 0 7 1.667 7 5Z"
        )
    }

    val MessageCircleHeart: ImageVector by lazy {
        lucideIcon(
            "message-circle-heart",
            "M2.992 16.342a2 2 0 0 1 .094 1.167l-1.065 3.29a1 1 0 0 0 1.236 1.168l3.413-.998a2 2 0 0 1 1.099.092 10 10 0 1 0-4.777-4.719",
            "M7.828 13.07A3 3 0 0 1 12 8.764a3 3 0 0 1 5.004 2.224 3 3 0 0 1-.832 2.083l-3.447 3.62a1 1 0 0 1-1.45-.001z"
        )
    }

    /** The speech-bubble glyph the Add controls use to say "WhatsApp" in our own colours. */
    val MessageCircle: ImageVector by lazy {
        lucideIcon("message-circle", "M7.9 20A9 9 0 1 0 4 16.1L2 22Z")
    }

    val Moon: ImageVector by lazy {
        lucideIcon(
            "moon",
            "M20.985 12.486a9 9 0 1 1-9.473-9.472c.405-.022.617.46.402.803a6 6 0 0 0 8.268 8.268c.344-.215.825-.004.803.401"
        )
    }

    val Plane: ImageVector by lazy {
        lucideIcon(
            "plane",
            "M17.8 19.2 16 11l3.5-3.5C21 6 21.5 4 21 3c-1-.5-3 0-4.5 1.5L13 8 4.8 6.2c-.5-.1-.9.1-1.1.5l-.3.5c-.2.5-.1 1 .3 1.3L9 12l-2 3H4l-1 1 3 2 2 3 1-1v-3l3-2 3.5 5.3c.3.4.8.5 1.3.3l.5-.2c.4-.3.6-.7.5-1.2z"
        )
    }

    val Search: ImageVector by lazy {
        lucideIcon("search", "m21 21-4.34-4.34", "M3 11a8 8 0 1 0 16 0a8 8 0 1 0-16 0")
    }

    val X: ImageVector by lazy {
        lucideIcon("x", "M18 6 6 18", "m6 6 12 12")
    }

    val ArrowLeft: ImageVector by lazy {
        lucideIcon("arrow-left", "m12 19-7-7 7-7", "M19 12H5")
    }

    val ChevronLeft: ImageVector by lazy {
        lucideIcon("chevron-left", "m15 18-6-6 6-6")
    }

    val ChevronRight: ImageVector by lazy {
        lucideIcon("chevron-right", "m9 18 6-6-6-6")
    }

    val ChevronDown: ImageVector by lazy {
        lucideIcon("chevron-down", "m6 9 6 6 6-6")
    }

    val Plus: ImageVector by lazy {
        lucideIcon("plus", "M5 12h14", "M12 5v14")
    }

    val Check: ImageVector by lazy {
        lucideIcon("check", "M20 6 9 17l-5-5")
    }

    val Download: ImageVector by lazy {
        lucideIcon(
            "download",
            "M12 15V3",
            "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4",
            "m7 10 5 5 5-5"
        )
    }

    val Share2: ImageVector by lazy {
        lucideIcon(
            "share-2",
            "M15 5a3 3 0 1 0 6 0a3 3 0 1 0-6 0",
            "M3 12a3 3 0 1 0 6 0a3 3 0 1 0-6 0",
            "M15 19a3 3 0 1 0 6 0a3 3 0 1 0-6 0",
            "M8.59 13.51 15.42 17.49",
            "M15.41 6.51 8.59 10.49"
        )
    }

    val Trash2: ImageVector by lazy {
        lucideIcon(
            "trash-2",
            "M3 6h18",
            "M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6",
            "M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2",
            "M10 11v6",
            "M14 11v6"
        )
    }

    val Pencil: ImageVector by lazy {
        lucideIcon(
            "pencil",
            "M21.174 6.812a1 1 0 0 0-3.986-3.987L3.842 16.174a2 2 0 0 0-.5.83l-1.321 4.352a.5.5 0 0 0 .623.622l4.353-1.32a2 2 0 0 0 .83-.497z",
            "m15 5 4 4"
        )
    }

    val Image: ImageVector by lazy {
        lucideIcon(
            "image",
            "M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z",
            "M7 9a2 2 0 1 0 4 0a2 2 0 1 0-4 0",
            "m21 15-3.086-3.086a2 2 0 0 0-2.828 0L6 21"
        )
    }

    /** Stacked photos, the segmented "Photos" glyph. */
    val Images: ImageVector by lazy {
        lucideIcon(
            "images",
            "M18 22H4a2 2 0 0 1-2-2V6",
            "m22 13-1.296-1.296a2.41 2.41 0 0 0-3.408 0L11 18",
            "M15 8a2 2 0 1 0 4 0a2 2 0 1 0-4 0",
            "M8 2h12a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2z"
        )
    }

    val Camera: ImageVector by lazy {
        lucideIcon(
            "camera",
            "M13.997 4a2 2 0 0 1 1.76 1.05l.486.9A2 2 0 0 0 18.003 7H20a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V9a2 2 0 0 1 2-2h1.997a2 2 0 0 0 1.759-1.048l.489-.904A2 2 0 0 1 10.004 4z",
            "M9 13a3 3 0 1 0 6 0a3 3 0 1 0-6 0"
        )
    }

    val Video: ImageVector by lazy {
        lucideIcon(
            "video",
            "m16 13 5.223 3.482a.5.5 0 0 0 .777-.416V7.87a.5.5 0 0 0-.752-.432L16 10.5",
            "M4 6h10a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2z"
        )
    }

    val Type: ImageVector by lazy {
        lucideIcon(
            "type",
            "M12 4v16",
            "M4 7V5a1 1 0 0 1 1-1h14a1 1 0 0 1 1 1v2",
            "M9 20h6"
        )
    }

    val Eraser: ImageVector by lazy {
        lucideIcon(
            "eraser",
            "M21 21H8a2 2 0 0 1-1.42-.587l-3.994-3.999a2 2 0 0 1 0-2.828l10-10a2 2 0 0 1 2.829 0l5.999 6a2 2 0 0 1 0 2.828L12.834 21",
            "m5.082 11.09 8.828 8.828"
        )
    }

    val Brush: ImageVector by lazy {
        lucideIcon(
            "brush",
            "m11 10 3 3",
            "M6.5 21A3.5 3.5 0 1 0 3 17.5a2.62 2.62 0 0 1-.708 1.792A1 1 0 0 0 3 21z",
            "M9.969 17.031 21.378 5.624a1 1 0 0 0-3.002-3.002L6.967 14.031"
        )
    }

    /** Sparkle wand, the editor's Auto cut-out tool. */
    val Wand2: ImageVector by lazy {
        lucideIcon(
            "wand-2",
            "m21.64 3.64-1.28-1.28a1.21 1.21 0 0 0-1.72 0L2.36 18.64a1.21 1.21 0 0 0 0 1.72l1.28 1.28a1.2 1.2 0 0 0 1.72 0L21.64 5.36a1.2 1.2 0 0 0 0-1.72",
            "m14 7 3 3",
            "M5 6v4",
            "M19 14v4",
            "M10 2v2",
            "M7 8H3",
            "M21 16h-4",
            "M11 3H9"
        )
    }

    val Undo2: ImageVector by lazy {
        lucideIcon(
            "undo-2",
            "M9 14 4 9l5-5",
            "M4 9h10.5a5.5 5.5 0 0 1 5.5 5.5a5.5 5.5 0 0 1-5.5 5.5H11"
        )
    }

    val Redo2: ImageVector by lazy {
        lucideIcon(
            "redo-2",
            "m15 14 5-5-5-5",
            "M20 9H9.5A5.5 5.5 0 0 0 4 14.5A5.5 5.5 0 0 0 9.5 20H13"
        )
    }

    val ZoomIn: ImageVector by lazy {
        lucideIcon(
            "zoom-in",
            "M3 11a8 8 0 1 0 16 0a8 8 0 1 0-16 0",
            "M21 21 16.65 16.65",
            "M11 8v6",
            "M8 11h6"
        )
    }

    val Settings: ImageVector by lazy {
        lucideIcon(
            "settings",
            "M9.671 4.136a2.34 2.34 0 0 1 4.659 0 2.34 2.34 0 0 0 3.319 1.915 2.34 2.34 0 0 1 2.33 4.033 2.34 2.34 0 0 0 0 3.831 2.34 2.34 0 0 1-2.33 4.033 2.34 2.34 0 0 0-3.319 1.915 2.34 2.34 0 0 1-4.659 0 2.34 2.34 0 0 0-3.32-1.915 2.34 2.34 0 0 1-2.33-4.033 2.34 2.34 0 0 0 0-3.831A2.34 2.34 0 0 1 6.35 6.051a2.34 2.34 0 0 0 3.319-1.915",
            "M9 12a3 3 0 1 0 6 0a3 3 0 1 0-6 0"
        )
    }

    val Globe: ImageVector by lazy {
        lucideIcon(
            "globe",
            "M2 12a10 10 0 1 0 20 0a10 10 0 1 0-20 0",
            "M12 2a14.5 14.5 0 0 0 0 20 14.5 14.5 0 0 0 0-20",
            "M2 12h20"
        )
    }

    val Mail: ImageVector by lazy {
        lucideIcon(
            "mail",
            "m22 7-8.991 5.727a2 2 0 0 1-2.009 0L2 7",
            "M4 4h16a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z"
        )
    }

    val Star: ImageVector by lazy {
        lucideIcon(
            "star",
            "M11.525 2.295a.53.53 0 0 1 .95 0l2.31 4.679a2.123 2.123 0 0 0 1.595 1.16l5.166.756a.53.53 0 0 1 .294.904l-3.736 3.638a2.123 2.123 0 0 0-.611 1.878l.882 5.14a.53.53 0 0 1-.771.56l-4.618-2.428a2.122 2.122 0 0 0-1.973 0L6.396 21.01a.53.53 0 0 1-.77-.56l.881-5.139a2.122 2.122 0 0 0-.611-1.879L2.16 9.795a.53.53 0 0 1 .294-.906l5.165-.755a2.122 2.122 0 0 0 1.597-1.16z"
        )
    }

    /** Filled star for the picked rating in PIP gold. */
    val StarFilled: ImageVector by lazy {
        lucideIcon(
            "star-filled",
            "M11.525 2.295a.53.53 0 0 1 .95 0l2.31 4.679a2.123 2.123 0 0 0 1.595 1.16l5.166.756a.53.53 0 0 1 .294.904l-3.736 3.638a2.123 2.123 0 0 0-.611 1.878l.882 5.14a.53.53 0 0 1-.771.56l-4.618-2.428a2.122 2.122 0 0 0-1.973 0L6.396 21.01a.53.53 0 0 1-.77-.56l.881-5.139a2.122 2.122 0 0 0-.611-1.879L2.16 9.795a.53.53 0 0 1 .294-.906l5.165-.755a2.122 2.122 0 0 0 1.597-1.16z",
            filled = true
        )
    }

    val Bell: ImageVector by lazy {
        lucideIcon(
            "bell",
            "M10.268 21a2 2 0 0 0 3.464 0",
            "M3.262 15.326A1 1 0 0 0 4 17h16a1 1 0 0 0 .74-1.673C19.41 13.956 18 12.499 18 8A6 6 0 0 0 6 8c0 4.499-1.411 5.956-2.738 7.326"
        )
    }

    val Shield: ImageVector by lazy {
        lucideIcon(
            "shield",
            "M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z"
        )
    }

    val Info: ImageVector by lazy {
        lucideIcon(
            "info",
            "M2 12a10 10 0 1 0 20 0a10 10 0 1 0-20 0",
            "M12 16v-4",
            "M12 8h.01"
        )
    }

    val ExternalLink: ImageVector by lazy {
        lucideIcon(
            "external-link",
            "M15 3h6v6",
            "M10 14 21 3",
            "M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6"
        )
    }

    val MoreVertical: ImageVector by lazy {
        lucideIcon(
            "more-vertical",
            "M11 5a1 1 0 1 0 2 0a1 1 0 1 0-2 0",
            "M11 12a1 1 0 1 0 2 0a1 1 0 1 0-2 0",
            "M11 19a1 1 0 1 0 2 0a1 1 0 1 0-2 0"
        )
    }

    /** Horizontal ellipsis, the card menu affordance on My Packs. */
    val MoreHorizontal: ImageVector by lazy {
        lucideIcon(
            "more-horizontal",
            "M4 12a1 1 0 1 0 2 0a1 1 0 1 0-2 0",
            "M11 12a1 1 0 1 0 2 0a1 1 0 1 0-2 0",
            "M18 12a1 1 0 1 0 2 0a1 1 0 1 0-2 0"
        )
    }

    val Grid: ImageVector by lazy {
        lucideIcon(
            "grid",
            "M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z",
            "M3 9h18",
            "M3 15h18",
            "M9 3v18",
            "M15 3v18"
        )
    }

    val Bookmark: ImageVector by lazy {
        lucideIcon("bookmark", "m19 21-7-4-7 4V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2v16z")
    }

    val Home: ImageVector by lazy {
        lucideIcon(
            "home",
            "M15 21v-8a1 1 0 0 0-1-1h-4a1 1 0 0 0-1 1v8",
            "M3 10a2 2 0 0 1 .709-1.528l7-5.999a2 2 0 0 1 2.582 0l7 5.999A2 2 0 0 1 21 10v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"
        )
    }

    val Layers: ImageVector by lazy {
        lucideIcon(
            "layers",
            "M12.83 2.18a2 2 0 0 0-1.66 0L2.6 6.08a1 1 0 0 0 0 1.83l8.58 3.91a2 2 0 0 0 1.66 0l8.58-3.9a1 1 0 0 0 0-1.83z",
            "m22 17.65-9.17 4.16a2 2 0 0 1-1.66 0L2 17.65",
            "m22 12.65-9.17 4.16a2 2 0 0 1-1.66 0L2 12.65"
        )
    }

    val Smile: ImageVector by lazy {
        lucideIcon(
            "smile",
            "M2 12a10 10 0 1 0 20 0a10 10 0 1 0-20 0",
            "M8 14s1.5 2 4 2 4-2 4-2",
            "M9 9h.01",
            "M15 9h.01"
        )
    }

    val Sticker: ImageVector by lazy {
        lucideIcon(
            "sticker",
            "M21 9a2.4 2.4 0 0 0-.706-1.706l-3.588-3.588A2.4 2.4 0 0 0 15 3H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2z",
            "M15 3v5a1 1 0 0 0 1 1h5",
            "M8 13h.01",
            "M16 13h.01",
            "M10 16s.8 1 2 1c1.3 0 2-1 2-1"
        )
    }

    val RefreshCw: ImageVector by lazy {
        lucideIcon(
            "refresh-cw",
            "M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8",
            "M21 3v5h-5",
            "M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16",
            "M8 16H3v5"
        )
    }

    /** Counter-clockwise retry arrow the failed Add control shows. */
    val RotateCcw: ImageVector by lazy {
        lucideIcon(
            "rotate-ccw",
            "M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8",
            "M3 3v5h5"
        )
    }

    val AlertTriangle: ImageVector by lazy {
        lucideIcon(
            "alert-triangle",
            "m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3",
            "M12 9v4",
            "M12 17h.01"
        )
    }

    val WifiOff: ImageVector by lazy {
        lucideIcon(
            "wifi-off",
            "M12 20h.01",
            "M8.5 16.429a5 5 0 0 1 7 0",
            "M5 12.859a10 10 0 0 1 5.17-2.69",
            "M19 12.859a10 10 0 0 0-2.007-1.523",
            "M2 8.82a15 15 0 0 1 4.177-2.643",
            "M22 8.82a15 15 0 0 0-11.288-3.764",
            "m2 2 20 20"
        )
    }

    /** Open spinner arc; rotate it for an indeterminate spinner. */
    val Loader: ImageVector by lazy {
        lucideIcon("loader", "M21 12a9 9 0 1 1-6.219-8.56")
    }
}
