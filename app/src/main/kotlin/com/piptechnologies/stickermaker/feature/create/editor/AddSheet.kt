package com.piptechnologies.stickermaker.feature.create.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import coil.compose.AsyncImage
import com.piptechnologies.stickermaker.R
import com.piptechnologies.stickermaker.core.design.Border
import com.piptechnologies.stickermaker.core.design.BorderStrong
import com.piptechnologies.stickermaker.core.design.Hanken
import com.piptechnologies.stickermaker.core.design.Ink
import com.piptechnologies.stickermaker.core.design.Ink2
import com.piptechnologies.stickermaker.core.design.LoveIcons
import com.piptechnologies.stickermaker.core.design.LoveShapes
import com.piptechnologies.stickermaker.core.design.Mono
import com.piptechnologies.stickermaker.core.design.Muted
import com.piptechnologies.stickermaker.core.design.Muted2
import com.piptechnologies.stickermaker.core.design.Rose
import com.piptechnologies.stickermaker.core.design.RoseTint
import com.piptechnologies.stickermaker.core.design.Surface
import com.piptechnologies.stickermaker.core.design.components.CategoryChip
import com.piptechnologies.stickermaker.core.design.components.SegmentedControl
import com.piptechnologies.stickermaker.feature.create.AddTab
import com.piptechnologies.stickermaker.feature.create.CreatePackViewModel
import com.piptechnologies.stickermaker.feature.create.CreateUiState
import com.piptechnologies.stickermaker.feature.create.EditorTool
import com.piptechnologies.stickermaker.feature.create.SkinPopoverUi
import com.piptechnologies.stickermaker.feature.create.ToneCellUi
import com.piptechnologies.stickermaker.feature.create.ToneState
import com.piptechnologies.stickermaker.feature.create.decor.DecorCatalog
import com.piptechnologies.stickermaker.feature.create.decor.DecorFonts
import com.piptechnologies.stickermaker.feature.create.decor.DecorPiece
import com.piptechnologies.stickermaker.feature.create.decor.DecorSpec
import com.piptechnologies.stickermaker.feature.create.decor.EmojiCatalog
import com.piptechnologies.stickermaker.feature.create.decor.EmojiItem
import com.piptechnologies.stickermaker.feature.create.decor.FontMood
import com.piptechnologies.stickermaker.feature.create.decor.LayerContent
import com.piptechnologies.stickermaker.feature.create.decor.NamedColour
import com.piptechnologies.stickermaker.feature.create.decor.SkinTone
import com.piptechnologies.stickermaker.feature.create.decor.TextLayerPainter
import com.piptechnologies.stickermaker.feature.create.decor.TextStyleBook
import com.piptechnologies.stickermaker.feature.create.decor.TextStyleId
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// The Add sheet (spec §9): Text, Emoji and Stickers, in an overlay pinned to the bottom of the editor.

/** Coil's models for the bundled emoji and decoration art. */
private const val EMOJI_ASSETS = "file:///android_asset/emoji/"
private const val DECOR_ASSETS = "file:///android_asset/decor/"

private const val OPEN_MS = 280
private const val CLOSE_MS = 200
private const val TAB_FADE_MS = 120
private val OpenEasing = CubicBezierEasing(0.2f, 0.7f, 0.2f, 1f)

/** The sheet is never taller than this, the keyboard and the navigation bar aside (spec §9). */
private val SheetMaxHeight = 442.dp
/** The handle dragged down further than this closes the sheet. */
private val CloseDrag = 40.dp
/** The handle's strip: 10 dp above and below the 4 dp handle, all of it takes the drag. */
private val HandleStrip = 24.dp
private val EmojiGridMaxHeight = 240.dp
private val StickerGridMaxHeight = 290.dp
/** The Text tab's "Aa" samples are lettered at this size, the Bubble one at .8 of it. */
private val SampleSize = 20.dp
private const val BUBBLE_SAMPLE_SCALE = 0.8f
/** A style chip keeps this much height for its sample; the sample's glow and tail may reach past it. */
private val SampleSlot = 26.dp
/** The Bubble sample's tail hangs under its letters: lifted this much, bubble and tail sit clear of the label. */
private val BubbleSampleLift = 3.dp
private const val FAILED_ALPHA = 0.4f

private val SheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
private val FieldShape = RoundedCornerShape(12.dp)
private val StyleChipShape = RoundedCornerShape(12.dp)
private val EmojiTileShape = RoundedCornerShape(12.dp)
private val DecorTileShape = RoundedCornerShape(14.dp)
private val ToneShape = RoundedCornerShape(10.dp)
private val PopoverShape = RoundedCornerShape(14.dp)

/** `rgb(20,22,28)`: the sheet's and the popover's shadow. */
private val ShadowInk = Color(0xFF14161C)
/** `#F6F7F9` under an idle style chip, an emoji and a decoration piece. */
private val TileFill = Color(0xFFF6F7F9)
/** `0 10 24 -10 rgba(20,22,28,.35)` */
private val PopoverShadow = Color(0x5914161C)

private val FieldText = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.W400, fontSize = 15.sp, color = Ink)
private val CounterText = TextStyle(fontFamily = Mono, fontWeight = FontWeight.W400, fontSize = 11.sp)
private val PhraseText = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.W600, fontSize = 12.5.sp)
private val StyleLabel = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.W600, fontSize = 10.sp)
private val SectionText =
    TextStyle(fontFamily = Mono, fontWeight = FontWeight.W600, fontSize = 11.sp, letterSpacing = 0.08.em)

// The skin-tone popover: six 38 dp tones 2 dp apart in a card with 4 dp of padding.
private val ToneSize = 38.dp
private val ToneGap = 2.dp
private val PopoverPadding = 4.dp
private val PopoverWidth = ToneSize * SkinTone.entries.size + ToneGap * (SkinTone.entries.size - 1) + PopoverPadding * 2
/** The card is start-aligned this far into the grid, and its bottom sits this far above the pressed emoji. */
private val PopoverInset = 4.dp
private val PopoverLift = 10.dp
/** The tail: the lower half of a 12 dp square on its corner, its centre 1 dp under the card's edge. */
private val TailHalfWidth = 7.5.dp
private val TailHeight = 9.5.dp
/** The tail's centre keeps this far from the card's ends: its corner radius and the tail's own half. */
private val TailCorner = 22.dp
/** Room around the card inside the popover's window, for its shadow and its tail. */
private val PopoverMargin = 16.dp

// ------------------------------------------------------------------ sheet

/**
 * The Add sheet (spec §9), shown while the Add tool is on: an overlay at the bottom of the editor, over
 * its footer, with no scrim, so the canvas above stays live. It slides up over 280 ms and down over
 * 200 ms, rides the keyboard, and is at most 442 dp high. Under its drag handle (down 40 dp to close)
 * sit the Text · Emoji · Stickers tabs, whose contents crossfade over 120 ms. On its way out it takes no
 * more taps. [onCovers] hears how high it stands over the screen's bottom, in px, once it is up, and 0
 * again as it leaves.
 */
@Composable
internal fun AddSheet(
    state: CreateUiState,
    viewModel: CreatePackViewModel,
    modifier: Modifier = Modifier,
    onCovers: (Int) -> Unit = {}
) {
    val open = state.tool == EditorTool.Add
    val focusManager = LocalFocusManager.current
    // The keyboard leaves as the sheet starts down, not once its field is gone.
    LaunchedEffect(open) { if (!open) focusManager.clearFocus() }
    AnimatedVisibility(
        visible = open,
        modifier = modifier,
        enter = slideInVertically(tween(OPEN_MS, easing = OpenEasing)) { it },
        exit = slideOutVertically(tween(CLOSE_MS, easing = FastOutLinearInEasing)) { it }
    ) {
        // Only once the slide-in is over: until then, what it will cover is still in view.
        val up = rememberUpdatedState(open && transition.currentState == EnterExitState.Visible)
        val covers = rememberUpdatedState(onCovers)
        val height = remember { mutableIntStateOf(0) }
        // Read outside the composition: the keyboard sliding in changes the height on every frame.
        LaunchedEffect(Unit) {
            snapshotFlow { if (up.value) height.intValue else 0 }.collect { covers.value(it) }
        }
        SheetSurface(
            open = open,
            onClose = viewModel::closeAddSheet,
            modifier = Modifier.onSizeChanged { height.intValue = it.height }
        ) {
            SegmentedControl(
                options = AddTab.entries.map { stringResource(addTabLabel(it)) },
                selectedIndex = state.addTab.ordinal,
                onSelect = {
                    focusManager.clearFocus()
                    viewModel.setAddTab(AddTab.entries[it])
                },
                inset = 3.dp,
                segmentHeight = 34.dp,
                gap = 0.dp,
                idleColor = Ink2
            )
            Spacer(Modifier.height(10.dp))
            // The Add tool only opens once the decor data has loaded.
            val data = viewModel.data ?: return@SheetSurface
            val locale = LocalConfiguration.current.locales[0]
            // The chips are keyed by their words.
            val phrases = remember(data, locale) {
                data.phrases.forLocale(locale.language, locale.country).distinct()
            }
            Crossfade(targetState = state.addTab, animationSpec = tween(TAB_FADE_MS), label = "addTab") { tab ->
                when (tab) {
                    AddTab.Text -> TextTab(
                        text = state.textValue,
                        style = state.textStyle,
                        colour = state.textColour,
                        font = state.textFont,
                        colours = data.styles.colours,
                        phrases = phrases,
                        painter = viewModel.textPainter,
                        fonts = viewModel.fonts,
                        onText = viewModel::setText,
                        onStyle = viewModel::setTextStyle,
                        onColour = viewModel::setTextColour,
                        onFont = viewModel::setTextFont
                    )
                    AddTab.Emoji -> EmojiTab(
                        catalog = data.emoji,
                        tab = state.emojiTab,
                        recents = state.recents,
                        popover = state.skinPopover,
                        onPick = { viewModel.addEmoji(it) },
                        onTones = viewModel::openSkinTones,
                        onTab = viewModel::setEmojiTab,
                        onTone = viewModel::addEmoji,
                        onCloseTones = viewModel::closeSkinTones
                    )
                    AddTab.Stickers -> StickersTab(catalog = data.decor, onPick = viewModel::addDecor)
                }
            }
        }
    }
}

/**
 * The sheet's surface: white, 24 dp top corners, a soft shadow above its edge; 16 dp at the sides and
 * 14 dp under its content. It reaches under the keyboard and the navigation bar, which its content
 * clears. No touch on it reaches what it covers, and while not [open] (sliding out) none reaches its
 * own controls either.
 */
@Composable
private fun SheetSurface(
    open: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val drag = remember { mutableFloatStateOf(0f) }
    // Dragged shut and opened again before it had left: back up to its place.
    LaunchedEffect(open) {
        if (open && drag.floatValue != 0f) animate(drag.floatValue, 0f) { value, _ -> drag.floatValue = value }
    }
    Column(
        modifier
            .fillMaxWidth()
            .graphicsLayer { translationY = drag.floatValue }
            .sheetShadow()
            .clip(SheetShape)
            .background(Surface)
            .shield(blocked = !open)
            .navigationBarsPadding()
            .imePadding()
            .heightIn(max = SheetMaxHeight)
            .padding(start = 16.dp, end = 16.dp, bottom = 14.dp)
    ) {
        SheetHandle(drag, onClose)
        content()
    }
}

/**
 * `0 -10 30 -16 rgba(20,22,28,.3)`: a shadow that rises above the sheet's top edge, which an elevation
 * (lit from above) can't cast. It goes on under the sheet, where the rounded corners show it.
 */
private fun Modifier.sheetShadow(): Modifier = drawWithCache {
    val rise = 24.dp.toPx()
    val under = 24.dp.toPx()
    val brush = Brush.verticalGradient(
        0f to Color.Transparent,
        0.25f to ShadowInk.copy(alpha = 0.035f),
        0.5f to ShadowInk.copy(alpha = 0.10f),
        1f to ShadowInk.copy(alpha = 0.22f),
        startY = -rise,
        endY = under
    )
    onDrawBehind { drawRect(brush, topLeft = Offset(0f, -rise), size = Size(size.width, rise + under)) }
}

/** Keeps every touch on this from what lies under it and, while [blocked], from its own content too. */
private fun Modifier.shield(blocked: Boolean): Modifier = pointerInput(blocked) {
    if (!blocked) return@pointerInput
    awaitPointerEventScope {
        while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
    }
}

/**
 * The drag handle (spec §9): 36 × 4, #E1E5EB, in a strip that takes the drag. The sheet follows the
 * finger down ([drag], px); let go past 40 dp it closes ([onClose]), else it springs back. TalkBack
 * closes it with a double tap.
 */
@Composable
private fun SheetHandle(drag: MutableFloatState, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val close = rememberUpdatedState(onClose)
    val closeLabel = stringResource(R.string.common_close)
    Box(
        Modifier
            .fillMaxWidth()
            .height(HandleStrip)
            .pointerInput(Unit) {
                val limit = CloseDrag.toPx()
                var settling: Job? = null
                fun release() {
                    if (drag.floatValue > limit) {
                        close.value()
                    } else {
                        settling = scope.launch {
                            animate(drag.floatValue, 0f) { value, _ -> drag.floatValue = value }
                        }
                    }
                }
                detectVerticalDragGestures(
                    // A finger back on the handle takes it from the spring.
                    onDragStart = { settling?.cancel() },
                    onDragEnd = ::release,
                    onDragCancel = ::release
                ) { change, amount ->
                    change.consume()
                    drag.floatValue = (drag.floatValue + amount).coerceAtLeast(0f)
                }
            }
            .semantics {
                contentDescription = closeLabel
                onClick(closeLabel) {
                    close.value()
                    true
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.size(36.dp, 4.dp).background(BorderStrong, RoundedCornerShape(2.dp)))
    }
}

// ------------------------------------------------------------------- text

/**
 * Add › Text (spec §9): the field with its counter, the quick [phrases] and, while the keyboard is
 * down, the four style chips with a live "Aa" each, the eight [colours] and the three font moods.
 * [text], [style], [colour] and [font] are the selected text layer's, or what the next one gets.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TextTab(
    text: String,
    style: TextStyleId,
    colour: Int,
    font: FontMood,
    colours: List<NamedColour>,
    phrases: List<String>,
    painter: TextLayerPainter?,
    fonts: DecorFonts?,
    onText: (String) -> Unit,
    onStyle: (TextStyleId) -> Unit,
    onColour: (Int) -> Unit,
    onFont: (FontMood) -> Unit
) {
    val keyboardUp = WindowInsets.isImeVisible
    // It scrolls where a short screen or large text leaves it too little room.
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LayerTextField(text, onText)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(phrases, key = { it }) { phrase -> PhraseChip(phrase) { onText(phrase) } }
        }
        if (!keyboardUp) {
            StyleChips(style, colour, font, painter, onStyle)
            // One line where it fits (a wide screen); else the font moods go under the colours.
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = EndsApart,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    Modifier.align(Alignment.CenterVertically).selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(SWATCH_GAP_DP.dp)
                ) {
                    colours.forEach { named ->
                        Swatch(
                            colour = named.argb,
                            label = colourName(named),
                            selected = named.argb == colour,
                            onClick = { onColour(named.argb) },
                            diameter = 24.dp
                        )
                    }
                }
                FontChips(font, fonts, onFont, Modifier.align(Alignment.CenterVertically))
            }
        }
    }
}

/**
 * Two groups on a line: 7 dp apart or more, the first at the start and the second at the end. A group
 * that wraps onto a line of its own sits at the start.
 */
internal object EndsApart : Arrangement.Horizontal {
    private val between: Arrangement.Horizontal = Arrangement.SpaceBetween

    override val spacing = SWATCH_GAP_DP.dp

    override fun Density.arrange(
        totalSize: Int,
        sizes: IntArray,
        layoutDirection: LayoutDirection,
        outPositions: IntArray
    ) = with(between) { arrange(totalSize, sizes, layoutDirection, outPositions) }
}

/**
 * The text field: 46 dp or more, a Rose edge while it has the keyboard, the `type` icon, 15 sp input on
 * up to two lines and the mono counter. [value] is the layer's text and the truth: what a quick phrase,
 * another layer or a refused keystroke makes of it replaces what was typed, the caret at its end.
 * Done closes the keyboard; the sheet stays.
 */
@Composable
private fun LayerTextField(value: String, onValue: (String) -> Unit) {
    var typed by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    val shown = if (typed.text == value) typed else TextFieldValue(value, TextRange(value.length))
    // What was typed gives way for good, so no old caret or composing span comes back with the same text.
    if (shown !== typed) SideEffect { typed = shown }
    var focused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 46.dp)
            .clip(FieldShape)
            .background(Surface)
            .border(1.dp, if (focused) Rose else Border, FieldShape)
            // The whole row focuses the field, not only its line of text, and brings the keyboard back.
            .pointerInput(keyboard) {
                detectTapGestures {
                    focusRequester.requestFocus()
                    keyboard?.show()
                }
            }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Icon(LoveIcons.Type, contentDescription = null, modifier = Modifier.size(17.dp), tint = Muted)
        BasicTextField(
            value = shown,
            onValueChange = { next ->
                // A layer is lettered on lines of its own choosing: a typed line break is a space.
                val text = next.text.replace('\n', ' ').replace('\r', ' ')
                typed = if (text == next.text) next else next.copy(text = text)
                if (text != value) onValue(text)
            },
            maxLines = 2,
            textStyle = FieldText,
            cursorBrush = SolidColor(Rose),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                .onFocusChanged { focused = it.isFocused },
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (shown.text.isEmpty()) {
                        Text(
                            stringResource(R.string.create_text_placeholder),
                            style = FieldText,
                            color = Muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    inner()
                }
            }
        )
        Text(
            stringResource(R.string.create_name_counter, value.length, DecorSpec.TEXT_MAX_CHARS),
            style = CounterText,
            color = Muted2
        )
    }
}

/** A quick phrase: 32 dp, pill, white with a hairline, 12.5 sp / 600. A tap replaces the text. */
@Composable
private fun PhraseChip(phrase: String, onClick: () -> Unit) {
    Box(
        Modifier
            .height(32.dp)
            .clip(LoveShapes.Pill)
            .background(Surface)
            .border(1.dp, Border, LoveShapes.Pill)
            .clickable(role = Role.Button, onClickLabel = phrase, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(phrase, style = PhraseText, color = Ink, maxLines = 1, softWrap = false)
    }
}

/**
 * The four styles, equal wide and 58 dp high: each shows "Aa" lettered by [painter] in that style, in
 * the text's [colour] and [font], over its name. The [selected] one is Rose-tinted with a Rose edge.
 */
@Composable
private fun StyleChips(
    selected: TextStyleId,
    colour: Int,
    font: FontMood,
    painter: TextLayerPainter?,
    onStyle: (TextStyleId) -> Unit
) {
    val sample = stringResource(R.string.create_text_style_sample)
    val density = LocalDensity.current
    // Four small bitmaps, lettered again only when the colour or the font changes.
    val samples = remember(painter, sample, colour, font, density) {
        if (painter == null) {
            emptyMap()
        } else {
            val scale = with(density) { SampleSize.toPx() } / TextStyleBook.DEFAULT_TEXT_PX
            TextStyleId.entries.associateWith { style ->
                val fit = if (style == TextStyleId.Bubble) BUBBLE_SAMPLE_SCALE else 1f
                painter.render(LayerContent.Text(sample, style, colour, font), scale * fit).asImageBitmap()
            }
        }
    }
    Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextStyleId.entries.forEach { style ->
            StyleChip(
                label = stringResource(textStyleLabel(style)),
                sample = samples[style],
                selected = style == selected,
                onClick = { onStyle(style) },
                modifier = Modifier.weight(1f),
                lift = if (style == TextStyleId.Bubble) BubbleSampleLift else 0.dp
            )
        }
    }
}

/**
 * One style: its sample, pixel for pixel and centred on its letters ([lift] higher), 3 dp over its
 * 10 sp / 600 label.
 */
@Composable
private fun StyleChip(
    label: String,
    sample: ImageBitmap?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    lift: Dp = 0.dp
) {
    Column(
        modifier
            .height(58.dp)
            .clip(StyleChipShape)
            .background(if (selected) RoseTint else TileFill)
            .border(1.dp, if (selected) Rose else Border, StyleChipShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically)
    ) {
        Box(Modifier.height(SampleSlot), contentAlignment = Alignment.Center) {
            if (sample != null) {
                Image(
                    bitmap = sample,
                    contentDescription = null,
                    modifier = Modifier
                        .wrapContentSize(unbounded = true)
                        .offset(y = -lift)
                )
            }
        }
        Text(label, style = StyleLabel, color = Ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Rounded · Hand · Display, 7 dp apart: 32 dp pills, each lettered in its own face ([fonts]). The
 * [selected] one is Rose on a Rose tint; the others Ink on white.
 */
@Composable
private fun FontChips(
    selected: FontMood,
    fonts: DecorFonts?,
    onFont: (FontMood) -> Unit,
    modifier: Modifier = Modifier
) {
    val families = remember(fonts) {
        FontMood.entries.associateWith { mood -> fonts?.let { FontFamily(it.face(fontMoodFace(mood)).typeface) } }
    }
    Row(modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(SWATCH_GAP_DP.dp)) {
        FontMood.entries.forEach { mood ->
            val on = mood == selected
            Box(
                Modifier
                    .height(32.dp)
                    .clip(LoveShapes.Pill)
                    .background(if (on) RoseTint else Surface)
                    .border(1.dp, if (on) Rose else Border, LoveShapes.Pill)
                    .selectable(selected = on, role = Role.RadioButton) { onFont(mood) }
                    .padding(horizontal = 11.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(fontMoodLabel(mood)),
                    style = TextStyle(fontFamily = families[mood] ?: Hanken, fontSize = fontMoodSp(mood).sp),
                    color = if (on) Rose else Ink,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

// ------------------------------------------------------------------ emoji

/**
 * Add › Emoji (spec §9): the Love row, the category chips (Recent last, once something was used) and
 * the six-wide grid of the chip that is on ([tab]), which scrolls inside the sheet. A tap adds an
 * emoji; a long press on one with skin tones opens the [popover] over it.
 */
@Composable
private fun EmojiTab(
    catalog: EmojiCatalog,
    tab: String,
    recents: List<String>,
    popover: SkinPopoverUi?,
    onPick: (String) -> Unit,
    onTones: (String) -> Unit,
    onTab: (String) -> Unit,
    onTone: (String, SkinTone) -> Unit,
    onCloseTones: () -> Unit
) {
    val chips = emojiChips(hasRecents = recents.isNotEmpty())
    val on = emojiChipOn(tab, chips)
    val emoji = remember(catalog, on, recents) {
        if (on == EMOJI_RECENT) recents.mapNotNull(catalog::byFile) else catalog.tab(on)
    }
    val love = stringResource(R.string.create_emoji_love)
    // Every chip's grid starts at its top.
    val gridState = remember(on) { LazyGridState() }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LazyRow(
            modifier = Modifier.semantics { contentDescription = love },
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(vertical = 2.dp)
        ) {
            items(catalog.love, key = { it.file }) { item ->
                EmojiCell(item, onPick, onTones, Modifier.size(40.dp).clip(ToneShape))
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(chips, key = { it }) { id ->
                CategoryChip(
                    label = emojiTabLabel(id)?.let { stringResource(it) } ?: id,
                    selected = id == on,
                    onClick = { onTab(id) }
                )
            }
        }
        // The popover hangs from this box: it is the grid's own.
        Box {
            LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                modifier = Modifier.heightIn(max = EmojiGridMaxHeight),
                state = gridState,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(emoji, key = { it.file }) { item ->
                    EmojiCell(
                        item, onPick, onTones,
                        Modifier
                            .aspectRatio(1f)
                            .clip(EmojiTileShape)
                            .background(TileFill),
                        padding = 6.dp
                    )
                }
            }
            if (popover != null) SkinPopover(popover, gridState, onTone, onCloseTones)
        }
    }
}

/**
 * One emoji, [padding] inside [modifier]'s box. A tap picks it; a long press, on one with skin tones,
 * asks for them with a tick. TalkBack reads its glyph, which it knows by name in every language.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EmojiCell(
    item: EmojiItem,
    onPick: (String) -> Unit,
    onTones: (String) -> Unit,
    modifier: Modifier = Modifier,
    padding: Dp = 0.dp
) {
    val haptics = LocalHapticFeedback.current
    Box(
        modifier
            .combinedClickable(
                role = Role.Button,
                onClickLabel = item.glyph,
                onLongClick = if (item.skinTones) {
                    {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onTones(item.file)
                    }
                } else {
                    null
                },
                onClick = { onPick(item.file) }
            )
            .padding(padding)
    ) {
        AsyncImage(
            model = EMOJI_ASSETS + item.file,
            contentDescription = item.glyph,
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * The skin-tone popover (spec §9), in a window of its own over the grid: a white card of six tones just
 * above the pressed emoji, start-aligned, its tail pointing at the emoji. A tone that is there adds the
 * emoji in it ([onTone]); one still downloading shows a spinner and one that failed a dimmed mark, and
 * neither takes a tap. A tap outside, or Back, closes it ([onClose]).
 */
@Composable
private fun SkinPopover(
    popover: SkinPopoverUi,
    gridState: LazyGridState,
    onTone: (String, SkinTone) -> Unit,
    onClose: () -> Unit
) {
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    // Where the pressed emoji is in the grid; it only changes if the grid is laid out again.
    val pressed by remember(gridState, popover.file) {
        derivedStateOf {
            val layout = gridState.layoutInfo
            val item = layout.visibleItemsInfo.firstOrNull { it.key == popover.file }
            PressedEmoji(
                centre = item?.let { it.offset.x + it.size.width / 2f } ?: 0f,
                top = item?.offset?.y ?: 0,
                room = layout.viewportSize.width.toFloat()
            )
        }
    }
    val room = pressed.room
    val width = with(density) { PopoverWidth.toPx() }
    // The emoji's centre from the grid's start edge; the grid's own positions are from its left.
    val centre = if (rtl) room - pressed.centre else pressed.centre
    val placement = with(density) {
        skinPopoverPlacement(centre, room, width, inset = PopoverInset.toPx(), corner = TailCorner.toPx())
    }
    val left = if (rtl) room - placement.start - width else placement.start
    val top = pressed.top
    val position = remember(left, top, density) {
        with(density) {
            AbovePressedEmoji(
                cardLeft = left.roundToInt(),
                emojiTop = top,
                lift = PopoverLift.roundToPx(),
                margin = PopoverMargin.roundToPx()
            )
        }
    }
    // The tail's centre in the card, from the card's left.
    val tail = with(density) { placement.tail.toDp() }.let { if (rtl) PopoverWidth - it else it }
    Popup(
        popupPositionProvider = position,
        onDismissRequest = onClose,
        properties = PopupProperties(focusable = true)
    ) {
        PopoverCard(tail) {
            popover.cells.forEach { cell -> ToneCell(cell) { onTone(popover.file, cell.tone) } }
        }
    }
}

/** The pressed emoji in its grid, in px: its [centre] from the grid's left, its [top], the grid's width ([room]). */
private data class PressedEmoji(val centre: Float, val top: Int, val room: Float)

/**
 * Puts the popover's window so that its card ([margin] inside the window on every side) starts
 * [cardLeft] px from the grid's left and ends [lift] px above the pressed emoji ([emojiTop], px from
 * the grid's top), kept on the screen.
 */
private class AbovePressedEmoji(
    private val cardLeft: Int,
    private val emojiTop: Int,
    private val lift: Int,
    private val margin: Int
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val x = anchorBounds.left + cardLeft - margin
        // The card's bottom edge is the window's, less the margin under it.
        val y = anchorBounds.top + emojiTop - lift - (popupContentSize.height - margin)
        return IntOffset(
            x.coerceIn(0, max(0, windowSize.width - popupContentSize.width)),
            y.coerceAtLeast(0)
        )
    }
}

/**
 * The popover's card: white, a hairline, radius 14, 4 dp of padding, its tones 2 dp apart, a soft
 * shadow, and the tail under it, [tail] from its left edge.
 */
@Composable
private fun PopoverCard(tail: Dp, content: @Composable () -> Unit) {
    Box(
        Modifier
            .padding(PopoverMargin)
            .drawWithCache {
                // Over the card's bottom hairline, so card and tail read as one shape.
                val x = tail.toPx()
                val half = TailHalfWidth.toPx()
                val hairline = 1.dp.toPx()
                val top = size.height - hairline
                val tip = Offset(x, size.height + TailHeight.toPx())
                val shape = Path().apply {
                    moveTo(x - half, top)
                    lineTo(tip.x, tip.y)
                    lineTo(x + half, top)
                    close()
                }
                onDrawWithContent {
                    drawContent()
                    drawPath(shape, Surface)
                    drawLine(Border, Offset(x - half, size.height - hairline / 2f), tip, hairline)
                    drawLine(Border, Offset(x + half, size.height - hairline / 2f), tip, hairline)
                }
            }
    ) {
        Row(
            Modifier
                .shadow(10.dp, PopoverShape, ambientColor = Color.Transparent, spotColor = PopoverShadow)
                .background(Surface, PopoverShape)
                .border(1.dp, Border, PopoverShape)
                .padding(PopoverPadding),
            horizontalArrangement = Arrangement.spacedBy(ToneGap)
        ) {
            content()
        }
    }
}

/** One tone, 38 dp: its art when it is there, a spinner while it downloads, a dimmed mark when it failed. */
@Composable
private fun ToneCell(cell: ToneCellUi, onPick: () -> Unit) {
    val label = stringResource(skinToneLabel(cell.tone))
    Box(
        Modifier
            .size(ToneSize)
            .clip(ToneShape)
            .clickable(
                enabled = cell.state == ToneState.Ready,
                role = Role.Button,
                onClickLabel = label,
                onClick = onPick
            )
            .semantics { contentDescription = label }
            .padding(2.dp),
        contentAlignment = Alignment.Center
    ) {
        when (cell.state) {
            ToneState.Ready -> AsyncImage(
                model = cell.model,
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
            ToneState.Loading -> CircularProgressIndicator(Modifier.size(14.dp), color = Rose, strokeWidth = 2.dp)
            ToneState.Failed -> Icon(
                LoveIcons.WifiOff,
                contentDescription = null,
                modifier = Modifier.size(18.dp).alpha(FAILED_ALPHA),
                tint = Muted
            )
        }
    }
}

// --------------------------------------------------------------- stickers

/**
 * Add › Stickers (spec §9): the Doodles, then the Props, four to a row under their mono headers,
 * scrolling inside the sheet. A tap adds the piece.
 */
@Composable
private fun StickersTab(catalog: DecorCatalog, onPick: (String) -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val sections = remember(catalog) {
        listOf(
            R.string.create_decor_doodles to catalog.set(DecorCatalog.DOODLES),
            R.string.create_decor_props to catalog.set(DecorCatalog.PROPS)
        )
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = Modifier.heightIn(max = StickerGridMaxHeight),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        sections.forEach { (title, pieces) ->
            item(key = title, span = { GridItemSpan(maxLineSpan) }, contentType = "header") {
                Text(
                    stringResource(title).uppercase(locale),
                    style = SectionText,
                    color = Muted,
                    modifier = Modifier.padding(top = 2.dp).semantics { heading() }
                )
            }
            items(pieces, key = { it.file }, contentType = { "piece" }) { piece -> DecorTile(piece, onPick) }
        }
    }
}

/** One decoration piece: a square tile, radius 14, a hairline, 10 dp around its art. TalkBack reads its label. */
@Composable
private fun DecorTile(piece: DecorPiece, onPick: (String) -> Unit) {
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(DecorTileShape)
            .background(TileFill)
            .border(1.dp, Border, DecorTileShape)
            .clickable(role = Role.Button, onClickLabel = piece.label) { onPick(piece.file) }
            .padding(10.dp)
    ) {
        AsyncImage(
            model = DECOR_ASSETS + piece.file,
            contentDescription = piece.label,
            modifier = Modifier.fillMaxSize()
        )
    }
}
