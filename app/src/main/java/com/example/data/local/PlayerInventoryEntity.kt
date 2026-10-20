package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.coroutines.flow.Flow
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * Entity representing an item in the player's local inventory for offline cache.
 */
@Entity(tableName = "player_inventory")
data class PlayerInventoryEntity(
    @PrimaryKey
    val itemId: String,                  // E.g. "booster_speed_01", "broth_seasoning_07", "box_tier_legendary"
    val category: String,               // "BOOSTER", "BOX", "INGREDIENT", "RELIC"
    val name: String,                   // Display Name
    val quantity: Int = 1,
    val description: String = "",
    val tier: Int = 1,
    val isConsumable: Boolean = true,
    val acquiredAt: Long = System.currentTimeMillis()
)

@Dao
interface PlayerInventoryDao {

    @Query("SELECT * FROM player_inventory ORDER BY acquiredAt DESC")
    fun getAllInventory(): Flow<List<PlayerInventoryEntity>>

    @Query("SELECT * FROM player_inventory WHERE category = :category ORDER BY acquiredAt DESC")
    fun getInventoryByCategory(category: String): Flow<List<PlayerInventoryEntity>>

    @Query("SELECT * FROM player_inventory WHERE itemId = :id LIMIT 1")
    suspend fun getItemById(id: String): PlayerInventoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(item: PlayerInventoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<PlayerInventoryEntity>)

    @Query("UPDATE player_inventory SET quantity = quantity + :delta WHERE itemId = :id")
    suspend fun updateQuantity(id: String, delta: Int)

    @Query("DELETE FROM player_inventory WHERE itemId = :id")
    suspend fun deleteItem(id: String)

    @Query("DELETE FROM player_inventory WHERE quantity <= 0")
    suspend fun cleanupDepletedItems()
}
