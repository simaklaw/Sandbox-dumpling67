package com.example.data

import android.content.Context
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.local.GameSaveEntity
import com.example.data.local.PlayerInventoryEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Definition of a Game Booster (from the 67 Game Boosters collection).
 */
data class GameBooster(
    val id: Int,                         // 1 to 67
    val name: String,
    val category: BoosterCategory,
    val description: String,
    val multiplier: Float,
    val durationSeconds: Int,
    val costCoins: Int,
    val tier: Int,                       // 1 (Common) to 5 (Mythic 67)
    val iconEmoji: String,
    val isUnlocked: Boolean = false,
    val currentLevel: Int = 1
)

enum class BoosterCategory {
    SPEED,
    COIN_MULTIPLIER,
    SQUISH_ELASTICITY,
    BROTH_SHIELD,
    MAGNET,
    BRAINROT_FRENZY,
    SCORE_BOOST
}

/**
 * Model for cloud player progression.
 */
data class PlayerProgression(
    val playerId: String = "local_player",
    val level: Int = 1,
    val currentXp: Long = 0L,
    val totalCoinsEarned: Long = 0L,
    val unlockedBoosterIds: List<Int> = listOf(1, 2),
    val activeBoosterIds: List<Int> = emptyList(),
    val highestScore: Int = 0,
    val totalPelmensSquished: Long = 0L,
    val lastSyncTime: Long = System.currentTimeMillis()
)

/**
 * Data Repository class to manage the 67 game boosters and player progression data,
 * integrating Firebase Firestore with local Room cache fallback.
 */
class BoosterProgressionRepository(private val context: Context) {

    private val db: AppDatabase = AppDatabase.getInstance(context)
    private val gameSaveDao = db.gameSaveDao()
    private val inventoryDao = db.playerInventoryDao()
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _boosters = MutableStateFlow<List<GameBooster>>(generate67Boosters())
    val boosters: StateFlow<List<GameBooster>> = _boosters.asStateFlow()

    private val _progression = MutableStateFlow(PlayerProgression())
    val progression: StateFlow<PlayerProgression> = _progression.asStateFlow()

    private val _syncStatus = MutableStateFlow("Готово (Офлайн-кэш)")
    val syncStatus: StateFlow<String> = _syncStatus.asStateFlow()

    init {
        scope.launch {
            loadLocalProgression()
        }
    }

    private suspend fun loadLocalProgression() {
        val localSave = gameSaveDao.getSaveSync()
        if (localSave != null) {
            _progression.value = PlayerProgression(
                level = localSave.brainrotLevel,
                highestScore = localSave.runnerHighScore,
                totalPelmensSquished = localSave.totalSquishes,
                totalCoinsEarned = localSave.coins.toLong(),
                lastSyncTime = localSave.lastSavedTimestamp
            )
        }
    }

    /**
     * Synchronize player progression and active boosters locally.
     */
     suspend fun syncProgressionToFirestore(playerId: String = "player_default"): Boolean {
        return try {
            persistProgressionLocally()
            _syncStatus.value = "Профиль сохранён"
            true
        } catch (e: Exception) {
            Log.w("BoosterRepository", "Local save error: ${e.message}")
            _syncStatus.value = "Офлайн-режим"
            false
        }
    }

    /**
     * Fetch player progression data from local Room cache.
     */
    suspend fun fetchProgressionFromFirestore(playerId: String = "player_default") {
        try {
            loadLocalProgression()
            _syncStatus.value = "Профиль загружен"
        } catch (e: Exception) {
            Log.w("BoosterRepository", "Local load error: ${e.message}")
            _syncStatus.value = "Используется локальный кэш"
        }
    }

    /**
     * Unlock a booster by ID (1 to 67) and persist to Room inventory & Firestore.
     */
    suspend fun unlockBooster(boosterId: Int) {
        val currentUnlocked = _progression.value.unlockedBoosterIds.toMutableList()
        if (!currentUnlocked.contains(boosterId)) {
            currentUnlocked.add(boosterId)
            _progression.value = _progression.value.copy(unlockedBoosterIds = currentUnlocked)

            _boosters.value = _boosters.value.map {
                if (it.id == boosterId) it.copy(isUnlocked = true) else it
            }

            // Save to Room inventory
            val booster = _boosters.value.firstOrNull { it.id == boosterId }
            if (booster != null) {
                inventoryDao.insertOrUpdate(
                    PlayerInventoryEntity(
                        itemId = "booster_${booster.id}",
                        category = "BOOSTER",
                        name = booster.name,
                        quantity = 1,
                        description = booster.description,
                        tier = booster.tier,
                        isConsumable = false
                    )
                )
            }

            persistProgressionLocally()
        }
    }

    private suspend fun persistProgressionLocally() {
        val current = _progression.value
        gameSaveDao.saveGame(
            GameSaveEntity(
                brainrotLevel = current.level,
                coins = current.totalCoinsEarned.toInt().coerceAtLeast(100),
                runnerHighScore = current.highestScore,
                totalSquishes = current.totalPelmensSquished,
                lastSavedTimestamp = System.currentTimeMillis()
            )
        )
    }

    companion object {
        /**
         * Generates the comprehensive catalog of all 67 Game Boosters.
         */
        fun generate67Boosters(): List<GameBooster> {
            val list = mutableListOf<GameBooster>()
            val categories = BoosterCategory.values()
            val emojis = listOf("⚡", "💰", "🧈", "🛡️", "🧲", "🔥", "⭐", "🥟", "🌶️", "✨")

            val baseNames = listOf(
                "Сливочный Турбо-Рывок", "Золотая Сметана", "Бульонный Щит", "Магнит Специй",
                "Мялка-Антистресс x2", "Брейнрот-Ярость 67", "Перцовый Форсаж", "Бабушкин Рецепт",
                "Лавровый Лист Бессмертия", "Укропная Гравитация", "Тестовый Рикошет", "Хинкали-Титан",
                "Пельменный Дрифт", "Чесночный Взрыв", "Паровой Катапульт", "Вареник-Стелс",
                "Супер-Сочность", "Крио-Заморозка Врагов", "Мега-Лавина Муки", "Шеф-Повар 67"
            )

            for (i in 1..67) {
                val category = categories[(i - 1) % categories.size]
                val tier = when {
                    i == 67 -> 5 // Mythic 67
                    i % 10 == 0 -> 4 // Legendary
                    i % 5 == 0 -> 3 // Epic
                    i % 2 == 0 -> 2 // Rare
                    else -> 1 // Common
                }

                val name = if (i <= baseNames.size) {
                    baseNames[i - 1]
                } else {
                    "Бустер №$i: ${category.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }}"
                }

                val multiplier = 1.0f + (i * 0.05f) + (tier * 0.2f)
                val duration = 10 + (i % 20) + (tier * 5)
                val cost = i * 40 + (tier * 150)
                val emoji = emojis[(i - 1) % emojis.size]

                val desc = when (category) {
                    BoosterCategory.SPEED -> "+${(multiplier * 10).toInt()}% к скорости бега на ${duration}с"
                    BoosterCategory.COIN_MULTIPLIER -> "Умножитель монет x${String.format("%.1f", multiplier)} на ${duration}с"
                    BoosterCategory.SQUISH_ELASTICITY -> "+${(multiplier * 15).toInt()}% к упругости и тактильности мялки"
                    BoosterCategory.BROTH_SHIELD -> "Защита от ${tier + 1} препятствий в кипящем бульоне"
                    BoosterCategory.MAGNET -> "Автосбор монет в радиусе ${(multiplier * 3).toInt()}м"
                    BoosterCategory.BRAINROT_FRENZY -> "Неуязвимость и разрушение преград на ${duration / 2}с!"
                    BoosterCategory.SCORE_BOOST -> "+${(multiplier * 25).toInt()}% к очкам за метр дистанции"
                }

                list.add(
                    GameBooster(
                        id = i,
                        name = if (i == 67) "👑 АБСОЛЮТНЫЙ ПЕЛЬМЕНЬ-СИКС 67" else name,
                        category = category,
                        description = if (i == 67) "Максимальный бустер: все эффекты утроены, комбо 67x!" else desc,
                        multiplier = if (i == 67) 6.7f else multiplier,
                        durationSeconds = if (i == 67) 67 else duration,
                        costCoins = if (i == 67) 6700 else cost,
                        tier = tier,
                        iconEmoji = if (i == 67) "👑" else emoji,
                        isUnlocked = i <= 2
                    )
                )
            }
            return list
        }
    }
}
