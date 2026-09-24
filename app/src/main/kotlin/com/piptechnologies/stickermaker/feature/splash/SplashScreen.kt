package com.piptechnologies.stickermaker.feature.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.piptechnologies.stickermaker.core.design.Canvas
import com.piptechnologies.stickermaker.core.design.Hanken
import com.piptechnologies.stickermaker.core.design.LoveIcons
import com.piptechnologies.stickermaker.core.design.LoveStickersTheme
import com.piptechnologies.stickermaker.core.design.Rose
import com.piptechnologies.stickermaker.core.design.components.HonestyLine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// Exact Prototype copy.
private const val APP_NAME = "Love Stickers"
private const val HONESTY_TEXT = "Free · No ads · No account"

/** The prototype advances after 1.6 s (Prototype armSplash). */
private const val SPLASH_BEAT_MS = 1_600L

private val TitleInk = Color(0xFF171A20)
private val MarkShape = RoundedCornerShape(26.dp)
private val TitleText = TextStyle(
    fontFamily = Hanken,
    fontWeight = FontWeight.W800,
    fontSize = 24.sp,
    letterSpacing = (-0.02).em
)

/**
 * Launch screen: app mark 88/26 in rose, name, one rose spinner, honesty
 * line. Shows for the branding beat while the onboarding flag loads, then
 * calls [onFinished] with it (first run -> onboarding, after that -> home).
 */
@Composable
fun SplashScreen(
    onFinished: (onboarded: Boolean) -> Unit,
    viewModel: SplashViewModel = hiltViewModel()
) {
    val latestOnFinished by rememberUpdatedState(onFinished)
    LaunchedEffect(Unit) {
        delay(SPLASH_BEAT_MS)
        val onboarded = viewModel.uiState
            .map { it.onboarded }
            .filterNotNull()
            .first()
        latestOnFinished(onboarded)
    }
    SplashContent()
}

/** Stateless splash layout, previewable without Hilt. */
@Composable
fun SplashContent(modifier: Modifier = Modifier) {
    // Previews render one static frame, so they start fully faded in.
    val startVisible = LocalInspectionMode.current
    val fade = remember { Animatable(if (startVisible) 1f else 0f) }
    LaunchedEffect(Unit) { fade.animateTo(1f, tween(durationMillis = 400)) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Canvas)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize()
                .padding(start = 24.dp, end = 24.dp, bottom = 30.dp)
                .alpha(fade.value),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .shadow(24.dp, MarkShape, spotColor = Rose.copy(alpha = 0.55f))
                    .clip(MarkShape)
                    .background(Rose),
                contentAlignment = Alignment.Center
            ) {
                Icon(LoveIcons.HeartFilled, null, Modifier.size(44.dp), tint = Color.White)
            }
            Spacer(Modifier.height(20.dp))
            Text(APP_NAME, style = TitleText, color = TitleInk)
            Spacer(Modifier.height(28.dp))
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = Rose,
                strokeWidth = 2.dp
            )
        }
        HonestyLine(
            text = HONESTY_TEXT,
            modifier = Modifier.padding(bottom = 34.dp)
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun SplashPreview() {
    LoveStickersTheme { SplashContent() }
}
