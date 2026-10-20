package com.example.model

enum class CharacterRarity(val displayName: String, val badgeColorHex: Long) {
    COMMON("Обычный", 0xFF81C784),
    RARE("Редкий", 0xFF42A5F5),
    EPIC("Эпический", 0xFFAB47BC),
    LEGENDARY("Легендарный 67", 0xFFFFB300),
    SECRET("Секретный Космос", 0xFFFF4081)
}

enum class PelmenType {
    PELMEN_SIX,         // Главный герой Пельмень-Сикс
    COMMON_PELMEN,      // Обычный Пельмень
    GOLDEN_PELMEN,      // Золотой Пельмень
    PELMEN_67,          // Пельмень с жестом 67
    SLAP_PELMEN,        // Пельмень-пощёчина
    PELBABU,            // Пельбабу (Labubu стиль)
    KHINKALI,           // Хинкаль
    SIGMA,              // Пельмень-сигма
    GLASSES,            // Пельмень в очках
    CHUBBY,             // Толстый Пельмень
    BABY,               // Маленький Пельмешек
    CUTLET,             // Злой Котлета (прирученный)
    NUMBERS_67,         // Цифры 6 и 7 (парные)
    COSMIC,             // Космический Пельмень
    CRAZY_FROG          // Легендарный Crazy Frog Пельмень (Дин-динг-бем-бем!)
}

data class PelmenCharacter(
    val id: String,
    val name: String,
    val title: String,
    val description: String,
    val rarity: CharacterRarity,
    val type: PelmenType,
    val baseColorHex: Long,
    val accentColorHex: Long,
    val voicePitch: Float,
    val quote: String,
    val perkName: String,
    val perkDescription: String,
    val coinBonusPercent: Int = 0,
    val speedBonusPercent: Int = 0,
    val boostDurationSec: Float = 0f,
    val magnetRadiusBonus: Float = 0f
)

enum class BoxType(
    val title: String,
    val costCoins: Int,
    val description: String,
    val colorHex: Long,
    val tapsRequired: Int
) {
    COMMON_BOX("Обычная Коробка", 50, "Обычные и редкие пельмешки!", 0xFF81C784, 3),
    RARE_BOX("Редкая Коробка", 120, "Гарантия Редкого + шанс на Эпик!", 0xFF42A5F5, 4),
    GOLDEN_67_BOX("Золотая 67 Коробка", 250, "Эпические, Легендарные и Секретные!", 0xFFFFB300, 5)
}

enum class RunnerLocation(
    val title: String,
    val subtitle: String,
    val iconEmoji: String,
    val topColorHex: Long,
    val bottomColorHex: Long,
    val groundColorHex: Long
) {
    KITCHEN(
        title = "Бабушкина Кухня",
        subtitle = "Тёплая деревянная столешница с маслом",
        iconEmoji = "🍳",
        topColorHex = 0xFF81D4FA,
        bottomColorHex = 0xFFFFCC80,
        groundColorHex = 0xFFFFB74D
    ),
    SPACE(
        title = "Космический Бульон",
        subtitle = "Звёздная невесомость и кометы-пельмени",
        iconEmoji = "🌌",
        topColorHex = 0xFF1A237E,
        bottomColorHex = 0xFF4A148C,
        groundColorHex = 0xFF311B92
    ),
    OHIO(
        title = "Огайо 67",
        subtitle = "Абсурдный мир с летающими цифрами 67",
        iconEmoji = "🤪",
        topColorHex = 0xFFFF4081,
        bottomColorHex = 0xFFFFD54F,
        groundColorHex = 0xFFFF6D00
    )
}

data class GameState(
    val coins: Int = 150,
    val pelmenPoints: Int = 20,
    val brainrotScore: Int = 0,
    val brainrotLevel: Int = 1,
    val heroSpeedLevel: Int = 1,
    val heroJumpLevel: Int = 1,
    val heroBoostLevel: Int = 1,
    val heroMagnetLevel: Int = 1,
    val unlockedCharacterIds: Set<String> = setOf("pelmen_six", "common_pelmen"),
    val equippedCompanionIds: List<String> = listOf("common_pelmen"),
    val runnerHighScore: Int = 0,
    val slapHighScore: Int = 0,
    val soundEnabled: Boolean = true,
    val musicEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val selectedLocation: RunnerLocation = RunnerLocation.KITCHEN,
    val lastDailyRewardTimestamp: Long = 0L,
    val parentalLockEnabled: Boolean = false,
    val missionSquishCount: Int = 0,
    val missionOpenBoxesCount: Int = 0,
    val missionRunnerScore: Int = 0,
    val mission67BoosterCount: Int = 0,
    val missionSquishClaimed: Boolean = false,
    val missionBoxesClaimed: Boolean = false,
    val missionRunnerClaimed: Boolean = false,
    val missionBoosterClaimed: Boolean = false,
    val unlockedAccessoryIds: Set<String> = setOf("hat_none", "hat_party", "glasses_none", "glasses_cool", "body_none", "body_bow"),
    val equippedHatId: String = "hat_party",
    val equippedGlassesId: String = "glasses_cool",
    val equippedBodyId: String = "body_bow",
    val palmRejectionEnabled: Boolean = true,
    val palmRejectionMarginDp: Float = 22f,
    val totalSquishCount: Int = 0,
    val totalFeedCount: Int = 0,
    val totalPlayTimeSeconds: Long = 0L
) {
    val totalHeroLevel: Int
        get() = heroSpeedLevel + heroJumpLevel + heroBoostLevel + heroMagnetLevel

    fun isDailyRewardReady(): Boolean {
        val now = System.currentTimeMillis()
        // Available if at least 12 hours passed or never claimed
        return (now - lastDailyRewardTimestamp) > 12 * 60 * 60 * 1000L
    }

    fun millisUntilNextDailyReward(): Long {
        val now = System.currentTimeMillis()
        val cooldown = 12 * 60 * 60 * 1000L
        val diff = (lastDailyRewardTimestamp + cooldown) - now
        return diff.coerceAtLeast(0L)
    }
}

data class DailyMissionItem(
    val id: String,
    val title: String,
    val emoji: String,
    val currentProgress: Int,
    val maxProgress: Int,
    val rewardCoins: Int,
    val isClaimed: Boolean
) {
    val isCompleted: Boolean
        get() = currentProgress >= maxProgress
}

