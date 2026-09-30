package com.piptechnologies.stickermaker.core.telemetry

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Guarded facades over Firebase Analytics and Crashlytics, the same contract as the other PIP
 * apps: every Firebase entry point throws when no FirebaseApp is initialized (Robolectric, or any
 * build carrying the placeholder google-services.json), and telemetry must never decide whether
 * real work runs, so every call is wrapped and failures are silent.
 *
 * Privacy rule, non-negotiable: events carry only coarse counts and enum-like strings. A catalog
 * pack id is one of a fixed published list; a pack the user made is only ever "own", never its
 * name or id, and nothing the user typed or picked (photos, search text, messages) is sent.
 */
object AppAnalytics {

    @Volatile
    private var analytics: FirebaseAnalytics? = null

    /** Called once from LoveStickersApp.onCreate; without it (unit tests) every event is dropped. */
    fun init(context: Context) {
        analytics = runCatching { FirebaseAnalytics.getInstance(context.applicationContext) }.getOrNull()
    }

    /** A navigation destination became current. [route] is the route pattern; its arguments are dropped. */
    fun logScreen(route: String) = log(FirebaseAnalytics.Event.SCREEN_VIEW) {
        putString(FirebaseAnalytics.Param.SCREEN_NAME, route.substringBefore('/'))
        putString(FirebaseAnalytics.Param.SCREEN_CLASS, "MainActivity")
    }

    /** First-run setup ended on Home: the name pack was "added" to WhatsApp, "saved" to My Packs, or "skipped". */
    fun logOnboardingComplete(namePack: String) = log("onboarding_complete") { putString("name_pack", namePack) }

    /** A name pack was lettered. Enum-like values only: never the names. */
    fun logNamePackBuilt(relation: String, tone: String, character: String, hasYourName: Boolean, language: String) =
        log("name_pack_built") {
            putString("relation", relation)
            putString("tone", tone)
            putString("character", character)
            putLong("has_your_name", if (hasYourName) 1L else 0L)
            putString("language", language)
        }

    /** The download that starts adding catalog pack [packId] began. */
    fun logPackAddStarted(packId: String) = log("pack_add_started") { putPack(packId) }

    /** Catalog pack [packId] failed to download (the failure itself goes to [CrashReporting]). */
    fun logPackDownloadFailed(packId: String) = log("pack_download_failed") { putPack(packId) }

    /** WhatsApp confirmed pack [packId]. */
    fun logPackAdded(packId: String) = log("pack_added") { putPack(packId) }

    /** WhatsApp's sheet for [packId] closed without adding it; [rejected] when WhatsApp named a validation error. */
    fun logPackAddCancelled(packId: String, rejected: Boolean) = log("pack_add_cancelled") {
        putPack(packId)
        putLong("rejected", if (rejected) 1L else 0L)
    }

    /** The Create flow exported a pack of [stickers] stickers, headed to WhatsApp or kept in My Packs. */
    fun logPackCreated(stickers: Int, animated: Boolean, toWhatsApp: Boolean) = log("pack_created") {
        putLong("stickers", stickers.toLong())
        putLong("animated", if (animated) 1L else 0L)
        putString("destination", if (toWhatsApp) "whatsapp" else "my_packs")
    }

    /** The app's Play Store link went to the share sheet. */
    fun logAppShared() = log(FirebaseAnalytics.Event.SHARE) {
        putString(FirebaseAnalytics.Param.CONTENT_TYPE, "app")
        putString(FirebaseAnalytics.Param.METHOD, "share_sheet")
    }

    /**
     * A step of the rating sheet. [outcome]: "shown" (prompt only), "store" (five stars, Play
     * listing opened), "feedback" (note mailed), "later" or "dismissed" (prompt only).
     * [source]: "prompt" (after a pack was added) or "settings" (Rate us).
     */
    fun logRating(outcome: String, source: String) = log("app_rating") {
        putString("outcome", outcome)
        putString("source", source)
    }

    /** The in-app language became [tag]: a tag from AppLanguages, or AppLanguages.SYSTEM ("system"). */
    fun logLanguageChanged(tag: String) = log("language_changed") { putString("language", tag) }

    private inline fun log(event: String, params: Bundle.() -> Unit = {}) {
        runCatching { analytics?.logEvent(event, Bundle().apply(params)) }
    }

    /** Catalog ids as they are; every pack the user made reports as "own". */
    private fun Bundle.putPack(packId: String) {
        putString("pack_id", if (packId.startsWith(OWN_PACK_PREFIX)) "own" else packId)
    }

    /** Own packs are stored as "own-<random>" (MyPacksRepository.saveOwnPack). */
    private const val OWN_PACK_PREFIX = "own-"
}

/** Crashlytics non-fatals for the places the app deliberately swallows a throwable. */
object CrashReporting {

    fun record(throwable: Throwable, where: String) {
        runCatching {
            FirebaseCrashlytics.getInstance().apply {
                setCustomKey("where", where)
                recordException(throwable)
            }
        }
    }

    fun setKey(key: String, value: String) {
        runCatching { FirebaseCrashlytics.getInstance().setCustomKey(key, value) }
    }

    fun log(message: String) {
        runCatching { FirebaseCrashlytics.getInstance().log(message) }
    }
}
