package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "blind_box_rewards")
data class BlindBoxRewardEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val itemId: Int, // 1..67
    val name: String,
    val category: String, // "Пельмени", "Соусы", "Артефакты", "Бусты", "Головные уборы"
    val rarity: String, // "Обычный", "Редкий", "Эпический", "Легендарный", "Мифический 67"
    val description: String,
    val quote: String,
    val iconEmoji: String,
    val colorHex: Long,
    val statBonus: String,
    val obtainedTimestamp: Long = System.currentTimeMillis(),
    val boxTypeOpened: String
)
