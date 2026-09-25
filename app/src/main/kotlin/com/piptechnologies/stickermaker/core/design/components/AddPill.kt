package com.piptechnologies.stickermaker.core.design.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piptechnologies.stickermaker.R
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
import com.piptechnologies.stickermaker.core.design.Subtle
import kotlin.math.roundToInt

/**
 * The add-to-WhatsApp state machine every Add control renders:
 * idle -> downloading -> sent -> added, with failed + retry on the side.
 */
enum class AddVisualState { Idle, Downloading, Sent, Added, Failed }

// Green-tinted circle the compact Added pill uses (My Packs cards); not in the core palette.
private val GreenTintBg = Color(0xFFE2F3EA)
private val GreenTintLine = Color(0xFFCDEBDC)

private val PillText = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.W600, fontSize = 13.sp)

/**
 * Compact 36dp add pill used on pack cards.
 * Downloading shows a rose fill growing left to right with the percentage;
 * the whole control keeps a 44dp touch target around the 36dp visual.
 *
 * @param progress 0..1, only read while [state] is [AddVisualState.Downloading].
 * @param iconOnlyWhenAdded collapses the Added state to a 36dp green-tinted check circle
 * (the My Packs card variant).
 */
@Composable
fun AddPill(
    state: AddVisualState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    progress: Float = 0f,
    iconOnlyWhenAdded: Boolean = false,
    idleLabel: String = stringResource(R.string.add_pill_add),
    sentLabel: String = stringResource(R.string.add_pill_sent),
    addedLabel: String = stringResource(R.string.add_pill_added),
    failedLabel: String = stringResource(R.string.add_pill_retry)
) {
    val compactAdded = iconOnlyWhenAdded && state == AddVisualState.Added
    val bg by animateColorAsState(
        targetValue = when (state) {
            AddVisualState.Idle, AddVisualState.Downloading, AddVisualState.Sent -> RoseTint
            AddVisualState.Added -> if (compactAdded) GreenTintBg else Subtle
            AddVisualState.Failed -> AmberTint
        },
        label = "pillBg"
    )
    val lineColor = when (state) {
        AddVisualState.Idle, AddVisualState.Downloading, AddVisualState.Sent -> RoseLine
        AddVisualState.Added -> if (compactAdded) GreenTintLine else Border
        AddVisualState.Failed -> AmberLine
    }
    val fg = when (state) {
        AddVisualState.Idle, AddVisualState.Downloading, AddVisualState.Sent -> Rose
        AddVisualState.Added -> Green
        AddVisualState.Failed -> Amber
    }
    val fill by animateFloatAsState(
        targetValue = if (state == AddVisualState.Downloading) progress.coerceIn(0f, 1f) else 0f,
        label = "pillFill"
    )
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val stateLabel = when (state) {
        AddVisualState.Idle -> idleLabel
        AddVisualState.Downloading ->
            stringResource(R.string.add_pill_downloading, (progress.coerceIn(0f, 1f) * 100).roundToInt())
        AddVisualState.Sent -> sentLabel
        AddVisualState.Added -> addedLabel
        AddVisualState.Failed -> failedLabel
    }

    // Outer box carries the 44dp touch target; the pill inside stays 36dp as designed.
    Box(
        modifier = modifier
            .sizeIn(minWidth = 44.dp, minHeight = 44.dp)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClickLabel = stateLabel,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .height(36.dp)
                .then(if (compactAdded) Modifier.width(36.dp) else Modifier.widthIn(min = if (state == AddVisualState.Downloading) 72.dp else 0.dp))
                .alpha(if (pressed) 0.82f else 1f)
                .clip(LoveShapes.Pill)
                .background(bg)
                .border(1.dp, lineColor, LoveShapes.Pill),
            contentAlignment = Alignment.Center
        ) {
            // Rose progress fill inside the control. matchParentSize keeps it out of
            // the pill's own measurement: sized against the row instead, the fill
            // stretched the pill as the download progressed.
            if (fill > 0f) {
                Box(Modifier.matchParentSize()) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fill)
                            .background(Rose.copy(alpha = 0.18f))
                    )
                }
            }
            Crossfade(targetState = state, label = "pillContent") { st ->
                Row(
                    modifier = Modifier.padding(
                        start = if (st == AddVisualState.Downloading || compactAdded) 0.dp else 11.dp,
                        end = if (st == AddVisualState.Downloading || compactAdded) 0.dp else 13.dp
                    ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    when (st) {
                        AddVisualState.Idle -> {
                            Icon(LoveIcons.MessageCircle, null, Modifier.size(15.dp), tint = fg)
                            Spacer(Modifier.width(6.dp))
                            Text(idleLabel, style = PillText, color = fg)
                        }
                        AddVisualState.Downloading ->
                            Text(
                                stringResource(R.string.common_percent, (progress.coerceIn(0f, 1f) * 100).roundToInt()),
                                style = PillText,
                                color = fg
                            )
                        AddVisualState.Sent -> {
                            CircularProgressIndicator(Modifier.size(13.dp), color = fg, strokeWidth = 2.dp)
                            Spacer(Modifier.width(6.dp))
                            Text(sentLabel, style = PillText, color = fg)
                        }
                        AddVisualState.Added -> {
                            Icon(LoveIcons.Check, null, Modifier.size(15.dp), tint = fg)
                            if (!compactAdded) {
                                Spacer(Modifier.width(6.dp))
                                Text(addedLabel, style = PillText, color = fg)
                            }
                        }
                        AddVisualState.Failed -> {
                            Icon(LoveIcons.RotateCcw, null, Modifier.size(14.dp), tint = fg)
                            Spacer(Modifier.width(6.dp))
                            Text(failedLabel, style = PillText, color = fg)
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun AddPillIdlePreview() {
    LoveStickersTheme { AddPill(state = AddVisualState.Idle, onClick = {}) }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun AddPillDownloadingPreview() {
    LoveStickersTheme { AddPill(state = AddVisualState.Downloading, progress = 0.64f, onClick = {}) }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun AddPillSentPreview() {
    LoveStickersTheme { AddPill(state = AddVisualState.Sent, onClick = {}) }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun AddPillAddedPreview() {
    LoveStickersTheme { AddPill(state = AddVisualState.Added, onClick = {}) }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun AddPillAddedCompactPreview() {
    LoveStickersTheme { AddPill(state = AddVisualState.Added, iconOnlyWhenAdded = true, onClick = {}) }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun AddPillFailedPreview() {
    LoveStickersTheme { AddPill(state = AddVisualState.Failed, onClick = {}) }
}
