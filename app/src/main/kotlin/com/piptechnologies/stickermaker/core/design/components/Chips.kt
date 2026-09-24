package com.piptechnologies.stickermaker.core.design.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piptechnologies.stickermaker.core.design.Border
import com.piptechnologies.stickermaker.core.design.Hanken
import com.piptechnologies.stickermaker.core.design.Ink2
import com.piptechnologies.stickermaker.core.design.LoveIcons
import com.piptechnologies.stickermaker.core.design.LoveShapes
import com.piptechnologies.stickermaker.core.design.LoveStickersTheme
import com.piptechnologies.stickermaker.core.design.Rose
import com.piptechnologies.stickermaker.core.design.RoseTint
import com.piptechnologies.stickermaker.core.design.Surface

private val ChipText = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.W600, fontSize = 13.sp)

/**
 * The eight theme hues from the design system (oklch .93 .045, hue only), with the
 * deeper foreground each tint pairs with. Keyed by the design's hue degrees.
 */
object LoveHue {
    val Tints: Map<Int, Color> = mapOf(
        10 to Color(0xFFFFDCE1), 45 to Color(0xFFFFDFD0), 85 to Color(0xFFF6E6C7),
        150 to Color(0xFFD4F1D8), 200 to Color(0xFFC6F2F4), 250 to Color(0xFFD2EBFF),
        300 to Color(0xFFECE2FF), 330 to Color(0xFFFADEF6)
    )
    val Deeps: Map<Int, Color> = mapOf(
        10 to Color(0xFFB3485F), 45 to Color(0xFFB2511E), 85 to Color(0xFF966800),
        150 to Color(0xFF1C8742), 200 to Color(0xFF008892), 250 to Color(0xFF1F74BF),
        300 to Color(0xFF7F5BB6), 330 to Color(0xFF9D4F98)
    )

    /** Design tint for [hue] degrees; falls back to an HSL approximation for other hues. */
    fun tint(hue: Int): Color = Tints[hue] ?: Color.hsl(hue.toFloat().mod(360f), 0.45f, 0.93f)

    /** Deep foreground for [hue] degrees; HSL approximation for hues outside the map. */
    fun deep(hue: Int): Color = Deeps[hue] ?: Color.hsl(hue.toFloat().mod(360f), 0.42f, 0.42f)
}

/**
 * 36dp filter chip from the Home chip row. Selected turns rose tint with a rose
 * border; pass a [hue] to tint the selected state with a theme hue instead.
 *
 * @param leadingIcon optional 13dp glyph (the Saved chip's filled heart).
 */
@Composable
fun CategoryChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    hue: Int? = null
) {
    val selectedBg = if (hue != null) LoveHue.tint(hue) else RoseTint
    val selectedFg = if (hue != null) LoveHue.deep(hue) else Rose
    val bg by animateColorAsState(if (selected) selectedBg else Surface, label = "chipBg")
    val fg by animateColorAsState(if (selected) selectedFg else Ink2, label = "chipFg")
    val line by animateColorAsState(if (selected) selectedFg else Border, label = "chipLine")

    Row(
        modifier = modifier
            .height(36.dp)
            .clip(LoveShapes.Pill)
            .background(bg)
            .border(1.dp, line, LoveShapes.Pill)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .padding(horizontal = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leadingIcon != null) {
            Icon(leadingIcon, null, Modifier.size(13.dp), tint = fg)
            Spacer(Modifier.width(6.dp))
        }
        Text(label, style = ChipText, color = fg)
    }
}

/**
 * Horizontally scrolling chip row with the design's 8dp gaps and 20dp side gutters.
 * Fill it with [CategoryChip] items through the LazyRow scope.
 */
@Composable
fun ChipRow(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp),
    content: LazyListScope.() -> Unit
) {
    LazyRow(
        modifier = modifier,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content
    )
}

@Preview(showBackground = true, backgroundColor = 0xFFFAFBFC, widthDp = 390)
@Composable
private fun ChipRowPreview() {
    LoveStickersTheme {
        ChipRow(modifier = Modifier.padding(vertical = 10.dp)) {
            item { CategoryChip("Trending", selected = true, onClick = {}) }
            item {
                CategoryChip("Saved · 2", selected = false, onClick = {}, leadingIcon = LoveIcons.HeartFilled)
            }
            item { CategoryChip("Animated", selected = false, onClick = {}) }
            item { CategoryChip("Couples", selected = false, onClick = {}) }
            item { CategoryChip("Cute", selected = false, onClick = {}) }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFAFBFC)
@Composable
private fun ChipHueSelectedPreview() {
    LoveStickersTheme {
        Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CategoryChip("Good night", selected = true, onClick = {}, hue = 250)
            CategoryChip("Funny", selected = true, onClick = {}, hue = 85)
        }
    }
}
