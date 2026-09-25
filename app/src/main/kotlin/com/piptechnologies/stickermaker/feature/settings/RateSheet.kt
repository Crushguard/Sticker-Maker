package com.piptechnologies.stickermaker.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.piptechnologies.stickermaker.R
import com.piptechnologies.stickermaker.core.design.Border
import com.piptechnologies.stickermaker.core.design.Gold
import com.piptechnologies.stickermaker.core.design.Green
import com.piptechnologies.stickermaker.core.design.Hanken
import com.piptechnologies.stickermaker.core.design.Ink
import com.piptechnologies.stickermaker.core.design.Ink2
import com.piptechnologies.stickermaker.core.design.LoveIcons
import com.piptechnologies.stickermaker.core.design.LoveStickersTheme
import com.piptechnologies.stickermaker.core.design.Muted
import com.piptechnologies.stickermaker.core.design.Rose
import com.piptechnologies.stickermaker.core.design.RoseTint
import com.piptechnologies.stickermaker.core.design.Surface
import com.piptechnologies.stickermaker.core.design.components.LoveBottomSheet
import kotlinx.coroutines.delay

/** A tap on a star is the answer; the sheet moves on by itself after this. */
private const val AUTO_ADVANCE_MS = 380L

// ---- Off-token colors from the prototype's rating sheet ---- //
private val StarOff = Color(0xFFD8DCE3)
private val GreenTint = Color(0xFFE2F3EA)

private val StageTitleStyle = TextStyle(
    fontFamily = Hanken, fontWeight = FontWeight.W700, fontSize = 20.sp,
    letterSpacing = (-0.01).em
)
private val FeedbackTitleStyle = TextStyle(
    fontFamily = Hanken, fontWeight = FontWeight.W700, fontSize = 19.sp,
    letterSpacing = (-0.01).em
)
private val StageBodyStyle = TextStyle(
    fontFamily = Hanken, fontWeight = FontWeight.W400, fontSize = 13.5.sp, lineHeight = 20.sp
)
private val HintStyle = TextStyle(
    fontFamily = Hanken, fontWeight = FontWeight.W400, fontSize = 11.5.sp
)
private val PrimaryButtonStyle = TextStyle(
    fontFamily = Hanken, fontWeight = FontWeight.W600, fontSize = 16.sp
)
private val GhostButtonStyle = TextStyle(
    fontFamily = Hanken, fontWeight = FontWeight.W600, fontSize = 14.5.sp
)
private val FeedbackFieldStyle = TextStyle(
    fontFamily = Hanken, fontWeight = FontWeight.W400, fontSize = 14.sp, lineHeight = 21.sp
)

internal enum class RateStage { Stars, Store, Feedback, Thanks }

/**
 * The "Rate us" sheet from the design: tap a star and it moves on by itself.
 * Five stars asks for a Google Play review; one to four opens a private
 * feedback box that ends on a thanks stage.
 *
 * @param onOpenStore Opens the Play listing; the sheet closes right after.
 * @param onSendFeedback Hands the note off (mail composer). Return true when
 * it launched, so the sheet can advance to the thanks stage.
 */
@Composable
internal fun RateSheet(
    onDismiss: () -> Unit,
    onOpenStore: () -> Unit,
    onSendFeedback: (String) -> Boolean
) {
    var stage by rememberSaveable { mutableStateOf(RateStage.Stars) }
    var rating by rememberSaveable { mutableIntStateOf(0) }
    var feedback by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(rating, stage) {
        if (stage == RateStage.Stars && rating > 0) {
            delay(AUTO_ADVANCE_MS)
            stage = if (rating >= 5) RateStage.Store else RateStage.Feedback
        }
    }

    LoveBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 22.dp, end = 22.dp, bottom = 24.dp)
                .imePadding()
        ) {
            when (stage) {
                RateStage.Stars -> RateStarsStage(
                    rating = rating,
                    onPick = { rating = it },
                    onLater = onDismiss
                )
                RateStage.Store -> RateStoreStage(
                    onStore = {
                        onOpenStore()
                        onDismiss()
                    },
                    onNotNow = onDismiss
                )
                RateStage.Feedback -> RateFeedbackStage(
                    text = feedback,
                    onTextChange = { feedback = it },
                    onSend = {
                        if (feedback.isNotBlank() && onSendFeedback(feedback)) {
                            stage = RateStage.Thanks
                        }
                    },
                    onCancel = onDismiss
                )
                RateStage.Thanks -> RateThanksStage(onDone = onDismiss)
            }
        }
    }
}

// ------------------------------------------------------------------ //
// Stages
// ------------------------------------------------------------------ //

@Composable
private fun RateStarsStage(
    rating: Int,
    onPick: (Int) -> Unit,
    onLater: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconTile(
            icon = LoveIcons.Star,
            iconSize = 32.dp,
            tileSize = 64.dp,
            corner = 19.dp,
            background = RoseTint,
            tint = Rose
        )
        Text(
            stringResource(R.string.rate_title),
            style = StageTitleStyle,
            color = Ink,
            modifier = Modifier.padding(top = 14.dp)
        )
        Text(
            stringResource(R.string.rate_body),
            style = StageBodyStyle,
            color = Ink2,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(top = 6.dp)
                .widthIn(max = 290.dp)
        )
        Row(
            modifier = Modifier.padding(top = 22.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (value in 1..5) {
                val picked = value <= rating
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .clickable(
                            role = Role.Button,
                            onClickLabel = starLabel(value),
                            onClick = { onPick(value) }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (picked) LoveIcons.StarFilled else LoveIcons.Star,
                        starLabel(value),
                        Modifier.size(38.dp),
                        tint = if (picked) Gold else StarOff
                    )
                }
            }
        }
        Text(
            stringResource(
                when {
                    rating == 0 -> R.string.rate_hint_idle
                    rating >= 5 -> R.string.rate_hint_five
                    else -> R.string.rate_hint_low
                }
            ),
            style = HintStyle,
            color = Muted,
            textAlign = TextAlign.Center
        )
        GhostButton(
            label = stringResource(R.string.rate_maybe_later),
            onClick = onLater,
            modifier = Modifier.padding(top = 14.dp)
        )
    }
}

/** "1 star" / "n stars", the prototype's aria labels. */
@Composable
private fun starLabel(value: Int): String = pluralStringResource(R.plurals.rate_star, value, value)

@Composable
private fun RateStoreStage(
    onStore: () -> Unit,
    onNotNow: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconTile(
            icon = SettingsIcons.PartyPopper,
            iconSize = 32.dp,
            tileSize = 64.dp,
            corner = 19.dp,
            background = GreenTint,
            tint = Green
        )
        Row(
            modifier = Modifier.padding(top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            repeat(5) {
                Icon(LoveIcons.StarFilled, null, Modifier.size(20.dp), tint = Gold)
            }
        }
        Text(
            stringResource(R.string.rate_store_title),
            style = StageTitleStyle,
            color = Ink,
            modifier = Modifier.padding(top = 14.dp)
        )
        Text(
            stringResource(R.string.rate_store_body),
            style = StageBodyStyle,
            color = Ink2,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(top = 6.dp)
                .widthIn(max = 290.dp)
        )
        RosePrimaryButton(
            label = stringResource(R.string.rate_store_cta),
            icon = LoveIcons.ExternalLink,
            onClick = onStore,
            modifier = Modifier.padding(top = 20.dp)
        )
        GhostButton(
            label = stringResource(R.string.common_not_now),
            onClick = onNotNow,
            modifier = Modifier.padding(top = 10.dp)
        )
    }
}

@Composable
private fun RateFeedbackStage(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onCancel: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        IconTile(
            icon = SettingsIcons.MessageSquareText,
            iconSize = 26.dp,
            tileSize = 52.dp,
            corner = 15.dp,
            background = RoseTint,
            tint = Rose
        )
        Text(
            stringResource(R.string.rate_feedback_title),
            style = FeedbackTitleStyle,
            color = Ink,
            modifier = Modifier.padding(top = 14.dp)
        )
        Text(
            stringResource(R.string.rate_feedback_body),
            style = StageBodyStyle,
            color = Ink2,
            modifier = Modifier.padding(top = 6.dp)
        )
        Box(
            modifier = Modifier
                .padding(top = 14.dp)
                .fillMaxWidth()
                .height(104.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Surface)
                .border(1.dp, Border, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 13.dp)
        ) {
            BasicTextField(
                value = text,
                onValueChange = onTextChange,
                textStyle = FeedbackFieldStyle.copy(color = Ink),
                cursorBrush = SolidColor(Rose),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { innerTextField ->
                    if (text.isEmpty()) {
                        Text(stringResource(R.string.rate_feedback_placeholder), style = FeedbackFieldStyle, color = Muted)
                    }
                    innerTextField()
                }
            )
        }
        RosePrimaryButton(
            label = stringResource(R.string.rate_feedback_cta),
            icon = SettingsIcons.Send,
            enabled = text.isNotBlank(),
            onClick = onSend,
            modifier = Modifier.padding(top = 14.dp)
        )
        GhostButton(
            label = stringResource(R.string.common_cancel),
            onClick = onCancel,
            modifier = Modifier.padding(top = 10.dp)
        )
    }
}

@Composable
private fun RateThanksStage(onDone: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconTile(
            icon = LoveIcons.Check,
            iconSize = 32.dp,
            tileSize = 64.dp,
            corner = 19.dp,
            background = GreenTint,
            tint = Green
        )
        Text(
            stringResource(R.string.rate_thanks_title),
            style = StageTitleStyle,
            color = Ink,
            modifier = Modifier.padding(top = 14.dp)
        )
        Text(
            stringResource(R.string.rate_thanks_body),
            style = StageBodyStyle,
            color = Ink2,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(top = 6.dp)
                .widthIn(max = 290.dp)
        )
        RosePrimaryButton(
            label = stringResource(R.string.common_done),
            onClick = onDone,
            height = 52.dp,
            corner = 14.dp,
            modifier = Modifier.padding(top = 20.dp)
        )
    }
}

// ------------------------------------------------------------------ //
// Bits
// ------------------------------------------------------------------ //

@Composable
private fun IconTile(
    icon: ImageVector,
    iconSize: Dp,
    tileSize: Dp,
    corner: Dp,
    background: Color,
    tint: Color
) {
    Box(
        modifier = Modifier
            .size(tileSize)
            .clip(RoundedCornerShape(corner))
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, Modifier.size(iconSize), tint = tint)
    }
}

@Composable
private fun RosePrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    height: Dp = 54.dp,
    corner: Dp = 15.dp,
    enabled: Boolean = true
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(RoundedCornerShape(corner))
            .background(Rose)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClickLabel = label,
                onClick = onClick
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(19.dp), tint = Color.White)
            Spacer(Modifier.width(9.dp))
        }
        Text(label, style = PrimaryButtonStyle, color = Color.White)
    }
}

@Composable
private fun GhostButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = GhostButtonStyle, color = Ink2)
    }
}

// ------------------------------------------------------------------ //
// Previews (sheet inner layouts; ModalBottomSheet needs a window)
// ------------------------------------------------------------------ //

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, widthDp = 390)
@Composable
private fun RateStarsStagePreview() {
    LoveStickersTheme {
        Column(Modifier.background(Surface).padding(start = 22.dp, end = 22.dp, bottom = 24.dp)) {
            RateStarsStage(rating = 0, onPick = {}, onLater = {})
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, widthDp = 390)
@Composable
private fun RateStoreStagePreview() {
    LoveStickersTheme {
        Column(Modifier.background(Surface).padding(start = 22.dp, end = 22.dp, bottom = 24.dp)) {
            RateStoreStage(onStore = {}, onNotNow = {})
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, widthDp = 390)
@Composable
private fun RateFeedbackStagePreview() {
    LoveStickersTheme {
        Column(Modifier.background(Surface).padding(start = 22.dp, end = 22.dp, bottom = 24.dp)) {
            RateFeedbackStage(text = "", onTextChange = {}, onSend = {}, onCancel = {})
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, widthDp = 390)
@Composable
private fun RateThanksStagePreview() {
    LoveStickersTheme {
        Column(Modifier.background(Surface).padding(start = 22.dp, end = 22.dp, bottom = 24.dp)) {
            RateThanksStage(onDone = {})
        }
    }
}
