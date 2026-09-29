package com.piptechnologies.stickermaker.catalog

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.piptechnologies.stickermaker.core.data.db.LoveDb
import com.piptechnologies.stickermaker.core.data.db.MIGRATION_1_2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class LoveDbMigrationTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "migration-test.db"

    /** Version 1 exactly as Room 2.6 created it (LoveDb_Impl.createAllTables). */
    private val v1Schema = listOf(
        "CREATE TABLE IF NOT EXISTS `installed_packs` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `publisher` TEXT NOT NULL, `trayFile` TEXT NOT NULL, `animated` INTEGER NOT NULL, `category` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, `addedAt` INTEGER NOT NULL, `dirPath` TEXT NOT NULL, `whitelisted` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        "CREATE TABLE IF NOT EXISTS `installed_stickers` (`packId` TEXT NOT NULL, `fileName` TEXT NOT NULL, `emojis` TEXT NOT NULL, `indexInPack` INTEGER NOT NULL, PRIMARY KEY(`packId`, `fileName`))",
        "CREATE TABLE IF NOT EXISTS `own_packs` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `publisher` TEXT NOT NULL, `trayFile` TEXT NOT NULL, `animated` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `dirPath` TEXT NOT NULL, `whitelisted` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        "CREATE TABLE IF NOT EXISTS `own_stickers` (`packId` TEXT NOT NULL, `fileName` TEXT NOT NULL, `emojis` TEXT NOT NULL, `indexInPack` INTEGER NOT NULL, PRIMARY KEY(`packId`, `fileName`))",
        "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)",
        "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '1cbf4361a392886dece6f198fec2959d')",
    )

    @Before
    fun clean() {
        context.deleteDatabase(name)
    }

    @Test
    fun version1PacksSurviveWithImageDataVersion1() {
        object : SQLiteOpenHelper(context, name, null, 1) {
            override fun onCreate(db: SQLiteDatabase) {
                v1Schema.forEach(db::execSQL)
                db.execSQL("INSERT INTO installed_packs VALUES ('gm-gn','Good Morning, Good Night','PIP Technologies','tray.png',0,'goodnight',6,1700000000000,'/data/packs/gm-gn',1)")
                db.execSQL("INSERT INTO installed_stickers VALUES ('gm-gn','01.webp','☀️,☕',0)")
                db.execSQL("INSERT INTO own_packs VALUES ('own-1','Mine','PIP Technologies','tray.png',0,1700000000000,'/data/own/own-1',0)")
            }

            override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }.writableDatabase.close()

        val db = Room.databaseBuilder(context, LoveDb::class.java, name)
            .addMigrations(MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()
        val pack = db.installedPackDao().getBlocking("gm-gn")!!
        assertEquals(1, pack.imageDataVersion)
        assertTrue(pack.whitelisted)
        val sticker = db.installedPackDao().stickersBlocking("gm-gn").single()
        assertEquals("☀️,☕", sticker.emojis)
        assertNull(sticker.accessibilityText)
        assertEquals(1, db.ownPackDao().getBlocking("own-1")!!.imageDataVersion)
        db.close()
    }
}
