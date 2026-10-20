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
        StickerEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun runnerHighScoreDao(): RunnerHighScoreDao
    abstract fun blindBoxRewardDao(): BlindBoxRewardDao
    abstract fun pelmenVariantDao(): PelmenVariantDao
    abstract fun gameSaveDao(): GameSaveDao
    abstract fun playerInventoryDao(): PlayerInventoryDao
    abstract fun unlockedSkinDao(): UnlockedSkinDao
    abstract fun stickerDao(): StickerDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pelmen_runner.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
