package com.piptechnologies.stickermaker.feature.home

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.piptechnologies.stickermaker.R
import com.piptechnologies.stickermaker.core.design.Canvas
import com.piptechnologies.stickermaker.core.design.Hanken
import com.piptechnologies.stickermaker.core.design.Ink
import com.piptechnologies.stickermaker.core.design.LoveIcons
import com.piptechnologies.stickermaker.core.design.LoveStickersTheme
import com.piptechnologies.stickermaker.core.design.Muted
import com.piptechnologies.stickermaker.core.design.Rose
import com.piptechnologies.stickermaker.core.design.Subtle
import com.piptechnologies.stickermaker.core.design.Surface
import com.piptechnologies.stickermaker.core.design.components.AddVisualState
import com.piptechnologies.stickermaker.core.design.components.CategoryChip
import com.piptechnologies.stickermaker.core.design.components.ChipRow
import com.piptechnologies.stickermaker.core.design.components.ConfirmSheet
import com.piptechnologies.stickermaker.core.design.components.EmptyState
import com.piptechnologies.stickermaker.core.design.components.LoveBottomNav
import com.piptechnologies.stickermaker.core.design.components.LoveHue
import com.piptechnologies.stickermaker.core.design.components.LoveNavItem
import com.piptechnologies.stickermaker.core.design.components.PackCard
import com.piptechnologies.stickermaker.core.design.components.ToastHost
import com.piptechnologies.stickermaker.core.design.components.TopBarIconButton
import com.piptechnologies.stickermaker.core.design.components.showToast
import com.piptechnologies.stickermaker.core.ui.UiText
import com.piptechnologies.stickermaker.core.ui.asString
import com.piptechnologies.stickermaker.whatsapp.AddStickerPackFlow
import com.piptechnologies.stickermaker.whatsapp.WhitelistCheck

// Copy lives in res/values/strings.xml (home_*, offline_*, no_whatsapp_*).

private val TitleInk = Color(0xFF171A20)
private val CancelInk = Color(0xFF626873)

private val BrandText = TextStyle(
    fontFamily = Hanken,
    fontWeight = FontWeight.W800,
    fontSize = 19.sp,
    letterSpacing = (-0.02).em
)
private val FieldText = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.W400, fontSize = 15.sp)
private val CancelText = TextStyle(fontFamily = Hanken, fontWeight = FontWeight.W600, fontSize = 14.sp)

/**
 * Home (Prototype `is.home`): slim brand bar with Search and Settings, chip
 * row (Trending · ♥ Saved · Animated · your themes), browse cards with heart
 * and the Add pill, the offline full state, and the 3-slot bottom nav
 * (Home · raised Create · My Packs). Saved is reached from the My Packs
 * toolbar heart; on Home the ♥ Saved chip filters in place.
 */
@Composable
fun HomeScreen(
    onOpenPack: (packId: String) -> Unit,
    onCreate: () -> Unit,
    onMyPacks: () -> Unit,
    onSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // WhatsApp's own confirm arrives back as an activity result.
    val addResultLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.onWhatsAppResult(
            AddStickerPackFlow.parseResult(result.resultCode, result.data)
        )
    }

    LaunchedEffect(Unit) {
        viewModel.toasts.collect { snackbarHostState.showToast(it.message.asString(context), it.withCheck) }
    }

    // A finished download hands off to WhatsApp (repository stops at Sent).
    val pending = state.pendingWhatsAppAdd
    LaunchedEffect(pending) {
        if (pending == null) return@LaunchedEffect
        val intent =
            AddStickerPackFlow.createBestIntent(context, pending.packId, pending.packName)
        when {
            intent != null -> {
                viewModel.onWhatsAppLaunched(pending.packId)
                try {
                    addResultLauncher.launch(intent)
                } catch (notFound: ActivityNotFoundException) {
                    viewModel.onWhatsAppMissingAtLaunch(pending.packId)
                }
            }
            !AddStickerPackFlow.isWhatsAppInstalled(context) ->
                viewModel.onWhatsAppMissingAtLaunch(pending.packId)
            else -> viewModel.onAlreadyInWhatsApp(pending.packId)
        }
    }

    HomeContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onOpenPack = onOpenPack,
        onCreate = onCreate,
        onMyPacks = onMyPacks,
        onSettings = onSettings,
        onSelectChip = viewModel::onSelectChip,
        onOpenSearch = viewModel::onOpenSearch,
        onCloseSearch = viewModel::onCloseSearch,
        onQueryChange = viewModel::onQueryChange,
        onRetry = viewModel::onRetry,
        onToggleFavorite = viewModel::onToggleFavorite,
        onAdd = viewModel::onAddClicked
    )

    if (state.whatsAppMissingPackId != null) {
        ConfirmSheet(
            title = stringResource(R.string.no_whatsapp_title),
            body = stringResource(R.string.no_whatsapp_body),
            confirmLabel = stringResource(R.string.no_whatsapp_confirm),
            cancelLabel = stringResource(R.string.common_not_now),
            destructive = false,
            icon = LoveIcons.MessageCircle,
            onConfirm = {
                viewModel.onGetWhatsApp()
                openWhatsAppStorePage(context)
            },
            onDismiss = viewModel::onDismissWhatsAppMissing
        )
    }
}

/** Stateless Home layout, previewable without Hilt. */
@Composable
fun HomeContent(
    state: HomeUiState,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onOpenPack: (String) -> Unit = {},
    onCreate: () -> Unit = {},
    onMyPacks: () -> Unit = {},
    onSettings: () -> Unit = {},
    onSelectChip: (String) -> Unit = {},
    onOpenSearch: () -> Unit = {},
    onCloseSearch: () -> Unit = {},
    onQueryChange: (String) -> Unit = {},
    onRetry: () -> Unit = {},
    onToggleFavorite: (String) -> Unit = {},
    onAdd: (String) -> Unit = {}
) {
    Box(modifier = modifier.fillMaxSize().background(Canvas)) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f).statusBarsPadding()) {
                if (state.searchOpen) {
                    SearchHeader(
                        query = state.query,
                        onQueryChange = onQueryChange,
                        onCancel = onCloseSearch
                    )
                } else {
                    BrandBar(onOpenSearch = onOpenSearch, onSettings = onSettings)
                    ChipRow(Modifier.padding(top = 2.dp, bottom = 10.dp)) {
                        items(state.chips, key = { it.id }) { chip ->
                            CategoryChip(
                                label = chip.label.asString(),
                                selected = chip.id == state.activeChipId,
                                onClick = { onSelectChip(chip.id) },
                                leadingIcon = if (chip.showHeart) LoveIcons.HeartFilled else null
                            )
                        }
                    }
                }
                when {
                    state.loading -> Box(
                        Modifier.fillMaxSize().padding(bottom = 70.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = Rose,
                            strokeWidth = 2.dp
                        )
                    }
                    state.offline -> Box(
                        Modifier.fillMaxSize().padding(top = 24.dp, bottom = 70.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        EmptyState(
                            icon = LoveIcons.WifiOff,
                            large = true,
                            title = stringResource(R.string.offline_title),
                            body = stringResource(R.string.offline_body),
                            primaryLabel = stringResource(R.string.common_retry),
                            onPrimary = onRetry,
                            footnote = stringResource(R.string.offline_footnote)
                        )
                    }
                    else -> PackList(
                        state = state,
                        onOpenPack = onOpenPack,
                        onToggleFavorite = onToggleFavorite,
                        onAdd = onAdd
                    )
                }
            }
            Box(Modifier.fillMaxWidth().background(Surface).navigationBarsPadding()) {
                LoveBottomNav(
                    items = listOf(
                        LoveNavItem(LoveIcons.Home, stringResource(R.string.nav_home), selected = true, onClick = {}),
                        LoveNavItem(LoveIcons.Sticker, stringResource(R.string.nav_my_packs), selected = false, onClick = onMyPacks)
                    ),
                    onCreate = onCreate
                )
            }
        }
        ToastHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 68.dp)
        )
    }
}

/** 52dp top bar: 28dp rose mark + wordmark, then Search and Settings. */
@Composable
private fun BrandBar(onOpenSearch: () -> Unit, onSettings: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(start = 20.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(Rose),
                contentAlignment = Alignment.Center
            ) {
                Icon(LoveIcons.HeartFilled, null, Modifier.size(15.dp), tint = Color.White)
            }
            Text(
                stringResource(R.string.app_name),
                style = BrandText,
                color = TitleInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        TopBarIconButton(LoveIcons.Search, stringResource(R.string.home_search), onClick = onOpenSearch)
        TopBarIconButton(LoveIcons.Settings, stringResource(R.string.home_settings), onClick = onSettings)
    }
}

/** The search field that replaces the top bar and chips in place. */
@Composable
private fun SearchHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    onCancel: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, top = 8.dp, end = 12.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .height(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Surface)
                .border(1.dp, Rose, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(LoveIcons.Search, null, Modifier.size(17.dp), tint = Muted)
            Spacer(Modifier.width(9.dp))
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                textStyle = FieldText.copy(color = Ink),
                singleLine = true,
                cursorBrush = SolidColor(Rose),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            Text(stringResource(R.string.home_search_placeholder), style = FieldText, color = Muted)
                        }
                        innerTextField()
                    }
                }
            )
        }
        Box(
            modifier = Modifier
                .height(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable(role = Role.Button, onClickLabel = stringResource(R.string.common_cancel), onClick = onCancel)
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(stringResource(R.string.common_cancel), style = CancelText, color = CancelInk)
        }
    }
}

@Composable
private fun PackList(
    state: HomeUiState,
    onOpenPack: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onAdd: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, top = 2.dp, end = 20.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(state.packs, key = { it.id }) { pack ->
            PackCard(
                title = pack.name,
                stickerCount = pack.stickerCount,
                downloadsLabel = pack.downloadsLabel.asString(),
                animated = pack.animated,
                addState = pack.addState,
                addProgress = pack.addProgress,
                favorite = pack.favorite,
                onFavoriteToggle = { onToggleFavorite(pack.id) },
                onAdd = { onAdd(pack.id) },
                onClick = { onOpenPack(pack.id) },
                thumbnails = packThumbnails(pack)
            )
        }
        if (state.noResults) {
            item {
                EmptyState(
                    icon = LoveIcons.Search,
                    title = state.noResultsTitle.asString(),
                    body = stringResource(R.string.home_no_results_body),
                    modifier = Modifier.padding(top = 60.dp)
                )
            }
        }
    }
}

/** Six round previews: thumb URLs via Coil, or hue-tinted hearts offline. */
private fun packThumbnails(pack: HomePackUi): List<@Composable () -> Unit> =
    if (pack.thumbUrls.isNotEmpty()) {
        pack.thumbUrls.map { url -> { PackThumb(url) } }
    } else {
        List(minOf(pack.stickerCount, 6).coerceAtLeast(1)) { { HuePlaceholderThumb(pack.hue) } }
    }

@Composable
private fun PackThumb(url: String) {
    Box(
        modifier = Modifier.size(46.dp).background(Subtle),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = url,
            contentDescription = null,
            modifier = Modifier.size(42.dp)
        )
    }
}

@Composable
private fun HuePlaceholderThumb(hue: Int) {
    Box(
        modifier = Modifier.size(46.dp).background(LoveHue.tint(hue)),
        contentAlignment = Alignment.Center
    ) {
        Icon(LoveIcons.Heart, null, Modifier.size(22.dp), tint = LoveHue.deep(hue))
    }
}

/** "Get WhatsApp" opens its Play Store page (market://, then the web fallback). */
private fun openWhatsAppStorePage(context: Context) {
    val packageName = WhitelistCheck.CONSUMER_WHATSAPP_PACKAGE_NAME
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
        )
    } catch (notFound: ActivityNotFoundException) {
        try {
            context.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
                )
            )
        } catch (ignored: ActivityNotFoundException) {
            // No browser either; the toast already said what we tried.
        }
    }
}

// ------------------------------------------------------------- previews //

private fun previewPack(
    id: String,
    name: String,
    count: Int,
    adds: String,
    hue: Int,
    animated: Boolean = false,
    favorite: Boolean = false,
    addState: AddVisualState = AddVisualState.Idle,
    addProgress: Float = 0f
) = HomePackUi(
    id = id,
    name = name,
    stickerCount = count,
    downloadsLabel = UiText.Raw(adds),
    animated = animated,
    hue = hue,
    thumbUrls = emptyList(),
    favorite = favorite,
    addState = addState,
    addProgress = addProgress
)

private val previewChips = listOf(
    HomeChipUi(CHIP_TRENDING, UiText.Raw("Trending")),
    HomeChipUi(CHIP_SAVED, UiText.Raw("Saved · 2"), showHeart = true),
    HomeChipUi(CHIP_ANIMATED, UiText.Raw("Animated")),
    HomeChipUi("couples", UiText.Raw("Couples")),
    HomeChipUi("cute", UiText.Raw("Cute")),
    HomeChipUi("romantic", UiText.Raw("Romantic"))
)

private val previewPacks = listOf(
    previewPack("clingy-mango", "Clingy Mango", 18, "96.4K adds", 45, addState = AddVisualState.Added),
    previewPack("big-words", "Big Words", 18, "71.2K adds", 10, favorite = true),
    previewPack(
        "mango-moves", "Mango Moves", 12, "58.6K adds", 45,
        animated = true, addState = AddVisualState.Downloading, addProgress = 0.64f
    ),
    previewPack("sorry-love", "Sorry, My Love", 18, "52.3K adds", 330)
)

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun HomeTrendingPreview() {
    LoveStickersTheme {
        HomeContent(
            state = HomeUiState(
                loading = false,
                chips = previewChips,
                activeChipId = CHIP_TRENDING,
                packs = previewPacks
            )
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun HomeOfflinePreview() {
    LoveStickersTheme {
        HomeContent(
            state = HomeUiState(
                loading = false,
                offline = true,
                chips = previewChips,
                activeChipId = CHIP_TRENDING
            )
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun HomeSearchNoResultsPreview() {
    LoveStickersTheme {
        HomeContent(
            state = HomeUiState(
                loading = false,
                searchOpen = true,
                query = "dinosaur",
                noResults = true,
                noResultsTitle = UiText.Raw("No packs for “dinosaur”")
            )
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun HomeLoadingPreview() {
    LoveStickersTheme {
        HomeContent(state = HomeUiState(loading = true, chips = previewChips))
    }
}
