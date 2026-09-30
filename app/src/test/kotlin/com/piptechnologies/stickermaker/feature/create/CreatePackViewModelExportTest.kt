package com.piptechnologies.stickermaker.feature.create

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Looper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.piptechnologies.stickermaker.core.data.db.LoveDb
import com.piptechnologies.stickermaker.core.data.db.OwnPackEntity
import com.piptechnologies.stickermaker.core.data.prefs.PrefsRepository
import com.piptechnologies.stickermaker.core.data.repo.MyPacksRepository
import com.piptechnologies.stickermaker.whatsapp.StickerPackValidator
import com.piptechnologies.stickermaker.whatsapp.WebpInfo
import java.io.File
import java.time.Duration
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode

/**
 * The pack the view model saves (spec §3, §7): a motion preset makes the whole pack animated and a still
 * in it two frames, a pack without motion stays static, and every sticker is tagged with its own emoji.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class)
class CreatePackViewModelExportTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, LoveDb::class.java).allowMainThreadQueries().build()
    private val vm = CreatePackViewModel(
        context,
        MyPacksRepository(db.installedPackDao(), db.ownPackDao(), Dispatchers.Unconfined),
        PrefsRepository(context),
        Dispatchers.IO
    )
    private val state get() = vm.state.value

    @After
    fun close() {
        db.close()
    }

    @Test
    fun aPresetAnimatesThePackAndEveryStickerKeepsItsTags() {
        cutOut(3)
        vm.addEmoji("red_heart.webp")
        vm.setPreset("heartbeat")
        vm.selectSticker(1)
        vm.addDecor("doodles-1.webp")
        assertTrue(state.animatedPack)

        val pack = saveOnly()
        assertTrue(pack.animated)
        val stickers = db.ownPackDao().stickersBlocking(pack.id)
        assertEquals(listOf("❤️", "👑", "❤️,😊"), stickers.map { it.emojis })
        val files = stickers.map { WebpInfo.parse(File(pack.dirPath, it.fileName).readBytes()) }
        assertEquals("the preset's frames, then two for each still", listOf(12, 2, 2), files.map { it.frameCount })
        val tray = File(pack.dirPath, pack.trayFile).readBytes()
        assertEquals(96 to 96, StickerPackValidator.pngDimensions(tray))
    }

    @Test
    fun withoutMotionThePackStaysStatic() {
        cutOut(3)
        vm.addEmoji("red_heart.webp")
        assertFalse(state.animatedPack)

        val pack = saveOnly()
        assertFalse(pack.animated)
        val stickers = db.ownPackDao().stickersBlocking(pack.id)
        assertEquals(listOf("❤️", "❤️,😊", "❤️,😊"), stickers.map { it.emojis })
        stickers.forEach {
            assertFalse(it.fileName, WebpInfo.parse(File(pack.dirPath, it.fileName).readBytes()).isAnimated)
        }
    }

    @Test
    fun anExportAfterEditsMakesANewPackAndTheOldOneGoes() {
        cutOut(3)
        vm.addEmoji("red_heart.webp")
        val first = exportToWhatsApp()
        assertEquals(listOf("❤️", "❤️,😊", "❤️,😊"), db.ownPackDao().stickersBlocking(first.id).map { it.emojis })

        // Nothing changed: the same pack is sent again, no new export.
        vm.addToWhatsApp()
        waitFor("the re-send to settle") { !state.exportState.isBusy() }
        assertEquals(listOf(first.id), db.ownPackDao().getAllBlocking().map { it.id })

        // A layer changed: a new pack with the new content; the old one is gone, files and all.
        vm.selectSticker(1)
        vm.addDecor("doodles-1.webp")
        val second = exportToWhatsApp()
        assertEquals(listOf(second.id), db.ownPackDao().getAllBlocking().map { it.id })
        assertEquals(listOf("❤️", "👑", "❤️,😊"), db.ownPackDao().stickersBlocking(second.id).map { it.emojis })
        assertFalse("the replaced pack's files are gone", File(first.dirPath).exists())
        assertTrue(File(second.dirPath).isDirectory)

        // The tray or the name changing counts too.
        vm.setPackName("Us")
        val third = exportToWhatsApp()
        assertEquals("Us", third.name)
        assertEquals(listOf(third.id), db.ownPackDao().getAllBlocking().map { it.id })
    }

    /**
     * Adds the pack to WhatsApp. There is none here, so the confirm comes back cancelled and the session
     * goes on; returns the saved pack once its stickers are registered and the state is idle again.
     */
    private fun exportToWhatsApp(): OwnPackEntity {
        val before = db.ownPackDao().getAllBlocking().map { it.id }
        vm.addToWhatsApp()
        var pack: OwnPackEntity? = null
        waitFor("the exported pack") {
            pack = db.ownPackDao().getAllBlocking().firstOrNull { it.id !in before }
            pack?.let { db.ownPackDao().stickersBlocking(it.id).size == 3 } == true && !state.exportState.isBusy()
        }
        return checkNotNull(pack)
    }

    /** Saves the pack to My Packs only and returns it once its stickers are registered. */
    private fun saveOnly(): OwnPackEntity {
        vm.saveToMyPacksOnly()
        var pack: OwnPackEntity? = null
        waitFor("the saved pack") {
            pack = db.ownPackDao().getAllBlocking().singleOrNull()
            pack?.let { db.ownPackDao().stickersBlocking(it.id).size == 3 } == true
        }
        return checkNotNull(pack)
    }

    /** [n] camera shots (no picker, no content resolver), cut out; the decor data is loaded. */
    private fun cutOut(n: Int) {
        repeat(n) { i ->
            val shot = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
            shot.eraseColor(Color.rgb(40 * i, 90, 200))
            vm.addCameraShot(shot)
            waitFor("shot ${i + 1}") { state.items.size == i + 1 }
        }
        waitFor("the decor data") { state.dataReady }
        vm.beginCutouts()
        waitFor("the cut-outs") { state.items.all { it.cut == CutStatus.Done } }
    }

    /** Runs the main looper (where the view model lives), its clock included, until [done]; at most 30 s. */
    private fun waitFor(what: String, done: () -> Boolean) {
        val end = System.currentTimeMillis() + 30_000
        while (true) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(20))
            if (done()) return
            check(System.currentTimeMillis() < end) { "timed out waiting for $what" }
            Thread.sleep(10)
        }
    }
}
