package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        RunnerHighScoreEntity::class,
        BlindBoxRewardEntity::class,
        PelmenVariantEntity::class,
        GameSaveEntity::class,
        PlayerInventoryEntity::class,
        UnlockedSkinEntity::class,
        StickerEntity::class,
        EntitlementEntity::class
    ],
    version = 6,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun runnerHighScoreDao(): RunnerHighScoreDao
    abstract fun blindBoxRewardDao(): BlindBoxRewardDao
    abstract fun pelmenVariantDao(): PelmenVariantDao
    abstract fun gameSaveDao(): GameSaveDao
    abstract fun playerInventoryDao(): PlayerInventoryDao
    abstract fun unlockedSkinDao(): UnlockedSkinDao
    abstract fun stickerDao(): StickerDao
    abstract fun entitlementDao(): EntitlementDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pelmen_runner.db"
                )
                    // Явная миграция 5→6 имеет приоритет: текущие пользователи данные не теряют.
                    // fallback срабатывает только для цепочек короче 5 (пред-релизные сборки).
                    // Перед публичным релизом fallback убрать и написать полную цепочку миграций.
                    .addMigrations(MIGRATION_5_6)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
