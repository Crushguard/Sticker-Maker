package com.piptechnologies.stickermaker.feature.namepack

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.piptechnologies.stickermaker.R
import com.piptechnologies.stickermaker.core.design.Border
import com.piptechnologies.stickermaker.core.design.Canvas
import com.piptechnologies.stickermaker.core.design.Hanken
import com.piptechnologies.stickermaker.core.design.Ink2
import com.piptechnologies.stickermaker.core.design.Rose
import com.piptechnologies.stickermaker.feature.namepack.engine.Character

private val BuildingTitle = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.W800, fontSize = 22.sp, letterSpacing = (-0.02).em)
private val BuildingSub = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.W400, fontSize = 14.sp, lineHeight = 21.sp)

/**
 * The character waits (still + gentle bob until Claude Design's loops land) over a 200 × 4 bar
 * bound to the 12 real renders; nothing else moves. No top strip, chip or Skip. Gaps are the
 * design's net ones: art to bar 18, bar to title 20, title to sub 6. The art's white outline is
 * baked in by NamePackAssets.waitArt; the design's soft drop shadow is left out (Compose shadows
 * follow shapes, not alpha).
 */
@Composable
internal fun BuildingStep(state: NamePackUiState) {
    val reduceMotion = rememberReduceMotion()
    val progress by animateFloatAsState(
        targetValue = state.progress.coerceIn(0f, 1f),
        animationSpec = tween(if (reduceMotion) 0 else 120, easing = LinearEasing),
        label = "buildBar"
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Canvas)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = 24.dp, end = 24.dp, bottom = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        WaitingArt(state.waitArt, state.waitArtCharacter, reduceMotion)
        Spacer(Modifier.height(18.dp))
        Box(
            Modifier
                .width(200.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Border)
                .semantics { progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f) }
        ) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(progress).background(Rose))
        }
        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.namepack_building_title), style = BuildingTitle, color = TitleInk, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.namepack_building_sub), style = BuildingSub, color = Ink2, textAlign = TextAlign.Center)
    }
}

/** [character] is whose art [image] is, which the description names (not always the one picked). */
@Composable
private fun WaitingArt(image: ImageBitmap?, character: Character, reduceMotion: Boolean) {
    val bob by rememberInfiniteTransition(label = "wait").animateFloat(
        initialValue = 0f,
        targetValue = -6f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bob"
    )
    Box(Modifier.size(224.dp).graphicsLayer { translationY = if (reduceMotion) 0f else bob.dp.toPx() }) {
        if (image != null) {
            Image(image, contentDescription = stringResource(R.string.namepack_waiting_alt, character.label), modifier = Modifier.fillMaxSize())
        }
    }
}
