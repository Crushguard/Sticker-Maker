package com.piptechnologies.stickermaker.core.design.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.piptechnologies.stickermaker.core.design.Ink
import com.piptechnologies.stickermaker.core.design.LoveIcons
import com.piptechnologies.stickermaker.core.design.LoveStickersTheme
import com.piptechnologies.stickermaker.core.design.Muted
import com.piptechnologies.stickermaker.core.design.Subtle
import com.piptechnologies.stickermaker.core.design.Surface

private val SegmentText = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.W600, fontSize = 13.sp)
private val TrackShape = RoundedCornerShape(12.dp)
private val ThumbShape = RoundedCornerShape(9.dp)

/**
 * Two-to-three option segmented control (Photos / Camera / Video on Create):
 * subtle track, 12 radius, 4dp inset, white 38dp thumb that slides under the
 * selected option with [animateDpAsState].
 *
 * @param icons optional 16dp leading glyphs, index-aligned with [options]
 * (null entries render text only).
 */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    icons: List<ImageVector?>? = null
) {
    require(options.isNotEmpty()) { "SegmentedControl needs at least one option" }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clip(TrackShape)
            .background(Subtle)
            .padding(4.dp)
    ) {
        val gap = 2.dp
        val segmentWidth = (maxWidth - gap * (options.size - 1)) / options.size
        val thumbOffset by animateDpAsState(
            targetValue = (segmentWidth + gap) * selectedIndex.coerceIn(0, options.lastIndex),
            label = "segThumb"
        )
        // Sliding white thumb.
        Box(
            Modifier
                .offset(x = thumbOffset)
                .width(segmentWidth)
                .height(38.dp)
                .shadow(1.dp, ThumbShape, spotColor = Color(0x0F000000))
                .clip(ThumbShape)
                .background(Surface)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            options.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                val fg = if (selected) Ink else Muted
                Row(
                    modifier = Modifier
                        .width(segmentWidth)
                        .height(38.dp)
                        .clip(ThumbShape)
                        .clickable(
                            role = Role.Tab,
                            onClickLabel = label
                        ) { onSelect(index) },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val icon = icons?.getOrNull(index)
                    if (icon != null) {
                        Icon(icon, null, Modifier.size(16.dp), tint = fg)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(label, style = SegmentText, color = fg)
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFAFBFC, widthDp = 350)
@Composable
private fun SegmentedThreePreview() {
    LoveStickersTheme {
        SegmentedControl(
            options = listOf("Photos", "Camera", "Video"),
            selectedIndex = 0,
            onSelect = {},
            icons = listOf(LoveIcons.Images, LoveIcons.Camera, LoveIcons.Video),
            modifier = Modifier.padding(20.dp)
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFAFBFC, widthDp = 350)
@Composable
private fun SegmentedTwoPreview() {
    LoveStickersTheme {
        SegmentedControl(
            options = listOf("Stickers", "Animated"),
            selectedIndex = 1,
            onSelect = {},
            modifier = Modifier.padding(20.dp)
        )
    }
}
