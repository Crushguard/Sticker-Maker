package com.piptechnologies.stickermaker.feature.create.editor

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
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
import com.piptechnologies.stickermaker.core.design.components.LoveTopBar
import com.piptechnologies.stickermaker.core.design.components.ToastHost
import com.piptechnologies.stickermaker.core.design.components.TopBarIconButton
import com.piptechnologies.stickermaker.core.design.components.showToast
import com.piptechnologies.stickermaker.core.ui.asString
import com.piptechnologies.stickermaker.feature.create.CreateEvent
import com.piptechnologies.stickermaker.feature.create.CreateFooter
import com.piptechnologies.stickermaker.feature.create.CreatePackViewModel
import com.piptechnologies.stickermaker.feature.create.CreateSpec
import com.piptechnologies.stickermaker.feature.create.CutStatus
import com.piptechnologies.stickermaker.feature.create.EditorTool
import com.piptechnologies.stickermaker.feature.create.FooterDivider
import com.piptechnologies.stickermaker.feature.create.MonoCounterText
import com.piptechnologies.stickermaker.feature.create.PrimaryButton
import com.piptechnologies.stickermaker.feature.create.StickerRenderer
import com.piptechnologies.stickermaker.feature.create.createPackViewModel
import kotlin.math.roundToInt

private val UndoDisabled = Color(0xFFB4BAC4)
private val ToolIdle = Color(0xFFC3C9D2)
private val CheckerGrey = 0xFFE6E9EE.toInt()

/** Test tag of the cut-out canvas, used by the on-device screen tour. */
const val EDITOR_CANVAS_TAG = "editorCanvas"

/**
 * Create · step 2 (Cut out): the live editor. The on-device auto cut-out runs
 * per sticker; Brush restores, Erase removes (Porter-Duff strokes on the mask
 * bitmap), Text adds a caption, Zoom toggles a pinch/pan view, undo steps back
 * one touch, and the white-outline switch previews WhatsApp's recommended
 * die-cut edge. The rail carries a green check per finished sticker.
 */
@Composable
fun CreateEditorScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
    viewModel: CreatePackViewModel = createPackViewModel()
) {
    val state by viewModel.state.collectAsState()
    val toaster = remember { SnackbarHostState() }
    val context = LocalContext.current
    val captionTypeface = remember {
        try {
            ResourcesCompat.getFont(context, R.font.hg_extrabold)
        } catch (e: Exception) {
            null
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            if (event is CreateEvent.ShowToast) toaster.showToast(event.message.asString(context), event.check)
        }
    }

    val n = state.selectedCount
    val selected = state.selectedItems

    Box(Modifier.fillMaxSize().background(CanvasColor)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            LoveTopBar(
                title = stringResource(R.string.create_editor_title),
                onBack = onBack,
                height = 52.dp,
                actions = {
                    Text(
                        if (n > 0) stringResource(R.string.create_editor_position, state.activeIndex + 1, n) else "",
                        style = MonoCounterText,
                        color = Muted
                    )
                    TopBarIconButton(
                        icon = LoveIcons.Undo2,
                        contentDescription = stringResource(R.string.create_undo),
                        onClick = viewModel::undo,
                        tint = if (state.canUndo) Ink else UndoDisabled
                    )
                }
            )
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                EditorCanvasCard(
                    viewModel = viewModel,
                    tool = state.tool,
                    zoomed = state.zoomed,
                    activeIndex = state.activeIndex,
                    activeCut = state.activeCut,
                    outlineOn = state.activeOutlineOn,
                    text = state.activeText,
                    durationLabel = state.activeDurationLabel,
                    editorTick = state.editorTick,
                    captionTypeface = captionTypeface
                )
                EditorToolbar(
                    tool = state.tool,
                    zoomed = state.zoomed,
                    onTool = viewModel::selectTool,
                    onZoom = viewModel::toggleZoom
                )
                if (state.tool == EditorTool.Text) {
                    CaptionField(
                        text = state.activeText,
                        onTextChange = viewModel::setStickerText
                    )
                }
                if (state.tool == EditorTool.Brush || state.tool == EditorTool.Erase) {
                    BrushSizeRow(
                        label = stringResource(if (state.tool == EditorTool.Brush) R.string.create_brush_size else R.string.create_eraser_size),
                        brush = state.brush,
                        onBrush = viewModel::setBrush
                    )
                }
                OutlineRow(
                    checked = state.activeOutlineOn,
                    onToggle = { viewModel.toggleOutline() }
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(4.dp)
                ) {
                    itemsIndexed(selected, key = { _, item -> item.id }) { index, item ->
                        RailTile(
                            selected = index == state.activeIndex,
                            done = item.cut == CutStatus.Done,
                            pending = item.cut == CutStatus.Pending,
                            thumb = item.stickerThumb,
                            rawModel = item.pickerModel,
                            rawFrame = item.frameThumb,
                            onClick = { viewModel.selectSticker(index) }
                        )
                    }
                }
            }
            CreateFooter {
                PrimaryButton(
                    label = if (state.anyPending) {
                        stringResource(R.string.create_cutting_out)
                    } else {
                        pluralStringResource(R.plurals.create_next_count, n, n)
                    },
                    enabled = !state.anyPending,
                    onClick = onDone
                )
            }
        }
        ToastHost(
            hostState = toaster,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 88.dp)
        )
    }
}

// --------------------------------------------------------------- the canvas

@Composable
private fun EditorCanvasCard(
    viewModel: CreatePackViewModel,
    tool: EditorTool,
    zoomed: Boolean,
    activeIndex: Int,
    activeCut: CutStatus,
    outlineOn: Boolean,
    text: String,
    durationLabel: String?,
    editorTick: Int,
    captionTypeface: Typeface?
) {
    val bitmapPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG) }
    val checkerPaint = remember { Paint() }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var scale by remember { mutableFloatStateOf(1f) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }

    // The Zoom toggle starts at the prototype's 1.6x; pinch refines it.
    LaunchedEffect(zoomed, activeIndex) {
        scale = if (zoomed) 1.6f else 1f
        panX = 0f
        panY = 0f
    }

    fun viewToImage(p: Offset): Offset {
        val edge = canvasSize.width.toFloat()
        if (edge <= 0f) return Offset.Zero
        val centre = edge / 2f
        val bx = (p.x - panX - centre) / scale + centre
        val by = (p.y - panY - centre) / scale + centre
        val s0 = edge / CreateSpec.CANVAS_SIZE
        return Offset(bx / s0, by / s0)
    }

    val paintingTool = tool == EditorTool.Brush || tool == EditorTool.Erase
    val hint = stringResource(
        when {
            zoomed -> R.string.create_hint_zoomed
            tool == EditorTool.Auto ->
                if (activeCut == CutStatus.Done) R.string.create_hint_auto_done else R.string.create_hint_auto
            tool == EditorTool.Brush -> R.string.create_hint_brush
            tool == EditorTool.Erase -> R.string.create_hint_erase
            tool == EditorTool.Text -> R.string.create_hint_text
            else -> R.string.create_hint_default
        }
    )

    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(20.dp))
            .background(Surface)
            .border(1.dp, Border, RoundedCornerShape(20.dp))
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(27.dp)
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Subtle)
                    .testTag(EDITOR_CANVAS_TAG)
                    .onSizeChanged { canvasSize = it }
                    .pointerInput(zoomed, paintingTool, activeIndex) {
                        if (zoomed) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 4f)
                                val maxPan = (scale - 1f) * size.width / 2f
                                panX = (panX + pan.x).coerceIn(-maxPan, maxPan)
                                panY = (panY + pan.y).coerceIn(-maxPan, maxPan)
                            }
                        } else if (paintingTool) {
                            detectDragGestures(
                                onDragStart = { p ->
                                    val ip = viewToImage(p)
                                    viewModel.beginStroke(ip.x, ip.y)
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    val ip = viewToImage(change.position)
                                    viewModel.extendStroke(ip.x, ip.y)
                                },
                                onDragEnd = { viewModel.endStroke() },
                                onDragCancel = { viewModel.endStroke() }
                            )
                        }
                    }
                    .pointerInput(paintingTool, activeIndex) {
                        // Single taps dab with the brush/eraser — this also works while zoomed.
                        if (paintingTool) {
                            detectTapGestures { p ->
                                val ip = viewToImage(p)
                                viewModel.tapStroke(ip.x, ip.y)
                            }
                        }
                    }
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    // editorTick invalidates this draw whenever a mask/outline changed.
                    @Suppress("UNUSED_EXPRESSION") editorTick
                    val live = viewModel.activeCanvas()
                    val edge = size.width
                    val s0 = edge / CreateSpec.CANVAS_SIZE
                    drawIntoCanvas { canvas ->
                        val nc = canvas.nativeCanvas
                        nc.save()
                        nc.translate(panX, panY)
                        nc.scale(scale, scale, edge / 2f, edge / 2f)
                        nc.scale(s0, s0)
                        val src = live.source
                        val mask = live.mask
                        if (activeCut == CutStatus.Done && src != null && mask != null) {
                            drawChecker(nc, checkerPaint)
                            StickerRenderer.drawComposite(
                                nc,
                                src,
                                mask,
                                if (outlineOn) live.outline else null,
                                text,
                                captionTypeface
                            )
                        } else if (src != null) {
                            nc.drawBitmap(src, 0f, 0f, bitmapPaint)
                        }
                        nc.restore()
                    }
                }
                if (durationLabel != null) {
                    Text(
                        durationLabel,
                        style = TextStyle(fontFamily = Mono, fontWeight = FontWeight.W500, fontSize = 9.5.sp),
                        color = Ink2,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 10.dp, top = 8.dp)
                    )
                }
                if (activeCut == CutStatus.Pending) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .background(Color(0xB8FAFBFC)),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = Rose,
                            strokeWidth = 2.dp
                        )
                        Text(
                            stringResource(R.string.create_cutting_out_on_phone),
                            style = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.W600, fontSize = 12.5.sp),
                            color = Ink2,
                            modifier = Modifier.padding(top = 10.dp)
                        )
                    }
                }
            }
        }
        Text(
            hint,
            style = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.W500, fontSize = 11.sp),
            color = Ink2,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp)
        )
    }
}

/** 512-space transparency checker behind the cut subject. */
private fun drawChecker(canvas: android.graphics.Canvas, paint: Paint) {
    val cell = 32f
    val cells = (CreateSpec.CANVAS_SIZE / cell.toInt()).toInt()
    paint.color = android.graphics.Color.WHITE
    canvas.drawRect(0f, 0f, CreateSpec.CANVAS_SIZE.toFloat(), CreateSpec.CANVAS_SIZE.toFloat(), paint)
    paint.color = CheckerGrey
    for (y in 0 until cells) {
        for (x in 0 until cells) {
            if ((x + y) % 2 == 0) {
                canvas.drawRect(x * cell, y * cell, (x + 1) * cell, (y + 1) * cell, paint)
            }
        }
    }
}

// ---------------------------------------------------------------- controls

@Composable
private fun EditorToolbar(
    tool: EditorTool,
    zoomed: Boolean,
    onTool: (EditorTool) -> Unit,
    onZoom: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Ink)
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ToolButton(stringResource(R.string.create_tool_auto), LoveIcons.Wand2, tool == EditorTool.Auto, Modifier.weight(1f)) {
            onTool(EditorTool.Auto)
        }
        ToolButton(stringResource(R.string.create_tool_brush), LoveIcons.Brush, tool == EditorTool.Brush, Modifier.weight(1f)) {
            onTool(EditorTool.Brush)
        }
        ToolButton(stringResource(R.string.create_tool_erase), LoveIcons.Eraser, tool == EditorTool.Erase, Modifier.weight(1f)) {
            onTool(EditorTool.Erase)
        }
        ToolButton(stringResource(R.string.create_tool_text), LoveIcons.Type, tool == EditorTool.Text, Modifier.weight(1f)) {
            onTool(EditorTool.Text)
        }
        ToolButton(stringResource(R.string.create_tool_zoom), LoveIcons.ZoomIn, zoomed, Modifier.weight(1f), onZoom)
    }
}

@Composable
private fun ToolButton(
    label: String,
    icon: ImageVector,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val fg = if (active) Color.White else ToolIdle
    Column(
        modifier = modifier
            .height(54.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(if (active) Rose else Color.Transparent)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = fg)
        Spacer(Modifier.height(3.dp))
        Text(
            label,
            style = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.W600, fontSize = 10.sp),
            color = fg
        )
    }
}

@Composable
private fun CaptionField(text: String, onTextChange: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Surface)
            .border(1.dp, Border, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(LoveIcons.Type, null, Modifier.size(17.dp), tint = Muted)
        Spacer(Modifier.width(9.dp))
        BasicTextField(
            value = text,
            onValueChange = { onTextChange(it.take(CreateSpec.TEXT_MAX_CHARS)) },
            singleLine = true,
            textStyle = TextStyle(
                fontFamily = Hanken,
                fontWeight = FontWeight.W400,
                fontSize = 15.sp,
                color = Ink
            ),
            cursorBrush = SolidColor(Rose),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (text.isEmpty()) {
                        Text(
                            stringResource(R.string.create_caption_placeholder),
                            style = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.W400, fontSize = 15.sp),
                            color = Muted
                        )
                    }
                    inner()
                }
            }
        )
    }
}

@Composable
private fun BrushSizeRow(label: String, brush: Int, onBrush: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Surface)
            .border(1.dp, Border, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.W600, fontSize = 12.5.sp),
            color = Ink2
        )
        Spacer(Modifier.width(12.dp))
        Slider(
            value = brush.toFloat(),
            onValueChange = { onBrush(it.roundToInt()) },
            valueRange = 1f..3f,
            steps = 1,
            colors = SliderDefaults.colors(
                thumbColor = Rose,
                activeTrackColor = Rose,
                inactiveTrackColor = Border
            ),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun OutlineRow(checked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Surface)
            .border(1.dp, FooterDivider, RoundedCornerShape(14.dp))
            .padding(start = 14.dp, top = 12.dp, end = 14.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.create_outline_title),
                style = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.W600, fontSize = 14.sp),
                color = Ink
            )
            Text(
                stringResource(R.string.create_outline_body),
                style = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.W400, fontSize = 12.sp),
                color = Muted,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        AppSwitch(checked = checked, onCheckedChange = { onToggle() })
    }
}

// -------------------------------------------------------------------- rail

@Composable
private fun RailTile(
    selected: Boolean,
    done: Boolean,
    pending: Boolean,
    thumb: androidx.compose.ui.graphics.ImageBitmap?,
    rawModel: Any?,
    rawFrame: androidx.compose.ui.graphics.ImageBitmap?,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier
            .size(56.dp)
            .background(Subtle, shape)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) Rose else Border,
                shape = shape
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
                    .background(Color(0x99FAFBFC)),
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
    }
}
