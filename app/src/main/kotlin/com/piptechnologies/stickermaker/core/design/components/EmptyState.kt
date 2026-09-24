package com.piptechnologies.stickermaker.core.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piptechnologies.stickermaker.core.design.Hanken
import com.piptechnologies.stickermaker.core.design.Ink
import com.piptechnologies.stickermaker.core.design.Ink2
import com.piptechnologies.stickermaker.core.design.LoveIcons
import com.piptechnologies.stickermaker.core.design.LoveStickersTheme
import com.piptechnologies.stickermaker.core.design.Rose
import com.piptechnologies.stickermaker.core.design.Subtle

// Icon grey inside the tinted box, and the reassurance-line grey.
private val IconMuted = Color(0xFFB4BAC4)
private val FootnoteColor = Color(0xFF99A0AC)

private val BodyText = TextStyle(
    fontFamily = Hanken, fontWeight = FontWeight.W400, fontSize = 13.5.sp, lineHeight = 20.sp
)
private val PrimaryText = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.W600, fontSize = 14.5.sp)
private val GhostText = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.W600, fontSize = 14.sp)
private val FootnoteText = TextStyle(
    fontFamily = Hanken, fontWeight = FontWeight.W400, fontSize = 12.sp, lineHeight = 18.sp
)

/**
 * Centered empty state: icon in a subtle tinted box (56/16, or 64/20 when
 * [large], as on offline Home), title, body, an optional rose primary and an
 * optional ghost action, plus an optional reassurance footnote.
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    large: Boolean = false,
    iconTint: Color = IconMuted,
    iconBackground: Color = Subtle,
    primaryLabel: String? = null,
    onPrimary: (() -> Unit)? = null,
    ghostLabel: String? = null,
    onGhost: (() -> Unit)? = null,
    footnote: String? = null
) {
    val boxSize = if (large) 64.dp else 56.dp
    val boxRadius = if (large) 20.dp else 16.dp
    val titleStyle = TextStyle(
        fontFamily = Hanken,
        fontWeight = FontWeight.W700,
        fontSize = if (large) 18.sp else 17.sp
    )
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(boxSize)
                .clip(RoundedCornerShape(boxRadius))
                .background(iconBackground),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, Modifier.size(if (large) 28.dp else 24.dp), tint = iconTint)
        }
        Text(title, style = titleStyle, color = Ink, modifier = Modifier.padding(top = if (large) 16.dp else 14.dp))
        Text(
            body,
            style = BodyText,
            color = Ink2,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(top = 6.dp)
                .widthIn(max = 250.dp)
        )
        if (primaryLabel != null && onPrimary != null) {
            Box(
                modifier = Modifier
                    .padding(top = 18.dp)
                    .height(46.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(Rose)
                    .clickable(role = Role.Button, onClickLabel = primaryLabel, onClick = onPrimary)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(primaryLabel, style = PrimaryText, color = Color.White)
            }
        }
        if (ghostLabel != null && onGhost != null) {
            Box(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .height(42.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .clickable(role = Role.Button, onClickLabel = ghostLabel, onClick = onGhost)
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(ghostLabel, style = GhostText, color = Ink2)
            }
        }
        if (footnote != null) {
            Text(
                footnote,
                style = FootnoteText,
                color = FootnoteColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFAFBFC, widthDp = 390)
@Composable
private fun EmptyStateOfflinePreview() {
    LoveStickersTheme {
        EmptyState(
            icon = LoveIcons.WifiOff,
            title = "You're offline",
            body = "Packs stream from the cloud. Check your connection and try again.",
            large = true,
            primaryLabel = "Retry",
            onPrimary = {},
            footnote = "Packs you already added still work in WhatsApp.",
            modifier = Modifier.padding(vertical = 40.dp)
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFAFBFC, widthDp = 390)
@Composable
private fun EmptyStateMyPacksPreview() {
    LoveStickersTheme {
        EmptyState(
            icon = LoveIcons.Sticker,
            title = "Nothing here yet",
            body = "Add a pack from Home, or make your own from photos.",
            primaryLabel = "Browse packs",
            onPrimary = {},
            ghostLabel = "Make your own",
            onGhost = {},
            modifier = Modifier.padding(vertical = 40.dp)
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFAFBFC, widthDp = 390)
@Composable
private fun EmptyStateSavedPreview() {
    LoveStickersTheme {
        EmptyState(
            icon = LoveIcons.Heart,
            title = "No saved packs",
            body = "Tap the heart on any pack to keep it here.",
            primaryLabel = "Browse packs",
            onPrimary = {},
            modifier = Modifier.padding(vertical = 40.dp)
        )
    }
}
