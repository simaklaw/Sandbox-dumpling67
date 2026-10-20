package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RunnerHighScoreDao {

    @Query("SELECT * FROM runner_high_scores ORDER BY distance DESC, coins DESC LIMIT 10")
    fun getTopScores(): Flow<List<RunnerHighScoreEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScore(score: RunnerHighScoreEntity): Long

    @Query("DELETE FROM runner_high_scores")
    suspend fun clearScores()
}
