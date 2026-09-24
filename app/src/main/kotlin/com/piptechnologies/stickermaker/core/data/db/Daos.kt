package com.piptechnologies.stickermaker.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Installed catalog packs. The `*Blocking` variants exist for the StickerContentProvider,
 * which runs on a Binder thread and cannot suspend.
 */
@Dao
interface InstalledPackDao {

    @Query("SELECT * FROM installed_packs ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<InstalledPackEntity>>

    @Query("SELECT * FROM installed_packs ORDER BY addedAt DESC")
    suspend fun getAll(): List<InstalledPackEntity>

    @Query("SELECT * FROM installed_packs ORDER BY addedAt DESC")
    fun getAllBlocking(): List<InstalledPackEntity>

    @Query("SELECT * FROM installed_packs WHERE id = :id")
    suspend fun get(id: String): InstalledPackEntity?

    @Query("SELECT * FROM installed_packs WHERE id = :id")
    fun getBlocking(id: String): InstalledPackEntity?

    @Query("SELECT * FROM installed_stickers WHERE packId = :packId ORDER BY indexInPack")
    suspend fun stickers(packId: String): List<InstalledStickerEntity>

    @Query("SELECT * FROM installed_stickers WHERE packId = :packId ORDER BY indexInPack")
    fun stickersBlocking(packId: String): List<InstalledStickerEntity>

    @Query("SELECT * FROM installed_stickers WHERE packId = :packId ORDER BY indexInPack")
    fun observeStickers(packId: String): Flow<List<InstalledStickerEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPack(pack: InstalledPackEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStickers(stickers: List<InstalledStickerEntity>)

    @Query("DELETE FROM installed_packs WHERE id = :id")
    suspend fun deletePack(id: String)

    @Query("DELETE FROM installed_stickers WHERE packId = :packId")
    suspend fun deleteStickers(packId: String)

    @Query("UPDATE installed_packs SET whitelisted = :whitelisted WHERE id = :id")
    suspend fun setWhitelisted(id: String, whitelisted: Boolean)
}

/** Packs created in the app's create flow; same shape as [InstalledPackDao]. */
@Dao
interface OwnPackDao {

    @Query("SELECT * FROM own_packs ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<OwnPackEntity>>

    @Query("SELECT * FROM own_packs ORDER BY createdAt DESC")
    suspend fun getAll(): List<OwnPackEntity>

    @Query("SELECT * FROM own_packs ORDER BY createdAt DESC")
    fun getAllBlocking(): List<OwnPackEntity>

    @Query("SELECT * FROM own_packs WHERE id = :id")
    suspend fun get(id: String): OwnPackEntity?

    @Query("SELECT * FROM own_packs WHERE id = :id")
    fun getBlocking(id: String): OwnPackEntity?

    @Query("SELECT * FROM own_stickers WHERE packId = :packId ORDER BY indexInPack")
    suspend fun stickers(packId: String): List<OwnStickerEntity>

    @Query("SELECT * FROM own_stickers WHERE packId = :packId ORDER BY indexInPack")
    fun stickersBlocking(packId: String): List<OwnStickerEntity>

    @Query("SELECT * FROM own_stickers WHERE packId = :packId ORDER BY indexInPack")
    fun observeStickers(packId: String): Flow<List<OwnStickerEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPack(pack: OwnPackEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStickers(stickers: List<OwnStickerEntity>)

    @Query("DELETE FROM own_packs WHERE id = :id")
    suspend fun deletePack(id: String)

    @Query("DELETE FROM own_stickers WHERE packId = :packId")
    suspend fun deleteStickers(packId: String)

    @Query("UPDATE own_packs SET whitelisted = :whitelisted WHERE id = :id")
    suspend fun setWhitelisted(id: String, whitelisted: Boolean)
}
