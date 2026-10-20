package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BlindBoxRewardDao {

    @Query("SELECT * FROM blind_box_rewards ORDER BY obtainedTimestamp DESC")
    fun getAllRewards(): Flow<List<BlindBoxRewardEntity>>

    @Query("SELECT DISTINCT itemId FROM blind_box_rewards")
    fun getDistinctUnlockedItemIds(): Flow<List<Int>>

    @Query("SELECT COUNT(DISTINCT itemId) FROM blind_box_rewards")
    fun getDistinctCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReward(reward: BlindBoxRewardEntity): Long

    @Query("DELETE FROM blind_box_rewards")
    suspend fun clearAllRewards()
}
