package com.piptechnologies.stickermaker.core.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piptechnologies.stickermaker.core.design.Border
import com.piptechnologies.stickermaker.core.design.Ink
import com.piptechnologies.stickermaker.core.design.LoveIcons
import com.piptechnologies.stickermaker.core.design.LoveStickersTheme
import com.piptechnologies.stickermaker.core.design.Mono
import com.piptechnologies.stickermaker.core.design.Muted
import com.piptechnologies.stickermaker.core.design.Rose
import com.piptechnologies.stickermaker.core.design.Subtle
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement

private val LabelText = TextStyle(fontFamily = Mono, fontWeight = FontWeight.W500, fontSize = 9.5.sp)
private val CheckShape = RoundedCornerShape(7.dp)

/**
 * Square sticker cell used in the detail grid (radius 16, pack tint) and the
 * create-flow picker (radius 14, subtle bg, rose selection ring + check).
 *
 * @param selected draws the 2dp rose ring and, with [showCheckWhenSelected],
 * the 22/7 rose check square in the top-right corner.
 * @param onDelete shows a small ink delete badge in the top-right instead of
 * the check (own-pack editing).
 * @param label optional mono caption pinned bottom-start (picker frame ids).
 * @param rotation slight playful tilt the detail grid applies per tile.
 * @param bordered draws the 1dp hairline; the pack page's grid has none.
 */
@Composable
fun StickerTile(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    selected: Boolean = false,
    showCheckWhenSelected: Boolean = true,
    onDelete: (() -> Unit)? = null,
    background: Color = Subtle,
    radius: Dp = 14.dp,
    contentPadding: Dp = 8.dp,
    rotation: Float = 0f,
    bordered: Boolean = true,
    label: String? = null,
    contentDescription: String? = null,
    content: @Composable () -> Unit = {}
) {
    val shape = RoundedCornerShape(radius)
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .rotate(rotation)
            .clip(shape)
            .background(background)
            .then(
                if (bordered || selected) {
                    Modifier.border(
                        width = if (selected) 2.dp else 1.dp,
                        color = if (selected) Rose else Border,
                        shape = shape
                    )
                } else Modifier
            )
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        role = Role.Button,
                        onClickLabel = contentDescription,
                        onClick = onClick
                    )
                } else Modifier
            )
    ) {
        Box(
            modifier = Modifier
                .padding(contentPadding)
                .align(Alignment.Center)
        ) { content() }
        if (label != null) {
            Text(
                label,
                style = LabelText,
                color = Muted,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 8.dp, bottom = 6.dp)
            )
        }
        when {
            onDelete != null -> Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 6.dp, end = 6.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Ink)
                    .clickable(role = Role.Button, onClickLabel = "Remove sticker", onClick = onDelete),
                contentAlignment = Alignment.Center
            ) {
                Icon(LoveIcons.X, "Remove sticker", Modifier.size(12.dp), tint = Color.White)
            }
            selected && showCheckWhenSelected -> Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 6.dp, end = 6.dp)
                    .size(22.dp)
                    .clip(CheckShape)
                    .background(Rose),
                contentAlignment = Alignment.Center
            ) {
                Icon(LoveIcons.Check, null, Modifier.size(14.dp), tint = Color.White)
            }
        }
    }
}

/**
 * The add(+) cell that ends the create-flow grid: same footprint as a
 * [StickerTile], plus glyph centered, for growing the pack.
 */
@Composable
fun AddStickerTile(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String = "Add a sticker",
    radius: Dp = 14.dp
) {
    val shape = RoundedCornerShape(radius)
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(Subtle)
            .border(1.dp, Border, shape)
            .clickable(role = Role.Button, onClickLabel = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(LoveIcons.Plus, contentDescription, Modifier.size(22.dp), tint = Muted)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFAFBFC)
@Composable
private fun StickerTilePreview() {
    LoveStickersTheme {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StickerTile(
                modifier = Modifier.size(96.dp),
                background = Color(0xFFFFDFD0),
                radius = 16.dp,
                rotation = -3f
            ) {
                Icon(LoveIcons.HeartFilled, null, Modifier.size(34.dp), tint = Color(0xFFB2511E))
            }
            StickerTile(
                modifier = Modifier.size(96.dp),
                onClick = {},
                selected = true,
                label = "P4"
            ) {
                Icon(LoveIcons.Smile, null, Modifier.size(30.dp), tint = Muted)
            }
            StickerTile(
                modifier = Modifier.size(96.dp),
                onDelete = {}
            ) {
                Icon(LoveIcons.Sticker, null, Modifier.size(30.dp), tint = Muted)
            }
            AddStickerTile(onClick = {}, modifier = Modifier.size(96.dp))
        }
    }
}
