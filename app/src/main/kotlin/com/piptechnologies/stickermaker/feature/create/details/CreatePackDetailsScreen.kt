package com.piptechnologies.stickermaker.feature.create.details

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.piptechnologies.stickermaker.core.design.Border
import com.piptechnologies.stickermaker.core.design.Canvas as CanvasColor
import com.piptechnologies.stickermaker.core.design.Hanken
import com.piptechnologies.stickermaker.core.design.Ink
import com.piptechnologies.stickermaker.core.design.Ink2
import com.piptechnologies.stickermaker.core.design.LoveIcons
import com.piptechnologies.stickermaker.core.design.LoveStickersTheme
import com.piptechnologies.stickermaker.core.design.Mono
import com.piptechnologies.stickermaker.core.design.Muted
import com.piptechnologies.stickermaker.core.design.Muted2
import com.piptechnologies.stickermaker.core.design.Rose
import com.piptechnologies.stickermaker.core.design.Subtle
import com.piptechnologies.stickermaker.core.design.Surface
import com.piptechnologies.stickermaker.core.design.components.AddBar
import com.piptechnologies.stickermaker.core.design.components.AddVisualState
import com.piptechnologies.stickermaker.core.design.components.ConfirmSheet
import com.piptechnologies.stickermaker.core.design.components.LoveTopBar
import com.piptechnologies.stickermaker.core.design.components.ToastHost
import com.piptechnologies.stickermaker.core.design.components.showToast
import com.piptechnologies.stickermaker.feature.create.CreateEvent
import com.piptechnologies.stickermaker.feature.create.CreateFooter
import com.piptechnologies.stickermaker.feature.create.CreateItemUi
import com.piptechnologies.stickermaker.feature.create.CreatePackViewModel
import com.piptechnologies.stickermaker.feature.create.CreateSpec
import com.piptechnologies.stickermaker.feature.create.CreateUiState
import com.piptechnologies.stickermaker.feature.create.CutStatus
import com.piptechnologies.stickermaker.feature.create.createPackViewModel
import com.piptechnologies.stickermaker.feature.create.isBusy
import com.piptechnologies.stickermaker.whatsapp.AddStickerPackFlow

private val InfoCardBg = Color(0xFFF6F7F9)
private val InfoCardLine = Color(0xFFEBEEF2)

/**
 * Create · step 3 (Pack details): tray-icon picker (first sticker by default,
 * exported at 96×96), the pack-name field with its counter, the export-facts
 * info card, the shared Add bar with its states and the ghost
 * "Save to My Packs only". Export renders 512×512 WebP on-device, validates,
 * writes to filesDir and registers the pack before anything reaches WhatsApp.
 */
@Composable
fun CreatePackDetailsScreen(
    onBack: () -> Unit,
    onExported: () -> Unit,
    viewModel: CreatePackViewModel = createPackViewModel()
) {
    val state by viewModel.state.collectAsState()
    val toaster = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showNoWhatsApp by remember { mutableStateOf(false) }

    val addLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val added = AddStickerPackFlow.parseResult(result.resultCode, result.data) is
            AddStickerPackFlow.AddResult.Added
        viewModel.onWhatsAppResult(added)
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is CreateEvent.ShowToast -> toaster.showToast(event.message, event.check)
                is CreateEvent.LaunchAddToWhatsApp -> {
                    val intent = AddStickerPackFlow.createBestIntent(
                        context, event.identifier, event.packName
                    )
                    if (intent == null) {
                        // Nothing left to launch — the pack is already everywhere.
                        viewModel.onWhatsAppResult(added = true)
                    } else {
                        try {
                            addLauncher.launch(intent)
                        } catch (e: ActivityNotFoundException) {
                            viewModel.onWhatsAppResult(added = false)
                        }
                    }
                }
                CreateEvent.ExportComplete -> onExported()
            }
        }
    }

    CreatePackDetailsContent(
        state = state,
        toaster = toaster,
        onBack = onBack,
        onSelectTray = viewModel::selectTray,
        onNameChange = viewModel::setPackName,
        onAdd = {
            when {
                state.exportState == AddVisualState.Added -> viewModel.notifyAlreadyAdded()
                state.exportState.isBusy() -> Unit
                !AddStickerPackFlow.isWhatsAppInstalled(context) -> showNoWhatsApp = true
                else -> viewModel.addToWhatsApp()
            }
        },
        onSaveOnly = {
            if (!state.exportState.isBusy()) viewModel.saveToMyPacksOnly()
        }
    )

    if (showNoWhatsApp) {
        ConfirmSheet(
            title = "WhatsApp isn't installed",
            body = "Stickers are added inside WhatsApp. Install it, then come back to add this pack.",
            confirmLabel = "Get WhatsApp",
            cancelLabel = "Not now",
            destructive = false,
            icon = LoveIcons.MessageCircle,
            onConfirm = {
                showNoWhatsApp = false
                openWhatsAppStorePage(context)
            },
            onDismiss = { showNoWhatsApp = false }
        )
    }
}

private fun openWhatsAppStorePage(context: Context) {
    val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.whatsapp"))
    try {
        context.startActivity(market)
    } catch (e: ActivityNotFoundException) {
        try {
            context.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=com.whatsapp")
                )
            )
        } catch (ignored: ActivityNotFoundException) {
        }
    }
}

@Composable
private fun CreatePackDetailsContent(
    state: CreateUiState,
    toaster: SnackbarHostState,
    onBack: () -> Unit,
    onSelectTray: (Int) -> Unit,
    onNameChange: (String) -> Unit,
    onAdd: () -> Unit,
    onSaveOnly: () -> Unit
) {
    val selected = state.selectedItems
    val trayItem = selected.getOrNull(state.trayIndex) ?: selected.firstOrNull()

    Box(Modifier.fillMaxSize().background(CanvasColor)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            LoveTopBar(title = "Pack details", onBack = onBack, height = 52.dp)
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, top = 4.dp, end = 20.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                TrayHeader(trayItem)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(2.dp)
                ) {
                    itemsIndexed(selected, key = { _, item -> item.id }) { index, item ->
                        TrayRailTile(
                            item = item,
                            selected = index == state.trayIndex,
                            onClick = { onSelectTray(index) }
                        )
                    }
                }
                PackNameField(name = state.packName, onNameChange = onNameChange)
                InfoCard(count = selected.size, animated = state.animatedPack)
            }
            CreateFooter(bottomPadding = 14.dp) {
                AddBar(
                    state = state.exportState,
                    progress = state.exportProgress,
                    hint = null,
                    onClick = onAdd
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .height(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(
                            role = Role.Button,
                            onClickLabel = "Save to My Packs only",
                            onClick = onSaveOnly
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Save to My Packs only",
                        style = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.W600, fontSize = 14.sp),
                        color = Ink2
                    )
                }
            }
        }
        ToastHost(
            hostState = toaster,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 120.dp)
        )
    }
}

@Composable
private fun TrayHeader(trayItem: CreateItemUi?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(84.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Surface)
                .border(1.dp, Border, RoundedCornerShape(22.dp)),
            contentAlignment = Alignment.Center
        ) {
            StickerPreview(trayItem, Modifier.size(60.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(
                "Tray icon",
                style = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.W700, fontSize = 15.sp),
                color = Ink
            )
            Text(
                "Shown in WhatsApp's sticker picker. Tap a sticker below to use it.",
                style = TextStyle(
                    fontFamily = Hanken,
                    fontWeight = FontWeight.W400,
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp
                ),
                color = Muted,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
    }
}

@Composable
private fun TrayRailTile(item: CreateItemUi, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier
            .size(56.dp)
            .clip(shape)
            .background(Subtle)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) Rose else Border,
                shape = shape
            )
            .clickable(role = Role.Button, onClickLabel = "Use as tray icon", onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        StickerPreview(item, Modifier.fillMaxSize().padding(4.dp))
    }
}

/** Composite thumb when the sticker is cut; the raw picture otherwise. */
@Composable
private fun StickerPreview(item: CreateItemUi?, modifier: Modifier = Modifier) {
    when {
        item == null -> Box(modifier)
        item.cut == CutStatus.Done && item.stickerThumb != null -> Image(
            bitmap = item.stickerThumb,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = modifier
        )
        item.pickerModel != null -> AsyncImage(
            model = item.pickerModel,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(RoundedCornerShape(10.dp))
        )
        item.frameThumb != null -> Image(
            bitmap = item.frameThumb,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(RoundedCornerShape(10.dp))
        )
        else -> Box(modifier)
    }
}

@Composable
private fun PackNameField(name: String, onNameChange: (String) -> Unit) {
    Column {
        Text(
            "Pack name",
            style = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.W600, fontSize = 12.5.sp),
            color = Ink2,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Row(
            Modifier
                .fillMaxWidth()
                .height(50.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Surface)
                .border(1.dp, Border, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = name,
                onValueChange = { onNameChange(it.take(CreateSpec.NAME_MAX_CHARS)) },
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
                        if (name.isEmpty()) {
                            Text(
                                "e.g. Us, always",
                                style = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.W400, fontSize = 15.sp),
                                color = Muted
                            )
                        }
                        inner()
                    }
                }
            )
            Spacer(Modifier.width(9.dp))
            Text(
                "${name.length}/${CreateSpec.NAME_MAX_CHARS}",
                style = TextStyle(fontFamily = Mono, fontWeight = FontWeight.W400, fontSize = 11.sp),
                color = Muted2
            )
        }
    }
}

@Composable
private fun InfoCard(count: Int, animated: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(InfoCardBg)
            .border(1.dp, InfoCardLine, RoundedCornerShape(16.dp))
            .padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 14.dp)
    ) {
        Icon(
            LoveIcons.Info,
            null,
            Modifier.padding(top = 1.dp).size(16.dp),
            tint = Muted
        )
        Spacer(Modifier.width(10.dp))
        Text(
            buildAnnotatedString {
                withStyle(
                    SpanStyle(fontFamily = Mono, fontWeight = FontWeight.W500, fontSize = 11.5.sp)
                ) {
                    append("$count stickers · ${if (animated) "Animated" else "Static"} · 512×512 WebP")
                }
                append(" · under 100 KB each, made on this phone. Nothing is uploaded.")
            },
            style = TextStyle(
                fontFamily = Hanken,
                fontWeight = FontWeight.W400,
                fontSize = 12.5.sp,
                lineHeight = 19.sp
            ),
            color = Ink2
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFAFBFC, widthDp = 390, heightDp = 780)
@Composable
private fun CreatePackDetailsPreview() {
    LoveStickersTheme {
        CreatePackDetailsContent(
            state = CreateUiState(
                items = List(4) { index ->
                    CreateItemUi(
                        id = "m$index",
                        isVideo = false,
                        selected = true,
                        durationLabel = null,
                        pickerModel = null,
                        frameThumb = null,
                        stickerThumb = null,
                        cut = CutStatus.Done
                    )
                },
                selectedCount = 4,
                packName = "Us, always"
            ),
            toaster = SnackbarHostState(),
            onBack = {},
            onSelectTray = {},
            onNameChange = {},
            onAdd = {},
            onSaveOnly = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFAFBFC, widthDp = 390, heightDp = 780)
@Composable
private fun CreatePackDetailsAnimatedPreview() {
    LoveStickersTheme {
        CreatePackDetailsContent(
            state = CreateUiState(
                items = List(3) { index ->
                    CreateItemUi(
                        id = "v$index",
                        isVideo = true,
                        selected = true,
                        durationLabel = "▶ 0:03",
                        pickerModel = null,
                        frameThumb = null,
                        stickerThumb = null,
                        cut = CutStatus.Done
                    )
                },
                selectedCount = 3,
                animatedPack = true
            ),
            toaster = SnackbarHostState(),
            onBack = {},
            onSelectTray = {},
            onNameChange = {},
            onAdd = {},
            onSaveOnly = {}
        )
    }
}
