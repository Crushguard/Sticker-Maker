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
import androidx.compose.ui.res.stringResource
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
import com.piptechnologies.stickermaker.R
import com.piptechnologies.stickermaker.core.design.Canvas
import com.piptechnologies.stickermaker.core.design.Hanken
import com.piptechnologies.stickermaker.core.design.Ink
import com.piptechnologies.stickermaker.core.design.Ink2
import com.piptechnologies.stickermaker.core.design.LoveIcons
import com.piptechnologies.stickermaker.core.design.LoveStickersTheme
import com.piptechnologies.stickermaker.core.design.Mono
import com.piptechnologies.stickermaker.core.design.Muted
import com.piptechnologies.stickermaker.core.design.Subtle
import com.piptechnologies.stickermaker.core.design.Surface
import com.piptechnologies.stickermaker.core.design.components.AddVisualState
import com.piptechnologies.stickermaker.core.design.components.ConfirmSheet
import com.piptechnologies.stickermaker.core.design.components.EmptyState
import com.piptechnologies.stickermaker.core.design.components.LoveBottomNav
import com.piptechnologies.stickermaker.core.design.components.LoveBottomSheet
import com.piptechnologies.stickermaker.core.design.components.LoveNavItem
import com.piptechnologies.stickermaker.core.design.components.PackCard
import com.piptechnologies.stickermaker.core.design.components.SheetHeader
import com.piptechnologies.stickermaker.core.design.components.SheetListRow
import com.piptechnologies.stickermaker.core.design.components.ToastHost
import com.piptechnologies.stickermaker.core.design.components.TopBarIconButton
import com.piptechnologies.stickermaker.core.design.components.showToast
import com.piptechnologies.stickermaker.core.model.AddState
import com.piptechnologies.stickermaker.core.ui.UiText
import com.piptechnologies.stickermaker.core.ui.asString
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
                    toastHost.showToast(event.message.asString(context), event.withCheck)
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
            onHome = onBack,
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
                    label = stringResource(if (row.whitelisted) R.string.my_packs_menu_readd else R.string.my_packs_menu_add),
                    icon = if (row.whitelisted) LoveIcons.RefreshCw else LoveIcons.MessageCircle,
                    onClick = viewModel::onMenuAddToWhatsApp
                )
                if (row.own) {
                    SheetListRow(
                        label = stringResource(R.string.my_packs_menu_delete),
                        icon = LoveIcons.Trash2,
                        onClick = viewModel::onMenuDelete,
                        destructive = true
                    )
                } else {
                    SheetListRow(
                        label = stringResource(R.string.my_packs_menu_remove),
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
            title = stringResource(R.string.my_packs_remove_title, confirm.packName),
            body = stringResource(R.string.my_packs_remove_body),
            confirmLabel = stringResource(R.string.my_packs_remove_confirm),
            cancelLabel = stringResource(R.string.common_keep),
            onConfirm = viewModel::confirmRemove,
            onDismiss = viewModel::dismissConfirm
        )
        is MyPacksConfirm.DeleteOwn -> ConfirmSheet(
            title = stringResource(R.string.my_packs_delete_title, confirm.packName),
            body = stringResource(R.string.my_packs_delete_body),
            confirmLabel = stringResource(R.string.my_packs_delete_confirm),
            cancelLabel = stringResource(R.string.common_keep),
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::dismissConfirm
        )
        null -> Unit
    }

    if (state.showNoWhatsApp) {
        ConfirmSheet(
            title = stringResource(R.string.no_whatsapp_title),
            body = stringResource(R.string.no_whatsapp_body),
            confirmLabel = stringResource(R.string.no_whatsapp_confirm),
            cancelLabel = stringResource(R.string.common_not_now),
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
    onHome: () -> Unit,
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
                stringResource(R.string.nav_my_packs),
                style = MyPacksTitleText,
                color = Ink,
                modifier = Modifier.weight(1f)
            )
            TopBarIconButton(
                icon = LoveIcons.Heart,
                contentDescription = stringResource(R.string.my_packs_saved),
                onClick = onOpenSaved,
                badgeCount = state.savedCount
            )
            TopBarIconButton(
                icon = LoveIcons.Settings,
                contentDescription = stringResource(R.string.home_settings),
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
                    title = stringResource(R.string.my_packs_empty_title),
                    body = stringResource(R.string.my_packs_empty_body),
                    primaryLabel = stringResource(R.string.common_browse_packs),
                    onPrimary = onBrowse,
                    ghostLabel = stringResource(R.string.my_packs_empty_make_own),
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
                            stringResource(R.string.my_packs_section_installed, state.installed.size),
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
                            stringResource(R.string.my_packs_section_own, state.own.size),
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
        // Same 3-slot tab bar as Home (Prototype showNav on both tabs).
        Box(
            Modifier
                .fillMaxWidth()
                .background(Surface)
                .navigationBarsPadding()
        ) {
            LoveBottomNav(
                items = listOf(
                    LoveNavItem(LoveIcons.Home, stringResource(R.string.nav_home), selected = false, onClick = onHome),
                    LoveNavItem(LoveIcons.Sticker, stringResource(R.string.nav_my_packs), selected = true, onClick = {})
                ),
                onCreate = onCreate
            )
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
        downloadsLabel = row.metaLabel.asString(),
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
        text,
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
        Text(stringResource(R.string.my_packs_info_removal), style = InfoBodyText, color = Ink2)
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
    metaLabel = UiText.Raw(if (own) "yours" else "96.4K adds"),
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
            onBrowse = {}, onHome = {}, onCreate = {}, onOpenSaved = {}, onOpenSettings = {}
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
            onBrowse = {}, onHome = {}, onCreate = {}, onOpenSaved = {}, onOpenSettings = {}
        )
    }
}
