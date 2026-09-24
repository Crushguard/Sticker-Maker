package com.piptechnologies.stickermaker.feature.onboarding

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.annotation.DrawableRes
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.piptechnologies.stickermaker.R
import com.piptechnologies.stickermaker.core.design.Border
import com.piptechnologies.stickermaker.core.design.Hanken
import com.piptechnologies.stickermaker.core.design.LoveStickersTheme
import com.piptechnologies.stickermaker.core.design.Mono
import com.piptechnologies.stickermaker.core.design.Muted
import com.piptechnologies.stickermaker.core.design.Rose
import com.piptechnologies.stickermaker.core.design.Surface
import com.piptechnologies.stickermaker.core.design.components.HonestyLine
import com.piptechnologies.stickermaker.core.design.Canvas as CanvasColor

// Exact Prototype copy.
private const val SKIP_LABEL = "Skip"
private const val SLIDE1_TITLE = "Say it with a sticker"
private const val SLIDE1_BODY =
    "Curated love packs: cute, funny, romantic. One tap adds a pack to WhatsApp."
private const val SLIDE2_TITLE = "Make your own"
private const val SLIDE2_BODY =
    "Turn photos into stickers. Crop, cut out, add a word. Animated packs from short clips too."
private const val YOUR_PHOTO_LABEL = "YOUR PHOTO"
private const val CTA_NEXT = "Next"
private const val CTA_GET_STARTED = "Get started"
private const val HONESTY_TEXT = "Free · No ads · No account"
private const val STICKER_ALT =
    "Finished sticker: woman making a heart with her hands, white die-cut outline"
private const val PHOTO_ALT = "Original photo"

private val TitleInk = Color(0xFF171A20)
private val BodyInk = Color(0xFF565C67)
private val SkipInk = Color(0xFF626873)
private val DotIdle = Color(0xFFD8DCE3)

private val TitleText = TextStyle(
    fontFamily = Hanken,
    fontWeight = FontWeight.W800,
    fontSize = 26.sp,
    lineHeight = 30.sp,
    letterSpacing = (-0.02).em
)
private val BodyText = TextStyle(
    fontFamily = Hanken,
    fontWeight = FontWeight.W400,
    fontSize = 15.sp,
    lineHeight = 23.sp
)
private val SkipText = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.W600, fontSize = 13.5.sp)
private val CtaText = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.W600, fontSize = 16.sp)
private val PhotoChipText = TextStyle(
    fontFamily = Mono,
    fontWeight = FontWeight.W600,
    fontSize = 9.5.sp,
    letterSpacing = 0.06.em
)

/**
 * Two-slide onboarding (Prototype `is.onboarding`): sticker collage then the
 * photo-to-sticker composition, dot pager, one primary button ("Next" /
 * "Get started"), ghost Skip on the first slide only, honesty line under the
 * button. Both paths persist the onboarded flag before [onDone] fires.
 */
@Composable
fun OnboardingScreen(
    onDone: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val latestOnDone by rememberUpdatedState(onDone)
    LaunchedEffect(state.finished) {
        if (state.finished) latestOnDone()
    }
    OnboardingContent(
        page = state.page,
        onNext = viewModel::onNext,
        onSkip = viewModel::onSkip
    )
}

/** Stateless onboarding layout, previewable without Hilt. */
@Composable
fun OnboardingContent(
    page: Int,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CanvasColor)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = 24.dp, top = 4.dp, end = 24.dp, bottom = 26.dp)
    ) {
        // 40dp top strip; ghost Skip only on the first slide.
        Box(Modifier.fillMaxWidth().height(40.dp), contentAlignment = Alignment.CenterEnd) {
            if (page == 0) {
                Box(
                    modifier = Modifier
                        .height(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(role = Role.Button, onClickLabel = SKIP_LABEL, onClick = onSkip)
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(SKIP_LABEL, style = SkipText, color = SkipInk)
                }
            }
        }
        Crossfade(targetState = page, label = "obPage", modifier = Modifier.weight(1f)) { p ->
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (p == 0) SlideOneArt() else SlideTwoArt()
                Spacer(Modifier.height(26.dp))
                Text(
                    if (p == 0) SLIDE1_TITLE else SLIDE2_TITLE,
                    style = TitleText,
                    color = TitleInk,
                    textAlign = TextAlign.Center
                )
                Text(
                    if (p == 0) SLIDE1_BODY else SLIDE2_BODY,
                    style = BodyText,
                    color = BodyInk,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
        }
        // Dot pager: active dot stretches to 22dp in rose.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
        ) {
            PagerDot(active = page == 0)
            PagerDot(active = page == 1)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Rose)
                .clickable(role = Role.Button, onClick = onNext),
            contentAlignment = Alignment.Center
        ) {
            Text(if (page == 0) CTA_NEXT else CTA_GET_STARTED, style = CtaText, color = Color.White)
        }
        HonestyLine(text = HONESTY_TEXT, modifier = Modifier.padding(top = 14.dp))
    }
}

@Composable
private fun PagerDot(active: Boolean) {
    val width by animateDpAsState(if (active) 22.dp else 8.dp, label = "obDot")
    Box(
        Modifier
            .size(width = width, height = 8.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(if (active) Rose else DotIdle)
    )
}

/** Slide 1: four rotated catalog stickers in a loose collage (Prototype layout). */
@Composable
private fun SlideOneArt() {
    Box(Modifier.size(width = 290.dp, height = 250.dp)) {
        CollageSticker(R.drawable.ob_mango, x = 0.dp, y = 6.dp, side = 150.dp, rotation = -8f)
        CollageSticker(R.drawable.ob_words, x = 162.dp, y = 0.dp, side = 128.dp, rotation = 7f)
        CollageSticker(R.drawable.ob_flirty, x = 118.dp, y = 112.dp, side = 140.dp, rotation = -3f)
        CollageSticker(R.drawable.ob_gmgn, x = 8.dp, y = 150.dp, side = 104.dp, rotation = 9f)
    }
}

@Composable
private fun CollageSticker(@DrawableRes resId: Int, x: Dp, y: Dp, side: Dp, rotation: Float) {
    Image(
        painter = painterResource(resId),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .offset(x = x, y = y)
            .size(side)
            .rotate(rotation)
    )
}

/**
 * Slide 2: the finished die-cut sticker top right, a dashed rose arrow, the
 * original photo in a white-ringed circle bottom left, and a mono
 * "YOUR PHOTO" chip.
 */
@Composable
private fun SlideTwoArt() {
    Box(Modifier.size(width = 280.dp, height = 240.dp)) {
        Image(
            painter = painterResource(R.drawable.ob_sticker),
            contentDescription = STICKER_ALT,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .offset(x = 104.dp, y = 0.dp)
                .size(176.dp)
                .rotate(4f)
        )
        DashedArrow(
            modifier = Modifier
                .offset(x = 66.dp, y = 34.dp)
                .size(width = 74.dp, height = 58.dp)
        )
        Box(
            modifier = Modifier
                .offset(x = 0.dp, y = 112.dp)
                .size(128.dp)
                .rotate(-4f)
                .shadow(16.dp, CircleShape, spotColor = Color(0x591E141E))
                .clip(CircleShape)
                .background(Surface)
                .border(4.dp, Color.White, CircleShape)
        ) {
            Image(
                painter = painterResource(R.drawable.ob_photo),
                contentDescription = PHOTO_ALT,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Text(
            YOUR_PHOTO_LABEL,
            style = PhotoChipText,
            color = Muted,
            modifier = Modifier
                .offset(x = 10.dp, y = 96.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Surface)
                .border(1.dp, Border, RoundedCornerShape(6.dp))
                .padding(horizontal = 7.dp, vertical = 3.dp)
        )
    }
}

/** The prototype's dashed "photo becomes sticker" arrow, drawn to scale. */
@Composable
private fun DashedArrow(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val sx = size.width / 74f
        val sy = size.height / 58f
        val curve = Path().apply {
            moveTo(6f * sx, 52f * sy)
            cubicTo(10f * sx, 22f * sy, 34f * sx, 8f * sy, 64f * sx, 12f * sy)
        }
        drawPath(
            path = curve,
            color = Rose,
            style = Stroke(
                width = 2.4f * sx,
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(1f * sx, 7f * sx))
            )
        )
        val head = Path().apply {
            moveTo(55f * sx, 5f * sy)
            lineTo(66f * sx, 12f * sy)
            lineTo(56f * sx, 20f * sy)
        }
        drawPath(
            path = head,
            color = Rose,
            style = Stroke(width = 2.4f * sx, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingSlideOnePreview() {
    LoveStickersTheme { OnboardingContent(page = 0, onNext = {}, onSkip = {}) }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingSlideTwoPreview() {
    LoveStickersTheme { OnboardingContent(page = 1, onNext = {}, onSkip = {}) }
}
