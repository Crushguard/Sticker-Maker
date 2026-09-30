package com.piptechnologies.stickermaker.feature.create.editor

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
import com.piptechnologies.stickermaker.feature.create.CutStatus
import com.piptechnologies.stickermaker.feature.create.EditorTool
import com.piptechnologies.stickermaker.feature.create.FooterDivider
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
 * Auto, Brush, Erase, Add, Draw or Animate, undo steps back one touch, and the
 * outline switch previews WhatsApp's recommended die-cut edge. The rail carries
 * a green check per finished sticker.
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
                if (state.tool == EditorTool.Brush || state.tool == EditorTool.Erase) {
                    BrushSizeRow(
                        label = stringResource(if (state.tool == EditorTool.Brush) R.string.create_brush_size else R.string.create_eraser_size),
                        brush = state.brush,
                        onBrush = viewModel::setBrush
                    )
                }
                OutlineRow(
                    checked = state.outline.on,
                    onToggle = { viewModel.setOutlineOn(!state.outline.on) }
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
