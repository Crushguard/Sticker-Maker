package com.piptechnologies.stickermaker.core.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.piptechnologies.stickermaker.core.design.Hanken
import com.piptechnologies.stickermaker.core.design.Ink
import com.piptechnologies.stickermaker.core.design.Ink2
import com.piptechnologies.stickermaker.core.design.LoveIcons
import com.piptechnologies.stickermaker.core.design.LoveStickersTheme
import com.piptechnologies.stickermaker.core.design.Rose

// Toolbar icon grey (between Ink and Ink2), from the top-bar spec.
private val ToolbarIcon = Color(0xFF3D4550)

/**
 * Back header used across screens: 56dp (52 on pack detail - pass [height]),
 * a 40dp back arrow, 700 title that ellipsizes, then trailing [actions]
 * (build them with [TopBarIconButton] for 44dp targets).
 */
@Composable
fun LoveTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    centered: Boolean = false,
    height: Dp = 56.dp,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val titleStyle = TextStyle(
        fontFamily = Hanken,
        fontWeight = FontWeight.W700,
        fontSize = if (height <= 52.dp) 16.sp else 17.sp
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (onBack != null) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(role = Role.Button, onClickLabel = "Back", onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(LoveIcons.ArrowLeft, "Back", Modifier.size(22.dp), tint = Ink)
            }
        }
        Text(
            title,
            style = titleStyle,
            color = Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start,
            modifier = Modifier
                .weight(1f)
                .padding(start = if (onBack == null) 8.dp else 0.dp)
        )
        actions()
    }
}

/** 44dp toolbar icon button (search, settings, heart, share). */
@Composable
fun TopBarIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = ToolbarIcon,
    badgeCount: Int? = null
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClickLabel = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription, Modifier.size(21.dp), tint = tint)
        if (badgeCount != null && badgeCount > 0) {
            Text(
                badgeCount.toString(),
                style = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.W700, fontSize = 10.sp),
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 5.dp, end = 4.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(Rose)
                    .padding(horizontal = 5.dp, vertical = 1.dp)
            )
        }
    }
}

/**
 * Display header for Home-style screens: 26/800 title and an optional
 * one-line subtitle, with optional trailing actions on the title line.
 */
@Composable
fun LargeHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = MaterialTheme.typography.displayLarge,
                color = Color(0xFF171A20),
                modifier = Modifier.weight(1f)
            )
            actions()
        }
        if (subtitle != null) {
            Text(
                subtitle,
                style = TextStyle(
                    fontFamily = Hanken,
                    fontWeight = FontWeight.W400,
                    fontSize = 14.sp,
                    lineHeight = 21.sp
                ),
                color = Ink2,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFAFBFC, widthDp = 390)
@Composable
private fun LoveTopBarPreview() {
    LoveStickersTheme {
        LoveTopBar(
            title = "Settings",
            onBack = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFAFBFC, widthDp = 390)
@Composable
private fun LoveTopBarDetailPreview() {
    LoveStickersTheme {
        LoveTopBar(
            title = "Clingy Mango",
            onBack = {},
            height = 52.dp,
            actions = {
                TopBarIconButton(LoveIcons.Heart, "Save pack", onClick = {})
                TopBarIconButton(LoveIcons.Send, "Share Love Stickers", onClick = {})
            }
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFAFBFC, widthDp = 390)
@Composable
private fun LargeHeaderPreview() {
    LoveStickersTheme {
        LargeHeader(
            title = "Pick your themes",
            subtitle = "Home shows packs from the themes you choose. Change them anytime in Settings.",
            modifier = Modifier.padding(vertical = 14.dp)
        )
    }
}
