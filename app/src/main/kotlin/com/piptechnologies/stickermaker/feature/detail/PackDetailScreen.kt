package com.piptechnologies.stickermaker.feature.detail

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.piptechnologies.stickermaker.R
import com.piptechnologies.stickermaker.core.design.Canvas
import com.piptechnologies.stickermaker.core.design.Ink2
import com.piptechnologies.stickermaker.core.design.LoveIcons
import com.piptechnologies.stickermaker.core.design.LoveStickersTheme
import com.piptechnologies.stickermaker.core.design.Mono
import com.piptechnologies.stickermaker.core.design.Rose
import com.piptechnologies.stickermaker.core.design.Surface
import com.piptechnologies.stickermaker.core.design.components.AddBar
import com.piptechnologies.stickermaker.core.design.components.AddVisualState
import com.piptechnologies.stickermaker.core.design.components.AnimatedBadge
import com.piptechnologies.stickermaker.core.design.components.ConfirmSheet
import com.piptechnologies.stickermaker.core.design.components.EmptyState
import com.piptechnologies.stickermaker.core.design.components.LoveTopBar
import com.piptechnologies.stickermaker.core.design.components.StickerTile
import com.piptechnologies.stickermaker.core.design.components.ToastHost
import com.piptechnologies.stickermaker.core.design.components.TopBarIconButton
import com.piptechnologies.stickermaker.core.design.components.showToast
import com.piptechnologies.stickermaker.core.model.AddState
import com.piptechnologies.stickermaker.core.ui.UiText
import com.piptechnologies.stickermaker.core.ui.asString
import com.piptechnologies.stickermaker.whatsapp.AddStickerPackFlow
import kotlinx.coroutines.launch

// Detail meta line: mono 12 in Ink2 (the card meta is 11.5 in Muted).
private val DetailMetaText = TextStyle(fontFamily = Mono, fontWeight = FontWeight.W400, fontSize = 12.sp)

// Frame colours from the prototype that are not core palette tokens.
private val DetailTileBg = Color(0xFFF6F7F9)
private val FooterLine = Color(0xFFEEF0F4)

private const val WHATSAPP_PACKAGE = "com.whatsapp"

/**
 * Stickers · pack detail: 52dp back header with heart + share, one meta line,
 * every sticker in a 3-column grid, and the Add bar pinned to the footer with
 * its per-state hint (design/Screens.dc.html section 04-05).
 */
@Composable
fun PackDetailScreen(
    packId: String,
    onBack: () -> Unit,
    viewModel: PackDetailViewModel = hiltViewModel()
) {
    LaunchedEffect(packId) { viewModel.start(packId) }

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
                is PackDetailEvent.LaunchAddIntent -> try {
                    addLauncher.launch(event.intent)
                } catch (notFound: ActivityNotFoundException) {
                    viewModel.onAddLaunchFailed()
                }
                is PackDetailEvent.Toast -> scope.launch {
                    toastHost.showToast(event.message.asString(context), event.withCheck)
                }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        PackDetailContent(
            state = state,
            onBack = onBack,
            onToggleFavorite = viewModel::onToggleFavorite,
            onShare = { sharePlayStoreLink(context) },
            onAddClicked = viewModel::onAddClicked,
            onRetryLoad = viewModel::retryLoad
        )
        ToastHost(
            hostState = toastHost,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        )
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

/** Pure layout, driven by [PackDetailUiState]; also hosts the previews. */
@Composable
internal fun PackDetailContent(
    state: PackDetailUiState,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    onShare: () -> Unit,
    onAddClicked: () -> Unit,
    onRetryLoad: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .fillMaxSize()
            .background(Canvas)
            .statusBarsPadding()
    ) {
        LoveTopBar(
            title = state.title,
            onBack = onBack,
            height = 52.dp,
            actions = {
                TopBarIconButton(
                    icon = if (state.favorite) LoveIcons.HeartFilled else LoveIcons.Heart,
                    contentDescription = stringResource(if (state.favorite) R.string.pack_unsave else R.string.pack_save),
                    tint = if (state.favorite) Rose else Ink2,
                    onClick = onToggleFavorite
                )
                TopBarIconButton(
                    icon = LoveIcons.Send,
                    contentDescription = stringResource(R.string.pack_share),
                    tint = Ink2,
                    onClick = onShare
                )
            }
        )
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when {
                state.loading -> CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(28.dp),
                    color = Rose,
                    strokeWidth = 3.dp
                )
                state.unavailable -> EmptyState(
                    icon = LoveIcons.WifiOff,
                    title = stringResource(R.string.offline_title),
                    body = stringResource(R.string.offline_body),
                    large = true,
                    primaryLabel = stringResource(R.string.common_retry),
                    onPrimary = onRetryLoad,
                    footnote = stringResource(R.string.offline_footnote),
                    modifier = Modifier.align(Alignment.Center)
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, top = 2.dp, end = 20.dp, bottom = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item(key = "meta", span = { GridItemSpan(maxLineSpan) }) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(bottom = 4.dp)
                        ) {
                            Text(text = state.metaLine.asString(), style = DetailMetaText, color = Ink2)
                            if (state.animated) AnimatedBadge()
                        }
                    }
                    items(state.stickers, key = { it.key }) { sticker ->
                        StickerTile(
                            background = DetailTileBg,
                            radius = 16.dp,
                            contentPadding = 6.dp,
                            bordered = false
                        ) {
                            AsyncImage(
                                model = sticker.model,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                }
            }
        }
        if (!state.loading && !state.unavailable) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Surface)
                    .navigationBarsPadding()
            ) {
                HorizontalDivider(color = FooterLine, thickness = 1.dp)
                AddBar(
                    state = state.addState.toVisual(),
                    progress = (state.addState as? AddState.Downloading)?.progress ?: 0f,
                    onClick = onAddClicked,
                    modifier = Modifier.padding(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 20.dp)
                )
            }
        }
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

/** Share sheet with the app's Play Store link (the header's paper plane). */
private fun sharePlayStoreLink(context: Context) {
    val link = "https://play.google.com/store/apps/details?id=${context.packageName}"
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, link)
    runCatching { context.startActivity(Intent.createChooser(send, null)) }
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

private fun previewState(addState: AddState) = PackDetailUiState(
    loading = false,
    packId = "clingy-mango",
    title = "Clingy Mango",
    metaLine = UiText.Raw("18 stickers · 96.4K adds"),
    animated = false,
    stickers = List(9) { DetailSticker("p$it", "") },
    favorite = true,
    addState = addState
)

@Preview(showBackground = true, backgroundColor = 0xFFFAFBFC, widthDp = 390, heightDp = 780)
@Composable
private fun PackDetailIdlePreview() {
    LoveStickersTheme {
        PackDetailContent(
            state = previewState(AddState.Idle),
            onBack = {}, onToggleFavorite = {}, onShare = {}, onAddClicked = {}, onRetryLoad = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFAFBFC, widthDp = 390, heightDp = 780)
@Composable
private fun PackDetailDownloadingPreview() {
    LoveStickersTheme {
        PackDetailContent(
            state = previewState(AddState.Downloading(0.64f)),
            onBack = {}, onToggleFavorite = {}, onShare = {}, onAddClicked = {}, onRetryLoad = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFAFBFC, widthDp = 390, heightDp = 780)
@Composable
private fun PackDetailFailedPreview() {
    LoveStickersTheme {
        PackDetailContent(
            state = previewState(AddState.Failed(null)),
            onBack = {}, onToggleFavorite = {}, onShare = {}, onAddClicked = {}, onRetryLoad = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFAFBFC, widthDp = 390, heightDp = 780)
@Composable
private fun PackDetailOfflinePreview() {
    LoveStickersTheme {
        PackDetailContent(
            state = PackDetailUiState(loading = false, unavailable = true, title = "Clingy Mango"),
            onBack = {}, onToggleFavorite = {}, onShare = {}, onAddClicked = {}, onRetryLoad = {}
        )
    }
}
