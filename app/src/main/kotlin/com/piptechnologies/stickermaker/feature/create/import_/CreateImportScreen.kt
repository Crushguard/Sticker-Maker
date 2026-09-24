package com.piptechnologies.stickermaker.feature.create.import_

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.piptechnologies.stickermaker.core.design.Canvas as CanvasColor
import com.piptechnologies.stickermaker.core.design.Ink
import com.piptechnologies.stickermaker.core.design.LoveIcons
import com.piptechnologies.stickermaker.core.design.LoveStickersTheme
import com.piptechnologies.stickermaker.core.design.Mono
import com.piptechnologies.stickermaker.core.design.Muted
import com.piptechnologies.stickermaker.core.design.Rose
import com.piptechnologies.stickermaker.core.design.components.AddStickerTile
import com.piptechnologies.stickermaker.core.design.components.LoveTopBar
import com.piptechnologies.stickermaker.core.design.components.SegmentedControl
import com.piptechnologies.stickermaker.core.design.components.StickerTile
import com.piptechnologies.stickermaker.core.design.components.ToastHost
import com.piptechnologies.stickermaker.core.design.components.showToast
import com.piptechnologies.stickermaker.feature.create.CreateEvent
import com.piptechnologies.stickermaker.feature.create.CreateFooter
import com.piptechnologies.stickermaker.feature.create.CreatePackViewModel
import com.piptechnologies.stickermaker.feature.create.CreateSpec
import com.piptechnologies.stickermaker.feature.create.CreateUiState
import com.piptechnologies.stickermaker.feature.create.CreateItemUi
import com.piptechnologies.stickermaker.feature.create.CutStatus
import com.piptechnologies.stickermaker.feature.create.FooterHint
import com.piptechnologies.stickermaker.feature.create.ImportSource
import com.piptechnologies.stickermaker.feature.create.MonoCounterText
import com.piptechnologies.stickermaker.feature.create.PrimaryButton
import com.piptechnologies.stickermaker.feature.create.createPackViewModel

private val ViewfinderHintColor = Color(0xFFA2A9B4)

/**
 * Create · step 1 (Import): segmented Photos / Camera / Video, a 3-column
 * picker grid with rose check squares, and a footer primary that reads the
 * count and stays disabled under 3 stickers.
 *
 * Media arrives through the system photo picker ([ActivityResultContracts.PickVisualMedia],
 * no runtime permission) and the system camera preview capture
 * ([ActivityResultContracts.TakePicturePreview], no manifest entry needed).
 */
@Composable
fun CreateImportScreen(
    onBack: () -> Unit,
    onPicked: () -> Unit,
    viewModel: CreatePackViewModel = createPackViewModel()
) {
    val state by viewModel.state.collectAsState()
    val toaster = remember { SnackbarHostState() }

    // A finished export left this session behind; entering Import starts anew.
    LaunchedEffect(Unit) { viewModel.startFreshSessionIfFinished() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            if (event is CreateEvent.ShowToast) toaster.showToast(event.message, event.check)
        }
    }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(CreateSpec.MAX_STICKERS)
    ) { uris -> viewModel.addPickedImages(uris) }
    val videoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(CreateSpec.MAX_STICKERS)
    ) { uris -> viewModel.addPickedVideos(uris) }
    val camera = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap -> if (bitmap != null) viewModel.addCameraShot(bitmap) }

    CreateImportContent(
        state = state,
        toaster = toaster,
        onBack = onBack,
        onSelectSource = viewModel::selectSource,
        onToggleItem = viewModel::togglePicked,
        onAddMore = {
            if (state.source == ImportSource.Video) {
                videoPicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                )
            } else {
                photoPicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }
        },
        onShoot = {
            try {
                camera.launch(null)
            } catch (e: android.content.ActivityNotFoundException) {
                // No camera app on this device; the Photos picker still works.
            }
        },
        onNext = {
            if (state.selectedCount >= CreateSpec.MIN_STICKERS) {
                viewModel.beginCutouts()
                onPicked()
            }
        }
    )
}

@Composable
private fun CreateImportContent(
    state: CreateUiState,
    toaster: SnackbarHostState,
    onBack: () -> Unit,
    onSelectSource: (ImportSource) -> Unit,
    onToggleItem: (String) -> Unit,
    onAddMore: () -> Unit,
    onShoot: () -> Unit,
    onNext: () -> Unit
) {
    val n = state.selectedCount
    Box(Modifier.fillMaxSize().background(CanvasColor)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            LoveTopBar(
                title = "New pack",
                onBack = onBack,
                height = 52.dp,
                actions = {
                    Text(
                        if (n > 0) "$n selected" else "",
                        style = MonoCounterText,
                        color = Muted,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            )
            SegmentedControl(
                options = listOf("Photos", "Camera", "Video"),
                selectedIndex = state.source.ordinal,
                onSelect = { onSelectSource(ImportSource.entries[it]) },
                icons = listOf(LoveIcons.Images, LoveIcons.Camera, LoveIcons.Video),
                modifier = Modifier.padding(start = 20.dp, top = 2.dp, end = 20.dp, bottom = 12.dp)
            )
            if (state.source == ImportSource.Camera) {
                CameraPane(
                    shots = state.shots,
                    onShoot = onShoot,
                    modifier = Modifier.weight(1f)
                )
            } else {
                PickerGrid(
                    items = state.items.filter { it.isVideo == (state.source == ImportSource.Video) },
                    onToggleItem = onToggleItem,
                    onAddMore = onAddMore,
                    modifier = Modifier.weight(1f)
                )
            }
            CreateFooter {
                PrimaryButton(
                    label = if (n >= CreateSpec.MIN_STICKERS) "Next · $n stickers" else "Next",
                    enabled = n >= CreateSpec.MIN_STICKERS,
                    onClick = onNext
                )
                FooterHint(
                    if (n >= CreateSpec.MIN_STICKERS) {
                        "Next cuts out each subject on this phone. Up to 30 per pack."
                    } else {
                        "Pick at least 3 ($n of 3). Up to 30 per pack."
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

@Composable
private fun PickerGrid(
    items: List<CreateItemUi>,
    onToggleItem: (String) -> Unit,
    onAddMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 20.dp, end = 20.dp, bottom = 12.dp
        ),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items, key = { it.id }) { item ->
            StickerTile(
                onClick = { onToggleItem(item.id) },
                selected = item.selected,
                contentPadding = 0.dp,
                label = item.durationLabel,
                contentDescription = if (item.selected) "Remove from pack" else "Add to pack"
            ) {
                when {
                    item.pickerModel != null -> AsyncImage(
                        model = item.pickerModel,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    item.frameThumb != null -> Image(
                        bitmap = item.frameThumb,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    else -> Box(Modifier.fillMaxSize())
                }
            }
        }
        item(key = "add") {
            AddStickerTile(onClick = onAddMore)
        }
    }
}

@Composable
private fun CameraPane(
    shots: Int,
    onShoot: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hintStyle = TextStyle(fontFamily = Mono, fontWeight = FontWeight.W500, fontSize = 11.sp)
    Column(modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(20.dp))
                .background(Ink)
        ) {
            // Dashed framing guide, inset 18, like the design's viewfinder.
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(18.dp)
                    .drawBehind {
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.28f),
                            topLeft = Offset.Zero,
                            size = Size(size.width, size.height),
                            cornerRadius = CornerRadius(16.dp.toPx()),
                            style = Stroke(
                                width = 1.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
                            )
                        )
                    }
            )
            Text(
                "viewfinder",
                style = hintStyle,
                color = ViewfinderHintColor,
                modifier = Modifier.align(Alignment.Center)
            )
            Text(
                if (shots == 1) "1 shot" else "$shots shots",
                style = hintStyle,
                color = ViewfinderHintColor,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 14.dp, end = 16.dp)
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(66.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(4.dp, Rose, CircleShape)
                    .clickable(role = Role.Button, onClickLabel = "Take a picture", onClick = onShoot)
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFAFBFC, widthDp = 390, heightDp = 780)
@Composable
private fun CreateImportPreview() {
    LoveStickersTheme {
        CreateImportContent(
            state = CreateUiState(
                items = List(4) { index ->
                    CreateItemUi(
                        id = "m$index",
                        isVideo = false,
                        selected = index != 2,
                        durationLabel = null,
                        pickerModel = null,
                        frameThumb = null,
                        stickerThumb = null,
                        cut = CutStatus.None
                    )
                },
                selectedCount = 3
            ),
            toaster = SnackbarHostState(),
            onBack = {},
            onSelectSource = {},
            onToggleItem = {},
            onAddMore = {},
            onShoot = {},
            onNext = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFAFBFC, widthDp = 390, heightDp = 780)
@Composable
private fun CreateImportCameraPreview() {
    LoveStickersTheme {
        CreateImportContent(
            state = CreateUiState(source = ImportSource.Camera, shots = 2, selectedCount = 2),
            toaster = SnackbarHostState(),
            onBack = {},
            onSelectSource = {},
            onToggleItem = {},
            onAddMore = {},
            onShoot = {},
            onNext = {}
        )
    }
}
