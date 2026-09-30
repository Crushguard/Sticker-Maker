package com.piptechnologies.stickermaker.feature.namepack

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.piptechnologies.stickermaker.R
import com.piptechnologies.stickermaker.core.design.Canvas
import com.piptechnologies.stickermaker.core.design.LoveIcons
import com.piptechnologies.stickermaker.core.design.components.AddVisualState
import com.piptechnologies.stickermaker.core.design.components.ConfirmSheet
import com.piptechnologies.stickermaker.core.design.components.ToastHost
import com.piptechnologies.stickermaker.core.design.components.showToast
import com.piptechnologies.stickermaker.core.ui.asString
import com.piptechnologies.stickermaker.feature.language.AppLanguages
import com.piptechnologies.stickermaker.feature.language.LanguageSheet
import com.piptechnologies.stickermaker.feature.language.effectiveTag
import com.piptechnologies.stickermaker.whatsapp.AddStickerPackFlow
import com.piptechnologies.stickermaker.whatsapp.WhitelistCheck
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * The Custom Stickers route: your name → their name → building → reveal, one ViewModel,
 * system back walking the steps. Ends with [onFinished] (Home) or [onLeave] (back to the intro).
 */
@Composable
fun NamePackScreen(
    onFinished: () -> Unit,
    onLeave: () -> Unit,
    viewModel: NamePackViewModel = hiltViewModel()
) {
    // Main.immediate: typed text comes back from the ViewModel in the same frame, so fast typing
    // and IME composition never see a stale value (no dropped letters, no cursor jumps).
    val state by viewModel.uiState.collectAsState(context = Dispatchers.Main.immediate)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val toaster = remember { SnackbarHostState() }
    // A language change recreates the activity, so this is read fresh then.
    val languageTag = remember { AppLanguages.effectiveTag() }
    var languagesOpen by rememberSaveable { mutableStateOf(false) }
    var noWhatsApp by rememberSaveable { mutableStateOf(false) }
    val latestFinished by rememberUpdatedState(onFinished)
    val latestLeave by rememberUpdatedState(onLeave)

    LaunchedEffect(languageTag) { viewModel.onLanguage(languageTag) }

    val keyboard = LocalSoftwareKeyboardController.current
    // Leaving the name steps: drop the keyboard now, not when the field leaves composition.
    LaunchedEffect(state.flow.step) {
        if (state.flow.step == NameStep.BUILDING || state.flow.step == NameStep.REVEAL) keyboard?.hide()
    }

    val addLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val parsed = AddStickerPackFlow.parseResult(result.resultCode, result.data)
        viewModel.onWhatsAppResult(
            added = parsed is AddStickerPackFlow.AddResult.Added,
            rejected = (parsed as? AddStickerPackFlow.AddResult.Cancelled)?.validationError != null
        )
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                // Nothing may suspend between receiving the event and launching, or a recreation would lose it.
                is NamePackEvent.LaunchAdd -> try {
                    addLauncher.launch(event.intent)
                } catch (e: ActivityNotFoundException) {
                    viewModel.onWhatsAppResult(added = false)
                    noWhatsApp = true
                }
                NamePackEvent.ShowNoWhatsApp -> noWhatsApp = true
                is NamePackEvent.Toast -> scope.launch { toaster.showToast(event.message.asString(context)) }
                NamePackEvent.Finished -> latestFinished()
                NamePackEvent.Leave -> latestLeave()
            }
        }
    }

    // While WhatsApp's own sheet is on its way, back would lose its answer: the handler stays on
    // (so the route is not popped) but does nothing.
    BackHandler { if (state.addState != AddVisualState.Sent) viewModel.onBack() }

    Box(Modifier.fillMaxSize().background(Canvas)) {
        // Keyed by the step alone: state updates (typing, previews, progress ticks) are not new
        // targets, so a fade is never cut short and no old state is kept. The reveal cuts in over
        // Building and fades in (§e, §f).
        AnimatedContent(
            targetState = state.flow.step,
            transitionSpec = {
                if (targetState == NameStep.REVEAL) fadeIn(tween(300)) togetherWith fadeOut(snap())
                else fadeIn(tween(300)) togetherWith fadeOut(tween(300))
            },
            label = "namePackStep"
        ) { step ->
            // A leaving step keeps the state it last showed; the current one follows the live state.
            val shown = rememberHeld(state, live = step == state.flow.step)
            // Taps during a step's fade were aimed at the step before, whose controls sit at the
            // same spots (Skip, the button): a double tap must not reach the arriving step.
            Box(Modifier.fillMaxSize().blockTaps(transition.currentState != transition.targetState)) {
                when (step) {
                    NameStep.YOU -> NameYouStep(
                        state = shown,
                        onLanguage = { languagesOpen = true },
                        onSkip = viewModel::onSkipYou,
                        onType = viewModel::onYouChange,
                        onCharacter = viewModel::onCharacter,
                        onContinue = viewModel::onContinue
                    )
                    NameStep.LOVE -> NameLoveStep(
                        state = shown,
                        onLanguage = { languagesOpen = true },
                        onSkip = viewModel::onSkipLove,
                        onType = viewModel::onLoveChange,
                        onRelation = viewModel::onRelation,
                        onMake = viewModel::onMake
                    )
                    NameStep.BUILDING -> BuildingStep(shown)
                    NameStep.REVEAL -> RevealStep(
                        state = shown,
                        onTone = viewModel::onTone,
                        onCharacter = viewModel::onCharacter,
                        onAdd = viewModel::onAdd,
                        onNotNow = viewModel::onNotNow
                    )
                }
            }
        }
        // Above the step's bottom controls: the reveal's footer, or the name steps' button.
        val toastLift = if (state.flow.step == NameStep.REVEAL) 140.dp else 88.dp
        ToastHost(toaster, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = toastLift))
    }

    if (languagesOpen) LanguageSheet(onDismiss = { languagesOpen = false })

    if (noWhatsApp) {
        val opening = stringResource(R.string.toast_opening_play_store)
        ConfirmSheet(
            title = stringResource(R.string.no_whatsapp_title),
            body = stringResource(R.string.no_whatsapp_body),
            confirmLabel = stringResource(R.string.no_whatsapp_confirm),
            cancelLabel = stringResource(R.string.common_not_now),
            destructive = false,
            icon = LoveIcons.MessageCircle,
            onConfirm = {
                noWhatsApp = false
                scope.launch { toaster.showToast(opening) }
                openWhatsAppStorePage(context)
            },
            onDismiss = { noWhatsApp = false }
        )
    }
}

/** [value] while [live]; once not, the last live value (a step fading out keeps what it showed). */
@Composable
private fun <T> rememberHeld(value: T, live: Boolean): T {
    val held = remember { Held(value) }
    if (live) held.value = value
    return held.value
}

/** A plain holder: writing it during composition must not trigger recomposition. */
private class Held<T>(var value: T)

/** "Get WhatsApp" opens its Play Store page (market://, then the web fallback). */
private fun openWhatsAppStorePage(context: Context) {
    val packageName = WhitelistCheck.CONSUMER_WHATSAPP_PACKAGE_NAME
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")))
    } catch (notFound: ActivityNotFoundException) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")))
        } catch (ignored: ActivityNotFoundException) {
            // No browser either; the toast already said what we tried.
        }
    }
}
