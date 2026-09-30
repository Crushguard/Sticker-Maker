package com.piptechnologies.stickermaker.core.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.piptechnologies.stickermaker.core.data.di.DatabaseModule
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
@Config(sdk = [34], application = android.app.Application::class)
class LoveDbMigrationTest {

    private val v1 = listOf(
        "CREATE TABLE IF NOT EXISTS `installed_packs` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `publisher` TEXT NOT NULL, `trayFile` TEXT NOT NULL, `animated` INTEGER NOT NULL, `category` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, `addedAt` INTEGER NOT NULL, `dirPath` TEXT NOT NULL, `whitelisted` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        "CREATE TABLE IF NOT EXISTS `installed_stickers` (`packId` TEXT NOT NULL, `fileName` TEXT NOT NULL, `emojis` TEXT NOT NULL, `indexInPack` INTEGER NOT NULL, PRIMARY KEY(`packId`, `fileName`))",
        "CREATE TABLE IF NOT EXISTS `own_packs` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `publisher` TEXT NOT NULL, `trayFile` TEXT NOT NULL, `animated` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `dirPath` TEXT NOT NULL, `whitelisted` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        "CREATE TABLE IF NOT EXISTS `own_stickers` (`packId` TEXT NOT NULL, `fileName` TEXT NOT NULL, `emojis` TEXT NOT NULL, `indexInPack` INTEGER NOT NULL, PRIMARY KEY(`packId`, `fileName`))",
        "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)",
        "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '1cbf4361a392886dece6f198fec2959d')"
    )

    @Test
    fun aVersionOneDatabaseMigratesAndKeepsItsPacks() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // The app's own file and builder: a migration the module does not register fails here.
        val name = "love.db"
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            v1.forEach(db::execSQL)
            db.execSQL("INSERT INTO own_packs VALUES('own-1234abcd','Us','Made by you','tray.png',0,1,'/data/own',1)")
            db.execSQL("INSERT INTO installed_packs VALUES('gm-gn','Good Morning, Good Night','PIP Technologies','tray.png',0,'goodnight',6,1700000000000,'/data/packs/gm-gn',1)")
            db.execSQL("INSERT INTO installed_stickers VALUES('gm-gn','01.webp','☀️,☕',0)")
            db.version = 1
        }

        val room = DatabaseModule.provideDatabase(context)
        val own = room.ownPackDao().get("own-1234abcd")!!
        assertEquals(1, own.imageDataVersion)
        assertTrue(own.whitelisted)
        val installed = room.installedPackDao().get("gm-gn")!!
        assertEquals(1, installed.imageDataVersion)
        assertTrue(installed.whitelisted)
        val sticker = room.installedPackDao().stickers("gm-gn").single()
        assertEquals("☀️,☕", sticker.emojis)
        assertNull(sticker.accessibilityText)
        room.close()
    }
}
