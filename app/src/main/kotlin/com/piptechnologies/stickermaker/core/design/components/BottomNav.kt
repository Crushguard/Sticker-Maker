package com.piptechnologies.stickermaker.core.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piptechnologies.stickermaker.core.design.Hanken
import com.piptechnologies.stickermaker.core.design.LoveIcons
import com.piptechnologies.stickermaker.core.design.LoveStickersTheme
import com.piptechnologies.stickermaker.core.design.Muted2
import com.piptechnologies.stickermaker.core.design.Rose
import com.piptechnologies.stickermaker.core.design.Surface

// Nav hairline, one step lighter than Border.
private val NavHairline = Color(0xFFEBEEF2)

private val NavLabelText = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.W600, fontSize = 10.5.sp)

/** One tab of the bottom nav; [badge] draws a rose dot on the icon. */
data class LoveNavItem(
    val icon: ImageVector,
    val label: String,
    val selected: Boolean,
    val onClick: () -> Unit,
    val badge: Boolean = false
)

/**
 * 68dp bottom bar with the raised 52/16 rose Create button floating 18dp above
 * it in the middle (PDF Reader pattern). [items] split evenly around the
 * button - the design ships Home and My Packs, but any even split works.
 */
@Composable
fun LoveBottomNav(
    items: List<LoveNavItem>,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier,
    createContentDescription: String = "Create a pack",
    createIcon: ImageVector = LoveIcons.Plus
) {
    val leftCount = (items.size + 1) / 2
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(68.dp)
            .background(Surface)
            .drawBehind {
                drawRect(NavHairline, size = size.copy(height = 1.dp.toPx()))
            }
            .padding(horizontal = 6.dp)
    ) {
        items.take(leftCount).forEach { NavTab(it) }
        // Raised Create: 52/16, 18dp above the bar.
        Box(
            modifier = Modifier
                .width(64.dp)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .offset(y = (-18).dp)
                    .size(52.dp)
                    .shadow(12.dp, RoundedCornerShape(16.dp), spotColor = Rose.copy(alpha = 0.55f))
                    .clip(RoundedCornerShape(16.dp))
                    .background(Rose)
                    .clickable(role = Role.Button, onClickLabel = createContentDescription, onClick = onCreate),
                contentAlignment = Alignment.Center
            ) {
                Icon(createIcon, createContentDescription, Modifier.size(26.dp), tint = Color.White)
            }
        }
        items.drop(leftCount).forEach { NavTab(it) }
    }
}

@Composable
private fun RowScope.NavTab(item: LoveNavItem) {
    val tint = if (item.selected) Rose else Muted2
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable(
                role = Role.Tab,
                onClickLabel = item.label,
                onClick = item.onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box {
            Icon(item.icon, null, Modifier.size(21.dp), tint = tint)
            if (item.badge) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 3.dp, y = (-2).dp)
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(Rose)
                )
            }
        }
        Text(
            item.label,
            style = NavLabelText,
            color = tint,
            modifier = Modifier.padding(top = 3.dp)
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFAFBFC, widthDp = 390, heightDp = 110)
@Composable
private fun LoveBottomNavPreview() {
    LoveStickersTheme {
        Box(Modifier.padding(top = 30.dp)) {
            LoveBottomNav(
                items = listOf(
                    LoveNavItem(LoveIcons.Home, "Home", selected = true, onClick = {}),
                    LoveNavItem(LoveIcons.Sticker, "My Packs", selected = false, onClick = {})
                ),
                onCreate = {}
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFAFBFC, widthDp = 390, heightDp = 110)
@Composable
private fun LoveBottomNavBadgePreview() {
    LoveStickersTheme {
        Box(Modifier.padding(top = 30.dp)) {
            LoveBottomNav(
                items = listOf(
                    LoveNavItem(LoveIcons.Home, "Home", selected = false, onClick = {}),
                    LoveNavItem(LoveIcons.Sticker, "My Packs", selected = true, onClick = {}, badge = true)
                ),
                onCreate = {}
            )
        }
    }
}
