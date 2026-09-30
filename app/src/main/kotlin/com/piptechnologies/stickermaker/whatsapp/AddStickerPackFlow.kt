package com.piptechnologies.stickermaker.whatsapp

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.piptechnologies.stickermaker.core.telemetry.CrashReporting
import com.piptechnologies.stickermaker.core.telemetry.redacted
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The "add to WhatsApp" flow, ported from the launch/result logic of the sample's
 * `AddStickerPackActivity` (revision 06144a1). Screens launch the [Intent] through an
 * ActivityResult contract and feed the result to [parseResult].
 */
object AddStickerPackFlow {

    const val ACTION_ENABLE_STICKER_PACK = "com.whatsapp.intent.action.ENABLE_STICKER_PACK"
    const val EXTRA_STICKER_PACK_ID = "sticker_pack_id"
    const val EXTRA_STICKER_PACK_AUTHORITY = "sticker_pack_authority"
    const val EXTRA_STICKER_PACK_NAME = "sticker_pack_name"

    /** Set on a RESULT_CANCELED data intent when WhatsApp rejected the pack. */
    const val EXTRA_VALIDATION_ERROR = "validation_error"

    /** The sample's startActivityForResult request code, for callers still using one. */
    const val ADD_PACK_REQUEST_CODE = 200

    /** The plain add intent; wrap in a chooser or [createIntentForPackage] to target one app. */
    fun createIntentToAddStickerPack(identifier: String, stickerPackName: String): Intent =
        Intent().apply {
            action = ACTION_ENABLE_STICKER_PACK
            putExtra(EXTRA_STICKER_PACK_ID, identifier)
            putExtra(EXTRA_STICKER_PACK_AUTHORITY, StickerContentProvider.CONTENT_PROVIDER_AUTHORITY)
            putExtra(EXTRA_STICKER_PACK_NAME, stickerPackName)
        }

    /** Add intent pinned to one WhatsApp package (consumer or business). */
    fun createIntentForPackage(
        identifier: String,
        stickerPackName: String,
        whatsAppPackageName: String,
    ): Intent = createIntentToAddStickerPack(identifier, stickerPackName)
        .setPackage(whatsAppPackageName)

    /**
     * The add target the sample picks in `addStickerPackToWhatsApp`: a specific app when only
     * one still misses the pack, a chooser when both do, null when there is nothing to launch
     * (no WhatsApp installed, or the pack is already everywhere). Blocks on WhatsApp's providers
     * and throws what they throw: screens go through [resolveAddTarget].
     */
    fun createBestIntent(context: Context, identifier: String, stickerPackName: String): Intent? {
        val packageManager = context.packageManager
        if (!WhitelistCheck.isWhatsAppConsumerAppInstalled(packageManager) &&
            !WhitelistCheck.isWhatsAppSmbAppInstalled(packageManager)
        ) {
            return null
        }
        val inConsumer = WhitelistCheck.isStickerPackWhitelistedInWhatsAppConsumer(context, identifier)
        val inSmb = WhitelistCheck.isStickerPackWhitelistedInWhatsAppSmb(context, identifier)
        return when {
            !inConsumer && !inSmb -> createIntentToAddStickerPack(identifier, stickerPackName)
            !inConsumer -> createIntentForPackage(
                identifier, stickerPackName, WhitelistCheck.CONSUMER_WHATSAPP_PACKAGE_NAME
            )
            !inSmb -> createIntentForPackage(
                identifier, stickerPackName, WhitelistCheck.SMB_WHATSAPP_PACKAGE_NAME
            )
            else -> null
        }
    }

    /** True when WhatsApp consumer or WhatsApp Business is installed (see manifest <queries>). */
    fun isWhatsAppInstalled(context: Context): Boolean {
        val packageManager = context.packageManager
        return WhitelistCheck.isWhatsAppConsumerAppInstalled(packageManager) ||
            WhitelistCheck.isWhatsAppSmbAppInstalled(packageManager)
    }

    /** Where an add goes once WhatsApp was asked (see [resolveAddTarget]). */
    sealed interface AddTarget {
        /** Launch WhatsApp's own confirm with [intent]. */
        data class Launch(val intent: Intent) : AddTarget

        /** Every installed WhatsApp already has the pack: nothing to launch. */
        data object AlreadyAdded : AddTarget

        /** No WhatsApp is installed (any more). */
        data object NoWhatsApp : AddTarget
    }

    /**
     * [createBestIntent] for the add flows: its two queries into WhatsApp's processes (they can
     * start WhatsApp) run on [dispatcher]. A failing query is recorded under [where], messages
     * removed, and answered with the plain add intent so WhatsApp itself decides; with no WhatsApp
     * at all that launch fails on the screen, which shows its install sheet.
     */
    suspend fun resolveAddTarget(
        context: Context,
        identifier: String,
        stickerPackName: String,
        where: String,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
    ): AddTarget = withContext(dispatcher) {
        try {
            val best = createBestIntent(context, identifier, stickerPackName)
            when {
                best != null -> AddTarget.Launch(best)
                isWhatsAppInstalled(context) -> AddTarget.AlreadyAdded
                else -> AddTarget.NoWhatsApp
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            CrashReporting.record(e.redacted(), where)
            AddTarget.Launch(createIntentToAddStickerPack(identifier, stickerPackName))
        }
    }

    /** Outcome of the ENABLE_STICKER_PACK activity result. */
    sealed interface AddResult {
        /** WhatsApp accepted the pack (result code was not RESULT_CANCELED). */
        data object Added : AddResult

        /**
         * The user backed out, or WhatsApp rejected the pack; [validationError] carries
         * WhatsApp's reason when it rejected (developer-facing, like the sample shows in
         * debug builds only).
         */
        data class Cancelled(val validationError: String?) : AddResult
    }

    fun parseResult(resultCode: Int, data: Intent?): AddResult =
        if (resultCode == Activity.RESULT_CANCELED) {
            AddResult.Cancelled(data?.getStringExtra(EXTRA_VALIDATION_ERROR))
        } else {
            AddResult.Added
        }
}
