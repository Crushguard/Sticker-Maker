package com.piptechnologies.stickermaker.core.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piptechnologies.stickermaker.core.design.GreenSoft
import com.piptechnologies.stickermaker.core.design.Hanken
import com.piptechnologies.stickermaker.core.design.Ink
import com.piptechnologies.stickermaker.core.design.LoveIcons
import com.piptechnologies.stickermaker.core.design.LoveStickersTheme

private val ToastText = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.W600, fontSize = 13.5.sp)
private val ToastShape = RoundedCornerShape(13.dp)

/**
 * Ink-colored floating toast chip: white 13.5/600 text, 13 radius, soft drop
 * shadow, an optional leading icon (the confirmation check reads soft green).
 */
@Composable
fun DarkToast(
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTint: Color = GreenSoft
) {
    Row(
        modifier = modifier
            .shadow(14.dp, ToastShape, spotColor = Color(0x59141E3C))
            .clip(ToastShape)
            .background(Ink)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(15.dp), tint = iconTint)
            Spacer(Modifier.width(9.dp))
        }
        Text(message, style = ToastText, color = Color.White)
    }
}

/**
 * Snackbar host that renders every message as a centered [DarkToast].
 * Show messages with [showToast]; prefix has no special meaning - pass
 * [withCheck] there to control the leading check.
 */
@Composable
fun ToastHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    SnackbarHost(hostState = hostState, modifier = modifier) { data ->
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            val (message, withCheck) = decodeToast(data.visuals.message)
            DarkToast(
                message = message,
                icon = if (withCheck) LoveIcons.Check else null
            )
        }
    }
}

private const val CHECK_PREFIX = "\u0001ok\u0001"

/**
 * Queues a toast on this host. The design's confirmations ("Added to WhatsApp")
 * lead with a green check; set [withCheck] for those.
 */
suspend fun SnackbarHostState.showToast(message: String, withCheck: Boolean = false) {
    showSnackbar(
        message = if (withCheck) CHECK_PREFIX + message else message,
        duration = SnackbarDuration.Short
    )
}

private fun decodeToast(raw: String): Pair<String, Boolean> =
    if (raw.startsWith(CHECK_PREFIX)) raw.removePrefix(CHECK_PREFIX) to true else raw to false

@Preview(showBackground = true, backgroundColor = 0xFFFAFBFC)
@Composable
private fun DarkToastPreview() {
    LoveStickersTheme {
        DarkToast(
            message = "Added to WhatsApp",
            icon = LoveIcons.Check,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFAFBFC)
@Composable
private fun DarkToastPlainPreview() {
    LoveStickersTheme {
        DarkToast(
            message = "Saved · find it under Saved on Home",
            modifier = Modifier.padding(16.dp)
        )
    }
}
