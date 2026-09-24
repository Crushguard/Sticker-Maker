@file:OptIn(ExperimentalMaterial3Api::class)

package com.piptechnologies.stickermaker.core.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piptechnologies.stickermaker.core.design.Border
import com.piptechnologies.stickermaker.core.design.Destructive
import com.piptechnologies.stickermaker.core.design.Hanken
import com.piptechnologies.stickermaker.core.design.Ink
import com.piptechnologies.stickermaker.core.design.Ink2
import com.piptechnologies.stickermaker.core.design.LoveIcons
import com.piptechnologies.stickermaker.core.design.LoveStickersTheme
import com.piptechnologies.stickermaker.core.design.Rose
import com.piptechnologies.stickermaker.core.design.RoseTint
import com.piptechnologies.stickermaker.core.design.Subtle
import com.piptechnologies.stickermaker.core.design.Surface

// Red icon-box tint for destructive confirms; softer than DestructiveLine.
private val RedTint = Color(0xFFFBEAE5)

private val TitleText = TextStyle(
    fontFamily = Hanken, fontWeight = FontWeight.W700, fontSize = 18.sp, lineHeight = 23.sp
)
private val BodyText = TextStyle(
    fontFamily = Hanken, fontWeight = FontWeight.W400, fontSize = 13.5.sp, lineHeight = 20.sp
)
private val ButtonText = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.W600, fontSize = 15.sp)

/**
 * Confirmation sheet (RecoverMe pattern): icon box 44/13 (red tint when
 * destructive), title 18, body 13.5, then two equal 50/13 buttons - grey keep
 * and a filled confirm (destructive red, or rose for non-destructive asks like
 * "Get WhatsApp"). Tapping outside cancels.
 */
@Composable
fun ConfirmSheet(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    cancelLabel: String = "Keep",
    destructive: Boolean = true,
    icon: ImageVector = if (destructive) LoveIcons.Trash2 else LoveIcons.Info,
    sheetState: SheetState = rememberModalBottomSheetState()
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        containerColor = Surface,
        contentColor = Ink,
        scrimColor = Color(0xFF141A28).copy(alpha = 0.4f),
        dragHandle = null
    ) {
        ConfirmSheetContent(
            title = title,
            body = body,
            confirmLabel = confirmLabel,
            cancelLabel = cancelLabel,
            destructive = destructive,
            icon = icon,
            onConfirm = onConfirm,
            onCancel = onDismiss
        )
    }
}

/** The sheet's inner layout, exposed for previews and custom hosts. */
@Composable
fun ConfirmSheetContent(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    cancelLabel: String = "Keep",
    destructive: Boolean = true,
    icon: ImageVector = if (destructive) LoveIcons.Trash2 else LoveIcons.Info
) {
    val iconBg = if (destructive) RedTint else RoseTint
    val iconFg = if (destructive) Destructive else Rose
    Column(modifier = modifier.padding(start = 22.dp, top = 10.dp, end = 22.dp, bottom = 22.dp)) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, Modifier.size(22.dp), tint = iconFg)
        }
        Text(title, style = TitleText, color = Ink, modifier = Modifier.padding(top = 14.dp))
        Text(body, style = BodyText, color = Ink2, modifier = Modifier.padding(top = 6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(Subtle)
                    .border(1.dp, Border, RoundedCornerShape(13.dp))
                    .clickable(role = Role.Button, onClickLabel = cancelLabel, onClick = onCancel),
                contentAlignment = Alignment.Center
            ) {
                Text(cancelLabel, style = ButtonText, color = Ink)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(if (destructive) Destructive else Rose)
                    .clickable(role = Role.Button, onClickLabel = confirmLabel, onClick = onConfirm),
                contentAlignment = Alignment.Center
            ) {
                Text(confirmLabel, style = ButtonText, color = Color.White)
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, widthDp = 390)
@Composable
private fun ConfirmSheetDestructivePreview() {
    LoveStickersTheme {
        ConfirmSheetContent(
            title = "Remove “Clingy Mango” from this app?",
            body = "Deletes the local copy. WhatsApp keeps the pack until you remove it there.",
            confirmLabel = "Remove",
            cancelLabel = "Keep",
            onConfirm = {},
            onCancel = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, widthDp = 390)
@Composable
private fun ConfirmSheetRosePreview() {
    LoveStickersTheme {
        ConfirmSheetContent(
            title = "WhatsApp isn't installed",
            body = "Stickers are added inside WhatsApp. Install it, then come back to add this pack.",
            confirmLabel = "Get WhatsApp",
            cancelLabel = "Not now",
            destructive = false,
            icon = LoveIcons.MessageCircle,
            onConfirm = {},
            onCancel = {}
        )
    }
}
