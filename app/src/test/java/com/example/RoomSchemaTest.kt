package com.example

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Защита от потери прогресса: версия БД в @Database и файл схемы в app/schemas должны совпадать.
 * Поднял `version` — сначала выпусти `Migration` и закоммить новый N.json (его кладёт сборка в app/schemas).
 */
class RoomSchemaTest {
    private fun module(path: String): File =
        listOf(path, "app/$path").map { File(it) }.first { it.exists() }

    @Test
    fun schemaFileExistsForCurrentDatabaseVersion() {
        val src = module("src/main/java/com/example/data/local/AppDatabase.kt").readText()
        val version = Regex("""version\s*=\s*(\d+)""").find(src)!!.groupValues[1]
        val schema = File(module("schemas"), "com.example.data.local.AppDatabase/$version.json")
        assertTrue("Нет схемы Room для версии $version: ${schema.path}", schema.exists())
    }
}
