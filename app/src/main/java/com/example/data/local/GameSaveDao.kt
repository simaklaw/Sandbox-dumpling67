package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for local game save persistence.
 */
@Dao
interface GameSaveDao {

    @Query("SELECT * FROM game_saves WHERE saveSlotId = :slotId LIMIT 1")
    fun getSaveFlow(slotId: String = "active_slot"): Flow<GameSaveEntity?>

    @Query("SELECT * FROM game_saves WHERE saveSlotId = :slotId LIMIT 1")
    suspend fun getSaveSync(slotId: String = "active_slot"): GameSaveEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveGame(save: GameSaveEntity)

    @Query("UPDATE game_saves SET coins = :newCoins, lastSavedTimestamp = :timestamp WHERE saveSlotId = :slotId")
    suspend fun updateCoins(slotId: String = "active_slot", newCoins: Int, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE game_saves SET runnerHighScore = :highScore, lastSavedTimestamp = :timestamp WHERE saveSlotId = :slotId")
    suspend fun updateRunnerHighScore(slotId: String = "active_slot", highScore: Int, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE game_saves SET totalSquishes = totalSquishes + 1, lastSavedTimestamp = :timestamp WHERE saveSlotId = :slotId")
    suspend fun incrementSquishCount(slotId: String = "active_slot", timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM game_saves WHERE saveSlotId = :slotId")
    suspend fun deleteSave(slotId: String = "active_slot")
}
