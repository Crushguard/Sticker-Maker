package com.piptechnologies.stickermaker.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A catalog pack the user added: its files were downloaded to [dirPath] (tray + sticker
 * files live directly in that directory) and it is served to WhatsApp by the
 * StickerContentProvider.
 */
@Entity(tableName = "installed_packs")
data class InstalledPackEntity(
    @PrimaryKey val id: String,
    val name: String,
    val publisher: String,
    val trayFile: String,
    val animated: Boolean,
    val category: String,
    val sortOrder: Int,
    val addedAt: Long,
    val dirPath: String,
    val whitelisted: Boolean = false,
    /** WhatsApp's image_data_version: the catalog version these files came from. */
    @ColumnInfo(defaultValue = "1") val imageDataVersion: Int = 1,
)

/**
 * One sticker of an installed pack. [emojis] is the comma-joined emoji list; [accessibilityText] is the
 * sticker's lettering, served to WhatsApp as its accessibility text (null when the art has no text).
 */
@Entity(tableName = "installed_stickers", primaryKeys = ["packId", "fileName"])
data class InstalledStickerEntity(
    val packId: String,
    val fileName: String,
    val emojis: String,
    val indexInPack: Int,
    val accessibilityText: String? = null,
)

/** A pack the user created in the app; files live in [dirPath] like installed packs. */
@Entity(tableName = "own_packs")
data class OwnPackEntity(
    @PrimaryKey val id: String,
    val name: String,
    val publisher: String,
    val trayFile: String,
    val animated: Boolean,
    val createdAt: Long,
    val dirPath: String,
    val whitelisted: Boolean = false,
    /** Bumped whenever the pack's images change, so WhatsApp re-reads them (name packs re-letter). */
    @ColumnInfo(defaultValue = "1") val imageDataVersion: Int = 1,
)

/** One sticker of an own (user-created) pack. [emojis] is the comma-joined emoji list. */
@Entity(tableName = "own_stickers", primaryKeys = ["packId", "fileName"])
data class OwnStickerEntity(
    val packId: String,
    val fileName: String,
    val emojis: String,
    val indexInPack: Int,
)
