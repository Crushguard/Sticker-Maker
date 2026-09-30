package com.piptechnologies.stickermaker.feature.namepack

import android.content.Context
import android.graphics.Typeface
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.piptechnologies.stickermaker.R
import com.piptechnologies.stickermaker.core.data.db.LoveDb
import com.piptechnologies.stickermaker.core.data.repo.MyPacksRepository
import com.piptechnologies.stickermaker.feature.namepack.engine.Character
import com.piptechnologies.stickermaker.feature.namepack.engine.LetteringFont
import com.piptechnologies.stickermaker.feature.namepack.engine.NamePackAssets
import com.piptechnologies.stickermaker.feature.namepack.engine.NamePackBuilder
import com.piptechnologies.stickermaker.feature.namepack.engine.NamePackRequest
import com.piptechnologies.stickermaker.feature.namepack.engine.NamePackSaver
import com.piptechnologies.stickermaker.feature.namepack.engine.Relation
import com.piptechnologies.stickermaker.feature.namepack.engine.Slot
import com.piptechnologies.stickermaker.feature.namepack.engine.Tone
import java.io.File
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class)
class NamePackBuilderTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val assets = object : NamePackAssets(context) {
        override fun typeface(font: LetteringFont): Typeface = TestFonts.of(font)
    }
    private val builder = NamePackBuilder(assets)
    private val request = NamePackRequest("en", false, "Aymen", "Sara", Relation.GIRLFRIEND, Tone.SWEET, Character.MANGO)

    @Test
    fun buildsTwelveInOrderReportsProgressAndCaches() = runBlocking {
        val progress = mutableListOf<Int>()
        val stickers = builder.build(request) { synchronized(progress) { progress += it } }
        assertEquals(Slot.entries, stickers.map { it.slot })
        assertEquals((1..12).toList(), progress.sorted())
        assertEquals("Love you, Sara", stickers.first().text)
        assertEquals("Aymen ❤ Sara", stickers[Slot.OUR_NAMES.ordinal].text)
        assertEquals(NamePackBuilder.PREVIEW_PX, stickers.first().preview.width)
        assertTrue(stickers.all { it.webp.size in 1..(100 * 1024) })
        assertSame(stickers[3], builder.build(request)[3])
        assertNotEquals(stickers[1].text, builder.build(request.copy(tone = Tone.FLIRTY))[1].text)
    }

    @Test
    fun familyPacksStaySweet() = runBlocking {
        val mom = request.copy(relation = Relation.MOM, tone = Tone.FLIRTY)
        assertEquals(Tone.SWEET, mom.effectiveTone)
        assertEquals("Big kiss, Sara", builder.text(mom, Slot.KISS))
    }

    @Test
    fun savesAValidPackAndReLettersItInPlace() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, LoveDb::class.java).allowMainThreadQueries().build()
        val saver = NamePackSaver(
            context.filesDir,
            MyPacksRepository(db.installedPackDao(), db.ownPackDao(), Dispatchers.Unconfined),
            context.getString(R.string.config_support_email),
            context.getString(R.string.config_privacy_policy_url)
        )
        val tray = builder.tray(request)
        assertEquals(96, tray.bitmap.width)

        // The version counts from the clock (a re-made pack must be new to WhatsApp), then up by one per save.
        val version = saver.save(request.packId, "Aymen ❤ Sara", builder.build(request), tray.png)
        assertTrue("first version $version", version >= 1)
        val dir = File(context.filesDir, "own/${request.packId}")
        assertTrue(File(dir, "tray.png").isFile)
        assertEquals((1..12).map { "%02d.webp".format(it) }, dir.list()!!.filter { it.endsWith(".webp") }.sorted())
        assertEquals(12, db.ownPackDao().stickers(request.packId).size)

        val recast = request.copy(character = Character.CAPY, tone = Tone.FLIRTY)
        assertEquals(request.packId, recast.packId)
        assertEquals(version + 1, saver.save(recast.packId, "Aymen ❤ Sara", builder.build(recast), tray.png))
        assertTrue(File(context.filesDir, "own").list()!!.none { it.endsWith(".tmp") })
        db.close()
    }

    @Test
    fun theWaitingArtAndTileArtLoad() {
        val wait = assets.waitArt(Character.CAPY) // no capy-wait yet: Mango's still
        assertEquals(512, wait.bitmap.width)
        assertEquals("the waiting art's description names whose art is shown", Character.MANGO, wait.character)
        assertEquals(144, assets.tileArt(Character.BUNNY).width)
    }

    @Test
    fun aCancelledSaveStillLandsTheNewImagesAndTheNewVersionTogether() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, LoveDb::class.java).allowMainThreadQueries().build()
        val saver = NamePackSaver(
            context.filesDir,
            MyPacksRepository(db.installedPackDao(), db.ownPackDao(), Dispatchers.Unconfined),
            context.getString(R.string.config_support_email),
            context.getString(R.string.config_privacy_policy_url)
        )
        val tray = builder.tray(request)
        val first = builder.build(request)
        val version = saver.save(request.packId, "Aymen \u2764 Sara", first, tray.png)
        assertTrue("first version $version", version >= 1)

        val recast = request.copy(character = Character.CAPY, tone = Tone.FLIRTY)
        val second = builder.build(recast)
        assertFalse("the re-cast must change the images, or nothing below proves anything", first[0].webp.contentEquals(second[0].webp))

        // The screen closes while the save is in flight: the save starts, then its caller is cancelled.
        val caller = launch(start = CoroutineStart.UNDISPATCHED) {
            saver.save(recast.packId, "Aymen \u2764 Sara", second, tray.png)
        }
        caller.cancel()
        caller.join()
        assumeTrue("the save finished before it could be cancelled, so this run proves nothing", caller.isCancelled)

        // The images and the image data version still move together, or WhatsApp would keep its stale copy.
        val dir = File(context.filesDir, "own/${request.packId}")
        second.forEachIndexed { index, sticker ->
            val name = NamePackSaver.fileName(index)
            assertTrue("$name was not replaced by the re-cast image", File(dir, name).readBytes().contentEquals(sticker.webp))
        }
        assertEquals("new images need the next image data version", version + 1, db.ownPackDao().get(request.packId)!!.imageDataVersion)
        db.close()
    }

    @Test
    fun theCacheKeepsDifferentContentApart() = runBlocking {
        // Each variant differs from `request` in one thing only: a cache key that left it out would serve the wrong sticker.
        val variants = listOf(
            "another character" to request.copy(character = Character.CAPY),
            "another tone" to request.copy(tone = Tone.FLIRTY),
            "a family relation at the same tone" to request.copy(relation = Relation.MOM),
            "another language" to request.copy(lang = "de"),
            "another first name" to request.copy(you = "Omar"),
            "another second name" to request.copy(love = "Lina"),
        )
        variants.forEach { (what, variant) ->
            // Asking for the request again is a cache hit that also keeps its entries the freshest, so the variants cannot evict them.
            val cached = builder.build(request)
            val other = builder.build(variant)
            cached.indices.forEach { index ->
                assertNotSame("${Slot.entries[index].key}: $what was served the cached sticker", cached[index], other[index])
            }
        }
        assertEquals("Big kiss, Sara", builder.build(request.copy(relation = Relation.MOM))[Slot.KISS.ordinal].text)
    }
}
