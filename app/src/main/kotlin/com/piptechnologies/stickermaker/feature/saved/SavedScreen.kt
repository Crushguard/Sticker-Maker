package com.piptechnologies.stickermaker.feature.saved

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
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
import com.piptechnologies.stickermaker.core.design.LoveIcons
import com.piptechnologies.stickermaker.core.design.LoveStickersTheme
import com.piptechnologies.stickermaker.core.design.Mono
import com.piptechnologies.stickermaker.core.design.Muted
import com.piptechnologies.stickermaker.core.design.Subtle
import com.piptechnologies.stickermaker.core.design.components.AddVisualState
import com.piptechnologies.stickermaker.core.design.components.ConfirmSheet
import com.piptechnologies.stickermaker.core.design.components.EmptyState
import com.piptechnologies.stickermaker.core.design.components.LoveTopBar
import com.piptechnologies.stickermaker.core.design.components.PackCard
import com.piptechnologies.stickermaker.core.design.components.ToastHost
import com.piptechnologies.stickermaker.core.design.components.showToast
import com.piptechnologies.stickermaker.core.model.AddState
import com.piptechnologies.stickermaker.core.ui.UiText
import com.piptechnologies.stickermaker.core.ui.asString
import com.piptechnologies.stickermaker.whatsapp.AddStickerPackFlow
import kotlinx.coroutines.launch

// Mono count label in the header ("3 packs").
private val CountText = TextStyle(fontFamily = Mono, fontWeight = FontWeight.W500, fontSize = 12.sp)

private const val WHATSAPP_PACKAGE = "com.whatsapp"

/**
 * My Packs › Saved (design/Screens.dc.html section 07): back header with a
 * mono pack count, then the same browse cards as Home — heart to un-save,
 * pill with the full add state machine. Empty state points back to browsing.
 */
@Composable
fun SavedScreen(
    onBack: () -> Unit,
    onOpenPack: (String) -> Unit,
    onBrowse: () -> Unit = onBack,
    viewModel: SavedViewModel = hiltViewModel()
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
                is SavedEvent.LaunchAddIntent -> try {
                    addLauncher.launch(event.intent)
                } catch (notFound: ActivityNotFoundException) {
                    viewModel.onAddLaunchFailed()
                }
                is SavedEvent.Toast -> scope.launch {
                    toastHost.showToast(event.message.asString(context), event.withCheck)
                }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        SavedContent(
            state = state,
            onBack = onBack,
            onOpenPack = onOpenPack,
            onToggleFavorite = viewModel::onToggleFavorite,
            onAddClicked = viewModel::onAddClicked,
            onBrowse = onBrowse
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

/** Pure layout, driven by [SavedUiState]; also hosts the previews. */
@Composable
internal fun SavedContent(
    state: SavedUiState,
    onBack: () -> Unit,
    onOpenPack: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onAddClicked: (String) -> Unit,
    onBrowse: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .fillMaxSize()
            .background(Canvas)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        LoveTopBar(
            title = stringResource(R.string.saved_title),
            onBack = onBack,
            height = 52.dp,
            actions = {
                if (state.rows.isNotEmpty()) {
                    Text(
                        pluralStringResource(R.plurals.saved_pack_count, state.rows.size, state.rows.size),
                        style = CountText,
                        color = Muted,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }
        )
        when {
            state.loading -> Spacer(Modifier.weight(1f))
            state.empty -> Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                EmptyState(
                    icon = LoveIcons.Heart,
                    title = stringResource(R.string.saved_empty_title),
                    body = stringResource(R.string.saved_empty_body),
                    primaryLabel = stringResource(R.string.common_browse_packs),
                    onPrimary = onBrowse,
                    modifier = Modifier.padding(bottom = 70.dp)
                )
            }
            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 20.dp, top = 2.dp, end = 20.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.rows, key = { it.id }) { row ->
                    SavedCard(
                        row = row,
                        onOpenPack = onOpenPack,
                        onToggleFavorite = onToggleFavorite,
                        onAddClicked = onAddClicked
                    )
                }
            }
        }
    }
}

@Composable
private fun SavedCard(
    row: SavedRow,
    onOpenPack: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onAddClicked: (String) -> Unit
) {
    val thumbnails: List<@Composable () -> Unit> = row.thumbModels.map { model ->
        { SavedThumb(model) }
    }
    PackCard(
        title = row.name,
        stickerCount = row.stickerCount,
        downloadsLabel = row.metaLabel.asString(),
        animated = row.animated,
        addState = row.addState.toVisual(),
        addProgress = (row.addState as? AddState.Downloading)?.progress ?: 0f,
        favorite = true,
        onFavoriteToggle = { onToggleFavorite(row.id) },
        onAdd = { onAddClicked(row.id) },
        onClick = { onOpenPack(row.id) },
        thumbnails = thumbnails
    )
}

/** 46dp circle preview from a thumb URL or a local file. */
@Composable
private fun SavedThumb(model: Any) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Subtle),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = model,
            contentDescription = null,
            modifier = Modifier.size(42.dp),
            contentScale = ContentScale.Fit
        )
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

@Preview(showBackground = true, backgroundColor = 0xFFFAFBFC, widthDp = 390, heightDp = 780)
@Composable
private fun SavedPreview() {
    LoveStickersTheme {
        SavedContent(
            state = SavedUiState(
                loading = false,
                rows = listOf(
                    SavedRow(
                        id = "big-words", name = "Big Words", animated = false,
                        stickerCount = 18, metaLabel = UiText.Raw("71.2K adds"), own = false,
                        addState = AddState.Idle, thumbModels = emptyList()
                    ),
                    SavedRow(
                        id = "flirty-shy", name = "Flirty & Shy", animated = false,
                        stickerCount = 11, metaLabel = UiText.Raw("64.8K adds"), own = false,
                        addState = AddState.Downloading(0.42f), thumbModels = emptyList()
                    )
                )
            ),
            onBack = {}, onOpenPack = {}, onToggleFavorite = {}, onAddClicked = {}, onBrowse = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFAFBFC, widthDp = 390, heightDp = 780)
@Composable
private fun SavedEmptyPreview() {
    LoveStickersTheme {
        SavedContent(
            state = SavedUiState(loading = false),
            onBack = {}, onOpenPack = {}, onToggleFavorite = {}, onAddClicked = {}, onBrowse = {}
        )
    }
}
