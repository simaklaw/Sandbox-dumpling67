package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity for storing local game save persistence.
 */
@Entity(tableName = "game_saves")
data class GameSaveEntity(
    @PrimaryKey
    val saveSlotId: String = "active_slot",
    val coins: Int = 150,
    val pelmenPoints: Int = 20,
    val brainrotScore: Int = 0,
    val brainrotLevel: Int = 1,
    val runnerHighScore: Int = 0,
    val slapHighScore: Int = 0,
    val equippedSkinId: String = "pelmen_skin_01",
    val heroSpeedLevel: Int = 1,
    val heroJumpLevel: Int = 1,
    val heroBoostLevel: Int = 1,
    val heroMagnetLevel: Int = 1,
    val totalBoxesOpened: Int = 0,
    val totalSquishes: Long = 0L,
    val isSoundEnabled: Boolean = true,
    val isHapticsEnabled: Boolean = true,
    val lastSavedTimestamp: Long = System.currentTimeMillis(),
    val saveVersion: Int = 1
)
