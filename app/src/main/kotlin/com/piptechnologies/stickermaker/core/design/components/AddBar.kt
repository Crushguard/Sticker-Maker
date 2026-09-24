package com.piptechnologies.stickermaker.core.design.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piptechnologies.stickermaker.core.design.Amber
import com.piptechnologies.stickermaker.core.design.AmberLine
import com.piptechnologies.stickermaker.core.design.AmberTint
import com.piptechnologies.stickermaker.core.design.Border
import com.piptechnologies.stickermaker.core.design.Green
import com.piptechnologies.stickermaker.core.design.Hanken
import com.piptechnologies.stickermaker.core.design.LoveIcons
import com.piptechnologies.stickermaker.core.design.LoveShapes
import com.piptechnologies.stickermaker.core.design.LoveStickersTheme
import com.piptechnologies.stickermaker.core.design.Rose
import com.piptechnologies.stickermaker.core.design.RoseLine
import com.piptechnologies.stickermaker.core.design.RoseTint
import com.piptechnologies.stickermaker.core.design.Surface
import kotlin.math.roundToInt

private val BarText = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.W600, fontSize = 16.sp)
private val HintText = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.W400, fontSize = 11.5.sp)
private val HintColor = Color(0xFF99A0AC)

/**
 * Full-width 52/14 add bar pinned to the pack detail footer. Same state machine as
 * [AddPill], one size up: idle is the only filled rose primary on the screen, downloading
 * turns to rose tint with a growing fill, added flips to white with a green check.
 *
 * @param progress 0..1, only read while [state] is [AddVisualState.Downloading].
 * @param hint optional one-line explainer rendered centered under the bar
 * (pass null to render the bar alone).
 */
@Composable
fun AddBar(
    state: AddVisualState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    progress: Float = 0f,
    hint: String? = defaultAddBarHint(state),
    idleLabel: String = "Add to WhatsApp",
    downloadingLabel: String = "Downloading",
    sentLabel: String = "Sent to WhatsApp…",
    addedLabel: String = "Added to WhatsApp",
    failedLabel: String = "Download failed · Retry"
) {
    val bg by animateColorAsState(
        targetValue = when (state) {
            AddVisualState.Idle -> Rose
            AddVisualState.Downloading, AddVisualState.Sent -> RoseTint
            AddVisualState.Added -> Surface
            AddVisualState.Failed -> AmberTint
        },
        label = "barBg"
    )
    val fg = when (state) {
        AddVisualState.Idle -> Color.White
        AddVisualState.Downloading, AddVisualState.Sent -> Rose
        AddVisualState.Added -> Green
        AddVisualState.Failed -> Amber
    }
    val lineColor = when (state) {
        AddVisualState.Idle -> null
        AddVisualState.Downloading, AddVisualState.Sent -> RoseLine
        AddVisualState.Added -> Border
        AddVisualState.Failed -> AmberLine
    }
    val fill by animateFloatAsState(
        targetValue = if (state == AddVisualState.Downloading) progress.coerceIn(0f, 1f) else 0f,
        label = "barFill"
    )
    val pct = (progress.coerceIn(0f, 1f) * 100).roundToInt()

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(LoveShapes.Medium)
                .background(bg)
                .then(if (lineColor != null) Modifier.border(1.dp, lineColor, LoveShapes.Medium) else Modifier)
                .clickable(role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            if (fill > 0f) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .align(Alignment.CenterStart)
                        .fillMaxWidth(fill)
                        .background(Rose.copy(alpha = 0.16f))
                )
            }
            Crossfade(targetState = state, label = "barContent") { st ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    when (st) {
                        AddVisualState.Idle -> {
                            Icon(LoveIcons.MessageCircle, null, Modifier.size(19.dp), tint = fg)
                            Spacer(Modifier.width(8.dp))
                            Text(idleLabel, style = BarText, color = fg)
                        }
                        AddVisualState.Downloading -> {
                            Icon(LoveIcons.Download, null, Modifier.size(18.dp), tint = fg)
                            Spacer(Modifier.width(8.dp))
                            Text("$downloadingLabel · $pct%", style = BarText, color = fg)
                        }
                        AddVisualState.Sent -> {
                            CircularProgressIndicator(Modifier.size(16.dp), color = fg, strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(sentLabel, style = BarText, color = fg)
                        }
                        AddVisualState.Added -> {
                            Icon(LoveIcons.Check, null, Modifier.size(19.dp), tint = fg)
                            Spacer(Modifier.width(8.dp))
                            Text(addedLabel, style = BarText, color = fg)
                        }
                        AddVisualState.Failed -> {
                            Icon(LoveIcons.RotateCcw, null, Modifier.size(17.dp), tint = fg)
                            Spacer(Modifier.width(8.dp))
                            Text(failedLabel, style = BarText, color = fg)
                        }
                    }
                }
            }
        }
        if (hint != null) {
            Text(
                hint,
                style = HintText,
                color = HintColor,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
            )
        }
    }
}

/** The per-state explainer line the detail footer shows under the bar. */
fun defaultAddBarHint(state: AddVisualState): String = when (state) {
    AddVisualState.Idle -> "Downloads to your phone, then opens in WhatsApp."
    AddVisualState.Downloading -> "Streaming the pack from the cloud."
    AddVisualState.Sent -> "Confirm in WhatsApp to finish."
    AddVisualState.Added -> "Manage it in My Packs."
    AddVisualState.Failed -> "Check your connection, then tap to try again."
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, widthDp = 390)
@Composable
private fun AddBarIdlePreview() {
    LoveStickersTheme { AddBar(state = AddVisualState.Idle, onClick = {}, modifier = Modifier.padding(20.dp)) }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, widthDp = 390)
@Composable
private fun AddBarDownloadingPreview() {
    LoveStickersTheme {
        AddBar(state = AddVisualState.Downloading, progress = 0.64f, onClick = {}, modifier = Modifier.padding(20.dp))
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, widthDp = 390)
@Composable
private fun AddBarSentPreview() {
    LoveStickersTheme { AddBar(state = AddVisualState.Sent, onClick = {}, modifier = Modifier.padding(20.dp)) }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, widthDp = 390)
@Composable
private fun AddBarAddedPreview() {
    LoveStickersTheme { AddBar(state = AddVisualState.Added, onClick = {}, modifier = Modifier.padding(20.dp)) }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, widthDp = 390)
@Composable
private fun AddBarFailedPreview() {
    LoveStickersTheme { AddBar(state = AddVisualState.Failed, onClick = {}, modifier = Modifier.padding(20.dp)) }
}
