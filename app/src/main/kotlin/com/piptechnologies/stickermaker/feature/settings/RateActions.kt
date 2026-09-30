package com.piptechnologies.stickermaker.feature.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.piptechnologies.stickermaker.BuildConfig
import com.piptechnologies.stickermaker.feature.contact.deviceInfoBlock
import com.piptechnologies.stickermaker.feature.contact.mailtoUri

// Where the rating sheet sends people, shared by Settings › Rate us and the rating prompt.

// Subject line for the low-star feedback mail (mail needs one; not designed).
// It stays in English: the team reads it, like the device details below it.
private const val FEEDBACK_MAIL_SUBJECT = "Love Stickers · Feedback"

private val PLAY_MARKET_URI = "market://details?id=${BuildConfig.APPLICATION_ID}"
private val PLAY_LISTING_URL = "https://play.google.com/store/apps/details?id=${BuildConfig.APPLICATION_ID}"

/** Opens [url] in whichever app handles it; false when none does. */
internal fun openLink(context: Context, url: String): Boolean = try {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    true
} catch (_: ActivityNotFoundException) {
    false
}

/** Play listing of this app: the market: intent, then the https fallback. */
internal fun openPlayListing(context: Context): Boolean =
    openLink(context, PLAY_MARKET_URI) || openLink(context, PLAY_LISTING_URL)

/**
 * The rating sheet's low-star note, handed to the mail composer for the publisher's support
 * address with the disclosed device block. False when no app handles mailto:.
 */
internal fun sendFeedbackMail(context: Context, note: String): Boolean = try {
    context.startActivity(
        Intent(Intent.ACTION_SENDTO, mailtoUri(context, FEEDBACK_MAIL_SUBJECT, note.trim() + deviceInfoBlock()))
    )
    true
} catch (_: ActivityNotFoundException) {
    false
}
