package com.piptechnologies.stickermaker.core.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.piptechnologies.stickermaker.core.design.Border
import com.piptechnologies.stickermaker.core.design.Ink2
import com.piptechnologies.stickermaker.core.design.LoveIcons
import com.piptechnologies.stickermaker.core.design.LoveShapes
import com.piptechnologies.stickermaker.core.design.LoveStickersTheme
import com.piptechnologies.stickermaker.core.design.Mono
import com.piptechnologies.stickermaker.core.design.Muted
import com.piptechnologies.stickermaker.core.design.Rose
import com.piptechnologies.stickermaker.core.design.Subtle
import com.piptechnologies.stickermaker.core.design.Surface

// Heart at rest; brighter than Muted2, straight from the card spec.
private val HeartIdle = Color(0xFFB4BAC4)

private val MetaText = TextStyle(fontFamily = Mono, fontWeight = FontWeight.W400, fontSize = 11.5.sp)
private val BadgeText = TextStyle(
    fontFamily = Mono,
    fontWeight = FontWeight.W600,
    fontSize = 9.sp,
    letterSpacing = 0.06.em
)

/**
 * Home-grid pack card: white surface, 20 radius, 1px border. Title 16/700 with an
 * ANIMATED badge, mono meta line ("24 stickers · 42.5K adds"), a heart (or an
 * overflow menu on My Packs), the [AddPill], then a strip of round sticker previews.
 *
 * @param downloadsLabel the second meta segment, e.g. "42.5K adds" (empty hides it).
 * @param thumbnails up to six preview slots, each clipped into a 46dp circle.
 * @param onFavoriteToggle shows the heart when non-null; [favorite] fills it rose.
 * @param onMenu shows the overflow ellipsis instead of the heart when non-null.
 */
@Composable
fun PackCard(
    title: String,
    stickerCount: Int,
    onAdd: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    downloadsLabel: String = "",
    animated: Boolean = false,
    addState: AddVisualState = AddVisualState.Idle,
    addProgress: Float = 0f,
    favorite: Boolean = false,
    onFavoriteToggle: (() -> Unit)? = null,
    onMenu: (() -> Unit)? = null,
    iconOnlyWhenAdded: Boolean = false,
    thumbnails: List<@Composable () -> Unit> = emptyList()
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(1.dp, LoveShapes.Large, spotColor = Color(0x0814161C))
            .clip(LoveShapes.Large)
            .background(Surface)
            .border(1.dp, Border, LoveShapes.Large)
            .clickable(onClick = onClick, onClickLabel = title)
            .padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (animated) AnimatedBadge()
                }
                Text(
                    if (downloadsLabel.isBlank()) "$stickerCount stickers"
                    else "$stickerCount stickers · $downloadsLabel",
                    style = MetaText,
                    color = Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            when {
                onFavoriteToggle != null -> CardIconButton(
                    icon = if (favorite) LoveIcons.HeartFilled else LoveIcons.Heart,
                    tint = if (favorite) Rose else HeartIdle,
                    contentDescription = if (favorite) "Remove from saved" else "Save pack",
                    onClick = onFavoriteToggle
                )
                onMenu != null -> CardIconButton(
                    icon = LoveIcons.MoreHorizontal,
                    tint = Ink2,
                    contentDescription = "Pack options",
                    onClick = onMenu
                )
            }
            AddPill(
                state = addState,
                progress = addProgress,
                iconOnlyWhenAdded = iconOnlyWhenAdded,
                onClick = onAdd
            )
        }
        if (thumbnails.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                thumbnails.take(6).forEach { thumb ->
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape),
                        contentAlignment = Alignment.Center
                    ) { thumb() }
                }
            }
        }
    }
}

/** Mono 9 "ANIMATED" chip: subtle bg, 1px border, 5 radius. */
@Composable
fun AnimatedBadge(modifier: Modifier = Modifier, label: String = "ANIMATED") {
    Text(
        label,
        style = BadgeText,
        color = Ink2,
        modifier = modifier
            .clip(RoundedCornerShape(5.dp))
            .background(Subtle)
            .border(1.dp, Border, RoundedCornerShape(5.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

/** 36dp icon button used inside the card row (heart, ellipsis). */
@Composable
private fun CardIconButton(
    icon: ImageVector,
    tint: Color,
    contentDescription: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(11.dp))
            .clickable(role = Role.Button, onClickLabel = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription, Modifier.size(20.dp), tint = tint)
    }
}

@Composable
private fun previewThumbs(bg: Color): List<@Composable () -> Unit> =
    List(6) {
        {
            Box(Modifier.size(46.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
                Icon(LoveIcons.HeartFilled, null, Modifier.size(18.dp), tint = Color(0xFFB2511E))
            }
        }
    }

@Preview(showBackground = true, backgroundColor = 0xFFFAFBFC, widthDp = 390)
@Composable
private fun PackCardIdlePreview() {
    LoveStickersTheme {
        PackCard(
            title = "Love Notes",
            stickerCount = 24,
            downloadsLabel = "42.5K adds",
            animated = true,
            onAdd = {},
            onClick = {},
            onFavoriteToggle = {},
            thumbnails = previewThumbs(Color(0xFFFFDFD0)),
            modifier = Modifier.padding(20.dp)
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFAFBFC, widthDp = 390)
@Composable
private fun PackCardDownloadingPreview() {
    LoveStickersTheme {
        PackCard(
            title = "Clingy Mango",
            stickerCount = 18,
            downloadsLabel = "96.4K adds",
            addState = AddVisualState.Downloading,
            addProgress = 0.64f,
            favorite = true,
            onAdd = {},
            onClick = {},
            onFavoriteToggle = {},
            thumbnails = previewThumbs(Color(0xFFFFDCE1)),
            modifier = Modifier.padding(20.dp)
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFAFBFC, widthDp = 390)
@Composable
private fun PackCardAddedMenuPreview() {
    LoveStickersTheme {
        PackCard(
            title = "Us, always",
            stickerCount = 26,
            downloadsLabel = "yours",
            addState = AddVisualState.Added,
            iconOnlyWhenAdded = true,
            onAdd = {},
            onClick = {},
            onMenu = {},
            thumbnails = previewThumbs(Color(0xFFD4F1D8)),
            modifier = Modifier.padding(20.dp)
        )
    }
}
