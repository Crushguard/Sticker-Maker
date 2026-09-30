package com.piptechnologies.stickermaker.feature.rating

import android.content.Context
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.piptechnologies.stickermaker.R
import com.piptechnologies.stickermaker.core.telemetry.AppAnalytics
import com.piptechnologies.stickermaker.feature.settings.RateSheet
import com.piptechnologies.stickermaker.feature.settings.openPlayListing
import com.piptechnologies.stickermaker.feature.settings.sendFeedbackMail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Lets the screen's "Added to WhatsApp" toast land before the sheet rises. */
private const val PAUSE_DELAY_MS = 1_500L

/**
 * Shows the rating sheet at the natural pause after a pack WhatsApp confirmed, when
 * [RatingPromptController] says the user has earned the question. It lives next to the NavHost so
 * it follows the user to whichever screen the add finished on.
 *
 * @param atNaturalPause true on a screen where a finished add leaves the user browsing (Home, a
 * pack page, Saved, My Packs); false everywhere else, where an armed prompt waits.
 */
@Composable
fun RatingPromptHost(atNaturalPause: Boolean) {
    val context = LocalContext.current
    val armed by RatingPromptController.armed.collectAsState()
    var visible by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(armed, atNaturalPause) {
        if (!armed || !atNaturalPause) return@LaunchedEffect
        delay(PAUSE_DELAY_MS)
        val appContext = context.applicationContext
        if (withContext(Dispatchers.IO) { RatingPromptController.claimPause(appContext) }) {
            AppAnalytics.logRating(outcome = "shown", source = SOURCE)
            visible = true
        }
        // Last: disarming changes a key of this effect, which cancels it.
        RatingPromptController.disarm()
    }

    if (visible) {
        // The sheet reports every close through onDismiss, including the one right after a rating
        // or a note: only a close without an answer counts as a dismissal.
        var answered by remember { mutableStateOf(false) }
        RateSheet(
            onDismiss = {
                if (!answered) {
                    AppAnalytics.logRating(outcome = "dismissed", source = SOURCE)
                    RatingPromptController.onDismissed(context)
                }
                visible = false
            },
            onOpenStore = {
                if (openPlayListing(context)) {
                    answered = true
                    AppAnalytics.logRating(outcome = "store", source = SOURCE)
                    RatingPromptController.onRated(context)
                } else {
                    toast(context, R.string.toast_link_failed)
                }
            },
            onSendFeedback = { text ->
                val sent = sendFeedbackMail(context, text)
                if (sent) {
                    answered = true
                    AppAnalytics.logRating(outcome = "feedback", source = SOURCE)
                    RatingPromptController.onRated(context)
                } else {
                    toast(context, R.string.toast_no_email_app)
                }
                sent
            },
            onLater = {
                answered = true
                AppAnalytics.logRating(outcome = "later", source = SOURCE)
                RatingPromptController.onLater(context)
                visible = false
            }
        )
    }
}

private const val SOURCE = "prompt"

/** The screens' own toast hosts are out of reach here; the system toast carries the rare failure. */
private fun toast(context: Context, @StringRes message: Int) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}
