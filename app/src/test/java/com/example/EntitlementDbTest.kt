package com.example

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.local.AppDatabase
import com.example.data.local.EntitlementEntity
import com.example.data.local.MIGRATION_5_6
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EntitlementDbTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    private fun ent(id: String, product: String = "full_access", active: Boolean = true) = EntitlementEntity(
        licenseId = id, productId = product, kind = "durable", issuedAtMillis = 1L,
        active = active, source = "direct", token = "t-$id", updatedAtMillis = 1L
    )

    @Test
    fun migrate5To6_keepsOldData_andCreatesEntitlements() {
        val name = "migration-test"
        helper.createDatabase(name, 5).apply {
            execSQL(
                "INSERT INTO runner_stickers (id, title, description, emoji, biome, requiredDistance, isUnlocked, unlockedTimestamp) " +
                    "VALUES ('s1', 'Звезда', 'd', '⭐', 'kitchen', 10, 1, 5)"
            )
            close()
        }
        val db = helper.runMigrationsAndValidate(name, 6, true, MIGRATION_5_6)
        db.query("SELECT title FROM runner_stickers WHERE id = 's1'").use {
            assertTrue(it.moveToFirst())
            assertEquals("Звезда", it.getString(0))
        }
        db.query("SELECT COUNT(*) FROM entitlements").use {
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
