package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "runner_high_scores")
data class RunnerHighScoreEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val distance: Int,
    val coins: Int,
    val dumplings: Int,
    val enemiesSmashed: Int,
    val locationName: String,
    val timestamp: Long = System.currentTimeMillis()
)
