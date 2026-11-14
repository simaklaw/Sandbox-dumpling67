package com.example.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** 5 → 6: таблица прав на покупки. Существующие таблицы не меняются, прогресс сохраняется. */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `entitlements` (" +
                "`licenseId` TEXT NOT NULL, " +
                "`productId` TEXT NOT NULL, " +
                "`kind` TEXT NOT NULL, " +
                "`issuedAtMillis` INTEGER NOT NULL, " +
                "`active` INTEGER NOT NULL, " +
                "`source` TEXT NOT NULL, " +
                "`token` TEXT NOT NULL, " +
                "`updatedAtMillis` INTEGER NOT NULL, " +
                "PRIMARY KEY(`licenseId`))"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_entitlements_productId` ON `entitlements` (`productId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_entitlements_active` ON `entitlements` (`active`)")
    }
}
