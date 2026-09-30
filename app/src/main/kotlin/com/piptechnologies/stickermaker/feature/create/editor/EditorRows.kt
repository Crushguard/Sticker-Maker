package com.piptechnologies.stickermaker.feature.create.editor

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.piptechnologies.stickermaker.R
import com.piptechnologies.stickermaker.core.design.Border
import com.piptechnologies.stickermaker.core.design.Canvas as CanvasColor
import com.piptechnologies.stickermaker.core.design.Green
import com.piptechnologies.stickermaker.core.design.Hanken
import com.piptechnologies.stickermaker.core.design.Ink
import com.piptechnologies.stickermaker.core.design.Ink2
import com.piptechnologies.stickermaker.core.design.LoveIcons
import com.piptechnologies.stickermaker.core.design.Mono
import com.piptechnologies.stickermaker.core.design.Muted
import com.piptechnologies.stickermaker.core.design.Rose
import com.piptechnologies.stickermaker.core.design.Subtle
import com.piptechnologies.stickermaker.core.design.Surface
import com.piptechnologies.stickermaker.core.design.components.AppSwitch
import com.piptechnologies.stickermaker.core.ui.rememberReduceMotion
import com.piptechnologies.stickermaker.feature.create.FooterDivider
import com.piptechnologies.stickermaker.feature.create.decor.DecorSpec
import com.piptechnologies.stickermaker.feature.create.decor.MarkerSize
import com.piptechnologies.stickermaker.feature.create.decor.MotionMath
import com.piptechnologies.stickermaker.feature.create.decor.MotionPreset
import com.piptechnologies.stickermaker.feature.create.decor.NamedColour
import com.piptechnologies.stickermaker.feature.create.decor.OutlineStyle
import com.piptechnologies.stickermaker.feature.create.decor.OutlineThickness
import com.piptechnologies.stickermaker.feature.create.decor.SceneRenderer

// What sits under the Cut out tool bar (spec §8): the Draw row, the Animate presets strip with its note,
// the Outline row and the rail's tiles, and the swatch they and the Add sheet share.

private val RowShape = RoundedCornerShape(12.dp)
private val CardShape = RoundedCornerShape(14.dp)
private val TrackShape = RoundedCornerShape(9.dp)
private val CellShape = RoundedCornerShape(7.dp)
private val TileShape = RoundedCornerShape(14.dp)
private val BadgeShape = RoundedCornerShape(4.dp)

/** The white swatch's edge; every other swatch has `rgba(0,0,0,.08)`. */
private val WhiteSwatchLine = Color(0xFFD8DCE3)
private val SwatchLine = Color(0x14000000)
/** `0 1 2 rgba(0,0,0,.08)` under a track's selected cell. */
private val CellShadow = Color(0x14000000)
private val PendingVeil = Color(0x99FAFBFC)

/** A preset tile's mini of the sticker, inside its 56 dp tile. */
private val MiniSize = 44.dp
/** A tile's label may run this far past its tile on each side before it is cut. */
private val LabelOverhang = 8.dp
private const val PRESSED_SCALE = 0.96f
private const val PRESS_MS = 90

private val OutlineTitle = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.W600, fontSize = 14.sp)
private val OutlineBody = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.W400, fontSize = 12.sp)
private val CellText = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.W600, fontSize = 12.sp)
private val TileLabel =
    TextStyle(fontFamily = Hanken, fontWeight = FontWeight.W600, fontSize = 9.5.sp, lineHeight = 12.sp)
private val NoteText =
    TextStyle(fontFamily = Hanken, fontWeight = FontWeight.W400, fontSize = 11.5.sp, lineHeight = 1.45.em)
private val AnimBadgeText =
    TextStyle(fontFamily = Mono, fontWeight = FontWeight.W600, fontSize = 7.5.sp, letterSpacing = 0.05.em)

// ----------------------------------------------------------------- swatch

/**
 * A round colour swatch (spec §8, §9), [diameter] across: 22 dp under the tool bar, 24 dp in the Add
 * sheet. It has a hairline edge, and the [selected] one a 2 dp white ring in a 2 dp Rose ring around it,
 * drawn outside its bounds. [label] names the colour for TalkBack.
 */
@Composable
internal fun Swatch(
    colour: Int,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    diameter: Dp = ROW_SWATCH_DP.dp
) {
    Box(
        modifier
            .size(diameter)
            .drawBehind {
                if (selected) {
                    val radius = size.minDimension / 2f
                    drawCircle(Rose, radius + 4.dp.toPx())
                    drawCircle(Color.White, radius + 2.dp.toPx())
                }
            }
            .clip(CircleShape)
            .background(Color(colour))
            .border(1.dp, if (colour == DecorSpec.WHITE) WhiteSwatchLine else SwatchLine, CircleShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = label }
    )
}

/** [colour]'s name in the app language; its id where the app has no word for it. */
@Composable
internal fun colourName(colour: NamedColour): String = colourLabel(colour.id)?.let { stringResource(it) } ?: colour.id

/** The small segmented track of the rows (spec §8): #F3F5F8, radius 9, 2 dp inset, 2 dp between its cells. */
@Composable
private fun SegmentTrack(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier
            .background(Subtle, TrackShape)
            .padding(2.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

/** One cell of a [SegmentTrack]: 28 dp high, radius 7; white over a soft shadow when [selected]. */
private fun Modifier.segmentCell(selected: Boolean, onClick: () -> Unit): Modifier = this
    .height(28.dp)
    .then(if (selected) Modifier.shadow(1.dp, CellShape, spotColor = CellShadow) else Modifier)
    .clip(CellShape)
    .background(if (selected) Surface else Color.Transparent)
    .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)

// ---------------------------------------------------------------- Draw row

/**
 * The Draw row (spec §8): the marker's [colours] as 22 dp swatches, a flexible space, then its three
 * sizes as dots of 7, 10 and 13 dp in a segmented track. [colour] and [size] are the marker's now.
 * Where the card is too narrow for the spec's row, [drawRowFit] narrows the size cells, then the gaps.
 */
@Composable
internal fun DrawRow(
    colours: List<NamedColour>,
    colour: Int,
    size: MarkerSize,
    onColour: (Int) -> Unit,
    onSize: (MarkerSize) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RowShape)
            .background(Surface)
            .border(1.dp, Border, RowShape)
            .padding(horizontal = 12.dp)
    ) {
        val fit = drawRowFit(maxWidth.value, colours.size)
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.selectableGroup(), verticalAlignment = Alignment.CenterVertically) {
                colours.forEach { named ->
                    Swatch(
                        colour = named.argb,
                        label = colourName(named),
                        selected = named.argb == colour,
                        onClick = { onColour(named.argb) }
                    )
                    Spacer(Modifier.width(fit.gap.dp))
                }
            }
            Spacer(Modifier.weight(1f))
            SegmentTrack {
                MarkerSize.entries.forEach { SizeCell(it, selected = it == size, width = fit.cell.dp) { onSize(it) } }
            }
        }
    }
}

/** One marker size: a dot of 7, 10 or 13 dp, Ink when [selected] and #8B929D otherwise. */
@Composable
private fun SizeCell(size: MarkerSize, selected: Boolean, width: Dp, onClick: () -> Unit) {
    val label = stringResource(markerSizeLabel(size))
    val dot = when (size) {
        MarkerSize.S -> 7.dp
        MarkerSize.M -> 10.dp
        MarkerSize.L -> 13.dp
    }
    Box(
        Modifier
            .width(width)
            .segmentCell(selected, onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.size(dot).background(if (selected) Ink else Muted, CircleShape))
    }
}

// ----------------------------------------------------------- Animate strip

/**
 * The Animate strip and its note (spec §8): one 56 dp tile per preset, each playing a 44 dp mini of the
 * sticker ([thumb], the decorated still) in its motion on one shared frame clock, with the preset's name
 * under it; [selected] is the sticker's preset. While not [enabled] (a [clip], which already moves, or
 * a sticker not ready for layer tools) the strip is at 40%, its tiles ignore taps and hold frame 0.
 * With reduced motion the tiles show frame 0 too and the note says how to play. [renderer] draws the
 * particle presets' hearts and stars.
 */
@Composable
internal fun AnimateStrip(
    presets: List<MotionPreset>,
    selected: String,
    thumb: ImageBitmap?,
    clip: Boolean,
    enabled: Boolean,
    renderer: SceneRenderer?,
    onPreset: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val reduceMotion = rememberReduceMotion()
    val clock = remember { mutableLongStateOf(0L) }
    // No clock while the tiles are still: reduced motion, or a strip that is off (they hold frame 0).
    LaunchedEffect(reduceMotion, enabled) {
        if (reduceMotion || !enabled) {
            clock.longValue = 0L
            return@LaunchedEffect
        }
        val start = withFrameMillis { it }
        while (true) withFrameMillis { clock.longValue = it - start }
    }
    val note = animateNote(clip, reduceMotion)
    val body = stringResource(note.body)
    val noteText = note.prefix?.let { stringResource(it) + " " + body } ?: body
    Column(modifier.fillMaxWidth()) {
        LazyRow(
            modifier = Modifier
                .alpha(if (enabled) 1f else DISABLED_ALPHA)
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(start = 4.dp, top = 4.dp, end = 4.dp, bottom = 20.dp)
        ) {
            items(presets, key = { it.id }) { preset ->
                PresetTile(
                    preset = preset,
                    selected = preset.id == selected,
                    enabled = enabled,
                    thumb = thumb,
                    renderer = renderer,
                    clockMs = { clock.longValue },
                    onClick = { onPreset(preset.id) }
                )
            }
        }
        // 12 dp would part the strip from the note like any two rows; the design overlaps them by 6.
        Text(
            noteText,
            style = NoteText,
            color = Muted,
            modifier = Modifier.padding(start = 2.dp, top = 6.dp, end = 2.dp)
        )
    }
}

/**
 * One preset: a rail-style tile (Rose ring when [selected]) with the mini playing in it and its label,
 * whose bottom sits 17 dp under the tile. A press shrinks it to .96 over 90 ms, and it springs back.
 * [clockMs] is the strip's clock, read only while drawing.
 */
@Composable
private fun PresetTile(
    preset: MotionPreset,
    selected: Boolean,
    enabled: Boolean,
    thumb: ImageBitmap?,
    renderer: SceneRenderer?,
    clockMs: () -> Long,
    onClick: () -> Unit
) {
    val label = presetLabel(preset.id)?.let { stringResource(it) } ?: preset.id
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press = animateFloatAsState(
        targetValue = if (pressed) PRESSED_SCALE else 1f,
        animationSpec = if (pressed) {
            tween(PRESS_MS)
        } else {
            spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)
        },
        label = "tilePress"
    )
    Box(
        Modifier
            .size(56.dp)
            .graphicsLayer {
                scaleX = press.value
                scaleY = press.value
            }
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(TileShape)
                .background(Subtle)
                .border(if (selected) 2.dp else 1.dp, if (selected) Rose else Border, TileShape)
                .selectable(
                    selected = selected,
                    interactionSource = interaction,
                    indication = ripple(),
                    enabled = enabled,
                    role = Role.RadioButton,
                    onClick = onClick
                )
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center
        ) {
            // The base scale shrinks the mini about its centre; the pose turns it about the preset's pivot.
            Box(
                Modifier
                    .size(MiniSize)
                    .graphicsLayer {
                        scaleX = preset.baseScale
                        scaleY = preset.baseScale
                    }
            ) {
                if (thumb != null) {
                    Image(
                        bitmap = thumb,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { tilePose(preset, tileTimeMs(preset, clockMs()), size.width) }
                    )
                }
            }
            if (preset.particles != null && renderer != null) {
                // Hearts and stars go over the sticker, unscaled by its base scale (spec §7).
                Canvas(Modifier.size(MiniSize)) {
                    val particles = MotionMath.particles(preset, tileTimeMs(preset, clockMs()), canvas = size.width)
                    drawIntoCanvas { renderer.drawParticles(it.nativeCanvas, particles) }
                }
            }
        }
        Text(
            label,
            style = TileLabel,
            color = if (selected) Rose else Ink2,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = 17.dp)
                .wrapContentWidth(unbounded = true)
                .widthIn(max = 56.dp + LabelOverhang * 2)
                // The tile already says its name.
                .clearAndSetSemantics { }
        )
    }
}

// ------------------------------------------------------------- Outline row

/**
 * The Outline row (spec §8): the title and the WhatsApp line with the switch and, while [outline] is
 * on, a sub-row with Thin · Medium · Thick and the outline [colours] as 22 dp swatches. The sub-row
 * keeps to one line where it fits ([outlineRowFit]); else the swatches take a line of their own.
 */
@Composable
internal fun OutlineRow(
    outline: OutlineStyle,
    colours: List<NamedColour>,
    onToggle: (Boolean) -> Unit,
    onThickness: (OutlineThickness) -> Unit,
    onColour: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val title = stringResource(R.string.create_outline_title)
    Column(
        modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(Surface)
            .border(1.dp, FooterDivider, CardShape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = OutlineTitle, color = Ink)
                Text(
                    stringResource(R.string.create_outline_body),
                    style = OutlineBody,
                    color = Muted,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            AppSwitch(
                checked = outline.on,
                onCheckedChange = onToggle,
                modifier = Modifier.semantics { contentDescription = title }
            )
        }
        if (outline.on) OutlineOptions(outline, colours, onThickness, onColour)
    }
}

/** The sub-row: the thickness control, a flexible space and the colour swatches, or the two stacked. */
@Composable
private fun OutlineOptions(
    outline: OutlineStyle,
    colours: List<NamedColour>,
    onThickness: (OutlineThickness) -> Unit,
    onColour: (Int) -> Unit
) {
    val labels = OutlineThickness.entries.map { stringResource(thicknessLabel(it)) }
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val room = with(density) { maxWidth.toPx() }
        val fit = remember(labels, colours.size, room, measurer, density) {
            val widths = labels.map { measurer.measure(it, CellText, maxLines = 1, softWrap = false).size.width }
            outlineRowFit(widths, colours.size, density.density, room)
        }
        if (fit.oneLine) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ThicknessControl(labels, outline.thickness, fit.sidePadding.dp, onThickness)
                Spacer(Modifier.weight(1f))
                OutlineSwatches(colours, outline.colour, onColour)
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ThicknessControl(labels, outline.thickness, fit.sidePadding.dp, onThickness)
                OutlineSwatches(colours, outline.colour, onColour)
            }
        }
    }
}

/** Thin · Medium · Thick: 28 dp cells with [sidePadding] on both sides of a 12 sp / 600 label. */
@Composable
private fun ThicknessControl(
    labels: List<String>,
    selected: OutlineThickness,
    sidePadding: Dp,
    onThickness: (OutlineThickness) -> Unit
) {
    SegmentTrack {
        OutlineThickness.entries.forEachIndexed { index, thickness ->
            val on = thickness == selected
            Box(
                Modifier
                    .segmentCell(on) { onThickness(thickness) }
                    .padding(horizontal = sidePadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    labels[index],
                    style = CellText,
                    color = if (on) Ink else Ink2,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** The outline's colours, 7 dp apart. */
@Composable
private fun OutlineSwatches(colours: List<NamedColour>, selected: Int, onColour: (Int) -> Unit) {
    Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(SWATCH_GAP_DP.dp)) {
        colours.forEach { named ->
            Swatch(
                colour = named.argb,
                label = colourName(named),
                selected = named.argb == selected,
                onClick = { onColour(named.argb) }
            )
        }
    }
}

// -------------------------------------------------------------------- rail

/**
 * One sticker of the rail (spec §8): its decorated [thumb] once cut ([done]), the raw picture
 * ([rawModel], or [rawFrame] for a clip) before that, under a spinner while [pending]; a Rose ring when
 * [selected], a green check when done, and the mono ANIM badge when it moves ([animated]: a preset or
 * a clip), 4 dp outside its start edge and 6 dp under its bottom.
 */
@Composable
internal fun RailTile(
    selected: Boolean,
    done: Boolean,
    pending: Boolean,
    animated: Boolean,
    thumb: ImageBitmap?,
    rawModel: Any?,
    rawFrame: ImageBitmap?,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .size(56.dp)
            .background(Subtle, TileShape)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) Rose else Border,
                shape = TileShape
            )
            .clickable(
                role = Role.Button,
                onClickLabel = stringResource(R.string.create_edit_sticker),
                onClick = onClick
            )
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(if (done && thumb != null) 4.dp else 0.dp)
                .clip(RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            when {
                done && thumb != null -> Image(
                    bitmap = thumb,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
                rawModel != null -> AsyncImage(
                    model = rawModel,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                rawFrame != null -> Image(
                    bitmap = rawFrame,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                else -> Box(Modifier.fillMaxSize())
            }
        }
        if (pending) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(PendingVeil),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(Modifier.size(16.dp), color = Rose, strokeWidth = 2.dp)
            }
        }
        if (done) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 2.dp, end = 2.dp)
                    .size(18.dp)
                    .background(CanvasColor, RoundedCornerShape(7.dp))
                    .padding(2.dp)
                    .background(Green, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(LoveIcons.Check, null, Modifier.size(11.dp), tint = Color.White)
            }
        }
        if (animated) {
            Text(
                stringResource(R.string.create_rail_anim),
                style = AnimBadgeText,
                color = Ink2,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = (-4).dp, y = 6.dp)
                    .background(Surface, BadgeShape)
                    .border(1.dp, Border, BadgeShape)
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            )
        }
    }
}
