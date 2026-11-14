package com.example.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface EntitlementDao {
    @Upsert
    suspend fun upsert(e: EntitlementEntity)

    @Query("SELECT * FROM entitlements WHERE licenseId = :licenseId")
    suspend fun byLicenseId(licenseId: String): EntitlementEntity?

    @Query("SELECT productId FROM entitlements WHERE active = 1 AND kind = 'durable'")
    fun activeProductIds(): Flow<List<String>>

    @Query("SELECT * FROM entitlements WHERE active = 1 AND kind = 'durable'")
    fun activeDurableFlow(): Flow<List<EntitlementEntity>>

    @Query("SELECT * FROM entitlements WHERE active = 1 AND kind = 'durable'")
    suspend fun activeDurable(): List<EntitlementEntity>

    @Query("UPDATE entitlements SET active = 0, updatedAtMillis = :now WHERE licenseId = :licenseId")
    suspend fun deactivate(licenseId: String, now: Long)
}
