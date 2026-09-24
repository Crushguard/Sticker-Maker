package com.piptechnologies.stickermaker.feature.mypacks

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.piptechnologies.stickermaker.core.design.Canvas
import com.piptechnologies.stickermaker.core.design.Hanken
import com.piptechnologies.stickermaker.core.design.Ink
import com.piptechnologies.stickermaker.core.design.Ink2
import com.piptechnologies.stickermaker.core.design.LoveIcons
import com.piptechnologies.stickermaker.core.design.LoveStickersTheme
import com.piptechnologies.stickermaker.core.design.Mono
import com.piptechnologies.stickermaker.core.design.Muted
import com.piptechnologies.stickermaker.core.design.Subtle
import com.piptechnologies.stickermaker.core.design.components.AddVisualState
import com.piptechnologies.stickermaker.core.design.components.ConfirmSheet
import com.piptechnologies.stickermaker.core.design.components.EmptyState
import com.piptechnologies.stickermaker.core.design.components.LoveBottomSheet
import com.piptechnologies.stickermaker.core.design.components.PackCard
import com.piptechnologies.stickermaker.core.design.components.SheetHeader
import com.piptechnologies.stickermaker.core.design.components.SheetListRow
import com.piptechnologies.stickermaker.core.design.components.ToastHost
import com.piptechnologies.stickermaker.core.design.components.TopBarIconButton
import com.piptechnologies.stickermaker.core.design.components.showToast
import com.piptechnologies.stickermaker.core.model.AddState
import com.piptechnologies.stickermaker.whatsapp.AddStickerPackFlow
import java.io.File
import kotlinx.coroutines.launch

// Toolbar title: 22/800, tighter tracking (one step under the 26 display).
private val MyPacksTitleText = TextStyle(
    fontFamily = Hanken,
    fontWeight = FontWeight.W800,
    fontSize = 22.sp,
    letterSpacing = (-0.02).em
)

// Mono section eyebrow: "IN WHATSAPP · 2".
private val EyebrowText = TextStyle(
    fontFamily = Mono,
    fontWeight = FontWeight.W700,
    fontSize = 11.sp,
    letterSpacing = 0.08.em
)

private val InfoBodyText = TextStyle(
    fontFamily = Hanken,
    fontWeight = FontWeight.W400,
    fontSize = 12.sp,
    lineHeight = 18.sp
)

// Info-card greys from the frame (not core palette tokens).
private val InfoBg = Color(0xFFF6F7F9)
private val InfoLine = Color(0xFFEBEEF2)

private const val WHATSAPP_PACKAGE = "com.whatsapp"

/**
 * My Packs (design/Screens.dc.html section 07): toolbar with the Saved heart
 * (rose count badge) and Settings, "In WhatsApp" and "Made by you" sections,
 * the ⋯ card menu with its confirmation sheets, and the removal info card.
 * The WhatsApp whitelist is re-checked on every resume.
 */
@Composable
fun MyPacksScreen(
    onBack: () -> Unit,
    onOpenPack: (String) -> Unit,
    onCreate: () -> Unit,
    onOpenSaved: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    viewModel: MyPacksViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val toastHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val addLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.onAddResult(AddStickerPackFlow.parseResult(result.resultCode, result.data))
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is MyPacksEvent.LaunchAddIntent -> try {
                    addLauncher.launch(event.intent)
                } catch (notFound: ActivityNotFoundException) {
                    viewModel.onAddLaunchFailed()
                }
                is MyPacksEvent.Toast -> scope.launch {
                    toastHost.showToast(event.message, event.withCheck)
                }
            }
        }
    }

    // Re-check the whitelist whenever the user comes back from WhatsApp.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshWhitelist()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(Modifier.fillMaxSize()) {
        MyPacksContent(
            state = state,
            onOpenPack = onOpenPack,
            onPillClicked = viewModel::onPillClicked,
            onMenu = viewModel::openMenu,
            onBrowse = onBack,
            onCreate = onCreate,
            onOpenSaved = onOpenSaved,
            onOpenSettings = onOpenSettings
        )
        ToastHost(
            hostState = toastHost,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 84.dp)
        )
    }

    state.menuFor?.let { row ->
        LoveBottomSheet(onDismissRequest = viewModel::closeMenu) {
            SheetHeader(title = row.name)
            Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 20.dp)) {
                SheetListRow(
                    label = if (row.whitelisted) MENU_READD else MENU_ADD,
                    icon = if (row.whitelisted) LoveIcons.RefreshCw else LoveIcons.MessageCircle,
                    onClick = viewModel::onMenuAddToWhatsApp
                )
                if (row.own) {
                    SheetListRow(
                        label = MENU_DELETE,
                        icon = LoveIcons.Trash2,
                        onClick = viewModel::onMenuDelete,
                        destructive = true
                    )
                } else {
                    SheetListRow(
                        label = MENU_REMOVE,
                        icon = LoveIcons.Trash2,
                        onClick = viewModel::onMenuRemove,
                        destructive = true
                    )
                }
            }
        }
    }

    when (val confirm = state.confirm) {
        is MyPacksConfirm.RemoveInstalled -> ConfirmSheet(
            title = "Remove “${confirm.packName}” from this app?",
            body = REMOVE_BODY,
            confirmLabel = REMOVE_CONFIRM,
            cancelLabel = CONFIRM_KEEP,
            onConfirm = viewModel::confirmRemove,
            onDismiss = viewModel::dismissConfirm
        )
        is MyPacksConfirm.DeleteOwn -> ConfirmSheet(
            title = "Delete “${confirm.packName}”?",
            body = DELETE_BODY,
            confirmLabel = DELETE_CONFIRM,
            cancelLabel = CONFIRM_KEEP,
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::dismissConfirm
        )
        null -> Unit
    }

    if (state.showNoWhatsApp) {
        ConfirmSheet(
            title = NO_WHATSAPP_TITLE,
            body = NO_WHATSAPP_BODY,
            confirmLabel = NO_WHATSAPP_CONFIRM,
            cancelLabel = NO_WHATSAPP_CANCEL,
            destructive = false,
            icon = LoveIcons.MessageCircle,
            onConfirm = {
                openWhatsAppOnPlayStore(context)
                viewModel.dismissNoWhatsApp()
            },
            onDismiss = viewModel::dismissNoWhatsApp
        )
    }
}

/** Pure layout, driven by [MyPacksUiState]; also hosts the previews. */
@Composable
internal fun MyPacksContent(
    state: MyPacksUiState,
    onOpenPack: (String) -> Unit,
    onPillClicked: (String) -> Unit,
    onMenu: (String) -> Unit,
    onBrowse: () -> Unit,
    onCreate: () -> Unit,
    onOpenSaved: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .fillMaxSize()
            .background(Canvas)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(start = 20.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                TITLE_MY_PACKS,
                style = MyPacksTitleText,
                color = Ink,
                modifier = Modifier.weight(1f)
            )
            TopBarIconButton(
                icon = LoveIcons.Heart,
                contentDescription = "Saved packs",
                onClick = onOpenSaved,
                badgeCount = state.savedCount
            )
            TopBarIconButton(
                icon = LoveIcons.Settings,
                contentDescription = "Settings",
                onClick = onOpenSettings
            )
        }
        when {
            state.loading -> Spacer(Modifier.weight(1f))
            state.empty -> Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                EmptyState(
                    icon = LoveIcons.Sticker,
                    title = EMPTY_TITLE,
                    body = EMPTY_BODY,
                    primaryLabel = EMPTY_PRIMARY,
                    onPrimary = onBrowse,
                    ghostLabel = EMPTY_GHOST,
                    onGhost = onCreate,
                    modifier = Modifier.padding(bottom = 70.dp)
                )
            }
            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (state.installed.isNotEmpty()) {
                    item(key = "eyebrow-installed") {
                        SectionEyebrow(
                            "$SECTION_IN_WHATSAPP · ${state.installed.size}",
                            topPadding = 4.dp
                        )
                    }
                    items(state.installed, key = { it.id }) { row ->
                        MyPackCard(row, onOpenPack, onPillClicked, onMenu)
                    }
                }
                if (state.own.isNotEmpty()) {
                    item(key = "eyebrow-own") {
                        SectionEyebrow(
                            "$SECTION_MADE_BY_YOU · ${state.own.size}",
                            topPadding = 8.dp
                        )
                    }
                    items(state.own, key = { it.id }) { row ->
                        MyPackCard(row, onOpenPack, onPillClicked, onMenu)
                    }
                }
                item(key = "info-removal") { RemovalInfoCard() }
            }
        }
    }
}

@Composable
private fun MyPackCard(
    row: MyPackRow,
    onOpenPack: (String) -> Unit,
    onPillClicked: (String) -> Unit,
    onMenu: (String) -> Unit
) {
    val thumbnails: List<@Composable () -> Unit> = row.thumbFiles.map { file ->
        { PackThumb(file) }
    }
    PackCard(
        title = row.name,
        stickerCount = row.stickerCount,
        downloadsLabel = row.metaLabel,
        animated = row.animated,
        addState = row.addState.toVisual(),
        addProgress = (row.addState as? AddState.Downloading)?.progress ?: 0f,
        iconOnlyWhenAdded = true,
        onAdd = { onPillClicked(row.id) },
        onClick = { onOpenPack(row.id) },
        onMenu = { onMenu(row.id) },
        thumbnails = thumbnails
    )
}

/** 46dp circle preview from a local sticker file. */
@Composable
private fun PackThumb(file: File) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Subtle),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = file,
            contentDescription = null,
            modifier = Modifier.size(42.dp),
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
private fun SectionEyebrow(text: String, topPadding: Dp) {
    Text(
        text.uppercase(),
        style = EyebrowText,
        color = Muted,
        modifier = Modifier.padding(top = topPadding)
    )
}

@Composable
private fun RemovalInfoCard() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(InfoBg)
            .border(1.dp, InfoLine, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Icon(
            LoveIcons.Info,
            contentDescription = null,
            modifier = Modifier
                .padding(top = 1.dp)
                .size(16.dp),
            tint = Muted
        )
        Spacer(Modifier.width(9.dp))
        Text(INFO_REMOVAL, style = InfoBodyText, color = Ink2)
    }
}

/** [AddState] -> the visual state the design components render. */
private fun AddState.toVisual(): AddVisualState = when (this) {
    AddState.Idle -> AddVisualState.Idle
    is AddState.Downloading -> AddVisualState.Downloading
    AddState.Sent -> AddVisualState.Sent
    AddState.Added -> AddVisualState.Added
    is AddState.Failed -> AddVisualState.Failed
}

/** "Get WhatsApp": Play Store detail page, web fallback. */
private fun openWhatsAppOnPlayStore(context: Context) {
    val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$WHATSAPP_PACKAGE"))
    runCatching { context.startActivity(market) }.onFailure {
        val web = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://play.google.com/store/apps/details?id=$WHATSAPP_PACKAGE")
        )
        runCatching { context.startActivity(web) }
    }
}

// ---- Previews --------------------------------------------------------------

private fun previewRow(
    id: String,
    name: String,
    own: Boolean,
    whitelisted: Boolean,
    animated: Boolean = false
) = MyPackRow(
    id = id,
    name = name,
    animated = animated,
    stickerCount = if (own) 26 else 18,
    metaLabel = if (own) META_YOURS else "96.4K adds",
    own = own,
    whitelisted = whitelisted,
    addState = if (whitelisted) AddState.Added else AddState.Idle,
    thumbFiles = emptyList()
)

@Preview(showBackground = true, backgroundColor = 0xFFFAFBFC, widthDp = 390, heightDp = 780)
@Composable
private fun MyPacksPreview() {
    LoveStickersTheme {
        MyPacksContent(
            state = MyPacksUiState(
                loading = false,
                installed = listOf(
                    previewRow("clingy-mango", "Clingy Mango", own = false, whitelisted = true),
                    previewRow("mango-moves", "Mango Moves", own = false, whitelisted = true, animated = true)
                ),
                own = listOf(previewRow("own-1", "Us, always", own = true, whitelisted = false)),
                savedCount = 2
            ),
            onOpenPack = {}, onPillClicked = {}, onMenu = {},
            onBrowse = {}, onCreate = {}, onOpenSaved = {}, onOpenSettings = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFAFBFC, widthDp = 390, heightDp = 780)
@Composable
private fun MyPacksEmptyPreview() {
    LoveStickersTheme {
        MyPacksContent(
            state = MyPacksUiState(loading = false),
            onOpenPack = {}, onPillClicked = {}, onMenu = {},
            onBrowse = {}, onCreate = {}, onOpenSaved = {}, onOpenSettings = {}
        )
    }
}
