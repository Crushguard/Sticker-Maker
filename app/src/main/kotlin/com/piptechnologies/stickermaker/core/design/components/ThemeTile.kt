package com.piptechnologies.stickermaker.core.design.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.piptechnologies.stickermaker.core.design.Ink
import com.piptechnologies.stickermaker.core.design.LoveIcons
import com.piptechnologies.stickermaker.core.design.LoveStickersTheme
import com.piptechnologies.stickermaker.core.design.Rose
import com.piptechnologies.stickermaker.core.design.RoseTint
import com.piptechnologies.stickermaker.core.design.Surface

// Unchecked check-square border, one step darker than Border.
private val CheckIdleBorder = Color(0xFFD8DCE3)

private val TileText = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.W600, fontSize = 14.sp)
private val TileShape = RoundedCornerShape(16.dp)
private val IconBoxShape = RoundedCornerShape(12.dp)
private val CheckShape = RoundedCornerShape(7.dp)

/**
 * Customization-screen theme tile: 16-radius card at least 66dp tall with a
 * 40dp hue-tinted icon box, the theme label, and a 22/7 check square. Selected
 * turns rose tint with a rose border; the check square fills rose.
 *
 * @param hue theme hue in degrees; resolves tint and icon color via [LoveHue]
 * (Couples 10, Romantic 45, Funny 85, Long distance 150, Flirty 200,
 * Good night 250, Anime 300, Cute 330).
 */
@Composable
fun ThemeTile(
    label: String,
    icon: ImageVector,
    hue: Int,
    selected: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val bg by animateColorAsState(if (selected) RoseTint else Surface, label = "tileBg")
    val line by animateColorAsState(if (selected) Rose else Border, label = "tileLine")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 66.dp)
            .clip(TileShape)
            .background(bg)
            .border(1.dp, line, TileShape)
            .toggleable(value = selected, role = Role.Checkbox, onValueChange = onToggle)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(IconBoxShape)
                .background(LoveHue.tint(hue)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, Modifier.size(20.dp), tint = LoveHue.deep(hue))
        }
        Text(label, style = TileText, color = Ink, modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CheckShape)
                .background(if (selected) Rose else Surface)
                .border(1.dp, if (selected) Rose else CheckIdleBorder, CheckShape),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Icon(LoveIcons.Check, null, Modifier.size(14.dp), tint = Color.White)
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFAFBFC, widthDp = 200)
@Composable
private fun ThemeTileSelectedPreview() {
    LoveStickersTheme {
        ThemeTile(
            label = "Couples",
            icon = LoveIcons.HeartHandshake,
            hue = 10,
            selected = true,
            onToggle = {},
            modifier = Modifier.padding(10.dp)
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFAFBFC, widthDp = 200)
@Composable
private fun ThemeTileIdlePreview() {
    LoveStickersTheme {
        ThemeTile(
            label = "Good night",
            icon = LoveIcons.Moon,
            hue = 250,
            selected = false,
            onToggle = {},
            modifier = Modifier.padding(10.dp)
        )
    }
}
