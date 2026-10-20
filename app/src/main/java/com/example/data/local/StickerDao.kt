package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StickerDao {
    @Query("SELECT * FROM runner_stickers ORDER BY requiredDistance ASC")
    fun getAllStickers(): Flow<List<StickerEntity>>

    @Query("SELECT * FROM runner_stickers ORDER BY requiredDistance ASC")
    suspend fun getAllStickersList(): List<StickerEntity>

    @Query("SELECT * FROM runner_stickers WHERE isUnlocked = 1 ORDER BY requiredDistance ASC")
    fun getUnlockedStickers(): Flow<List<StickerEntity>>

    @Query("SELECT * FROM runner_stickers WHERE id = :id LIMIT 1")
    suspend fun getStickerById(id: String): StickerEntity?

    @Query("UPDATE runner_stickers SET isUnlocked = 1, unlockedTimestamp = :timestamp WHERE id = :id AND isUnlocked = 0")
    suspend fun unlockSticker(id: String, timestamp: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDefaultStickers(stickers: List<StickerEntity>)

    @Query("SELECT COUNT(*) FROM runner_stickers")
    suspend fun getStickerCount(): Int

    @Query("SELECT COUNT(*) FROM runner_stickers WHERE isUnlocked = 1")
    fun getUnlockedCount(): Flow<Int>
}
