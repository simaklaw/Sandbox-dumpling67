package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 🌟 Коллекционная наклейка для детского интерактивного альбома.
 */
@Entity(tableName = "runner_stickers")
data class StickerEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val description: String,
    val emoji: String,
    val biome: String,
    val requiredDistance: Int,
    val isUnlocked: Boolean = false,
    val unlockedTimestamp: Long = 0L
)
