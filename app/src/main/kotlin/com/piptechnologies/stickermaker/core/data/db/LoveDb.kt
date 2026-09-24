package com.piptechnologies.stickermaker.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

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
    version = 1,
    exportSchema = false,
)
abstract class LoveDb : RoomDatabase() {

    abstract fun installedPackDao(): InstalledPackDao

    abstract fun ownPackDao(): OwnPackDao
}
