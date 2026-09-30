package com.piptechnologies.stickermaker.feature.create.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piptechnologies.stickermaker.R
import com.piptechnologies.stickermaker.core.design.Border
import com.piptechnologies.stickermaker.core.design.Canvas as CanvasColor
import com.piptechnologies.stickermaker.core.design.Hanken
import com.piptechnologies.stickermaker.core.design.Ink
import com.piptechnologies.stickermaker.core.design.Ink2
import com.piptechnologies.stickermaker.core.design.LoveIcons
import com.piptechnologies.stickermaker.core.design.Muted
import com.piptechnologies.stickermaker.core.design.Rose
import com.piptechnologies.stickermaker.core.design.Surface
import com.piptechnologies.stickermaker.core.design.components.LoveTopBar
import com.piptechnologies.stickermaker.core.design.components.ToastHost
import com.piptechnologies.stickermaker.core.design.components.TopBarIconButton
import com.piptechnologies.stickermaker.core.design.components.showToast
import com.piptechnologies.stickermaker.core.ui.asString
import com.piptechnologies.stickermaker.feature.create.CreateEvent
import com.piptechnologies.stickermaker.feature.create.CreateFooter
import com.piptechnologies.stickermaker.feature.create.CreatePackViewModel
import com.piptechnologies.stickermaker.feature.create.CutStatus
import com.piptechnologies.stickermaker.feature.create.EditorTool
import com.piptechnologies.stickermaker.feature.create.MonoCounterText
import com.piptechnologies.stickermaker.feature.create.PrimaryButton
import com.piptechnologies.stickermaker.feature.create.createPackViewModel
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val UndoDisabled = Color(0xFFB4BAC4)

/**
 * Create · step 2 (Cut out): the live editor. The on-device auto cut-out runs
 * per sticker; the canvas card ([EditorCanvasCard]) shows the decorated sticker
 * with its layer overlay and Zoom button, the tool bar ([EditorToolbar]) picks
 * Auto, Brush, Erase, Add, Draw or Animate, and undo steps back one touch.
 * Under the bar sits the tool's own row (the brush size, the [DrawRow] or the
 * [AnimateStrip]), then the [OutlineRow] with the die-cut's switch, thickness
 * and colour, which stays in every tool. The rail carries a green check per
 * finished sticker and an ANIM badge on the ones that move.
 */
@Composable
fun CreateEditorScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
    viewModel: CreatePackViewModel = createPackViewModel()
) {
    val state by viewModel.state.collectAsState()
    val toaster = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            if (event is CreateEvent.ShowToast) {
                scope.launch { toaster.showToast(event.message.asString(context), event.check) }
            }
        }
    }

    val n = state.selectedCount
    val selected = state.selectedItems
    // Null until the decor data has loaded; state.dataReady brings the recomposition that reads it.
    val data = viewModel.data

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
                EditorCanvasCard(state = state, viewModel = viewModel)
                EditorToolbar(
                    tool = state.tool,
                    layerToolsEnabled = state.layerToolsEnabled,
                    onTool = viewModel::selectTool
                )
                // One row at a time under the bar; Auto has none, and Add opens its sheet instead.
                when (state.tool) {
                    EditorTool.Brush, EditorTool.Erase -> BrushSizeRow(
                        label = stringResource(
                            if (state.tool == EditorTool.Brush) {
                                R.string.create_brush_size
                            } else {
                                R.string.create_eraser_size
                            }
                        ),
                        brush = state.brush,
                        onBrush = viewModel::setBrush
                    )
                    EditorTool.Draw -> DrawRow(
                        colours = data?.styles?.colours.orEmpty(),
                        colour = state.drawColour,
                        size = state.drawSize,
                        onColour = viewModel::setDrawColour,
                        onSize = viewModel::setDrawSize
                    )
                    EditorTool.Animate -> AnimateStrip(
                        presets = data?.motion?.presets.orEmpty(),
                        selected = state.preset,
                        thumb = selected.getOrNull(state.activeIndex)?.stickerThumb,
                        clip = state.activeIsVideo,
                        enabled = state.layerToolsEnabled && !state.activeIsVideo,
                        renderer = viewModel.renderer,
                        onPreset = viewModel::setPreset
                    )
                    EditorTool.Auto, EditorTool.Add -> Unit
                }
                OutlineRow(
                    outline = state.outline,
                    colours = data?.styles?.outlineColours.orEmpty(),
                    onToggle = viewModel::setOutlineOn,
                    onThickness = viewModel::setOutlineThickness,
                    onColour = viewModel::setOutlineColour
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    // 8 dp under the tiles: the ANIM badge hangs 6 dp below them.
                    contentPadding = PaddingValues(start = 4.dp, top = 4.dp, end = 4.dp, bottom = 8.dp)
                ) {
                    itemsIndexed(selected, key = { _, item -> item.id }) { index, item ->
                        RailTile(
                            selected = index == state.activeIndex,
                            done = item.cut == CutStatus.Done,
                            pending = item.cut == CutStatus.Pending,
                            animated = item.animated || item.isVideo,
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
                    onClick = {
                        viewModel.flushEdits()
                        onDone()
                    }
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

// ---------------------------------------------------------------- controls

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
