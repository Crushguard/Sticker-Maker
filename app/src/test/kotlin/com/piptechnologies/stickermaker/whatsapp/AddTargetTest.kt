package com.piptechnologies.stickermaker.whatsapp

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.Executors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * [AddStickerPackFlow.resolveAddTarget] over the real WhitelistCheck queries: only WhatsApp is a
 * stand-in (Robolectric's package manager plus a provider at WhatsApp's whitelist authority).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AddTargetTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun noWhatsAppMeansTheInstallSheet() = runBlocking {
        assertEquals(AddStickerPackFlow.AddTarget.NoWhatsApp, resolve())
    }

    @Test
    fun aPackEveryWhatsAppHasNeedsNoLaunch() = runBlocking {
        installWhatsApp(WhitelistProvider::class.java).whitelisted = true
        assertEquals(AddStickerPackFlow.AddTarget.AlreadyAdded, resolve())
    }

    @Test
    fun aPackWhatsAppLacksLaunchesWhatsAppForIt() = runBlocking {
        installWhatsApp(WhitelistProvider::class.java)
        val intent = (resolve() as AddStickerPackFlow.AddTarget.Launch).intent
        assertEquals("com.whatsapp", intent.`package`)
        assertEquals(PACK_ID, intent.getStringExtra("sticker_pack_id"))
    }

    /** A throwing provider used to crash the screen (or come up through viewModelScope). */
    @Test
    fun aFailingWhitelistCheckFallsBackToThePlainAddIntent() = runBlocking {
        installWhatsApp(FailingWhitelistProvider::class.java)
        val intent = (resolve() as AddStickerPackFlow.AddTarget.Launch).intent
        assertEquals("com.whatsapp.intent.action.ENABLE_STICKER_PACK", intent.action)
        // No app pinned: WhatsApp itself decides.
        assertNull(intent.`package`)
        assertEquals(PACK_ID, intent.getStringExtra("sticker_pack_id"))
        assertEquals(PACK_NAME, intent.getStringExtra("sticker_pack_name"))
    }

    @Test
    fun aCancellationIsNotTakenForAFailingCheck() {
        installWhatsApp(CancellingWhitelistProvider::class.java)
        assertThrows(CancellationException::class.java) { runBlocking { resolve() } }
    }

    /** The queries can cold-start WhatsApp: never on the caller's (main) thread. */
    @Test
    fun whatsAppIsAskedOnTheDispatcherGiven() = runBlocking {
        val provider = installWhatsApp(WhitelistProvider::class.java)
        val io = Executors.newSingleThreadExecutor { Thread(it, "add-target-io") }
        try {
            AddStickerPackFlow.resolveAddTarget(context, PACK_ID, PACK_NAME, SITE, io.asCoroutineDispatcher())
        } finally {
            io.shutdown()
        }
        assertEquals("add-target-io", provider.queriedOn?.name)
    }

    private suspend fun resolve() = AddStickerPackFlow.resolveAddTarget(context, PACK_ID, PACK_NAME, SITE)

    /** Installs WhatsApp (consumer only) with [provider] answering its whitelist check. */
    private fun <T : ContentProvider> installWhatsApp(provider: Class<T>): T {
        val app = ApplicationInfo().apply {
            packageName = WHATSAPP
            enabled = true
        }
        shadowOf(context.packageManager).installPackage(
            PackageInfo().apply {
                packageName = WHATSAPP
                applicationInfo = app
                providers = arrayOf(
                    ProviderInfo().apply {
                        authority = WHITELIST_AUTHORITY
                        packageName = WHATSAPP
                        name = provider.name
                        applicationInfo = app
                    }
                )
            }
        )
        return Robolectric.setupContentProvider(provider, WHITELIST_AUTHORITY)
    }

    abstract class FakeWhitelistProvider : ContentProvider() {
        override fun onCreate() = true
        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
    }

    /** Answers like WhatsApp: one row whose "result" is 1 when the pack is already there. */
    class WhitelistProvider : FakeWhitelistProvider() {
        @Volatile var whitelisted = false
        @Volatile var queriedOn: Thread? = null

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor {
            queriedOn = Thread.currentThread()
            return MatrixCursor(arrayOf("result")).apply { addRow(arrayOf(if (whitelisted) 1 else 0)) }
        }
    }

    /** WhatsApp refusing the query, the way a cross-process provider can. */
    class FailingWhitelistProvider : FakeWhitelistProvider() {
        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor = throw SecurityException("Permission Denial: reading $uri")
    }

    class CancellingWhitelistProvider : FakeWhitelistProvider() {
        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor = throw CancellationException("cancelled")
    }

    private companion object {
        const val WHATSAPP = "com.whatsapp"
        const val WHITELIST_AUTHORITY = "com.whatsapp.provider.sticker_whitelist_check"
        const val PACK_ID = "own-1a2b3c4d"
        const val PACK_NAME = "Our pack"
        const val SITE = "test_add_intent"
    }
}
