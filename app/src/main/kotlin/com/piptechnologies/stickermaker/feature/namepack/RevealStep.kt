package com.piptechnologies.stickermaker.feature.namepack

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piptechnologies.stickermaker.R
import com.piptechnologies.stickermaker.core.design.Border
import com.piptechnologies.stickermaker.core.design.Canvas
import com.piptechnologies.stickermaker.core.design.Hanken
import com.piptechnologies.stickermaker.core.design.Ink
import com.piptechnologies.stickermaker.core.design.Ink2
import com.piptechnologies.stickermaker.core.design.LoveShapes
import com.piptechnologies.stickermaker.core.design.Mono
import com.piptechnologies.stickermaker.core.design.Rose
import com.piptechnologies.stickermaker.core.design.RoseTint
import com.piptechnologies.stickermaker.core.design.Surface
import com.piptechnologies.stickermaker.core.design.components.AddBar
import com.piptechnologies.stickermaker.core.design.components.AddVisualState
import com.piptechnologies.stickermaker.feature.namepack.engine.Character
import com.piptechnologies.stickermaker.feature.namepack.engine.Relation
import com.piptechnologies.stickermaker.feature.namepack.engine.Tone
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val PackNameStyle = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.W700, fontSize = 16.sp)
private val MetaStyle = TextStyle(fontFamily = Mono, fontWeight = FontWeight.W400, fontSize = 12.sp)
private val ToneText = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.W600, fontSize = 12.5.sp)
private val NotNowText = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.W600, fontSize = 14.sp)

/** Pop delay per tile: round(hypot(col − 1, row − 1.5) × 95) ms (design). */
private val PopDelaysMs = longArrayOf(171, 143, 171, 106, 48, 106, 106, 48, 106, 171, 143, 171)
private val PopEasing = CubicBezierEasing(0.2f, 0.9f, 0.3f, 1.3f)
private const val LAST_POP_MS = 591L

/** The pack (screens spec §f): header, meta + tones, re-cast row, 3-column grid, pinned Add bar. */
@Composable
internal fun RevealStep(
    state: NamePackUiState,
    onTone: (Tone) -> Unit,
    onCharacter: (Character) -> Unit,
    onAdd: () -> Unit,
    onNotNow: () -> Unit
) {
    val reduceMotion = rememberReduceMotion()
    val flow = state.flow
    val sending = state.addState == AddVisualState.Sent
    Column(Modifier.fillMaxSize().background(Canvas)) {
        RevealHeader(state.tray, state.packYou, state.packLove, state.packName)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, top = 2.dp, end = 20.dp, bottom = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.namepack_meta, relationForMeta(flow.relation)),
                    style = MetaStyle,
                    color = Ink2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ToneChip(stringResource(R.string.namepack_tone_sweet), flow.effectiveTone == Tone.SWEET, !sending) { onTone(Tone.SWEET) }
                    // Family relations letter Sweet only, so Flirty is not offered (design).
                    if (!flow.relation.family) {
                        ToneChip(stringResource(R.string.namepack_tone_flirty), flow.effectiveTone == Tone.FLIRTY, !sending) { onTone(Tone.FLIRTY) }
                    }
                }
            }
            CharacterRow(
                selected = flow.character,
                onPick = { if (!sending) onCharacter(it) },
                tileArt = state.tileArt,
                artSize = 40.dp,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            StickerGrid(state.tiles, reduceMotion)
            Text(
                stringResource(R.string.namepack_building_sub),
                style = HintStyle,
                color = FootnoteGrey,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp)
            )
        }
        RevealFooter(state.addState, reduceMotion, onAdd, onNotNow)
    }
}

/**
 * 52 header: the 28 tray heart, then the pack name, drawn with the app-mark heart (never an
 * emoji) when it holds both names. [packName] (from the ViewModel) is what TalkBack reads.
 */
@Composable
private fun RevealHeader(tray: ImageBitmap?, you: String, love: String, packName: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(52.dp)
            .padding(horizontal = 20.dp)
            .semantics(mergeDescendants = true) { contentDescription = packName },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (tray != null) Image(tray, contentDescription = null, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(9.dp))
        if (you.isNotEmpty()) {
            Text(you, style = PackNameStyle, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Icon(NamePackIcons.AppHeart, contentDescription = null, tint = Rose, modifier = Modifier.padding(horizontal = 5.dp).size(13.dp))
            Text(love, style = PackNameStyle, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        } else {
            Text(packName, style = PackNameStyle, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** The relation for the meta line: lower-cased as the design shows it; German nouns keep their capital. */
@Composable
private fun relationForMeta(relation: Relation): String {
    val label = stringResource(relation.labelRes())
    val locale = LocalConfiguration.current.locales[0]
    return if (locale.language == "de") label else label.lowercase(locale)
}

/** 32 tall tone chip, pad 13, 12.5/600, relation-chip colours. */
@Composable
private fun ToneChip(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) RoseTint else Surface, label = "toneBg")
    val fg by animateColorAsState(if (selected) Rose else Ink2, label = "toneFg")
    val line by animateColorAsState(if (selected) Rose else Border, label = "toneLine")
    Box(
        modifier = Modifier
            .height(32.dp)
            .clip(LoveShapes.Pill)
            .background(bg)
            .border(1.dp, line, LoveShapes.Pill)
            .clickable(role = Role.Button, onClickLabel = label, enabled = enabled, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = ToneText, color = fg, maxLines = 1)
    }
}

/** Three columns 10 apart; square tiles r16 on #F6F7F9 with the sticker at 104/110. */
@Composable
private fun StickerGrid(tiles: List<RevealTile>, reduceMotion: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        tiles.chunked(3).forEachIndexed { row, items ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items.forEachIndexed { col, tile ->
                    key(tile.slot) { StickerCell(tile, row * 3 + col, reduceMotion, Modifier.weight(1f)) }
                }
                repeat(3 - items.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Pops in once (0.2 → 1, 420 ms overshoot, staggered from the centre); a re-letter fades in over the old tile (160 ms). */
@Composable
private fun StickerCell(tile: RevealTile, index: Int, reduceMotion: Boolean, modifier: Modifier) {
    val scale = remember { Animatable(if (reduceMotion) 1f else 0.2f) }
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (reduceMotion) {
            alpha.animateTo(1f, tween(300))
        } else {
            delay(PopDelaysMs.getOrElse(index) { 0L })
            // Opacity and scale share the pop's timing, as in the design's keyframes.
            launch { alpha.animateTo(1f, tween(420, easing = PopEasing)) }
            scale.animateTo(1f, tween(420, easing = PopEasing))
        }
    }
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                this.alpha = alpha.value.coerceIn(0f, 1f)
            }
            .clip(RoundedCornerShape(16.dp))
            .background(TileBg),
        contentAlignment = Alignment.Center
    ) {
        FadeOver(tile.image, tile.text, if (reduceMotion) 0 else 160, Modifier.fillMaxSize(104f / 110f))
    }
}

/**
 * Swaps [image] by fading the new one in over the old, which stays opaque underneath: art that did
 * not change (a tone switch) never dims, only the lettering visibly changes (design §f).
 */
@Composable
private fun FadeOver(image: ImageBitmap, description: String, durationMs: Int, modifier: Modifier) {
    var shown by remember { mutableStateOf(image) }
    var under by remember { mutableStateOf<ImageBitmap?>(null) }
    val alpha = remember { Animatable(1f) }
    LaunchedEffect(image) {
        if (image == shown) return@LaunchedEffect
        under = shown
        shown = image
        alpha.snapTo(0f)
        alpha.animateTo(1f, tween(durationMs))
        under = null
    }
    Box(modifier) {
        under?.let { Image(it, contentDescription = null, modifier = Modifier.fillMaxSize()) }
        Image(shown, contentDescription = description, modifier = Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha.value })
    }
}

/**
 * Pinned white footer: the five-state Add bar (no hint line) and a ghost "Not now"; fades in after
 * the last tile, and answers taps only from then on (invisible, "Not now" would end the flow).
 */
@Composable
private fun RevealFooter(addState: AddVisualState, reduceMotion: Boolean, onAdd: () -> Unit, onNotNow: () -> Unit) {
    val alpha = remember { Animatable(if (reduceMotion) 1f else 0f) }
    var ready by remember { mutableStateOf(reduceMotion) }
    LaunchedEffect(Unit) {
        if (!reduceMotion) {
            delay(LAST_POP_MS)
            ready = true
            alpha.animateTo(1f, tween(300))
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { this.alpha = alpha.value }
            .background(Surface)
            .navigationBarsPadding()
    ) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(FooterLine))
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 14.dp)) {
            AddBar(
                state = addState,
                onClick = { if (ready) onAdd() },
                hint = null,
                failedLabel = stringResource(R.string.namepack_add_failed)
            )
            val notNow = stringResource(R.string.common_not_now)
            // While WhatsApp's own add sheet is on its way, leaving would lose its answer.
            val sending = addState == AddVisualState.Sent
            Box(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(role = Role.Button, onClickLabel = notNow, enabled = ready && !sending, onClick = onNotNow),
                contentAlignment = Alignment.Center
            ) {
                Text(notNow, style = NotNowText, color = Ink2)
            }
        }
    }
}
