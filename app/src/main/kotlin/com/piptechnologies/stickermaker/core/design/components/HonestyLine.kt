package com.piptechnologies.stickermaker.core.design.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piptechnologies.stickermaker.core.design.Hanken
import com.piptechnologies.stickermaker.core.design.LoveIcons
import com.piptechnologies.stickermaker.core.design.LoveStickersTheme

// The honesty line's quiet grey, lighter than Muted.
private val HonestyColor = Color(0xFF99A0AC)

private val HonestyText = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.W400, fontSize = 12.sp)

/**
 * The promise under every entry point (splash, onboarding): a 12dp
 * heart-handshake glyph and "Free · No ads · No account", centered.
 */
@Composable
fun HonestyLine(
    modifier: Modifier = Modifier,
    text: String = "Free · No ads · No account",
    icon: ImageVector? = LoveIcons.HeartHandshake,
    color: Color = HonestyColor
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(12.dp), tint = color)
            Spacer(Modifier.size(6.dp))
        }
        Text(text, style = HonestyText, color = color)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFAFBFC, widthDp = 390)
@Composable
private fun HonestyLinePreview() {
    LoveStickersTheme { HonestyLine(modifier = Modifier.padding(vertical = 12.dp)) }
}
