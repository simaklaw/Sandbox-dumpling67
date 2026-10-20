package com.example.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Local Room entity for unlocked 'Pelmen-Six' skins (supporting full offline caching).
 */
@Entity(tableName = "unlocked_pelmen_skins")
data class UnlockedSkinEntity(
    @PrimaryKey
    val skinId: String,                  // E.g. "pelmen_skin_01" .. "pelmen_skin_67"
    val skinName: String,                // Name of the skin variant
    val variantNumber: Int,              // 1 to 67
    val rarity: String,                  // Common, Rare, Epic, Legendary, Mythic 67
    val flavorBonusMultiplier: Float = 1.0f,
    val speedBonusMultiplier: Float = 1.0f,
    val isEquipped: Boolean = false,
    val isCachedOffline: Boolean = true,
    val unlockedTimestamp: Long = System.currentTimeMillis(),
    val upgradeLevel: Int = 1,
    val timesSquished: Int = 0,
    val iconEmoji: String = "🥟",
    val primaryColorHex: Long = 0xFFFFD54F,
    val secondaryColorHex: Long = 0xFFFFA000,
    val specialPerk: String = ""
)

@Dao
interface UnlockedSkinDao {

    @Query("SELECT * FROM unlocked_pelmen_skins ORDER BY variantNumber ASC")
    fun getAllUnlockedSkins(): Flow<List<UnlockedSkinEntity>>

    @Query("SELECT * FROM unlocked_pelmen_skins WHERE isEquipped = 1 LIMIT 1")
    fun getEquippedSkin(): Flow<UnlockedSkinEntity?>

    @Query("SELECT * FROM unlocked_pelmen_skins WHERE skinId = :id LIMIT 1")
    suspend fun getSkinById(id: String): UnlockedSkinEntity?

    @Query("SELECT COUNT(*) FROM unlocked_pelmen_skins")
    fun getUnlockedSkinCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(skin: UnlockedSkinEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(skins: List<UnlockedSkinEntity>)

    @Query("UPDATE unlocked_pelmen_skins SET isEquipped = CASE WHEN skinId = :skinId THEN 1 ELSE 0 END")
    suspend fun equipSkin(skinId: String)

    @Query("UPDATE unlocked_pelmen_skins SET timesSquished = timesSquished + 1 WHERE skinId = :skinId")
    suspend fun incrementSquish(skinId: String)

    @Query("UPDATE unlocked_pelmen_skins SET upgradeLevel = upgradeLevel + 1 WHERE skinId = :skinId")
    suspend fun upgradeSkin(skinId: String)
}
