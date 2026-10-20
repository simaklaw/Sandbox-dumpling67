package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PelmenVariantDao {

    @Query("SELECT * FROM pelmen_variants ORDER BY unlockedAt DESC")
    fun getAllVariants(): Flow<List<PelmenVariantEntity>>

    @Query("SELECT * FROM pelmen_variants WHERE variantId = :id LIMIT 1")
    fun getVariantById(id: String): Flow<PelmenVariantEntity?>

    @Query("SELECT * FROM pelmen_variants WHERE variantId = :id LIMIT 1")
    suspend fun getVariantSync(id: String): PelmenVariantEntity?

    @Query("SELECT COUNT(*) FROM pelmen_variants")
    fun getUnlockedCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(variant: PelmenVariantEntity)

    @Query("UPDATE pelmen_variants SET timesOpened = timesOpened + 1, unlockedAt = :timestamp WHERE variantId = :id")
    suspend fun incrementTimesOpened(id: String, timestamp: Long)

    @Query("UPDATE pelmen_variants SET isEquipped = :equipped WHERE variantId = :id")
    suspend fun setEquipped(id: String, equipped: Boolean)

    @Query("UPDATE pelmen_variants SET isEquipped = 0")
    suspend fun unequipAll()

    @Query("UPDATE pelmen_variants SET isFavorite = NOT isFavorite WHERE variantId = :id")
    suspend fun toggleFavorite(id: String)

    @Query("UPDATE pelmen_variants SET squishCount = squishCount + 1 WHERE variantId = :id")
    suspend fun incrementSquishCount(id: String)
}
