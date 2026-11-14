package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.EntitlementEntity
import com.example.data.local.MIGRATION_5_6
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject
import java.io.File
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EntitlementDbTest {
    private fun ent(id: String, product: String = "full_access", active: Boolean = true) = EntitlementEntity(
        licenseId = id, productId = product, kind = "durable", issuedAtMillis = 1L,
        active = active, source = "direct", token = "t-$id", updatedAtMillis = 1L
    )

    /**
     * Строим БД версии 5 по замороженной схеме (5.json: SQL таблиц и хеш), кладём строку, затем открываем
     * её через Room версии 6 с MIGRATION_5_6: Room сам сверяет получившиеся таблицы со схемой и падает при расхождении.
     */
    @Test
    fun migrate5To6_keepsOldData_andCreatesEntitlements() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-test.db"
        ctx.deleteDatabase(name)
        val schemaFile = listOf("schemas", "app/schemas").map { File(it) }.first { it.exists() }
        val json = JSONObject(File(schemaFile, "com.example.data.local.AppDatabase/5.json").readText()).getJSONObject("database")
        val old = ctx.openOrCreateDatabase(name, Context.MODE_PRIVATE, null)
        val entities = json.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val e = entities.getJSONObject(i)
            old.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", e.getString("tableName")))
        }
        val setup = json.getJSONArray("setupQueries")
        for (i in 0 until setup.length()) old.execSQL(setup.getString(i))
        old.execSQL(
            "INSERT INTO runner_stickers (id, title, description, emoji, biome, requiredDistance, isUnlocked, unlockedTimestamp) " +
                "VALUES ('s1', 'Звезда', 'd', '⭐', 'kitchen', 10, 1, 5)"
        )
        old.version = 5
        old.close()

        val db = Room.databaseBuilder(ctx, AppDatabase::class.java, name)
            .addMigrations(MIGRATION_5_6).allowMainThreadQueries().build()
        val sticker = db.openHelper.readableDatabase
        sticker.query("SELECT title FROM runner_stickers WHERE id = 's1'").use {
            assertTrue(it.moveToFirst())
            assertEquals("Звезда", it.getString(0))
        }
        sticker.query("SELECT COUNT(*) FROM entitlements").use {
            assertTrue(it.moveToFirst())
            assertEquals(0, it.getInt(0))
        }
        db.close()
    }

    @Test
    fun upsertIsIdempotent_andDeactivateRevokes() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        val dao = db.entitlementDao()

        dao.upsert(ent("L1"))
        dao.upsert(ent("L1")) // повторная активация того же ключа
        assertEquals(1, dao.activeDurable().size)
        assertEquals(listOf("full_access"), dao.activeProductIds().first())

        dao.upsert(ent("L2", product = "winter_pack"))
        assertEquals(setOf("full_access", "winter_pack"), dao.activeProductIds().first().toSet())

        dao.deactivate("L1", now = 9L) // возврат
        assertEquals(listOf("winter_pack"), dao.activeProductIds().first())
        assertNotNull(dao.byLicenseId("L1"))
        assertEquals(false, dao.byLicenseId("L1")!!.active)
        db.close()
    }
}
