package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.data.local.AppDatabase
import com.example.data.local.BlindBoxRewardDao
import com.example.data.local.BlindBoxRewardEntity
import com.example.data.local.GameSaveDao
import com.example.data.local.GameSaveEntity
import com.example.data.local.PelmenVariantDao
import com.example.data.local.PelmenVariantEntity
import com.example.data.local.PlayerInventoryDao
import com.example.data.local.PlayerInventoryEntity
import com.example.data.local.RunnerHighScoreEntity
import com.example.data.local.StickerDao
import com.example.data.local.StickerEntity
import com.example.data.local.UnlockedSkinDao
import com.example.data.local.UnlockedSkinEntity
import com.example.model.BoxType
import com.example.model.CharacterRarity
import com.example.model.GameState
import com.example.model.PelmenCharacter
import com.example.model.RunnerLocation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

class GameRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("pelmeshki_67_prefs", Context.MODE_PRIVATE)
    private val database = AppDatabase.getInstance(context)
    private val dao = database.runnerHighScoreDao()
    private val blindBoxDao: BlindBoxRewardDao = database.blindBoxRewardDao()
    private val pelmenVariantDao: PelmenVariantDao = database.pelmenVariantDao()
    val gameSaveDao: GameSaveDao = database.gameSaveDao()
    val unlockedSkinDao: UnlockedSkinDao = database.unlockedSkinDao()
    val playerInventoryDao: PlayerInventoryDao = database.playerInventoryDao()
    val stickerDao: StickerDao = database.stickerDao()
    private val ioScope = CoroutineScope(Dispatchers.IO)

    val topRunnerHighScores: Flow<List<RunnerHighScoreEntity>> = dao.getTopScores()
    val allBlindBoxRewards: Flow<List<BlindBoxRewardEntity>> = blindBoxDao.getAllRewards()
    val unlockedBlindBoxItemIds: Flow<List<Int>> = blindBoxDao.getDistinctUnlockedItemIds()
    val distinctBlindBoxCount: Flow<Int> = blindBoxDao.getDistinctCount()
    val unlockedPelmenVariants: Flow<List<PelmenVariantEntity>> = pelmenVariantDao.getAllVariants()
    val unlockedPelmenCount: Flow<Int> = pelmenVariantDao.getUnlockedCount()
    val localGameSave: Flow<GameSaveEntity?> = gameSaveDao.getSaveFlow()
    val offlineUnlockedSkins: Flow<List<UnlockedSkinEntity>> = unlockedSkinDao.getAllUnlockedSkins()
    val playerInventory: Flow<List<PlayerInventoryEntity>> = playerInventoryDao.getAllInventory()
    val allStickers: Flow<List<StickerEntity>> = stickerDao.getAllStickers()
    val unlockedStickers: Flow<List<StickerEntity>> = stickerDao.getUnlockedStickers()
    val unlockedStickerCount: Flow<Int> = stickerDao.getUnlockedCount()

    init {
        ioScope.launch {
            try {
                val starter = pelmenVariantDao.getVariantSync("pelmen_six")
                if (starter == null) {
                    pelmenVariantDao.insertOrUpdate(
                        PelmenVariantEntity(
                            variantId = "pelmen_six",
                            displayName = "Пельмень-Сикс (Оригинал)",
                            rarity = "Мифический 67",
                            description = "Легендарный герой с числом 67 на пузике. Король кухни и владыка брейнрота.",
                            quote = "Сикс-Севен! Беги и дави!",
                            emojiIcon = "🥟",
                            primaryColorHex = 0xFFFFD54F,
                            secondaryColorHex = 0xFFFFA000,
                            unlockedAt = System.currentTimeMillis(),
                            timesOpened = 1,
                            isEquipped = true,
                            isFavorite = true,
                            coinBonusPct = 10,
                            speedBonusPct = 10,
                            specialAbility = "Супер давка и множитель очков x2"
                        )
                    )
                }

                // Ensure offline skin cache has starter skin
                val starterSkin = unlockedSkinDao.getSkinById("pelmen_skin_01")
                if (starterSkin == null) {
                    unlockedSkinDao.insertOrUpdate(
                        UnlockedSkinEntity(
                            skinId = "pelmen_skin_01",
                            skinName = "Пельмень-Сикс (Оригинал)",
                            variantNumber = 1,
                            rarity = "Мифический 67",
                            flavorBonusMultiplier = 2.0f,
                            speedBonusMultiplier = 1.2f,
                            isEquipped = true,
                            isCachedOffline = true,
                            iconEmoji = "🥟",
                            primaryColorHex = 0xFFFFD54F,
                            secondaryColorHex = 0xFFFFA000,
                            specialPerk = "Супер давка и комбо-множитель x2"
                        )
                    )
                }

                // Ensure local save persistence is initialized
                val currentSave = gameSaveDao.getSaveSync()
                if (currentSave == null) {
                    gameSaveDao.saveGame(
                        GameSaveEntity(
                            saveSlotId = "active_slot",
                            coins = 150,
                            pelmenPoints = 20,
                            brainrotLevel = 1,
                            equippedSkinId = "pelmen_skin_01"
                        )
                    )
                }

                // Ensure stickers are seeded
                seedDefaultStickers()
            } catch (e: Exception) {
                android.util.Log.e("GameRepository", "Init db seeding error: ${e.message}", e)
            }
        }
    }

    suspend fun seedDefaultStickers() {
        if (stickerDao.getStickerCount() == 0) {
            stickerDao.insertDefaultStickers(
                listOf(
                    StickerEntity("stk_first_step", "Первый шажок", "Пробеги первые 50 метров", "🥟", "Кухня 67", 50),
                    StickerEntity("stk_crispy_toast", "Хрустящий тостик", "Покори дистанцию 100 метров", "🍞", "Кухня 67", 100),
                    StickerEntity("stk_ice_crystal", "Ледяная снежинка", "Доберись до Морозного Холодильника (160м)", "❄️", "Морозный Холодильник", 160),
                    StickerEntity("stk_polar_bear", "Мишка-Пингвин", "Преодолей ледяную дорожку (220м)", "🐧", "Морозный Холодильник", 220),
                    StickerEntity("stk_glacier_slide", "Скользун по льду", "Удержи равновесие на льду (280м)", "⛸️", "Морозный Холодильник", 280),
                    StickerEntity("stk_ice_cream", "Пломбирчик", "Добеги до конца ледяного мира (340м)", "🍦", "Морозный Холодильник", 340),
                    StickerEntity("stk_cloud_walker", "Облачный странник", "Взлети в Чайные Облака (400м)", "☁️", "Чайная Гора", 400),
                    StickerEntity("stk_steamy_kettle", "Чайник-Паровичок", "Взлети на струе теплого пара (460м)", "🫖", "Чайная Гора", 460),
                    StickerEntity("stk_rainbow_jelly", "Мармеладное желе", "Соверши супер-прыжок на желе (520м)", "🍮", "Чайная Гора", 520),
                    StickerEntity("stk_golden_six", "Золотой Пельмень 67", "Легендарный забег на 600 метров!", "🏆", "Чайная Гора", 600),
                    StickerEntity("stk_shield_master", "Мастер Пузыря", "Пройди сквозь все преграды (700м)", "🛡️", "Чайная Гора", 700),
                    StickerEntity("stk_cosmic_pelmen", "Космический Пельмешек", "Великий чемпион кухни и вселенной (800м)!", "🚀", "Чайная Гора", 800)
                )
            )
        }
    }

    private val _gameState = MutableStateFlow(loadState())
    val gameState: StateFlow<GameState> = _gameState.asStateFlow()

    private fun loadState(): GameState {
        val coins = prefs.getInt("coins", 150)
        val pelmenPoints = prefs.getInt("pelmen_points", 20)
        val brainrotScore = prefs.getInt("brainrot_score", 0)
        val brainrotLevel = prefs.getInt("brainrot_level", 1)
        val heroSpeed = prefs.getInt("hero_speed", 1)
        val heroJump = prefs.getInt("hero_jump", 1)
        val heroBoost = prefs.getInt("hero_boost", 1)
        val heroMagnet = prefs.getInt("hero_magnet", 1)
        val unlocked = prefs.getStringSet("unlocked_ids", setOf("pelmen_six", "common_pelmen")) ?: setOf("pelmen_six", "common_pelmen")
        val equipped = prefs.getString("equipped_ids", "common_pelmen")?.split(",")?.filter { it.isNotBlank() } ?: listOf("common_pelmen")
        val runnerHigh = prefs.getInt("runner_high", 0)
        val slapHigh = prefs.getInt("slap_high", 0)
        val sound = prefs.getBoolean("sound_enabled", true)
        val music = prefs.getBoolean("music_enabled", true)
        val haptics = prefs.getBoolean("haptics_enabled", true)
        val locName = prefs.getString("selected_loc", RunnerLocation.KITCHEN.name) ?: RunnerLocation.KITCHEN.name
        val location = try { RunnerLocation.valueOf(locName) } catch (_: Exception) { RunnerLocation.KITCHEN }
        val dailyRewardTime = prefs.getLong("daily_reward_time", 0L)
        val parentalLock = prefs.getBoolean("parental_lock", false)
        val missionSquish = prefs.getInt("m_squish", 0)
        val missionBoxes = prefs.getInt("m_boxes", 0)
        val missionRunner = prefs.getInt("m_runner", 0)
        val missionBooster = prefs.getInt("m_booster", 0)
        val claimSquish = prefs.getBoolean("m_claim_squish", false)
        val claimBoxes = prefs.getBoolean("m_claim_boxes", false)
        val claimRunner = prefs.getBoolean("m_claim_runner", false)
        val claimBooster = prefs.getBoolean("m_claim_booster", false)

        val defaultUnlockedAcc = setOf("hat_none", "hat_party", "glasses_none", "glasses_cool", "body_none", "body_bow")
        val unlockedAcc = prefs.getStringSet("unlocked_acc_ids", defaultUnlockedAcc) ?: defaultUnlockedAcc
        val equippedHat = prefs.getString("eq_hat", "hat_party") ?: "hat_party"
        val equippedGlasses = prefs.getString("eq_glasses", "glasses_cool") ?: "glasses_cool"
        val equippedBody = prefs.getString("eq_body", "body_bow") ?: "body_bow"
        val palmRejection = prefs.getBoolean("palm_rejection_enabled", true)
        val palmMargin = prefs.getFloat("palm_rejection_margin", 22f)
        val totalSquish = prefs.getInt("total_squish", 0)
        val totalFeed = prefs.getInt("total_feed", 0)
        val totalPlayTime = prefs.getLong("total_play_time", 120L)

        val loadedState = GameState(
            coins = coins,
            pelmenPoints = pelmenPoints,
            brainrotScore = brainrotScore,
            brainrotLevel = brainrotLevel,
            heroSpeedLevel = heroSpeed,
            heroJumpLevel = heroJump,
            heroBoostLevel = heroBoost,
            heroMagnetLevel = heroMagnet,
            unlockedCharacterIds = unlocked,
            equippedCompanionIds = equipped,
            runnerHighScore = runnerHigh,
            slapHighScore = slapHigh,
            soundEnabled = sound,
            musicEnabled = music,
            hapticsEnabled = haptics,
            selectedLocation = location,
            lastDailyRewardTimestamp = dailyRewardTime,
            parentalLockEnabled = parentalLock,
            missionSquishCount = missionSquish,
            missionOpenBoxesCount = missionBoxes,
            missionRunnerScore = missionRunner,
            mission67BoosterCount = missionBooster,
            missionSquishClaimed = claimSquish,
            missionBoxesClaimed = claimBoxes,
            missionRunnerClaimed = claimRunner,
            missionBoosterClaimed = claimBooster,
            unlockedAccessoryIds = unlockedAcc,
            equippedHatId = equippedHat,
            equippedGlassesId = equippedGlasses,
            equippedBodyId = equippedBody,
            palmRejectionEnabled = palmRejection,
            palmRejectionMarginDp = palmMargin,
            totalSquishCount = totalSquish,
            totalFeedCount = totalFeed,
            totalPlayTimeSeconds = totalPlayTime
        )
        return checkAndUnlockAccomplishments(loadedState)
    }

    private fun persist(state: GameState) {
        val checkedState = checkAndUnlockAccomplishments(state)
        _gameState.value = checkedState
        prefs.edit().apply {
            putInt("coins", checkedState.coins)
            putInt("pelmen_points", checkedState.pelmenPoints)
            putInt("brainrot_score", checkedState.brainrotScore)
            putInt("brainrot_level", checkedState.brainrotLevel)
            putInt("hero_speed", checkedState.heroSpeedLevel)
            putInt("hero_jump", checkedState.heroJumpLevel)
            putInt("hero_boost", checkedState.heroBoostLevel)
            putInt("hero_magnet", checkedState.heroMagnetLevel)
            putStringSet("unlocked_ids", checkedState.unlockedCharacterIds)
            putString("equipped_ids", checkedState.equippedCompanionIds.joinToString(","))
            putInt("runner_high", checkedState.runnerHighScore)
            putInt("slap_high", checkedState.slapHighScore)
            putBoolean("sound_enabled", checkedState.soundEnabled)
            putBoolean("music_enabled", checkedState.musicEnabled)
            putBoolean("haptics_enabled", checkedState.hapticsEnabled)
            putString("selected_loc", checkedState.selectedLocation.name)
            putLong("daily_reward_time", checkedState.lastDailyRewardTimestamp)
            putBoolean("parental_lock", checkedState.parentalLockEnabled)
            putInt("m_squish", checkedState.missionSquishCount)
            putInt("m_boxes", checkedState.missionOpenBoxesCount)
            putInt("m_runner", checkedState.missionRunnerScore)
            putInt("m_booster", checkedState.mission67BoosterCount)
            putBoolean("m_claim_squish", checkedState.missionSquishClaimed)
            putBoolean("m_claim_boxes", checkedState.missionBoxesClaimed)
            putBoolean("m_claim_runner", checkedState.missionRunnerClaimed)
            putBoolean("m_claim_booster", checkedState.missionBoosterClaimed)
            putStringSet("unlocked_acc_ids", checkedState.unlockedAccessoryIds)
            putString("eq_hat", checkedState.equippedHatId)
            putString("eq_glasses", checkedState.equippedGlassesId)
            putString("eq_body", checkedState.equippedBodyId)
            putBoolean("palm_rejection_enabled", checkedState.palmRejectionEnabled)
            putFloat("palm_rejection_margin", checkedState.palmRejectionMarginDp)
            putInt("total_squish", checkedState.totalSquishCount)
            putInt("total_feed", checkedState.totalFeedCount)
            putLong("total_play_time", checkedState.totalPlayTimeSeconds)
            apply()
        }
    }

    fun addPlayTimeSeconds(seconds: Long) {
        val current = _gameState.value
        persist(current.copy(totalPlayTimeSeconds = current.totalPlayTimeSeconds + seconds))
    }

    fun checkAndUnlockAccomplishments(state: GameState): GameState {
        val newUnlocked = state.unlockedAccessoryIds.toMutableSet()

        if (state.totalFeedCount >= 3 || state.coins >= 30) newUnlocked.add("hat_chef")
        if (state.coins >= 150) newUnlocked.add("hat_crown")
        if (state.missionOpenBoxesCount >= 1) newUnlocked.add("hat_tophat")
        if (state.totalSquishCount >= 10) newUnlocked.add("hat_viking")
        if (state.lastDailyRewardTimestamp > 0) newUnlocked.add("hat_santa")

        if (state.totalSquishCount >= 5) newUnlocked.add("glasses_star")
        if (state.coins >= 80) newUnlocked.add("glasses_smart")
        if (state.missionOpenBoxesCount >= 2) newUnlocked.add("glasses_3d")
        if (state.totalFeedCount >= 1) newUnlocked.add("glasses_heart")

        if (state.coins >= 200) newUnlocked.add("body_medal")
        if (state.lastDailyRewardTimestamp > 0) newUnlocked.add("body_scarf")
        if (state.missionOpenBoxesCount >= 3) newUnlocked.add("body_cape")

        return if (newUnlocked.size != state.unlockedAccessoryIds.size) {
            state.copy(unlockedAccessoryIds = newUnlocked)
        } else {
            state
        }
    }

    fun recordSquishTap() {
        val current = _gameState.value
        val updated = current.copy(totalSquishCount = current.totalSquishCount + 1)
        persist(updated)
    }

    fun recordAnimalFeed() {
        val current = _gameState.value
        val updated = current.copy(totalFeedCount = current.totalFeedCount + 1)
        persist(updated)
    }

    fun equipAccessory(category: com.example.model.AccessoryCategory, accessoryId: String) {
        val current = _gameState.value
        if (!current.unlockedAccessoryIds.contains(accessoryId)) return
        val updated = when (category) {
            com.example.model.AccessoryCategory.HAT -> current.copy(equippedHatId = accessoryId)
            com.example.model.AccessoryCategory.GLASSES -> current.copy(equippedGlassesId = accessoryId)
            com.example.model.AccessoryCategory.BODY -> current.copy(equippedBodyId = accessoryId)
        }
        persist(updated)
    }

    fun buyAccessoryWithCoins(accessoryId: String, price: Int): Boolean {
        val current = _gameState.value
        if (current.coins < price) return false
        val newUnlocked = current.unlockedAccessoryIds.toMutableSet()
        newUnlocked.add(accessoryId)
        persist(current.copy(coins = current.coins - price, unlockedAccessoryIds = newUnlocked))
        return true
    }

    fun addCoins(amount: Int) {
        val current = _gameState.value
        val newCoins = (current.coins + amount).coerceAtLeast(0)
        persist(current.copy(coins = newCoins))
    }

    fun addBrainrot(points: Int) {
        val current = _gameState.value
        val newScore = current.brainrotScore + points
        val newLevel = (newScore / 200) + 1
        persist(current.copy(brainrotScore = newScore, brainrotLevel = newLevel))
    }

    fun addPelmenPoints(amount: Int) {
        val current = _gameState.value
        persist(current.copy(pelmenPoints = (current.pelmenPoints + amount).coerceAtLeast(0)))
    }

    fun setLocation(location: RunnerLocation) {
        val current = _gameState.value
        persist(current.copy(selectedLocation = location))
    }

    fun claimDailyReward(rewardChoice: String = "box"): Boolean {
        val current = _gameState.value
        if (!current.isDailyRewardReady()) return false
        val now = System.currentTimeMillis()
        if (rewardChoice == "coins") {
            persist(
                current.copy(
                    coins = current.coins + 200,
                    brainrotScore = current.brainrotScore + 50,
                    lastDailyRewardTimestamp = now
                )
            )
        } else {
            // Free Loot Box reward: +100 coins + 50 brainrot + 1 free box unlock
            persist(
                current.copy(
                    coins = current.coins + 100,
                    brainrotScore = current.brainrotScore + 50,
                    lastDailyRewardTimestamp = now
                )
            )
        }
        return true
    }

    fun updateRunnerHighScore(score: Int) {
        val current = _gameState.value
        if (score > current.runnerHighScore) {
            persist(current.copy(runnerHighScore = score))
        }
    }

    fun saveRunnerScoreResult(
        distance: Int,
        coins: Int,
        dumplings: Int,
        enemiesSmashed: Int,
        locationName: String
    ) {
        updateRunnerHighScore(distance)
        ioScope.launch {
            dao.insertScore(
                RunnerHighScoreEntity(
                    distance = distance,
                    coins = coins,
                    dumplings = dumplings,
                    enemiesSmashed = enemiesSmashed,
                    locationName = locationName
                )
            )
        }
    }

    fun updateSlapHighScore(score: Int) {
        val current = _gameState.value
        if (score > current.slapHighScore) {
            persist(current.copy(slapHighScore = score))
        }
    }

    fun toggleEquippedCompanion(characterId: String) {
        val current = _gameState.value
        if (!current.unlockedCharacterIds.contains(characterId)) return
        val currentList = current.equippedCompanionIds.toMutableList()
        if (currentList.contains(characterId)) {
            currentList.remove(characterId)
        } else {
            // Max 3 companion slots
            if (currentList.size < 3) {
                currentList.add(characterId)
            } else {
                currentList[0] = characterId
            }
        }
        persist(current.copy(equippedCompanionIds = currentList))
    }

    fun upgradeHeroStat(stat: String): Boolean {
        val current = _gameState.value
        val cost = when (stat) {
            "speed" -> current.heroSpeedLevel * 40
            "jump" -> current.heroJumpLevel * 40
            "boost" -> current.heroBoostLevel * 50
            "magnet" -> current.heroMagnetLevel * 50
            else -> 999
        }

        if (current.coins < cost) return false

        val updated = when (stat) {
            "speed" -> current.copy(coins = current.coins - cost, heroSpeedLevel = current.heroSpeedLevel + 1)
            "jump" -> current.copy(coins = current.coins - cost, heroJumpLevel = current.heroJumpLevel + 1)
            "boost" -> current.copy(coins = current.coins - cost, heroBoostLevel = current.heroBoostLevel + 1)
            "magnet" -> current.copy(coins = current.coins - cost, heroMagnetLevel = current.heroMagnetLevel + 1)
            else -> current
        }
        persist(updated)
        return true
    }

    data class BoxOpeningResult(
        val character: PelmenCharacter,
        val blindBoxItem: BlindBoxItem,
        val isDuplicate: Boolean,
        val bonusPelmenPoints: Int,
        val bonusCoins: Int
    )

    fun openBox(boxType: BoxType): BoxOpeningResult? {
        val current = _gameState.value
        if (current.coins < boxType.costCoins) return null

        val newCoins = current.coins - boxType.costCoins

        // 1. Pick from the 67 Collectible Items Catalog
        val candidateItems = when (boxType) {
            BoxType.COMMON_BOX -> {
                val roll = Random.nextInt(100)
                when {
                    roll < 70 -> BlindBox67Catalog.ITEMS.filter { it.rarity == CharacterRarity.COMMON }
                    roll < 95 -> BlindBox67Catalog.ITEMS.filter { it.rarity == CharacterRarity.RARE }
                    else -> BlindBox67Catalog.ITEMS.filter { it.rarity == CharacterRarity.EPIC }
                }
            }
            BoxType.RARE_BOX -> {
                val roll = Random.nextInt(100)
                when {
                    roll < 50 -> BlindBox67Catalog.ITEMS.filter { it.rarity == CharacterRarity.RARE }
                    roll < 85 -> BlindBox67Catalog.ITEMS.filter { it.rarity == CharacterRarity.EPIC }
                    else -> BlindBox67Catalog.ITEMS.filter { it.rarity == CharacterRarity.LEGENDARY }
                }
            }
            BoxType.GOLDEN_67_BOX -> {
                val roll = Random.nextInt(100)
                when {
                    roll < 35 -> BlindBox67Catalog.ITEMS.filter { it.rarity == CharacterRarity.EPIC }
                    roll < 80 -> BlindBox67Catalog.ITEMS.filter { it.rarity == CharacterRarity.LEGENDARY }
                    else -> BlindBox67Catalog.ITEMS.filter { it.rarity == CharacterRarity.SECRET }
                }
            }
        }.ifEmpty { BlindBox67Catalog.ITEMS }

        val chosenItem = candidateItems.random()

        // 2. Save result to Room Local Database
        val rewardEntity = BlindBoxRewardEntity(
            itemId = chosenItem.id,
            name = chosenItem.name,
            category = chosenItem.category,
            rarity = chosenItem.rarity.displayName,
            description = chosenItem.description,
            quote = chosenItem.quote,
            iconEmoji = chosenItem.iconEmoji,
            colorHex = chosenItem.colorHex,
            statBonus = chosenItem.statBonus,
            obtainedTimestamp = System.currentTimeMillis(),
            boxTypeOpened = boxType.title
        )
        ioScope.launch {
            blindBoxDao.insertReward(rewardEntity)
        }

        // 3. Corresponding Companion/Character selection
        val charId = when (chosenItem.id) {
            1 -> "pelmen_six"
            2 -> "golden_pelmen"
            3 -> "pelbabu"
            4 -> "khinkali"
            5 -> "sigma"
            6 -> "glasses"
            7 -> "chubby"
            8 -> "baby"
            9 -> "cutlet"
            10 -> "numbers_67"
            11 -> "cosmic"
            12 -> "crazy_frog"
            else -> GameCharacters.ALL_CHARACTERS[chosenItem.id % GameCharacters.ALL_CHARACTERS.size].id
        }
        val matchingCharacter = GameCharacters.getById(charId)

        val isDuplicate = current.unlockedCharacterIds.contains(matchingCharacter.id)

        // Save / Update Pelmen Variant in Room Database
        ioScope.launch {
            val existing = pelmenVariantDao.getVariantSync(matchingCharacter.id)
            if (existing != null) {
                pelmenVariantDao.incrementTimesOpened(matchingCharacter.id, System.currentTimeMillis())
            } else {
                pelmenVariantDao.insertOrUpdate(
                    PelmenVariantEntity(
                        variantId = matchingCharacter.id,
                        displayName = matchingCharacter.name,
                        rarity = chosenItem.rarity.displayName,
                        description = matchingCharacter.description,
                        quote = matchingCharacter.quote,
                        emojiIcon = "🥟",
                        primaryColorHex = matchingCharacter.baseColorHex,
                        secondaryColorHex = matchingCharacter.accentColorHex,
                        unlockedAt = System.currentTimeMillis(),
                        timesOpened = 1,
                        isEquipped = false,
                        isFavorite = false,
                        coinBonusPct = matchingCharacter.coinBonusPercent,
                        speedBonusPct = matchingCharacter.speedBonusPercent,
                        specialAbility = chosenItem.statBonus
                    )
                )
            }
        }

        val bonusPoints = if (isDuplicate) {
            when (chosenItem.rarity) {
                CharacterRarity.COMMON -> 15
                CharacterRarity.RARE -> 30
                CharacterRarity.EPIC -> 60
                CharacterRarity.LEGENDARY -> 120
                CharacterRarity.SECRET -> 250
            }
        } else 0
        val bonusCoins = if (isDuplicate) 30 else 0

        val newUnlocked = current.unlockedCharacterIds + matchingCharacter.id
        val updatedState = current.copy(
            coins = newCoins + bonusCoins,
            pelmenPoints = current.pelmenPoints + bonusPoints + 5,
            brainrotScore = current.brainrotScore + 50,
            unlockedCharacterIds = newUnlocked,
            missionOpenBoxesCount = current.missionOpenBoxesCount + 1
        )
        persist(updatedState)

        return BoxOpeningResult(
            character = matchingCharacter,
            blindBoxItem = chosenItem,
            isDuplicate = isDuplicate,
            bonusPelmenPoints = bonusPoints,
            bonusCoins = bonusCoins
        )
    }

    fun incrementSquishMission() {
        val current = _gameState.value
        persist(current.copy(missionSquishCount = current.missionSquishCount + 1))
    }

    fun incrementBoosterMission() {
        val current = _gameState.value
        persist(current.copy(mission67BoosterCount = current.mission67BoosterCount + 1))
    }

    fun recordRunnerMissionProgress(enemiesSmashed: Int) {
        val current = _gameState.value
        persist(current.copy(missionRunnerScore = current.missionRunnerScore + enemiesSmashed))
    }

    fun claimMissionReward(missionId: String): Boolean {
        val current = _gameState.value
        when (missionId) {
            "squish_10" -> {
                if (current.missionSquishCount >= 10 && !current.missionSquishClaimed) {
                    persist(current.copy(coins = current.coins + 120, brainrotScore = current.brainrotScore + 40, missionSquishClaimed = true))
                    return true
                }
            }
            "open_2_boxes" -> {
                if (current.missionOpenBoxesCount >= 2 && !current.missionBoxesClaimed) {
                    persist(current.copy(coins = current.coins + 150, brainrotScore = current.brainrotScore + 60, missionBoxesClaimed = true))
                    return true
                }
            }
            "smash_5_enemies" -> {
                if (current.missionRunnerScore >= 5 && !current.missionRunnerClaimed) {
                    persist(current.copy(coins = current.coins + 140, brainrotScore = current.brainrotScore + 50, missionRunnerClaimed = true))
                    return true
                }
            }
            "booster_3_times" -> {
                if (current.mission67BoosterCount >= 3 && !current.missionBoosterClaimed) {
                    persist(current.copy(coins = current.coins + 167, brainrotScore = current.brainrotScore + 67, missionBoosterClaimed = true))
                    return true
                }
            }
        }
        return false
    }

    fun getDailyMissions(): List<com.example.model.DailyMissionItem> {
        val current = _gameState.value
        return listOf(
            com.example.model.DailyMissionItem(
                id = "squish_10",
                title = "Помять 10 пельмешек",
                emoji = "🥟",
                currentProgress = current.missionSquishCount,
                maxProgress = 10,
                rewardCoins = 120,
                isClaimed = current.missionSquishClaimed
            ),
            com.example.model.DailyMissionItem(
                id = "open_2_boxes",
                title = "Открыть 2 блайнд-бокса",
                emoji = "📦",
                currentProgress = current.missionOpenBoxesCount,
                maxProgress = 2,
                rewardCoins = 150,
                isClaimed = current.missionBoxesClaimed
            ),
            com.example.model.DailyMissionItem(
                id = "smash_5_enemies",
                title = "Раздавить 5 врагов в раннере",
                emoji = "💥",
                currentProgress = current.missionRunnerScore,
                maxProgress = 5,
                rewardCoins = 140,
                isClaimed = current.missionRunnerClaimed
            ),
            com.example.model.DailyMissionItem(
                id = "booster_3_times",
                title = "Запустить буст «67» 3 раза",
                emoji = "⚡",
                currentProgress = current.mission67BoosterCount,
                maxProgress = 3,
                rewardCoins = 167,
                isClaimed = current.missionBoosterClaimed
            )
        )
    }

    fun toggleSound(enabled: Boolean) {
        val current = _gameState.value
        persist(current.copy(soundEnabled = enabled))
    }

    fun toggleMusic(enabled: Boolean) {
        val current = _gameState.value
        persist(current.copy(musicEnabled = enabled))
    }

    fun toggleHaptics(enabled: Boolean) {
        val current = _gameState.value
        persist(current.copy(hapticsEnabled = enabled))
    }

    fun setParentalLock(enabled: Boolean) {
        val current = _gameState.value
        persist(current.copy(parentalLockEnabled = enabled))
    }

    fun togglePalmRejection(enabled: Boolean) {
        val current = _gameState.value
        persist(current.copy(palmRejectionEnabled = enabled))
    }

    fun setPalmRejectionMargin(marginDp: Float) {
        val current = _gameState.value
        persist(current.copy(palmRejectionMarginDp = marginDp.coerceIn(12f, 48f)))
    }

    fun toggleFavoritePelmenVariant(variantId: String) {
        ioScope.launch {
            pelmenVariantDao.toggleFavorite(variantId)
        }
    }

    fun setEquippedPelmenVariant(variantId: String) {
        ioScope.launch {
            pelmenVariantDao.unequipAll()
            pelmenVariantDao.setEquipped(variantId, true)
        }
        toggleEquippedCompanion(variantId)
    }

    fun recordPelmenVariantSquish(variantId: String) {
        ioScope.launch {
            pelmenVariantDao.incrementSquishCount(variantId)
        }
        incrementSquishMission()
    }

    fun togglePelmenVariantFavorite(variantId: String) {
        ioScope.launch {
            pelmenVariantDao.toggleFavorite(variantId)
        }
    }

    fun unlockSticker(stickerId: String) {
        ioScope.launch {
            stickerDao.unlockSticker(stickerId, System.currentTimeMillis())
        }
    }

    suspend fun checkAndUnlockDistanceStickers(distanceMeters: Int): List<StickerEntity> {
        if (stickerDao.getStickerCount() == 0) {
            seedDefaultStickers()
        }
        val newlyUnlocked = mutableListOf<StickerEntity>()
        val all = stickerDao.getAllStickersList()
        val now = System.currentTimeMillis()
        for (stk in all) {
            if (!stk.isUnlocked && distanceMeters >= stk.requiredDistance) {
                stickerDao.unlockSticker(stk.id, now)
                newlyUnlocked.add(stk.copy(isUnlocked = true, unlockedTimestamp = now))
            }
        }
        return newlyUnlocked
    }

    fun resetProgress() {
        persist(
            GameState(
                coins = 150,
                pelmenPoints = 20,
                brainrotScore = 0,
                brainrotLevel = 1,
                heroSpeedLevel = 1,
                heroJumpLevel = 1,
                heroBoostLevel = 1,
                heroMagnetLevel = 1,
                unlockedCharacterIds = setOf("pelmen_six", "common_pelmen"),
                equippedCompanionIds = listOf("common_pelmen"),
                runnerHighScore = 0,
                slapHighScore = 0,
                selectedLocation = RunnerLocation.KITCHEN
            )
        )
    }
}
