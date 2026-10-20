package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pelmen_variants")
data class PelmenVariantEntity(
    @PrimaryKey
    val variantId: String,               // Unique ID: e.g. "pelmen_six", "golden_pelmen", "khinkali"
    val displayName: String,            // "Пельмень-Сикс"
    val rarity: String,                 // "Обычный", "Редкий", "Эпический", "Легендарный", "Мифический 67"
    val description: String,
    val quote: String,
    val emojiIcon: String,
    val primaryColorHex: Long,
    val secondaryColorHex: Long,
    val unlockedAt: Long = System.currentTimeMillis(),
    val timesOpened: Int = 1,           // How many times obtained from blind-boxes
    val isEquipped: Boolean = false,
    val isFavorite: Boolean = false,
    val squishCount: Int = 0,
    val coinBonusPct: Int = 0,
    val speedBonusPct: Int = 0,
    val specialAbility: String = ""
)
