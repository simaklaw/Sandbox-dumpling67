package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Кэш прав на купленное для работы офлайн. Источник истины — backend: право подтверждено подписанным токеном
 * (проверка подписи в `monetization/`), а эта таблица лишь хранит результат. `resetProgress` её не трогает.
 */
@Entity(
    tableName = "entitlements",
    indices = [Index("productId"), Index("active")]
)
data class EntitlementEntity(
    @PrimaryKey val licenseId: String,   // ключ идемпотентности: повторная активация не создаёт вторую строку
    val productId: String,
    val kind: String,                    // "durable" | "consumable"
    val issuedAtMillis: Long,
    val active: Boolean,                 // false после отзыва (возврат)
    val source: String,                  // "direct"
    val token: String,                   // подписанный entitlement, для повторной проверки при чтении
    val updatedAtMillis: Long
)
