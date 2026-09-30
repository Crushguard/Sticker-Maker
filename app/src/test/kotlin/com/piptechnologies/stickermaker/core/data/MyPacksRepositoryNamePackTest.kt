package com.piptechnologies.stickermaker.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.piptechnologies.stickermaker.core.data.db.LoveDb
import com.piptechnologies.stickermaker.core.data.repo.MyPacksRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class)
class MyPacksRepositoryNamePackTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, LoveDb::class.java).allowMainThreadQueries().build()
    private val repo = MyPacksRepository(db.installedPackDao(), db.ownPackDao(), Dispatchers.Unconfined)
    private val files = (1..12).map { "%02d.webp".format(it) }

    @After
    fun close() = db.close()

    @Test
    fun reLetteringKeepsTheIdBumpsTheVersionAndKeepsTheWhitelist() = runBlocking {
        val first = repo.saveNamePack("own-np-abc", "Aymen ❤ Sara", "PIP Technologies", files.map { it to listOf("❤️") }, "/data/own/own-np-abc", "tray.png", now = T)
        assertEquals((T / 60_000L).toInt(), first)
        db.ownPackDao().setWhitelisted("own-np-abc", true)

        assertEquals(first + 1, repo.saveNamePack("own-np-abc", "Aymen ❤ Sara", "PIP Technologies", files.map { it to listOf("😘") }, "/data/own/own-np-abc", "tray.png", now = T))

        val pack = db.ownPackDao().get("own-np-abc")!!
        assertEquals(first + 1, pack.imageDataVersion)
        assertTrue(pack.whitelisted)
        val rows = db.ownPackDao().stickers("own-np-abc")
        assertEquals(files, rows.map { it.fileName })
        assertTrue(rows.all { it.emojis == "😘" })
    }

    @Test
    fun aPackReMadeAfterItsRowWentGetsAVersionWhatsAppHasNotSeen() = runBlocking {
        // Clear data or a reinstall drops the row; the same names and language give the same id again.
        val first = repo.saveNamePack("own-np-abc", "Aymen \u2764 Sara", "PIP Technologies", files.map { it to listOf("\u2764\uFE0F") }, "/data/own/own-np-abc", "tray.png", now = T)
        db.ownPackDao().deleteStickers("own-np-abc")
        db.ownPackDao().deletePack("own-np-abc")

        val again = repo.saveNamePack("own-np-abc", "Aymen \u2764 Sara", "PIP Technologies", files.map { it to listOf("\u2764\uFE0F") }, "/data/own/own-np-abc", "tray.png", now = T + 5 * 60_000L)
        assertTrue("re-made at version $again after $first: WhatsApp would keep the old images", again > first)
    }

    @Test
    fun createdPacksStartAtVersionOne() = runBlocking {
        val id = repo.saveOwnPack("Us", "Made by you", false, files.take(3).map { it to listOf("❤️") }, "/data/own/x", "tray.png")
        assertEquals(1, db.ownPackDao().get(id)!!.imageDataVersion)
    }

    private companion object {
        /** A fixed clock, in milliseconds since the epoch. */
        const val T = 1_790_000_000_000L
    }
}
