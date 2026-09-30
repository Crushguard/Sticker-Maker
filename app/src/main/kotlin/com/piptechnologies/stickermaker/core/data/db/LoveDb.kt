package com.piptechnologies.stickermaker.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * The app database. The Hilt module that builds it lives in the data layer;
 * this file only defines the schema and access points.
 */
@Database(
    entities = [
        InstalledPackEntity::class,
        InstalledStickerEntity::class,
        OwnPackEntity::class,
        OwnStickerEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class LoveDb : RoomDatabase() {

    abstract fun installedPackDao(): InstalledPackDao

    abstract fun ownPackDao(): OwnPackDao

    companion object {
        /**
         * Version 2: every installed and own pack carries WhatsApp's image_data_version (existing packs
         * stay at 1; a catalog update or a re-lettered name pack raises it), and each installed sticker
         * its accessibility text.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `installed_packs` ADD COLUMN `imageDataVersion` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `own_packs` ADD COLUMN `imageDataVersion` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `installed_stickers` ADD COLUMN `accessibilityText` TEXT")
            }
        }
    }
}
