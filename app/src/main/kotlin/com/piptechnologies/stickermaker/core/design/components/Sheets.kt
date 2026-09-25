@file:OptIn(ExperimentalMaterial3Api::class)

package com.piptechnologies.stickermaker.core.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piptechnologies.stickermaker.R
import com.piptechnologies.stickermaker.core.design.Destructive
import com.piptechnologies.stickermaker.core.design.Hanken
import com.piptechnologies.stickermaker.core.design.Ink
import com.piptechnologies.stickermaker.core.design.Ink2
import com.piptechnologies.stickermaker.core.design.LoveIcons
import com.piptechnologies.stickermaker.core.design.LoveStickersTheme
import com.piptechnologies.stickermaker.core.design.Surface
import com.piptechnologies.stickermaker.core.ui.inLayoutDirection

// Drag handle grey from the sheet spec (between Border and BorderStrong).
private val HandleColor = Color(0xFFE1E5EB)

private val SheetTitleText = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.W700, fontSize = 15.sp)
private val RowText = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.W600, fontSize = 14.5.sp)

/**
 * Bottom sheet in the app's dress: 24dp top radius, white surface, the 36x4
 * drag handle. Card menus, the simulated WhatsApp confirm and the rating sheet
 * all sit on this.
 */
@Composable
fun LoveBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    showDragHandle: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = Surface,
        contentColor = Ink,
        scrimColor = Color(0xFF14161C).copy(alpha = 0.42f),
        dragHandle = if (showDragHandle) {
            { SheetDragHandle() }
        } else null,
        content = content
    )
}

/** The 36x4 pill handle every sheet carries. */
@Composable
fun SheetDragHandle(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(top = 14.dp, bottom = 10.dp)
            .size(width = 36.dp, height = 4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(HandleColor)
    )
}

/**
 * Sheet title row: 15/700 title with an optional 40dp close button on the end.
 */
@Composable
fun SheetHeader(
    title: String,
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null
) {
    val closeLabel = stringResource(R.string.common_close)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = SheetTitleText.inLayoutDirection(), color = Ink, modifier = Modifier.weight(1f))
        if (onClose != null) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(role = Role.Button, onClickLabel = closeLabel, onClick = onClose),
                contentAlignment = Alignment.Center
            ) {
                Icon(LoveIcons.X, closeLabel, Modifier.size(18.dp), tint = Ink2)
            }
        }
    }
}

/**
 * 50dp menu row for card menus (Re-add to WhatsApp, Remove from this app, ...).
 * Destructive rows read in the destructive red.
 */
@Composable
fun SheetListRow(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = false
) {
    val fg = if (destructive) Destructive else Ink
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, Modifier.size(19.dp), tint = fg)
        Spacer(Modifier.width(12.dp))
        Text(label, style = RowText, color = fg)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, widthDp = 390)
@Composable
private fun SheetContentPreview() {
    // ModalBottomSheet needs a window, so the preview shows the sheet's inner layout.
    LoveStickersTheme {
        Column(Modifier.background(Surface).padding(horizontal = 12.dp, vertical = 4.dp)) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { SheetDragHandle() }
            SheetHeader(title = "Clingy Mango", onClose = {})
            SheetListRow(label = "Re-add to WhatsApp", icon = LoveIcons.RefreshCw, onClick = {})
            SheetListRow(
                label = "Remove from this app",
                icon = LoveIcons.Trash2,
                onClick = {},
                destructive = true
            )
        }
    }
}
